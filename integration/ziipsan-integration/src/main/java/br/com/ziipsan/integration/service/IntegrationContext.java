package br.com.ziipsan.integration.service;

import br.com.ziipsan.core.tenant.TenantContextHolder;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Contexto do tenant da requisição, no mesmo padrão do
 * {@code GlobalSearchService} (P1.33): o schema vem do contexto resolvido
 * pelo filtro de autenticação, nunca do corpo ou do caminho, e é validado
 * por regex antes de qualquer interpolação em SQL. O filtro por
 * {@code tenant_id} é aplicado em toda consulta, além da RLS forçada.
 */
public final class IntegrationContext {

    private static final Pattern SCHEMA = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private IntegrationContext() {
    }

    public static UUID tenantId() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("A requisição não identifica o tenant.");
        }
        return tenantId;
    }

    public static String schema() {
        String schema = TenantContextHolder.getTenant();
        if (schema == null || !SCHEMA.matcher(schema).matches()) {
            throw new IllegalStateException("Schema do tenant inválido no contexto.");
        }
        return schema;
    }
}
