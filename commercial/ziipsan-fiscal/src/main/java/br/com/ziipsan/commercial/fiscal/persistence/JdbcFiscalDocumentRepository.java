package br.com.ziipsan.commercial.fiscal.persistence;

import br.com.ziipsan.commercial.fiscal.port.AuthorizedXmlStore;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository;
import br.com.ziipsan.commercial.fiscal.rules.TaxRateValidity.TaxRateRow;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Implementação JDBC da porta de persistência fiscal (V4_5).
 *
 * <p>Isolamento por tenant, sem vazar entre conexões de um pool: cada operação roda numa
 * transação explícita que começa com {@code SET LOCAL search_path} para o schema do tenant
 * e {@code SET LOCAL app.current_tenant} — as duas configurações morrem com a transação
 * (o mesmo padrão que as policies de RLS com {@code FORCE} exigem). O nome do schema é
 * validado por regex antes de interpolar: nunca vem de entrada externa sem validação.
 *
 * <p>A reserva de número é a operação crítica: {@code SELECT ... FOR UPDATE} na linha da
 * série, na mesma transação que grava o documento em RESERVED. Duas reservas simultâneas
 * serializam na trava da linha; o índice único por (emitente, tipo, série, número) é a
 * segunda barreira, e a unicidade de origem é a terceira.
 */
public final class JdbcFiscalDocumentRepository implements FiscalDocumentRepository, AuthorizedXmlStore {

    private final DataSource dataSource;
    private final String schema;

    public JdbcFiscalDocumentRepository(DataSource dataSource, String schema) {
        if (schema == null || !schema.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("O schema do tenant e invalido: " + schema);
        }
        this.dataSource = dataSource;
        this.schema = schema;
    }

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private <T> T inTransaction(UUID tenantId, Work<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("SET LOCAL search_path TO " + schema + ", public");
                }
                try (PreparedStatement setTenant = connection.prepareStatement(
                        "SELECT set_config('app.current_tenant', ?, true)")) {
                    setTenant.setString(1, tenantId.toString());
                    setTenant.executeQuery();
                }
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // O erro original e o que importa; o rollback falho nao o substitui.
                }
                throw failure;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha de acesso ao banco fiscal", e);
        }
    }

    @Override
    public Optional<IssuerRecord> findIssuer(UUID tenantId, UUID issuerId) {
        return inTransaction(tenantId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, ibge, document_kind, document_hash FROM fiscal_issuers "
                            + "WHERE tenant_id = ? AND id = ?")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, issuerId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new IssuerRecord(
                            (UUID) rows.getObject("id"), rows.getString("ibge"),
                            rows.getString("document_kind"), rows.getString("document_hash")));
                }
            }
        });
    }

    @Override
    public void ensureSeries(UUID tenantId, UUID issuerId, String documentType, String series) {
        inTransaction(tenantId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO fiscal_series (tenant_id, issuer_id, document_type, series) "
                            + "VALUES (?, ?, ?, ?) "
                            + "ON CONFLICT (tenant_id, issuer_id, document_type, series) DO NOTHING")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, issuerId);
                statement.setString(3, documentType);
                statement.setString(4, series);
                statement.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public ReservedSlot reserveSlot(UUID tenantId, UUID issuerId, String documentType, String series,
            String sourceType, UUID sourceId, String environment, Integer tpAmb, BigDecimal gross) {
        return inTransaction(tenantId, connection -> {
            UUID seriesId;
            long number;
            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT id, next_number FROM fiscal_series WHERE tenant_id = ? AND issuer_id = ? "
                            + "AND document_type = ? AND series = ? FOR UPDATE")) {
                lock.setObject(1, tenantId);
                lock.setObject(2, issuerId);
                lock.setString(3, documentType);
                lock.setString(4, series);
                try (ResultSet rows = lock.executeQuery()) {
                    if (!rows.next()) {
                        throw new IllegalStateException(
                                "Serie fiscal inexistente: crie-a com ensureSeries antes de emitir");
                    }
                    seriesId = (UUID) rows.getObject("id");
                    number = rows.getLong("next_number");
                }
            }
            try (PreparedStatement bump = connection.prepareStatement(
                    "UPDATE fiscal_series SET next_number = ?, updated_at = NOW() WHERE id = ?")) {
                bump.setLong(1, number + 1);
                bump.setObject(2, seriesId);
                bump.executeUpdate();
            }
            UUID documentId = UUID.randomUUID();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO fiscal_documents (id, tenant_id, issuer_id, series_id, document_type, "
                            + "series, number, source_type, source_id, status, environment, tp_amb, gross) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'RESERVED', ?, ?, ?)")) {
                insert.setObject(1, documentId);
                insert.setObject(2, tenantId);
                insert.setObject(3, issuerId);
                insert.setObject(4, seriesId);
                insert.setString(5, documentType);
                insert.setString(6, series);
                insert.setLong(7, number);
                insert.setString(8, sourceType);
                insert.setObject(9, sourceId);
                insert.setString(10, environment);
                if (tpAmb == null) {
                    insert.setNull(11, Types.SMALLINT);
                } else {
                    insert.setInt(11, tpAmb);
                }
                insert.setBigDecimal(12, gross);
                insert.executeUpdate();
            } catch (SQLException e) {
                // 23505 aqui so pode ser a unicidade de origem: o numero vem da serie travada
                // e e unico por construcao; qualquer outra unicidade ja estava garantida.
                if ("23505".equals(e.getSQLState())) {
                    throw new DuplicateSourceException(
                            "A origem " + sourceType + "/" + sourceId + " ja gerou um documento " + documentType);
                }
                throw e;
            }
            return new ReservedSlot(documentId, number);
        });
    }

    @Override
    public void signDocument(UUID tenantId, UUID documentId, String dpsId, String unsignedXml, List<TaxLine> taxes) {
        inTransaction(tenantId, connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE fiscal_documents SET dps_id = ?, unsigned_xml = ?, status = 'SIGNED', "
                            + "updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                update.setString(1, dpsId);
                update.setString(2, unsignedXml);
                update.setObject(3, tenantId);
                update.setObject(4, documentId);
                if (update.executeUpdate() != 1) {
                    throw new IllegalStateException("Documento inexistente para assinar: " + documentId);
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO fiscal_document_taxes (tenant_id, document_id, tax_code, rate, amount, informative) "
                            + "VALUES (?, ?, ?, ?, ?, ?)")) {
                for (TaxLine tax : taxes) {
                    insert.setObject(1, tenantId);
                    insert.setObject(2, documentId);
                    insert.setString(3, tax.taxCode());
                    insert.setBigDecimal(4, tax.rate());
                    insert.setBigDecimal(5, tax.amount());
                    insert.setBoolean(6, tax.informative());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            return null;
        });
    }

    @Override
    public void markAuthorized(UUID tenantId, UUID documentId, String protocol, String accessKey) {
        updateStatus(tenantId, documentId,
                "UPDATE fiscal_documents SET status = 'AUTHORIZED', protocol = ?, access_key = ?, "
                        + "authorized_at = NOW(), updated_at = NOW() WHERE tenant_id = ? AND id = ?",
                protocol, accessKey);
    }

    @Override
    public void markRejected(UUID tenantId, UUID documentId, String code, String message) {
        updateStatus(tenantId, documentId,
                "UPDATE fiscal_documents SET status = 'REJECTED', rejection_code = ?, "
                        + "rejection_message = ?, updated_at = NOW() WHERE tenant_id = ? AND id = ?",
                code, message);
    }

    @Override
    public void markPendingReconciliation(UUID tenantId, UUID documentId) {
        inTransaction(tenantId, connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE fiscal_documents SET status = 'PENDING_RECONCILIATION', updated_at = NOW() "
                            + "WHERE tenant_id = ? AND id = ?")) {
                update.setObject(1, tenantId);
                update.setObject(2, documentId);
                if (update.executeUpdate() != 1) {
                    throw new IllegalStateException("Documento inexistente: " + documentId);
                }
                return null;
            }
        });
    }

    @Override
    public void markCancelled(UUID tenantId, UUID documentId) {
        inTransaction(tenantId, connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE fiscal_documents SET status = 'CANCELLED', cancelled_at = NOW(), "
                            + "updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                update.setObject(1, tenantId);
                update.setObject(2, documentId);
                if (update.executeUpdate() != 1) {
                    throw new IllegalStateException("Documento inexistente: " + documentId);
                }
                return null;
            }
        });
    }

    @Override
    public void markReplaced(UUID tenantId, UUID originalId, UUID replacementId) {
        inTransaction(tenantId, connection -> {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE fiscal_documents SET status = 'REPLACED', replaced_by_id = ?, "
                            + "updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                update.setObject(1, replacementId);
                update.setObject(2, tenantId);
                update.setObject(3, originalId);
                if (update.executeUpdate() != 1) {
                    throw new IllegalStateException("Documento inexistente: " + originalId);
                }
                return null;
            }
        });
    }

    private void updateStatus(UUID tenantId, UUID documentId, String sql, String first, String second) {
        inTransaction(tenantId, connection -> {
            try (PreparedStatement update = connection.prepareStatement(sql)) {
                update.setString(1, first);
                update.setString(2, second);
                update.setObject(3, tenantId);
                update.setObject(4, documentId);
                if (update.executeUpdate() != 1) {
                    throw new IllegalStateException("Documento inexistente: " + documentId);
                }
                return null;
            }
        });
    }

    @Override
    public void registerEvent(UUID tenantId, EventRecord event) {
        inTransaction(tenantId, connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO fiscal_document_events (id, tenant_id, document_id, event_id, event_type, "
                            + "event_kind, status, reason, requested_by, approved_by, approved_by_second) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                insert.setObject(1, event.id());
                insert.setObject(2, tenantId);
                insert.setObject(3, event.documentId());
                insert.setString(4, event.eventId());
                insert.setString(5, event.eventType());
                insert.setString(6, event.eventKind());
                insert.setString(7, event.status());
                insert.setString(8, event.reason());
                setNullableUuid(insert, 9, event.requestedBy());
                setNullableUuid(insert, 10, event.approvedBy());
                setNullableUuid(insert, 11, event.approvedBySecond());
                insert.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public void store(UUID tenantId, UUID documentId, byte[] xmlGzip, String sha256, String source) {
        storeAuthorizedXml(tenantId, documentId, xmlGzip, sha256, source);
    }

    @Override
    public void storeAuthorizedXml(UUID tenantId, UUID documentId, byte[] xmlGzip, String sha256, String source) {
        inTransaction(tenantId, connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO fiscal_documents_xml (tenant_id, document_id, xml_gzip, sha256, source) "
                            + "VALUES (?, ?, ?, ?, ?)")) {
                insert.setObject(1, tenantId);
                insert.setObject(2, documentId);
                insert.setBytes(3, xmlGzip);
                insert.setString(4, sha256);
                insert.setString(5, source);
                insert.executeUpdate();
                return null;
            }
        });
    }

    @Override
    public Optional<DocumentRecord> findDocument(UUID tenantId, UUID documentId) {
        return inTransaction(tenantId, connection -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, issuer_id, document_type, series, number, dps_id, access_key, source_type, "
                            + "source_id, status, environment, tp_amb, gross, net, unsigned_xml, protocol, "
                            + "rejection_code, rejection_message, replaced_by_id FROM fiscal_documents "
                            + "WHERE tenant_id = ? AND id = ?")) {
                query.setObject(1, tenantId);
                query.setObject(2, documentId);
                try (ResultSet rows = query.executeQuery()) {
                    return rows.next() ? Optional.of(readDocument(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<DocumentRecord> findBySource(UUID tenantId, String sourceType, UUID sourceId, String documentType) {
        return inTransaction(tenantId, connection -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, issuer_id, document_type, series, number, dps_id, access_key, source_type, "
                            + "source_id, status, environment, tp_amb, gross, net, unsigned_xml, protocol, "
                            + "rejection_code, rejection_message, replaced_by_id FROM fiscal_documents "
                            + "WHERE tenant_id = ? AND source_type = ? AND source_id = ? AND document_type = ?")) {
                query.setObject(1, tenantId);
                query.setString(2, sourceType);
                query.setObject(3, sourceId);
                query.setString(4, documentType);
                try (ResultSet rows = query.executeQuery()) {
                    return rows.next() ? Optional.of(readDocument(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<EventRecord> findEventByDocumentAndKind(UUID tenantId, UUID documentId, String eventKind) {
        return inTransaction(tenantId, connection -> {
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id, document_id, event_id, event_type, event_kind, status, reason, "
                            + "requested_by, approved_by, approved_by_second FROM fiscal_document_events "
                            + "WHERE tenant_id = ? AND document_id = ? AND event_kind = ? "
                            + "ORDER BY registered_at DESC, created_at DESC LIMIT 1")) {
                query.setObject(1, tenantId);
                query.setObject(2, documentId);
                query.setString(3, eventKind);
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new EventRecord(
                            (UUID) rows.getObject("id"), (UUID) rows.getObject("document_id"),
                            rows.getString("event_id"), rows.getString("event_type"),
                            rows.getString("event_kind"), rows.getString("status"),
                            rows.getString("reason"), (UUID) rows.getObject("requested_by"),
                            (UUID) rows.getObject("approved_by"), (UUID) rows.getObject("approved_by_second")));
                }
            }
        });
    }

    @Override
    public List<TaxRateRow> taxRates(UUID tenantId) {
        return inTransaction(tenantId, connection -> {
            List<TaxRateRow> rows = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT tax_code, scope, valid_from, valid_to, rate FROM tax_rates WHERE tenant_id = ?")) {
                query.setObject(1, tenantId);
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        Date validTo = result.getDate("valid_to");
                        rows.add(new TaxRateRow(
                                result.getString("tax_code"), result.getString("scope"),
                                result.getDate("valid_from").toLocalDate(),
                                validTo == null ? null : validTo.toLocalDate(),
                                result.getBigDecimal("rate")));
                    }
                }
            }
            return rows;
        });
    }

    private static DocumentRecord readDocument(ResultSet rows) throws SQLException {
        Object tpAmb = rows.getObject("tp_amb");
        return new DocumentRecord(
                (UUID) rows.getObject("id"), (UUID) rows.getObject("issuer_id"),
                rows.getString("document_type"), rows.getString("series"), rows.getLong("number"),
                rows.getString("dps_id"), rows.getString("access_key"), rows.getString("source_type"),
                (UUID) rows.getObject("source_id"), rows.getString("status"), rows.getString("environment"),
                tpAmb == null ? null : ((Number) tpAmb).intValue(),
                rows.getBigDecimal("gross"), rows.getBigDecimal("net"), rows.getString("unsigned_xml"),
                rows.getString("protocol"), rows.getString("rejection_code"),
                rows.getString("rejection_message"), (UUID) rows.getObject("replaced_by_id"));
    }

    private static void setNullableUuid(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, value);
        }
    }
}
