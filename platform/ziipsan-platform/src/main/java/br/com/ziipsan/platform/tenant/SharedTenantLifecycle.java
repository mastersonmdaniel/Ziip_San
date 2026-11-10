package br.com.ziipsan.platform.tenant;

import br.com.ziipsan.platform.infra.crypto.TenantKeyStore;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * P4.6, item 6 do aceite: no SCHEMA COMPARTILHADO, exportacao, restauracao e
 * eliminacao de UM tenant sao operacoes por {@code tenant_id} — nao ha schema
 * para despejar nem para apagar (ADR-0005, consequencias negativas assumidas).
 *
 * <p>Regras:
 * <ul>
 *   <li>Toda a operacao e filtrada por {@code tenant_id = ?} com o id como
 *       PARAMETRO; identificadores (schema, tabela, coluna) nunca vem de
 *       entrada livre — sao validados contra o padrao de nome do projeto e so
 *       entao interpolados, porque identificador nao pode ser parametro JDBC.</li>
 *   <li>A eliminacao segue a ordem: (1) exportar (o manifesto de contagens e o
 *       conteudo saem ANTES de apagar — apagar sem exportar destroi a unica
 *       copia; o conteudo exportado VIAJA no {@link ErasureReport} para o
 *       chamador o poder arquivar — correcção R-P46-04: antes o erase
 *       exportava internamente e descartava o conteudo, devolvendo só
 *       contagens, e a "exportação antes de apagar" não produzia artefacto
 *       recuperável); (2) apagar as linhas por {@code tenant_id} — se uma
 *       tabela falhar a meio, as linhas já apagadas são REPOSTAS a partir
 *       da exportação (compensação) e a eliminação falha em voz alta, com a
 *       chave intacta; (3) crypto-shredding
 *       da chave do tenant ({@link TenantKeyStore#destroyAll}, P0.28 — apaga o
 *       material cifrado de todas as versoes em {@code public.tenant_key});
 *       (4) confirmar a destruicao ({@link TenantKeyStore#destroyed}); sem a
 *       confirmacao, a eliminacao FALHA em voz alta em vez de se declarar
 *       feita: linhas apagadas com a chave viva deixariam copias e backups do
 *       tenant decifraveis para sempre.</li>
 *   <li>A restauracao reinsere, por {@code tenant_id}, as linhas de uma
 *       exportacao anterior neste mesmo schema compartilhado. Restaurar um
 *       tenant cuja chave ja foi destruida e recusado: os campos cifrados
 *       dessa exportacao nunca mais decifrariam, e fingir a restauracao
 *       esconderia a perda.</li>
 *   <li>Esta classe so opera sobre as tabelas que o CHAMADOR declarar como
 *       tabelas de tenant (com coluna {@code tenant_id}). Ela nao descobre
 *       tabelas sozinha: varrer o schema apagaria tambem tabelas globais.</li>
 * </ul>
 *
 * <p>O acesso as linhas esta atras de {@link TenantRowStore} para a orquestracao
 * ser testavel sem banco; a implementacao de producao e
 * {@link JdbcTenantRowStore} (JDBC via {@link JdbcTemplate}).
 */
public final class SharedTenantLifecycle {

    /** Nome de schema/tabela/coluna aceito (mesmo padrao do MultiTenantConnectionProvider). */
    static final Pattern IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    /** Acesso as linhas de tenant de um schema, sempre por tenant_id. */
    public interface TenantRowStore {

        /** Linhas do tenant numa tabela (a exportacao). */
        List<Map<String, Object>> exportByTenant(String schema, String table, UUID tenantId);

        /** Apaga as linhas do tenant numa tabela; devolve quantas apagou. */
        int deleteByTenant(String schema, String table, UUID tenantId);

        /** Insere uma linha exportada de volta (a restauracao); a linha traz o tenant_id dela. */
        void insertRow(String schema, String table, Map<String, Object> row);
    }

    /**
     * Resultado de uma eliminacao: o que foi exportado, o que foi apagado e a chave destruida.
     *
     * <p>{@code exportedContent} é o CONTEÚDO integral da exportação feita
     * antes de apagar (tabela → linhas), para o chamador o arquivar como
     * artefacto recuperável da eliminação (correcção R-P46-04);
     * {@code exportedRows} mantém as contagens derivadas dele.
     */
    public record ErasureReport(UUID tenantId, String schema,
                                Map<String, Integer> exportedRows,
                                Map<String, Integer> deletedRows,
                                boolean keyDestroyed,
                                Map<String, List<Map<String, Object>>> exportedContent) {
    }

    private final TenantRowStore rows;
    private final TenantKeyStore keyStore;

    public SharedTenantLifecycle(TenantRowStore rows, TenantKeyStore keyStore) {
        this.rows = Objects.requireNonNull(rows, "TenantRowStore e obrigatorio");
        this.keyStore = Objects.requireNonNull(keyStore, "TenantKeyStore e obrigatorio");
    }

    /** Exportacao de um tenant do schema compartilhado: tabela -> linhas (so as dele). */
    public Map<String, List<Map<String, Object>>> exportTenant(
            String schema, List<String> tenantTables, UUID tenantId) {
        validateScope(schema, tenantTables, tenantId);
        Map<String, List<Map<String, Object>>> export = new LinkedHashMap<>();
        for (String table : tenantTables) {
            export.put(table, List.copyOf(rows.exportByTenant(schema, table, tenantId)));
        }
        return export;
    }

    /** Restauracao de uma exportacao neste schema; devolve linhas inseridas por tabela. */
    public Map<String, Integer> restoreTenant(
            String schema, Map<String, List<Map<String, Object>>> export, UUID tenantId) {
        Objects.requireNonNull(export, "exportacao a restaurar e obrigatoria");
        validateScope(schema, List.copyOf(export.keySet()), tenantId);
        if (keyStore.destroyed(tenantId)) {
            throw new IllegalStateException(
                    "Tenant " + tenantId + " ja teve a chave destruida (crypto-shredding): "
                            + "restaurar a exportacao traria campos cifrados indecifraveis. Restauracao recusada.");
        }
        // Correcção R-P46-04: validação de TODAS as linhas de TODAS as
        // tabelas ANTES de inserir a primeira. A forma anterior validava e
        // inseria intercalado: uma linha alheia na 2.ª tabela abortava com
        // as linhas da 1.ª já inseridas — restauração parcial que o
        // javadoc prometia não existir ("abortada antes de misturar").
        for (Map.Entry<String, List<Map<String, Object>>> entry : export.entrySet()) {
            for (Map<String, Object> row : entry.getValue()) {
                Object rowTenant = row.get("tenant_id");
                if (rowTenant == null || !tenantId.toString().equals(rowTenant.toString())) {
                    throw new IllegalArgumentException(
                            "Linha da tabela " + entry.getKey() + " nao pertence ao tenant " + tenantId
                                    + " (tenant_id=" + rowTenant + "); restauracao abortada antes de misturar tenants.");
                }
            }
        }
        Map<String, Integer> restored = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : export.entrySet()) {
            int count = 0;
            for (Map<String, Object> row : entry.getValue()) {
                rows.insertRow(schema, entry.getKey(), row);
                count++;
            }
            restored.put(entry.getKey(), count);
        }
        return restored;
    }

    /**
     * Eliminacao de um tenant do schema compartilhado: exporta, apaga por
     * tenant_id e destroi a chave (crypto-shredding), confirmando a destruicao.
     */
    public ErasureReport eraseTenant(String schema, List<String> tenantTables, UUID tenantId) {
        Map<String, List<Map<String, Object>>> export = exportTenant(schema, tenantTables, tenantId);
        Map<String, Integer> exported = new LinkedHashMap<>();
        export.forEach((table, lines) -> exported.put(table, lines.size()));

        // Correcção R-P46-04: a fase de DELETE corre com compensação. Sem
        // transacção multi-tabela nesta abstracção, uma falha na 2.ª tabela
        // deixava a 1.ª já apagada e a exportação perdida com a excepção.
        // Agora: as tabelas já apagadas são repostas da exportação, a
        // chave NÃO é destruída, e a falha sobe com a causa — a eliminação
        // ou conclui inteira ou não deixa rasto parcial silencioso.
        Map<String, Integer> deleted = new LinkedHashMap<>();
        List<String> deletedTables = new java.util.ArrayList<>();
        try {
            for (String table : tenantTables) {
                deleted.put(table, rows.deleteByTenant(schema, table, tenantId));
                deletedTables.add(table);
            }
        } catch (RuntimeException deleteFailure) {
            for (String table : deletedTables) {
                for (Map<String, Object> row : export.getOrDefault(table, List.of())) {
                    rows.insertRow(schema, table, row);
                }
            }
            throw new IllegalStateException(
                    "Eliminacao do tenant " + tenantId + " falhou a meio e foi compensada: "
                            + "as linhas ja apagadas foram repostas da exportacao e a chave "
                            + "NAO foi destruida. Causa: " + deleteFailure.getMessage(),
                    deleteFailure);
        }

        keyStore.destroyAll(tenantId);
        if (!keyStore.destroyed(tenantId)) {
            throw new IllegalStateException(
                    "Crypto-shredding nao confirmado para o tenant " + tenantId
                            + ": as linhas foram apagadas do schema " + schema
                            + ", mas a chave continua viva e copias do tenant seguem decifraveis. "
                            + "Intervencao manual necessaria (destruir a chave em public.tenant_key).");
        }
        return new ErasureReport(tenantId, schema, Map.copyOf(exported), Map.copyOf(deleted), true,
                Map.copyOf(export));
    }

    private static void validateScope(String schema, List<String> tenantTables, UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        if (schema == null || !IDENTIFIER.matcher(schema).matches() || "public".equals(schema)) {
            throw new IllegalArgumentException("Schema invalido para operacao por tenant_id: " + schema);
        }
        if (tenantTables == null || tenantTables.isEmpty()) {
            throw new IllegalArgumentException("A lista de tabelas de tenant nao pode ser vazia");
        }
        for (String table : tenantTables) {
            if (table == null || !IDENTIFIER.matcher(table).matches()) {
                throw new IllegalArgumentException("Nome de tabela invalido: " + table);
            }
        }
        if (tenantTables.size() != tenantTables.stream().distinct().count()) {
            throw new IllegalArgumentException("Lista de tabelas com duplicadas: " + tenantTables);
        }
    }

    /**
     * Implementacao JDBC do {@link TenantRowStore}: tenant_id sempre como parametro.
     *
     * <p>Correcção da execução do IT (correção P4.6, 10/11/2026): as
     * tabelas de tenant têm RLS FORCE — valem até para o dono — e a policy
     * é {@code tenant_id = current_tenant_id()}. Sem a GUC
     * {@code app.current_tenant} definida NA MESMA transacção, a leitura
     * devolvia zero linhas e o DELETE apagava zero, em silêncio: o
     * lifecycle "funcionava" nos testes unitários (duplos sem RLS) e era
     * um no-op em banco real. Cada operação corre agora numa transacção
     * própria sobre uma conexão dedicada, com
     * {@code set_config('app.current_tenant', ?, true)} antes da operação
     * — o mesmo padrão transaccional do provisionamento.
     */
    public static final class JdbcTenantRowStore implements TenantRowStore {

        private final DataSource dataSource;

        public JdbcTenantRowStore(DataSource dataSource) {
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource e obrigatorio");
        }

        @Override
        public List<Map<String, Object>> exportByTenant(String schema, String table, UUID tenantId) {
            requireIdentifier(schema);
            requireIdentifier(table);
            return inTenantTransaction(tenantId, conn -> {
                try (var ps = conn.prepareStatement(
                        "SELECT * FROM " + schema + "." + table + " WHERE tenant_id = ?")) {
                    ps.setObject(1, tenantId);
                    List<Map<String, Object>> out = new java.util.ArrayList<>();
                    try (var rs = ps.executeQuery()) {
                        var meta = rs.getMetaData();
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= meta.getColumnCount(); i++) {
                                row.put(meta.getColumnLabel(i), rs.getObject(i));
                            }
                            out.add(row);
                        }
                    }
                    return out;
                }
            });
        }

        @Override
        public int deleteByTenant(String schema, String table, UUID tenantId) {
            requireIdentifier(schema);
            requireIdentifier(table);
            return inTenantTransaction(tenantId, conn -> {
                try (var ps = conn.prepareStatement(
                        "DELETE FROM " + schema + "." + table + " WHERE tenant_id = ?")) {
                    ps.setObject(1, tenantId);
                    return ps.executeUpdate();
                }
            });
        }

        @Override
        public void insertRow(String schema, String table, Map<String, Object> row) {
            requireIdentifier(schema);
            requireIdentifier(table);
            if (row == null || row.isEmpty()) {
                throw new IllegalArgumentException("Linha vazia nao pode ser inserida em " + table);
            }
            Object tenantValue = row.get("tenant_id");
            if (tenantValue == null) {
                throw new IllegalArgumentException("Linha sem tenant_id nao pode ser inserida em " + table);
            }
            UUID tenantId = tenantValue instanceof UUID u ? u : UUID.fromString(tenantValue.toString());
            List<String> columns = List.copyOf(row.keySet());
            for (String column : columns) {
                requireIdentifier(column);
            }
            String sql = "INSERT INTO " + schema + "." + table
                    + " (" + String.join(", ", columns) + ") VALUES ("
                    + String.join(", ", columns.stream().map(c -> "?").toList()) + ")";
            inTenantTransaction(tenantId, conn -> {
                try (var ps = conn.prepareStatement(sql)) {
                    for (int i = 0; i < columns.size(); i++) {
                        ps.setObject(i + 1, row.get(columns.get(i)));
                    }
                    ps.executeUpdate();
                    return null;
                }
            });
        }

        /** Trabalho JDBC que pode falhar com SQLException (embrulhada por {@link #inTenantTransaction}). */
        private interface TenantWork<T> {
            T run(java.sql.Connection conn) throws java.sql.SQLException;
        }

        /**
         * Corre o trabalho numa transacção com a GUC do tenant definida
         * (set_config local à transacção): é o que torna a operação visível
         * à RLS FORCE em vez de um no-op silencioso.
         */
        private <T> T inTenantTransaction(UUID tenantId, TenantWork<T> work) {
            try (var conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    try (var guc = conn.prepareStatement(
                            "SELECT set_config('app.current_tenant', ?, true)")) {
                        guc.setString(1, tenantId.toString());
                        guc.execute();
                    }
                    T result = work.run(conn);
                    conn.commit();
                    return result;
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("Falha JDBC na operacao por tenant_id", e);
            }
        }

        private static void requireIdentifier(String identifier) {
            if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
                throw new IllegalArgumentException("Identificador invalido: " + identifier);
            }
        }
    }
}
