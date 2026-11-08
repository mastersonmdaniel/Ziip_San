package br.com.ziipsan.intelligence.gis.webedit;

import br.com.ziipsan.core.tenant.TenantContextHolder;

import java.util.UUID;

/**
 * Tenant/usuário da requisição, no mesmo desenho do
 * GisChangeSetContext (GIS-04) e do GisQgisContext (ED-55).
 * PENDÊNCIA 12 (herdada da ED-55): como o endpoint de banco
 * define o GUC a partir do mapeamento de papéis é decisão de
 * integração/dono — esta entrega NÃO a resolve; usa o contexto
 * da requisição da api, como os vizinhos.
 */
final class GisWebEditContext {

    private GisWebEditContext() {
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
