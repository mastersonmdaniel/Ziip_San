package br.com.ziipsan.commercial.registry.legacy;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

/**
 * Store JDBC da V14_1 (P1.2).
 *
 * <p>Segue o mesmo contrato de transação do motor da P1.31: cada método
 * abre a sua conexão, fixa {@code app.current_tenant} (a policy RLS
 * FORÇADA de {@code register_tenant_table} exige o contexto) e confirma
 * ou desfaz. As tabelas são qualificadas pelo schema — nunca se depende
 * de search_path. As issues vão para JSONB via {@link Json} (o mesmo
 * cuidado da V1_1: nunca {@code Map.toString()} numa coluna jsonb).
 *
 * <p>Mensagens de issue nunca carregam valores brutos (ver
 * {@link LegacyIssue}); a serialização aqui só as transporta.
 */
public final class JdbcLegacyStagingStore implements LegacyStagingStore {

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;

    public JdbcLegacyStagingStore(DataSource dataSource, String schema, UUID tenantId) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("dataSource, schema e tenantId são obrigatórios.");
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
                throw new IllegalStateException("Falha no staging do legado: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha no staging do legado: " + e.getMessage(), e);
        }
    }

    private String t(String table) {
        return schema + "." + table;
    }

    // ------------------------------------------------------------------
    // Lotes
    // ------------------------------------------------------------------

    @Override
    public LegacyBatch createBatch(String sourceSystem, String mappingName, String mappingVersion,
                                   String externalBatchKey, String sourceKind, UUID createdBy) {
        return inTx(connection -> {
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("legacy_import_batches")
                            + " (id, tenant_id, source_system, mapping_name, mapping_version,"
                            + " external_batch_key, source_kind, status, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, 'STAGED', ?)")) {
                int i = 0;
                ps.setObject(++i, id);
                ps.setObject(++i, tenantId);
                ps.setString(++i, sourceSystem);
                ps.setString(++i, mappingName);
                ps.setString(++i, mappingVersion);
                ps.setString(++i, externalBatchKey);
                ps.setString(++i, sourceKind);
                setUuid(ps, ++i, createdBy);
                ps.executeUpdate();
            }
            return new LegacyBatch(id, sourceSystem, mappingName, mappingVersion,
                    externalBatchKey, sourceKind, LegacyBatch.Status.STAGED, null);
        });
    }

    @Override
    public Optional<LegacyBatch> findBatchByKey(String sourceSystem, String externalBatchKey) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM " + t("legacy_import_batches")
                            + " WHERE tenant_id = ? AND source_system = ? AND external_batch_key = ?")) {
                ps.setObject(1, tenantId);
                ps.setString(2, sourceSystem);
                ps.setString(3, externalBatchKey);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readBatch(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<LegacyBatch> findBatchById(UUID batchId) {
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM " + t("legacy_import_batches")
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, batchId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readBatch(rs)) : Optional.empty();
                }
            }
        });
    }

    private LegacyBatch readBatch(ResultSet rs) throws SQLException {
        return new LegacyBatch(
                (UUID) rs.getObject("id"),
                rs.getString("source_system"),
                rs.getString("mapping_name"),
                rs.getString("mapping_version"),
                rs.getString("external_batch_key"),
                rs.getString("source_kind"),
                LegacyBatch.Status.valueOf(rs.getString("status")),
                (UUID) rs.getObject("batch_order_id"));
    }

    @Override
    public void updateBatchOrder(UUID batchId, UUID orderId) {
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("legacy_import_batches")
                            + " SET batch_order_id = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, orderId);
                ps.setObject(2, tenantId);
                ps.setObject(3, batchId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void markBatchApplied(UUID batchId) {
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("legacy_import_batches")
                            + " SET status = 'APPLIED', updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, batchId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void deleteBatch(UUID batchId) {
        inTx(connection -> {
            for (String sql : List.of(
                    "DELETE FROM " + t("legacy_import_reconciliation") + " WHERE tenant_id = ? AND batch_id = ?",
                    "DELETE FROM " + t("legacy_import_lineage") + " WHERE tenant_id = ? AND batch_id = ?",
                    "DELETE FROM " + t("legacy_import_staging_installments") + " WHERE tenant_id = ? AND batch_id = ?",
                    "DELETE FROM " + t("legacy_import_staging_units") + " WHERE tenant_id = ? AND batch_id = ?",
                    "DELETE FROM " + t("legacy_import_batches") + " WHERE tenant_id = ? AND id = ?")) {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, batchId);
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Unidades
    // ------------------------------------------------------------------

    @Override
    public void insertUnits(UUID batchId, List<StagedUnit> units) {
        if (units.isEmpty()) {
            return;
        }
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("legacy_import_staging_units")
                            + " (id, tenant_id, batch_id, source_row_no, legacy_key, matricula, matricula_dv,"
                            + " locality_code, sector_code, block_code, route_code, category_code,"
                            + " lot, sublot, reading_sequence, balance_cents, connected_on,"
                            + " holder_name, holder_document_kind, holder_document_masked, holder_document_valid,"
                            + " status, applied_unit_id, issues)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)")) {
                for (StagedUnit unit : units) {
                    int i = 0;
                    ps.setObject(++i, unit.id());
                    ps.setObject(++i, tenantId);
                    ps.setObject(++i, batchId);
                    ps.setInt(++i, unit.sourceRowNo());
                    ps.setString(++i, unit.legacyKey());
                    setText(ps, ++i, unit.matricula());
                    setText(ps, ++i, unit.matriculaDv());
                    setText(ps, ++i, unit.localityCode());
                    setText(ps, ++i, unit.sectorCode());
                    setText(ps, ++i, unit.blockCode());
                    setText(ps, ++i, unit.routeCode());
                    setText(ps, ++i, unit.categoryCode());
                    setLong(ps, ++i, unit.lot());
                    setLong(ps, ++i, unit.sublot());
                    setLong(ps, ++i, unit.readingSequence());
                    setLong(ps, ++i, unit.balanceCents());
                    setDate(ps, ++i, unit.connectedOn());
                    setText(ps, ++i, unit.holderName());
                    setText(ps, ++i, unit.holderDocumentKind());
                    setText(ps, ++i, unit.holderDocumentMasked());
                    setBoolean(ps, ++i, unit.holderDocumentValid());
                    ps.setString(++i, unit.status().name());
                    setUuid(ps, ++i, unit.appliedUnitId());
                    ps.setString(++i, issuesJson(unit.issues()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    @Override
    public List<StagedUnit> listUnits(UUID batchId) {
        return inTx(connection -> {
            List<StagedUnit> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM " + t("legacy_import_staging_units")
                            + " WHERE tenant_id = ? AND batch_id = ? ORDER BY source_row_no, id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, batchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readUnit(rs));
                    }
                }
            }
            return List.copyOf(out);
        });
    }

    private StagedUnit readUnit(ResultSet rs) throws SQLException {
        return new StagedUnit(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("batch_id"),
                rs.getInt("source_row_no"),
                rs.getString("legacy_key"),
                rs.getString("matricula"),
                rs.getString("matricula_dv"),
                rs.getString("locality_code"),
                rs.getString("sector_code"),
                rs.getString("block_code"),
                rs.getString("route_code"),
                rs.getString("category_code"),
                getLong(rs, "lot"),
                getLong(rs, "sublot"),
                getLong(rs, "reading_sequence"),
                getLong(rs, "balance_cents"),
                getDate(rs, "connected_on"),
                rs.getString("holder_name"),
                rs.getString("holder_document_kind"),
                rs.getString("holder_document_masked"),
                getBoolean(rs, "holder_document_valid"),
                parseIssues(rs.getString("issues")),
                StagedUnit.Status.valueOf(rs.getString("status")),
                (UUID) rs.getObject("applied_unit_id"));
    }

    @Override
    public void updateUnit(StagedUnit unit) {
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("legacy_import_staging_units")
                            + " SET status = ?, applied_unit_id = ?, issues = ?::jsonb, updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setString(1, unit.status().name());
                setUuid(ps, 2, unit.appliedUnitId());
                ps.setString(3, issuesJson(unit.issues()));
                ps.setObject(4, tenantId);
                ps.setObject(5, unit.id());
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Prestações
    // ------------------------------------------------------------------

    @Override
    public void insertInstallments(UUID batchId, List<StagedInstallment> installments) {
        if (installments.isEmpty()) {
            return;
        }
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("legacy_import_staging_installments")
                            + " (id, tenant_id, batch_id, staging_unit_id, unit_legacy_key,"
                            + " legacy_agreement_ref, installment_no, due_date, amount_cents, status)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'STAGED')")) {
                for (StagedInstallment installment : installments) {
                    int i = 0;
                    ps.setObject(++i, installment.id());
                    ps.setObject(++i, tenantId);
                    ps.setObject(++i, batchId);
                    setUuid(ps, ++i, installment.stagingUnitId());
                    ps.setString(++i, installment.unitLegacyKey());
                    ps.setString(++i, installment.legacyAgreementRef());
                    ps.setInt(++i, installment.installmentNo());
                    setDate(ps, ++i, installment.dueDate());
                    ps.setLong(++i, installment.amountCents());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    @Override
    public List<StagedInstallment> listInstallments(UUID batchId) {
        return inTx(connection -> {
            List<StagedInstallment> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM " + t("legacy_import_staging_installments")
                            + " WHERE tenant_id = ? AND batch_id = ? ORDER BY unit_legacy_key, installment_no")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, batchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new StagedInstallment(
                                (UUID) rs.getObject("id"),
                                (UUID) rs.getObject("batch_id"),
                                (UUID) rs.getObject("staging_unit_id"),
                                rs.getString("unit_legacy_key"),
                                rs.getString("legacy_agreement_ref"),
                                rs.getInt("installment_no"),
                                getDate(rs, "due_date"),
                                rs.getLong("amount_cents")));
                    }
                }
            }
            return List.copyOf(out);
        });
    }

    // ------------------------------------------------------------------
    // Linhagem
    // ------------------------------------------------------------------

    @Override
    public void insertLineage(UUID batchId, String recordKind, String sourceSystem, String sourceKey,
                              String mappingName, String mappingVersion, UUID stagingId) {
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("legacy_import_lineage")
                            + " (id, tenant_id, batch_id, record_kind, source_system, source_key,"
                            + " mapping_name, mapping_version, staging_id)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int i = 0;
                ps.setObject(++i, UUID.randomUUID());
                ps.setObject(++i, tenantId);
                ps.setObject(++i, batchId);
                ps.setString(++i, recordKind);
                ps.setString(++i, sourceSystem);
                ps.setString(++i, sourceKey);
                ps.setString(++i, mappingName);
                ps.setString(++i, mappingVersion);
                setUuid(ps, ++i, stagingId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<LegacyLineageRow> listLineage(UUID batchId) {
        return inTx(connection -> {
            List<LegacyLineageRow> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM " + t("legacy_import_lineage")
                            + " WHERE tenant_id = ? AND batch_id = ? ORDER BY created_at, id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, batchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new LegacyLineageRow(
                                (UUID) rs.getObject("id"),
                                rs.getString("record_kind"),
                                rs.getString("source_system"),
                                rs.getString("source_key"),
                                rs.getString("mapping_name"),
                                rs.getString("mapping_version"),
                                (UUID) rs.getObject("staging_id"),
                                rs.getString("target_table"),
                                (UUID) rs.getObject("target_id"),
                                (UUID) rs.getObject("service_order_id")));
                    }
                }
            }
            return List.copyOf(out);
        });
    }

    @Override
    public void markUnitApplied(UUID batchId, UUID stagingId, UUID unitId, UUID orderId) {
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE " + t("legacy_import_lineage")
                            + " SET target_table = 'consumer_units', target_id = ?,"
                            + " service_order_id = ?, applied_at = NOW()"
                            + " WHERE tenant_id = ? AND batch_id = ? AND staging_id = ?"
                            + " AND record_kind = 'UNIT'")) {
                ps.setObject(1, unitId);
                ps.setObject(2, orderId);
                ps.setObject(3, tenantId);
                ps.setObject(4, batchId);
                ps.setObject(5, stagingId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Conciliação
    // ------------------------------------------------------------------

    @Override
    public void saveReconciliation(UUID batchId, List<LegacyReconciliation.Row> rows) {
        inTx(connection -> {
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM " + t("legacy_import_reconciliation")
                            + " WHERE tenant_id = ? AND batch_id = ?")) {
                del.setObject(1, tenantId);
                del.setObject(2, batchId);
                del.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO " + t("legacy_import_reconciliation")
                            + " (id, tenant_id, batch_id, dimension, dimension_key,"
                            + " legacy_count, staged_count, legacy_amount_cents, staged_amount_cents, matches)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (LegacyReconciliation.Row row : rows) {
                    int i = 0;
                    ps.setObject(++i, UUID.randomUUID());
                    ps.setObject(++i, tenantId);
                    ps.setObject(++i, batchId);
                    ps.setString(++i, row.dimension().name());
                    ps.setString(++i, row.key());
                    setLong(ps, ++i, row.legacyCount());
                    setLong(ps, ++i, row.stagedCount());
                    setLong(ps, ++i, row.legacyAmountCents());
                    setLong(ps, ++i, row.stagedAmountCents());
                    ps.setBoolean(++i, row.matches());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    @Override
    public List<LegacyReconciliation.Row> listReconciliation(UUID batchId) {
        return inTx(connection -> {
            List<LegacyReconciliation.Row> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM " + t("legacy_import_reconciliation")
                            + " WHERE tenant_id = ? AND batch_id = ? ORDER BY dimension, dimension_key")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, batchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new LegacyReconciliation.Row(
                                LegacyReconciliation.Dimension.valueOf(rs.getString("dimension")),
                                rs.getString("dimension_key"),
                                getLong(rs, "legacy_count"),
                                getLong(rs, "staged_count"),
                                getLong(rs, "legacy_amount_cents"),
                                getLong(rs, "staged_amount_cents"),
                                rs.getBoolean("matches")));
                    }
                }
            }
            return List.copyOf(out);
        });
    }

    // ------------------------------------------------------------------
    // Conversão
    // ------------------------------------------------------------------

    private static String issuesJson(List<LegacyIssue> issues) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (LegacyIssue issue : issues) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("field", issue.field());
            row.put("code", issue.code());
            row.put("severity", issue.severity().name());
            row.put("message", issue.message());
            rows.add(row);
        }
        return Json.write(rows);
    }

    private static List<LegacyIssue> parseIssues(String json) {
        List<LegacyIssue> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        JsonNode root = Json.readTree(json);
        if (root == null || !root.isArray()) {
            return out;
        }
        for (JsonNode node : root) {
            out.add(new LegacyIssue(
                    node.path("field").asText(),
                    node.path("code").asText(),
                    LegacyIssue.Severity.valueOf(node.path("severity").asText("WARNING")),
                    node.path("message").asText()));
        }
        return out;
    }

    private static void setText(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private static void setBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BOOLEAN);
        } else {
            ps.setBoolean(index, value);
        }
    }

    private static void setDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setDate(index, Date.valueOf(value));
        }
    }

    private static Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean getBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDate getDate(ResultSet rs, String column) throws SQLException {
        Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }
}
