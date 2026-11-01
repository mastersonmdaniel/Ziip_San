package br.com.ziipsan.platform.tenant;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de tenant que o Hibernate carrega por sessão: o schema (para o search_path) e o
 * UUID (para a GUC app.current_tenant, que as policies de RLS consultam).
 *
 * <p>Os dois andam juntos de propósito. Antes o Hibernate recebia só o nome do schema, e nada
 * definia app.current_tenant: com RLS FORCE, toda leitura via JPA voltaria vazia e toda gravação
 * seria barrada (achado C2 do PDCA de 12/10/2026).
 *
 * <p>{@link #NONE} é a ausência de tenant: a conexão fica sem contexto, a RLS devolve zero linhas
 * e barra INSERT. Nunca é um "tenant raiz" que enxerga todos.
 */
public record TenantRef(String schema, UUID tenantId) {

    public static final TenantRef NONE = new TenantRef("public", null);

    public TenantRef {
        Objects.requireNonNull(schema, "schema");
    }

    public static TenantRef of(String schema, UUID tenantId) {
        return new TenantRef(Objects.requireNonNull(schema, "schema"), Objects.requireNonNull(tenantId, "tenantId"));
    }

    public boolean isNone() {
        return tenantId == null;
    }
}
