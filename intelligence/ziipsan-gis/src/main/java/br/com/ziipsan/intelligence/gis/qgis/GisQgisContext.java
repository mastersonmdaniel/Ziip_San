package br.com.ziipsan.intelligence.gis.qgis;

import br.com.ziipsan.core.tenant.TenantContextHolder;

import java.util.UUID;

/**
 * Tenant/usuario da requisicao da api (nunca da conexao QGIS direta),
 * no mesmo desenho do GisImportContext da GIS-07 e do
 * GisChangeSetContext da GIS-04.
 */
final class GisQgisContext {

    private GisQgisContext() {
    }

    static UUID tenantId() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto da requisicao");
        }
        return tenantId;
    }

    static UUID userId() {
        UUID userId = TenantContextHolder.getUserId();
        if (userId == null) {
            throw new IllegalStateException("sem usuario no contexto da requisicao");
        }
        return userId;
    }
}
