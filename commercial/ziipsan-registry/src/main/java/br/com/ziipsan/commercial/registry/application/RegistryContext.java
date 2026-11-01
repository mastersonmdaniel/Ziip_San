package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.domain.BaseEntity;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import br.com.ziipsan.core.tenant.TenantContextHolder;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Tenant e usuário da requisição, carimbo de autoria e checagem de versão, num lugar só.
 *
 * <p>O tenant vem SEMPRE do contexto (JwtTenantFilter, a partir do token), nunca do corpo nem da URL.
 * A RLS já esconde as linhas de outro tenant; a checagem explícita do tenant_id na leitura é uma
 * segunda barreira, barata, contra um erro de contexto.
 */
public final class RegistryContext {

    private RegistryContext() {
    }

    public static UUID tenantId() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto da requisição");
        }
        return tenantId;
    }

    /** Preenche tenant e autoria de uma entidade nova. */
    public static <E extends TenantAwareEntity> E forCreate(E entity) {
        entity.setTenantId(tenantId());
        entity.setTenantSchema(TenantContextHolder.getTenant());
        entity.setCreatedBy(TenantContextHolder.getUserId());
        entity.setUpdatedBy(TenantContextHolder.getUserId());
        return entity;
    }

    public static void touch(BaseEntity entity) {
        entity.setUpdatedBy(TenantContextHolder.getUserId());
    }

    /**
     * Registro do tenant da requisição ou 404. Registro de outro tenant também é 404 (nunca 403): a
     * resposta não pode revelar que o identificador existe em outro lugar.
     */
    public static <E extends TenantAwareEntity> E require(Optional<E> found, Supplier<String> notFoundMessage) {
        UUID tenantId = tenantId();
        return found.filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new ApiException.NotFound(notFoundMessage.get()));
    }

    /**
     * Controle otimista explícito: o PUT traz a versão que o usuário viu. Versão diferente é 409 sem
     * errors/warnings, que o frontend trata como "o registro mudou". A checagem do Hibernate no flush
     * continua valendo para a corrida entre duas gravações simultâneas.
     */
    public static void checkVersion(BaseEntity entity, Long version) {
        if (version == null) {
            throw ApiException.Validation.of(new ApiException.FieldError("version", "REQUIRED",
                    "Informe a versão do registro (campo version)."));
        }
        if (!version.equals(entity.getVersion())) {
            throw new ApiException.Conflict("stale-version",
                    "O registro foi alterado por outra pessoa. Recarregue e tente de novo.");
        }
    }
}
