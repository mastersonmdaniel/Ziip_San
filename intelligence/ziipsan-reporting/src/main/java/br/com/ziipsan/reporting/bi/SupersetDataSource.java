package br.com.ziipsan.reporting.bi;

import java.util.Objects;
import java.util.UUID;

/**
 * ED-47: uma fonte de dados (database) registrada no Superset.
 *
 * <p>O desenho é UM database por tenant, conectado com a role de banco
 * daquele tenant: é o que torna a "role por tenant" efetiva no Superset
 * (que não tem contexto de tenant próprio). Fonte compartilhada com role
 * genérica não é representável neste tipo de propósito — o campo
 * {@code tenantId} é obrigatório e a guarda confere a role contra ele.
 *
 * <p>Credencial NÃO mora aqui: a URI guardada é a de referência
 * (esquema, host, porta e banco), sem usuário nem senha; a senha da role
 * fica na configuração cifrada do Superset, alimentada pelo cofre na
 * fiação. URI com credencial embutida é recusada na construção.
 */
public record SupersetDataSource(
        String name,
        UUID tenantId,
        String roleName,
        Endpoint endpoint,
        Kind kind) {

    /** Endpoint de um banco PostgreSQL (host + porta + banco). */
    public record Endpoint(String host, int port, String database) {
        public Endpoint {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Host da fonte de dados é obrigatório");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Porta inválida: " + port);
            }
            if (database == null || database.isBlank()) {
                throw new IllegalArgumentException("Banco da fonte de dados é obrigatório");
            }
        }

        /** Identidade comparável do endpoint (host minúsculo, sem espaços). */
        public String key() {
            return host.trim().toLowerCase(java.util.Locale.ROOT) + ":" + port + "/" + database.trim();
        }
    }

    public enum Kind { READ_REPLICA, PRIMARY, OTHER }

    public SupersetDataSource {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome da fonte de dados é obrigatório");
        }
        Objects.requireNonNull(tenantId, "tenantId é obrigatório: fonte de BI é sempre de um tenant");
        Objects.requireNonNull(endpoint, "endpoint é obrigatório");
        Objects.requireNonNull(kind, "kind é obrigatório");
        if (!TenantBiRole.roleNameFor(tenantId).equals(roleName)) {
            throw new IllegalArgumentException(
                    "A fonte deve conectar com a role do próprio tenant (" + TenantBiRole.roleNameFor(tenantId) + ")");
        }
    }

    /**
     * Monta a fonte a partir da URI de referência do Superset
     * ({@code postgresql://host:porta/banco}). URI com usuário/senha,
     * com outro esquema ou sem host é recusada — credencial em URI já
     * vazou em log demais neste setor para ser aceita por silêncio.
     */
    public static SupersetDataSource fromReferenceUri(String name, UUID tenantId, String uri, Kind kind) {
        if (uri == null || !uri.startsWith("postgresql://")) {
            throw new IllegalArgumentException("URI da fonte deve ser postgresql://host:porta/banco, sem credenciais");
        }
        String rest = uri.substring("postgresql://".length());
        if (rest.contains("@")) {
            throw new IllegalArgumentException("URI da fonte não pode embutir credenciais");
        }
        int slash = rest.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("URI da fonte sem o banco: " + uri);
        }
        String hostPort = rest.substring(0, slash);
        String database = rest.substring(slash + 1);
        int colon = hostPort.lastIndexOf(':');
        String host = colon < 0 ? hostPort : hostPort.substring(0, colon);
        int port = colon < 0 ? 5432 : Integer.parseInt(hostPort.substring(colon + 1));
        return new SupersetDataSource(name, tenantId, TenantBiRole.roleNameFor(tenantId),
                new Endpoint(host, port, database), kind);
    }
}
