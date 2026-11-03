package br.com.ziipsan.intelligence.gis.catalog.application;

import br.com.ziipsan.core.tenant.TenantContextHolder;

import java.util.UUID;

/**
 * Tenant e usuário da requisição para o catálogo GIS, no mesmo desenho do
 * RegistryContext do cadastro (o tenant vem SEMPRE do contexto, a partir do
 * token — nunca do corpo nem da URL). O módulo GIS não depende do cadastro,
 * por isso o ajudante vive aqui, e não importado de lá.
 */
final class GisCatalogContext {

    private GisCatalogContext() {
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
