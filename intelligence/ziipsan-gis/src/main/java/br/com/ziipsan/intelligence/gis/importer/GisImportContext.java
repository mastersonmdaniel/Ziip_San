package br.com.ziipsan.intelligence.gis.importer;

import br.com.ziipsan.core.tenant.TenantContextHolder;

import java.util.UUID;

/**
 * Tenant e usuário da requisição para a importação GIS, no mesmo desenho
 * do {@code GisChangeSetContext} da GIS-04 (o tenant vem SEMPRE do
 * contexto, a partir do token — nunca do corpo nem da URL).
 */
final class GisImportContext {

    private GisImportContext() {
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
