package br.com.ziipsan.commercial.registry.importer;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * P1.40: persistência dos jobs de importação, no mesmo padrão JDBC do motor
 * de OS (ServiceOrderService): conexão própria por transação, GUC
 * {@code app.current_tenant} fixada na transação (a RLS forçada exige), e
 * filtro explícito por {@code tenant_id} em toda query.
 *
 * <p>A área de preparação guarda o texto cru da linha ({@code raw_json}); o
 * parse final para a tabela de destino é do aplicador do esquema, na
 * aplicação. A mensagem de recusa nunca ecoa o valor recusado.
 */
public final class ImportStore {

    public enum JobStatus {
        RECEIVED, VALIDATING, VALIDATED, APPLYING, APPLIED, REVERSED, FAILED
    }

    public enum RowStatus {
        PENDING, ACCEPTED, REJECTED
    }

    public record JobView(UUID id, String schemaCode, String originalFilename, String sha256,
                          List<String> header, JobStatus status, int totalRows, int acceptedCount,
                          int rejectedCount, int appliedCount, UUID batchOrderId, UUID reversalOrderId,
                          String errorMessage, OffsetDateTime createdAt, UUID createdBy) {
    }

    public record StagedRow(UUID id, int number, Map<String, String> values, RowStatus status,
                            boolean applied, String appliedTargetTable, UUID appliedTargetId,
                            String appliedTargetKey) {
    }

    public record RejectionRow(int rowNumber, String field, String reasonCode, String message) {
    }

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;

    public ImportStore(DataSource dataSource, String schema, UUID tenantId) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("dataSource, schema e tenantId sao obrigatorios");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
    }

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws Exception;
    }

    private <T> T inTx(Tx<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery().close();
            }
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                throw new IllegalStateException("Importacao falhou: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Importacao falhou: " + e.getMessage(), e);
        }
    }

    private String t(String table) {
        return schema + "." + table;
    }

    // ------------------------------------------------------------------ jobs

    public JobView createJob(String schemaCode, String originalFilename, String sha256,
                             List<String> header, UUID actorId) {
        return inTx(connection -> {
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("import_jobs")
                            + " (id, tenant_id, schema_code, original_filename, sha256, header_json,"
                            + " created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setString(3, schemaCode);
                ps.setString(4, originalFilename);
                ps.setString(5, sha256);
                ps.setString(6, Json.write(header));
                setUuid(ps, 7, actorId);
                setUuid(ps, 8, actorId);
                ps.executeUpdate();
            }
            return requireJob(connection, id);
        });
    }

    public Optional<JobView> findBySchemaAndSha(String schemaCode, String sha256) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM " + t("import_jobs")
                            + " WHERE tenant_id = ? AND schema_code = ? AND sha256 = ?")) {
                ps.setObject(1, tenantId);
                ps.setString(2, schemaCode);
                ps.setString(3, sha256);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readJob(rs)) : Optional.empty();
                }
            }
        });
    }

    public JobView requireJob(UUID jobId) {
        return inTx(connection -> requireJob(connection, jobId));
    }

    private JobView requireJob(Connection connection, UUID jobId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM " + t("import_jobs") + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("Importacao nao encontrada.");
                }
                return readJob(rs);
            }
        }
    }

    public List<JobView> listJobs(int limit) {
        return inTx(connection -> {
            List<JobView> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM " + t("import_jobs") + " WHERE tenant_id = ?"
                            + " ORDER BY created_at DESC, id DESC LIMIT ?")) {
                ps.setObject(1, tenantId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readJob(rs));
                    }
                }
            }
            return List.copyOf(out);
        });
    }

    public JobView setStatus(UUID jobId, JobStatus status, String errorMessage, UUID actorId) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("import_jobs")
                            + " SET status = ?, error_message = ?, updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, status.name());
                setText(ps, 2, errorMessage);
                setUuid(ps, 3, actorId);
                ps.setObject(4, tenantId);
                ps.setObject(5, jobId);
                ps.executeUpdate();
            }
            return requireJob(connection, jobId);
        });
    }

    public JobView setBatchOrder(UUID jobId, UUID orderId, UUID actorId) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("import_jobs")
                            + " SET batch_order_id = ?, updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                setUuid(ps, 1, orderId);
                setUuid(ps, 2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, jobId);
                ps.executeUpdate();
            }
            return requireJob(connection, jobId);
        });
    }

    public JobView setReversalOrder(UUID jobId, UUID orderId, UUID actorId) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("import_jobs")
                            + " SET reversal_order_id = ?, status = 'REVERSED', error_message = NULL,"
                            + " updated_by = ?, updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                setUuid(ps, 1, orderId);
                setUuid(ps, 2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, jobId);
                ps.executeUpdate();
            }
            return requireJob(connection, jobId);
        });
    }

    public JobView setApplied(UUID jobId, int appliedCount, UUID actorId) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("import_jobs")
                            + " SET status = 'APPLIED', applied_count = ?, error_message = NULL,"
                            + " updated_by = ?, updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                ps.setInt(1, appliedCount);
                setUuid(ps, 2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, jobId);
                ps.executeUpdate();
            }
            return requireJob(connection, jobId);
        });
    }

    /** Lote da OS pelo código (retomada: o lote pode existir sem o job apontar para ele). */
    public Optional<UUID> findBatchIdByCode(String code) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id FROM " + t("service_order_batches") + " WHERE tenant_id = ? AND code = ?")) {
                ps.setObject(1, tenantId);
                ps.setString(2, code);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of((UUID) rs.getObject(1)) : Optional.empty();
                }
            }
        });
    }

    // ------------------------------------------------------------------ linhas

    public void insertRows(UUID jobId, List<ImportCsvParser.ParsedRow> rows, UUID actorId) {
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("import_rows")
                            + " (id, tenant_id, import_id, row_number, raw_json, created_by, updated_by)"
                            + " VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)")) {
                for (ImportCsvParser.ParsedRow row : rows) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantId);
                    ps.setObject(3, jobId);
                    ps.setInt(4, row.number());
                    ps.setString(5, Json.write(row.values()));
                    setUuid(ps, 6, actorId);
                    setUuid(ps, 7, actorId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("import_jobs")
                            + " SET total_rows = ?, updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setInt(1, rows.size());
                setUuid(ps, 2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, jobId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public List<StagedRow> listRows(UUID jobId) {
        return inTx(connection -> {
            List<StagedRow> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM " + t("import_rows")
                            + " WHERE tenant_id = ? AND import_id = ? ORDER BY row_number")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, jobId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readRow(rs));
                    }
                }
            }
            return List.copyOf(out);
        });
    }

    /** Validação salva de forma atômica: veredictos, recusas e totais do job. */
    public JobView saveValidation(UUID jobId, ImportValidator.ValidationResult result, UUID actorId) {
        return inTx(connection -> {
            // Revalidação após falha: limpa o veredicto anterior (a validação é
            // reproduzível a partir da área de preparação).
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + t("import_rejections") + " WHERE tenant_id = ? AND import_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, jobId);
                ps.executeUpdate();
            }
            Map<Integer, Boolean> acceptedByNumber = new LinkedHashMap<>();
            for (StagedRow row : listRows(connection, jobId)) {
                acceptedByNumber.put(row.number(), false);
            }
            for (ImportCsvParser.ParsedRow row : result.accepted()) {
                acceptedByNumber.put(row.number(), true);
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("import_rows")
                            + " SET status = ?, updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND import_id = ? AND row_number = ?")) {
                for (Map.Entry<Integer, Boolean> entry : acceptedByNumber.entrySet()) {
                    ps.setString(1, entry.getValue() ? "ACCEPTED" : "REJECTED");
                    setUuid(ps, 2, actorId);
                    ps.setObject(3, tenantId);
                    ps.setObject(4, jobId);
                    ps.setInt(5, entry.getKey());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("import_rejections")
                            + " (id, tenant_id, import_id, row_number, field, reason_code, message)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (ImportValidator.Rejection rejection : result.rejections()) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantId);
                    ps.setObject(3, jobId);
                    ps.setInt(4, rejection.rowNumber());
                    ps.setString(5, rejection.field());
                    ps.setString(6, rejection.reasonCode());
                    ps.setString(7, rejection.message());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            int accepted = result.accepted().size();
            int rejected = acceptedByNumber.size() - accepted;
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("import_jobs")
                            + " SET status = 'VALIDATED', accepted_count = ?, rejected_count = ?,"
                            + " error_message = NULL, updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setInt(1, accepted);
                ps.setInt(2, rejected);
                setUuid(ps, 3, actorId);
                ps.setObject(4, tenantId);
                ps.setObject(5, jobId);
                ps.executeUpdate();
            }
            return requireJob(connection, jobId);
        });
    }

    /** Arquivo inteiro recusado (ex.: cabeçalho diferente do modelo): linha 0, campo "*". */
    public JobView saveFileRejection(UUID jobId, int totalRows, String reasonCode, String message, UUID actorId) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM " + t("import_rejections") + " WHERE tenant_id = ? AND import_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, jobId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("import_rows")
                            + " SET status = 'REJECTED', updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND import_id = ?")) {
                setUuid(ps, 1, actorId);
                ps.setObject(2, tenantId);
                ps.setObject(3, jobId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("import_rejections")
                            + " (id, tenant_id, import_id, row_number, field, reason_code, message)"
                            + " VALUES (?, ?, ?, 0, '*', ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, jobId);
                ps.setString(4, reasonCode);
                ps.setString(5, message);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("import_jobs")
                            + " SET status = 'VALIDATED', accepted_count = 0, rejected_count = ?,"
                            + " error_message = NULL, updated_by = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setInt(1, totalRows);
                setUuid(ps, 2, actorId);
                ps.setObject(3, tenantId);
                ps.setObject(4, jobId);
                ps.executeUpdate();
            }
            return requireJob(connection, jobId);
        });
    }

    public void markRowApplied(UUID rowId, ImportSchema.ImportApplier.Applied applied, UUID actorId) {
        inTx(connection -> {
            markRowApplied(connection, rowId, applied, actorId);
            return null;
        });
    }

    /** Marca a linha aplicada na mesma transação do aplicador (aplicar + marcar é atômico). */
    public void markRowApplied(Connection connection, UUID rowId,
                               ImportSchema.ImportApplier.Applied applied, UUID actorId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE " + t("import_rows")
                        + " SET applied = TRUE, applied_at = NOW(), applied_target_table = ?,"
                        + " applied_target_id = ?, applied_target_key = ?, updated_by = ?, updated_at = NOW()"
                        + " WHERE tenant_id = ? AND id = ?")) {
            ps.setString(1, applied.targetTable());
            ps.setObject(2, applied.targetId());
            ps.setString(3, applied.targetKey());
            setUuid(ps, 4, actorId);
            ps.setObject(5, tenantId);
            ps.setObject(6, rowId);
            ps.executeUpdate();
        }
    }

    public void markRowReversed(UUID rowId, UUID actorId) {
        inTx(connection -> {
            markRowReversed(connection, rowId, actorId);
            return null;
        });
    }

    /** Marca a linha revertida na mesma transação do inverso. */
    public void markRowReversed(Connection connection, UUID rowId, UUID actorId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE " + t("import_rows")
                        + " SET applied = FALSE, applied_at = NULL, updated_by = ?, updated_at = NOW()"
                        + " WHERE tenant_id = ? AND id = ?")) {
            setUuid(ps, 1, actorId);
            ps.setObject(2, tenantId);
            ps.setObject(3, rowId);
            ps.executeUpdate();
        }
    }

    public List<RejectionRow> listRejections(UUID jobId) {
        return inTx(connection -> {
            List<RejectionRow> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT row_number, field, reason_code, message FROM " + t("import_rejections")
                            + " WHERE tenant_id = ? AND import_id = ? ORDER BY row_number, field, reason_code")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, jobId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new RejectionRow(rs.getInt(1), rs.getString(2),
                                rs.getString(3), rs.getString(4)));
                    }
                }
            }
            return List.copyOf(out);
        });
    }

    // ------------------------------------------------------------ checagens DB

    /** FK genérica: a tabela/coluna vêm do esquema declarado em código, nunca do arquivo. */
    public boolean foreignKeyExists(String table, String column, String value) {
        if (!table.matches("[a-z][a-z0-9_]*") || !column.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("referencia de chave estrangeira invalida");
        }
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM " + schema + "." + table + " WHERE tenant_id = ? AND " + column + " = ?")) {
                ps.setObject(1, tenantId);
                ps.setString(2, value);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    /** Aplica uma linha com o aplicador do esquema, na mesma transação do chamador. */
    public ImportSchema.ImportApplier.Applied applyRow(Connection connection, ImportSchema schemaDef,
                                                       UUID actorId, Map<String, String> values)
            throws SQLException {
        return schemaDef.applier().apply(connection, schema, tenantId, actorId, values);
    }

    /** Conexão transacional com GUC do tenant, para aplicar/inverter linhas no serviço. */
    public Connection openTenantConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.executeQuery().close();
        }
        return connection;
    }

    // ------------------------------------------------------------------ leitura

    private List<StagedRow> listRows(Connection connection, UUID jobId) throws SQLException {
        List<StagedRow> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM " + t("import_rows")
                        + " WHERE tenant_id = ? AND import_id = ? ORDER BY row_number")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readRow(rs));
                }
            }
        }
        return out;
    }

    private JobView readJob(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        List<String> header = new ArrayList<>();
        var headerNode = Json.readTree(rs.getString("header_json"));
        if (headerNode.isArray()) {
            headerNode.forEach(item -> header.add(item.asText()));
        }
        return new JobView(
                (UUID) rs.getObject("id"), rs.getString("schema_code"), rs.getString("original_filename"),
                rs.getString("sha256"), List.copyOf(header), JobStatus.valueOf(rs.getString("status")),
                rs.getInt("total_rows"), rs.getInt("accepted_count"), rs.getInt("rejected_count"),
                rs.getInt("applied_count"), (UUID) rs.getObject("batch_order_id"),
                (UUID) rs.getObject("reversal_order_id"), rs.getString("error_message"),
                created == null ? null : created.toInstant().atOffset(ZoneOffset.UTC),
                (UUID) rs.getObject("created_by"));
    }

    private StagedRow readRow(ResultSet rs) throws SQLException {
        Map<String, Object> raw = Json.readMap(rs.getString("raw_json"));
        Map<String, String> values = new LinkedHashMap<>();
        raw.forEach((key, value) -> values.put(key, value == null ? "" : String.valueOf(value)));
        return new StagedRow(
                (UUID) rs.getObject("id"), rs.getInt("row_number"), Map.copyOf(values),
                RowStatus.valueOf(rs.getString("status")), rs.getBoolean("applied"),
                rs.getString("applied_target_table"), (UUID) rs.getObject("applied_target_id"),
                rs.getString("applied_target_key"));
    }

    private static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setText(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}
