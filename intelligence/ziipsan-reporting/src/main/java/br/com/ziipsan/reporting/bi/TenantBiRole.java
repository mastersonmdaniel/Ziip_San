package br.com.ziipsan.reporting.bi;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ED-47 (ADR-0027): role de banco do BI por tenant e o mapeamento
 * role -&gt; tenant.
 *
 * <p>A forma da exceção do Superset no ADR-0010 fixa: "uma role de banco
 * por tenant com restrição pela identidade da conexão". Identidade da
 * conexão é QUEM abriu a sessão (a role de LOGIN), não uma variável que
 * a sessão possa trocar: por isso o nome da role é derivado de forma
 * determinística e injetiva do {@code tenantId} (UUID sem hífens) e o
 * mapeamento {@link TenantBiRole} é a fonte que a função de contexto do
 * BI consulta por {@code current_user} (ver
 * {@code BiRoleProvisioningSql}). Um {@code SET app.current_tenant} feito
 * pelo usuário do Superset NÃO muda {@code current_user} — é esse o
 * ponto do teste de isolamento do aceite.
 *
 * <p>Modos de isolamento (ADR-0005, citados no aceite): schema próprio
 * (DEDICATED_SCHEMA) e schema compartilhado (SHARED_SCHEMA). Nos dois, a
 * role só enxerga a camada de views do BI; a diferença é onde as views
 * de origem ({@code rpt_*} da P3.22) vivem.
 */
public record TenantBiRole(
        UUID tenantId,
        String roleName,
        IsolationMode isolationMode,
        String sourceSchema) {

    public enum IsolationMode { DEDICATED_SCHEMA, SHARED_SCHEMA }

    /** Schema é identificador SQL: só minúsculas, dígitos e sublinhado. */
    private static final Pattern SCHEMA_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    public TenantBiRole {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório");
        Objects.requireNonNull(isolationMode, "isolationMode é obrigatório");
        if (!roleNameFor(tenantId).equals(roleName)) {
            throw new IllegalArgumentException(
                    "roleName não é a role do tenant: esperado " + roleNameFor(tenantId));
        }
        if (sourceSchema == null || !SCHEMA_PATTERN.matcher(sourceSchema).matches()) {
            throw new IllegalArgumentException("sourceSchema inválido: " + sourceSchema);
        }
    }

    public static TenantBiRole of(UUID tenantId, IsolationMode mode, String sourceSchema) {
        return new TenantBiRole(tenantId, roleNameFor(tenantId), mode, sourceSchema);
    }

    /**
     * Nome da role do tenant: {@code bi_} + UUID sem hífens (35 caracteres,
     * identificador PostgreSQL válido, único por tenant por construção).
     * Nenhum nome de tenant, pessoa ou empresa entra no nome da role.
     */
    public static String roleNameFor(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório");
        return "bi_" + tenantId.toString().replace("-", "");
    }

    /** Schema da camada de views do BI deste tenant (onde o Superset lê). */
    public String accessSchema() {
        return "bi_" + tenantId.toString().replace("-", "");
    }
}
