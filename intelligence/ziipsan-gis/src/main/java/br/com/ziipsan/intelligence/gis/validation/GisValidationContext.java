package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.core.tenant.TenantContextHolder;

import java.util.UUID;

/**
 * Tenant e usuário da requisição para o painel de qualidade, no mesmo
 * desenho do {@code GisCatalogContext} do catálogo (o tenant vem SEMPRE
 * do contexto, a partir do token — nunca do corpo nem da URL). Vive aqui
 * porque aquele ajudante é privado do pacote do catálogo.
 */
final class GisValidationContext {

    private GisValidationContext() {
    }

    static UUID tenantId() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto da requisição");
        }
        return tenantId;
    }

    static UUID userId() {
        UUID userId = TenantContextHolder.getUserId();
        if (userId == null) {
            throw new IllegalStateException("sem usuário no contexto da requisição");
        }
        return userId;
    }
}
