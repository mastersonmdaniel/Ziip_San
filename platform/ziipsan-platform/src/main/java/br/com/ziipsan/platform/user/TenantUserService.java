package br.com.ziipsan.platform.user;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.security.auth.PermissionCatalog;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gestão de usuários do tenant pela API própria (P0.13).
 *
 * <p>O {@code tenantId} vem sempre do contexto da requisição (token), nunca do corpo nem da
 * URL: a API não aceita campo de tenant em nenhum DTO. Um id que existe em outro tenant é
 * 404, nunca 403 — a resposta não pode revelar que o identificador existe em outro lugar.
 * Quem concede um perfil só concede o que possui: as permissões do perfil concedido precisam
 * estar entre as permissões do próprio ator.
 *
 * <p>Alterações nas tabelas {@code users} e {@code user_profiles} caem na trilha de auditoria
 * pelo trigger da V1_2, com autoria da GUC {@code app.current_user}.
 *
 * <p>P1.37: os perfis atribuíveis são todos os de fábrica do catálogo
 * ({@code GET /api/v1/profiles}), exceto os desligados no nascimento (auditor externo e
 * regulador, cuja liberação é da ED-75). A V1_18 (integração P1-C) alargou o CHECK da
 * V1_9 para os 26 códigos, então a gravação de um perfil novo no banco funciona;
 * antes dela, só os quatro mínimos passavam no CHECK.
 */
@Service
@Transactional
public class TenantUserService {

    /** Visão pública de um usuário do tenant (sem nenhum segredo: não há credencial aqui). */
    public record UserView(UUID id, String keycloakSub, String displayName, String email, String status,
                           List<String> profiles) {
    }

    public record CreateRequest(String keycloakSub, String displayName, String email, List<String> profiles) {
    }

    public record UpdateRequest(String displayName, String email, String status, List<String> profiles) {
    }

    private final TenantUserRepository users;
    private final UserProfileRepository profiles;

    public TenantUserService(TenantUserRepository users, UserProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        UUID tenantId = tenantId();
        return users.findAllByTenantId(tenantId).stream().map(u -> toView(u, tenantId)).toList();
    }

    @Transactional(readOnly = true)
    public UserView get(UUID id) {
        return toView(require(id), tenantId());
    }

    /** Versão otimista atual do usuário, para o ETag do GET e o {@code If-Match} do PUT (P1.11). */
    @Transactional(readOnly = true)
    public long versionOf(UUID id) {
        Long version = require(id).getVersion();
        return version == null ? 0L : version;
    }

    public UserView create(CreateRequest req) {
        UUID tenantId = tenantId();
        List<String> granted = validatedProfiles(req.profiles());
        checkGrantable(granted);
        if (users.findByKeycloakSubAndTenantId(req.keycloakSub(), tenantId).isPresent()) {
            throw new ApiException.Conflict("data-conflict",
                    "Já existe um usuário deste tenant vinculado a esse identificador do Keycloak.");
        }
        TenantUser user = new TenantUser();
        user.setTenantId(tenantId);
        user.setTenantSchema(TenantContextHolder.getTenant());
        user.setCreatedBy(TenantContextHolder.getUserId());
        user.setUpdatedBy(TenantContextHolder.getUserId());
        user.setKeycloakSub(req.keycloakSub());
        user.setDisplayName(req.displayName());
        user.setEmail(req.email());
        user.setStatus(TenantUser.UserStatus.ACTIVE);
        users.save(user);
        assignProfiles(user, tenantId, granted);
        return toView(user, tenantId);
    }

    public UserView update(UUID id, UpdateRequest req) {
        UUID tenantId = tenantId();
        TenantUser user = require(id);
        List<String> granted = validatedProfiles(req.profiles());
        checkGrantable(granted);
        user.setDisplayName(req.displayName());
        user.setEmail(req.email());
        try {
            user.setStatus(TenantUser.UserStatus.valueOf(req.status()));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.Validation.of(new ApiException.FieldError("status", "invalid",
                    "Status inválido: use ACTIVE, SUSPENDED ou DEACTIVATED."));
        }
        user.setUpdatedBy(TenantContextHolder.getUserId());
        users.save(user);
        profiles.deleteByUserIdAndTenantId(user.getId(), tenantId);
        assignProfiles(user, tenantId, granted);
        return toView(user, tenantId);
    }

    private TenantUser require(UUID id) {
        UUID tenantId = tenantId();
        return users.findByIdAndTenantId(id, tenantId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new ApiException.NotFound("Usuário não encontrado neste tenant."));
    }

    private UserView toView(TenantUser user, UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.now();
        List<String> active = profiles.findAllByUserIdAndTenantId(user.getId(), tenantId).stream()
                .filter(a -> !a.getValidFrom().isAfter(now)
                        && (a.getValidUntil() == null || a.getValidUntil().isAfter(now)))
                .map(UserProfileAssignment::getProfile)
                .sorted()
                .toList();
        return new UserView(user.getId(), user.getKeycloakSub(), user.getDisplayName(), user.getEmail(),
                user.getStatus().name(), active);
    }

    private void assignProfiles(TenantUser user, UUID tenantId, List<String> granted) {
        OffsetDateTime now = OffsetDateTime.now();
        for (String profile : granted) {
            UserProfileAssignment assignment = new UserProfileAssignment();
            assignment.setTenantId(tenantId);
            assignment.setTenantSchema(TenantContextHolder.getTenant());
            assignment.setCreatedBy(TenantContextHolder.getUserId());
            assignment.setUpdatedBy(TenantContextHolder.getUserId());
            assignment.setUserId(user.getId());
            assignment.setProfile(profile);
            assignment.setValidFrom(now);
            assignment.setValidUntil(null);
            profiles.save(assignment);
        }
    }

    private List<String> validatedProfiles(List<String> requested) {
        if (requested == null) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String name : requested) {
            PermissionCatalog.FactoryProfile profile;
            try {
                profile = PermissionCatalog.FactoryProfile.valueOf(name);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw ApiException.Validation.of(new ApiException.FieldError("profiles", "invalid",
                        "Perfil desconhecido: " + name + ". Veja os perfis de fábrica em GET /api/v1/profiles."));
            }
            // P1.37: auditor externo e regulador nascem desligados; a liberação é da ED-75.
            if (!profile.enabled()) {
                throw ApiException.Validation.of(new ApiException.FieldError("profiles", "disabled",
                        "Perfil desligado: " + name + ". A liberação de auditor externo e regulador é da ED-75."));
            }
            distinct.add(name);
        }
        return List.copyOf(distinct);
    }

    /** O ator só concede perfis cujas permissões ele próprio possui. */
    private void checkGrantable(List<String> granted) {
        Set<String> acting = SecurityContextHolder.getContext().getAuthentication() == null
                ? Set.of()
                : SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .filter(a -> !a.startsWith("ROLE_"))
                        .collect(Collectors.toSet());
        for (String profile : granted) {
            Set<String> needed = PermissionCatalog.FactoryProfile.valueOf(profile).permissions();
            if (!acting.containsAll(needed)) {
                throw new AccessDeniedException(
                        "Não é possível conceder o perfil " + profile + ": o ator não possui todas as suas permissões.");
            }
        }
    }

    private UUID tenantId() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto da requisição");
        }
        return tenantId;
    }
}
