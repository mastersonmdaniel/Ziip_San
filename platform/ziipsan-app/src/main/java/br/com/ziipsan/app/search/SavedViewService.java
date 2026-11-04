package br.com.ziipsan.app.search;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.search.SavedView;
import br.com.ziipsan.platform.search.SavedViewRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Visões salvas de filtros e colunas por usuário (P1.33, tabela {@code saved_views} da V1_17).
 *
 * <p>Regras de dono, sem exceção: a listagem devolve só as visões do usuário autenticado;
 * ler, alterar ou excluir a visão de outro dono dá 404 (nunca 403 — a resposta não revela
 * que a visão existe). O compartilhamento por unidade organizacional (P3.30) é só
 * armazenamento nesta frente: uma visão {@code ORG_UNIT} continua listada só ao próprio
 * dono até a P3.30 existir e definir o escopo por área — sem ela, "compartilhada" para
 * terceiros seria fingir um controle que não existe.
 *
 * <p>Validação do JSON: {@code filters} tem de ser um objeto JSON e {@code columns} um
 * array JSON; {@code sort}, quando presente, um objeto JSON. O conteúdo dos filtros NÃO é
 * interpretado aqui (cada lista valida os próprios filtros ao aplicar); o que se valida
 * é a forma, para uma visão corrompida não quebrar a lista depois.
 */
@Service
public class SavedViewService {

    public static final Set<String> RESOURCES =
            Set.of("customers", "consumer-units", "service-orders", "bills", "documents", "global");
    public static final Set<String> DENSITIES = Set.of("comfortable", "compact");
    public static final Set<String> VISIBILITIES = Set.of("PRIVATE", "ORG_UNIT");

    public record SavedViewInput(
            @NotBlank(message = "Informe o título da visão.") @Size(max = 200, message = "O título aceita no máximo 200 caracteres.") String title,
            @NotBlank(message = "Informe o recurso da visão.") String resource,
            String filters,
            String columns,
            String sort,
            String density,
            String visibility,
            UUID sharedOrgUnitId,
            Boolean isDefault) {
    }

    public record SavedViewView(UUID id, UUID ownerUserId, String title, String resource, String filters,
                                String columns, String sort, String density, String visibility,
                                UUID sharedOrgUnitId, boolean isDefault) {
    }

    private final SavedViewRepository views;

    public SavedViewService(SavedViewRepository views) {
        this.views = views;
    }

    @Transactional(readOnly = true)
    public List<SavedViewView> list() {
        UUID tenantId = requireTenant();
        UUID owner = requireUser();
        return views.findAllByTenantIdAndOwnerUserIdOrderByTitleAsc(tenantId, owner).stream()
                .map(SavedViewService::toView).toList();
    }

    @Transactional(readOnly = true)
    public SavedViewView get(UUID id) {
        return toView(requireOwned(id));
    }

    @Transactional
    public SavedViewView create(SavedViewInput input) {
        UUID tenantId = requireTenant();
        UUID owner = requireUser();
        validate(input, null, tenantId, owner);
        SavedView view = new SavedView();
        view.setTenantId(tenantId);
        view.setTenantSchema(TenantContextHolder.getTenant());
        view.setOwnerUserId(owner);
        apply(view, input);
        view.setCreatedBy(owner);
        view.setUpdatedBy(owner);
        SavedView saved = views.save(view);
        if (Boolean.TRUE.equals(input.isDefault())) {
            unsetOtherDefaults(tenantId, owner, saved);
        }
        return toView(saved);
    }

    @Transactional
    public SavedViewView update(UUID id, SavedViewInput input) {
        UUID tenantId = requireTenant();
        UUID owner = requireUser();
        SavedView view = requireOwned(id);
        validate(input, id, tenantId, owner);
        apply(view, input);
        view.setUpdatedBy(owner);
        SavedView saved = views.save(view);
        if (Boolean.TRUE.equals(input.isDefault())) {
            unsetOtherDefaults(tenantId, owner, saved);
        }
        return toView(saved);
    }

    @Transactional
    public void delete(UUID id) {
        views.delete(requireOwned(id));
    }

    private SavedView requireOwned(UUID id) {
        UUID tenantId = requireTenant();
        UUID owner = requireUser();
        return views.findByIdAndTenantId(id, tenantId)
                .filter(v -> owner.equals(v.getOwnerUserId()))
                .orElseThrow(() -> new ApiException.NotFound("Visão salva não encontrada."));
    }

    private void validate(SavedViewInput input, UUID selfId, UUID tenantId, UUID owner) {
        if (input.title() == null || input.title().isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError("title", "REQUIRED",
                    "Informe o título da visão."));
        }
        if (input.resource() == null || !RESOURCES.contains(input.resource())) {
            throw ApiException.Validation.of(new ApiException.FieldError("resource", "INVALID",
                    "Recurso desconhecido: " + input.resource() + "."));
        }
        String density = input.density() == null ? "comfortable" : input.density();
        if (!DENSITIES.contains(density)) {
            throw ApiException.Validation.of(new ApiException.FieldError("density", "INVALID",
                    "Densidade aceita comfortable ou compact."));
        }
        String visibility = input.visibility() == null ? "PRIVATE" : input.visibility();
        if (!VISIBILITIES.contains(visibility)) {
            throw ApiException.Validation.of(new ApiException.FieldError("visibility", "INVALID",
                    "Visibilidade aceita PRIVATE ou ORG_UNIT."));
        }
        if ("ORG_UNIT".equals(visibility) && input.sharedOrgUnitId() == null) {
            throw ApiException.Validation.of(new ApiException.FieldError("sharedOrgUnitId", "REQUIRED",
                    "Visão compartilhada exige a unidade organizacional."));
        }
        if (input.filters() != null) {
            try {
                if (!Json.readTree(input.filters()).isObject()) {
                    throw ApiException.Validation.of(new ApiException.FieldError("filters", "INVALID",
                            "Filtros têm de ser um objeto JSON."));
                }
            } catch (IllegalStateException e) {
                throw ApiException.Validation.of(new ApiException.FieldError("filters", "INVALID",
                        "Filtros têm de ser um objeto JSON válido."));
            }
        }
        if (input.columns() != null) {
            try {
                if (!Json.readTree(input.columns()).isArray()) {
                    throw ApiException.Validation.of(new ApiException.FieldError("columns", "INVALID",
                            "Colunas têm de ser um array JSON."));
                }
            } catch (IllegalStateException e) {
                throw ApiException.Validation.of(new ApiException.FieldError("columns", "INVALID",
                        "Colunas têm de ser um array JSON válido."));
            }
        }
        if (input.sort() != null) {
            try {
                if (!Json.readTree(input.sort()).isObject()) {
                    throw ApiException.Validation.of(new ApiException.FieldError("sort", "INVALID",
                            "Ordenação tem de ser um objeto JSON."));
                }
            } catch (IllegalStateException e) {
                throw ApiException.Validation.of(new ApiException.FieldError("sort", "INVALID",
                        "Ordenação tem de ser um objeto JSON válido."));
            }
        }
        String title = input.title() == null ? "" : input.title().trim();
        views.findByTenantIdAndOwnerUserIdAndTitle(tenantId, owner, title)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new ApiException.Conflict("saved-view-title-in-use",
                            "Já existe uma visão salva com este título.");
                });
    }

    private void apply(SavedView view, SavedViewInput input) {
        view.setTitle(input.title().trim());
        view.setResource(input.resource());
        view.setFilters(input.filters() == null ? "{}" : input.filters());
        view.setColumns(input.columns() == null ? "[]" : input.columns());
        view.setSort(input.sort());
        view.setDensity(input.density() == null ? "comfortable" : input.density());
        String visibility = input.visibility() == null ? "PRIVATE" : input.visibility();
        view.setVisibility(visibility);
        // PRIVATE nunca carrega unidade: sem compartilhamento, sem resíduo de um ORG_UNIT anterior.
        view.setSharedOrgUnitId("ORG_UNIT".equals(visibility) ? input.sharedOrgUnitId() : null);
        view.setDefault(Boolean.TRUE.equals(input.isDefault()));
    }

    private void unsetOtherDefaults(UUID tenantId, UUID owner, SavedView kept) {
        for (SavedView other : views.findAllByTenantIdAndOwnerUserIdAndResourceAndIsDefaultTrue(
                tenantId, owner, kept.getResource())) {
            if (!other.getId().equals(kept.getId())) {
                other.setDefault(false);
                views.save(other);
            }
        }
    }

    private static SavedViewView toView(SavedView view) {
        return new SavedViewView(view.getId(), view.getOwnerUserId(), view.getTitle(), view.getResource(),
                view.getFilters(), view.getColumns(), view.getSort(), view.getDensity(), view.getVisibility(),
                view.getSharedOrgUnitId(), view.isDefault());
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto");
        }
        return tenantId;
    }

    private static UUID requireUser() {
        UUID userId = TenantContextHolder.getUserId();
        if (userId == null) {
            throw new ApiException.BadRequest("user-required",
                    "A visão salva exige um usuário autenticado com identificador.");
        }
        return userId;
    }
}
