package br.com.ziipsan.intelligence.gis.changeset;

import br.com.ziipsan.core.tenant.TenantContextHolder;

import java.util.UUID;

/**
 * Tenant e usuário da requisição para os pacotes de edição GIS, no mesmo
 * desenho do {@code GisCatalogContext} do catálogo (o tenant vem SEMPRE
 * do contexto, a partir do token — nunca do corpo nem da URL).
 */
final class GisChangeSetContext {

    private GisChangeSetContext() {
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
