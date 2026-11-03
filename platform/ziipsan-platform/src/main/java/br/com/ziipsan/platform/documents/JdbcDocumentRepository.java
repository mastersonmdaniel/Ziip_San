package br.com.ziipsan.platform.documents;

import br.com.ziipsan.platform.documents.DocumentRecords.DataClassification;
import br.com.ziipsan.platform.documents.DocumentRecords.Document;
import br.com.ziipsan.platform.documents.DocumentRecords.DocumentType;
import br.com.ziipsan.platform.documents.DocumentRecords.RetentionClass;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanEvent;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanEventType;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * P1.20: implementacao JDBC da porta de documentos (V1_16).
 *
 * <p>Isolamento por tenant, no mesmo padrao do repositorio fiscal: cada operacao
 * roda numa transacao explicita que comeca com {@code SET LOCAL search_path}
 * para o schema do tenant e {@code SET LOCAL app.current_tenant} — as duas
 * configuracoes morrem com a transacao (o mesmo padrao que as policies de RLS
 * com {@code FORCE} exigem). O nome do schema e validado por regex antes de
 * interpolar: nunca vem de entrada externa sem validacao. Todas as leituras
 * filtram por tenant_id alem da RLS, para o isolamento nao depender de uma
 * unica camada.
 */
public final class JdbcDocumentRepository implements DocumentRepository {

    private final DataSource dataSource;
    private final String schema;

    public JdbcDocumentRepository(DataSource dataSource, String schema) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource e obrigatorio");
        }
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
            throw new DocumentStoreException("Falha de acesso ao banco de documentos", e);
        }
    }

    @Override
    public DocumentType createDocumentType(UUID tenantId, String code, String label) {
        return inTransaction(tenantId, connection -> {
            UUID id = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO document_types (id, tenant_id, code, label) VALUES (?, ?, ?, ?)")) {
                statement.setObject(1, id);
                statement.setObject(2, tenantId);
                statement.setString(3, code);
                statement.setString(4, label);
                statement.executeUpdate();
            }
            return findDocumentTypeRow(connection, tenantId, id)
                    .orElseThrow(() -> new DocumentStoreException("tipo de documento nao persistido", null));
        });
    }

    @Override
    public Optional<DocumentType> findDocumentType(UUID tenantId, UUID documentTypeId) {
        return inTransaction(tenantId,
                connection -> findDocumentTypeRow(connection, tenantId, documentTypeId));
    }

    private Optional<DocumentType> findDocumentTypeRow(Connection connection, UUID tenantId, UUID id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, tenant_id, code, label, active, created_at, updated_at"
                        + " FROM document_types WHERE tenant_id = ? AND id = ?")) {
            statement.setObject(1, tenantId);
            statement.setObject(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new DocumentType(
                        (UUID) rows.getObject("id"), (UUID) rows.getObject("tenant_id"),
                        rows.getString("code"), rows.getString("label"), rows.getBoolean("active"),
                        toInstant(rows.getTimestamp("created_at")),
                        toInstant(rows.getTimestamp("updated_at"))));
            }
        }
    }

    @Override
    public RetentionClass createRetentionClass(UUID tenantId, String code, String label,
                                               int retentionMonths) {
        return inTransaction(tenantId, connection -> {
            UUID id = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO retention_classes (id, tenant_id, code, label, retention_months)"
                            + " VALUES (?, ?, ?, ?, ?)")) {
                statement.setObject(1, id);
                statement.setObject(2, tenantId);
                statement.setString(3, code);
                statement.setString(4, label);
                statement.setInt(5, retentionMonths);
                statement.executeUpdate();
            }
            return findRetentionClassRow(connection, tenantId, id)
                    .orElseThrow(() -> new DocumentStoreException("classe de retencao nao persistida", null));
        });
    }

    @Override
    public Optional<RetentionClass> findRetentionClass(UUID tenantId, UUID retentionClassId) {
        return inTransaction(tenantId,
                connection -> findRetentionClassRow(connection, tenantId, retentionClassId));
    }

    private Optional<RetentionClass> findRetentionClassRow(Connection connection, UUID tenantId, UUID id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, tenant_id, code, label, retention_months, active, created_at, updated_at"
                        + " FROM retention_classes WHERE tenant_id = ? AND id = ?")) {
            statement.setObject(1, tenantId);
            statement.setObject(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new RetentionClass(
                        (UUID) rows.getObject("id"), (UUID) rows.getObject("tenant_id"),
                        rows.getString("code"), rows.getString("label"), rows.getInt("retention_months"),
                        rows.getBoolean("active"),
                        toInstant(rows.getTimestamp("created_at")),
                        toInstant(rows.getTimestamp("updated_at"))));
            }
        }
    }

    @Override
    public Document insertDocument(Document document) {
        return inTransaction(document.tenantId(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO documents (id, tenant_id, document_type_id, retention_class_id,"
                            + " data_classification, scan_status, original_filename,"
                            + " declared_content_type, detected_content_type, size_bytes, sha256,"
                            + " storage_bucket, storage_key, immutable, scanned_at, scanner,"
                            + " av_db_updated_at, threat)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                bindDocument(statement, document);
                statement.executeUpdate();
            }
            return findDocumentRow(connection, document.tenantId(), document.id())
                    .orElseThrow(() -> new DocumentStoreException("documento nao persistido", null));
        });
    }

    @Override
    public Optional<Document> findDocument(UUID tenantId, UUID documentId) {
        return inTransaction(tenantId,
                connection -> findDocumentRow(connection, tenantId, documentId));
    }

    @Override
    public Document updateDocument(Document document) {
        return inTransaction(document.tenantId(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE documents SET document_type_id = ?, retention_class_id = ?,"
                            + " data_classification = ?, scan_status = ?, original_filename = ?,"
                            + " declared_content_type = ?, detected_content_type = ?, size_bytes = ?,"
                            + " sha256 = ?, storage_bucket = ?, storage_key = ?, immutable = ?,"
                            + " scanned_at = ?, scanner = ?, av_db_updated_at = ?, threat = ?,"
                            + " updated_at = NOW()"
                            + " WHERE tenant_id = ? AND id = ?")) {
                int i = 1;
                statement.setObject(i++, document.documentTypeId());
                statement.setObject(i++, document.retentionClassId());
                statement.setString(i++, document.dataClassification().name());
                statement.setString(i++, document.scanStatus().name());
                statement.setString(i++, document.originalFilename());
                setNullableString(statement, i++, document.declaredContentType());
                statement.setString(i++, document.detectedContentType());
                statement.setLong(i++, document.sizeBytes());
                statement.setString(i++, document.sha256());
                statement.setString(i++, document.storageBucket());
                statement.setString(i++, document.storageKey());
                statement.setBoolean(i++, document.immutable());
                setNullableTimestamp(statement, i++, document.scannedAt());
                setNullableString(statement, i++, document.scanner());
                setNullableTimestamp(statement, i++, document.avDbUpdatedAt());
                setNullableString(statement, i++, document.threat());
                statement.setObject(i++, document.tenantId());
                statement.setObject(i, document.id());
                if (statement.executeUpdate() != 1) {
                    throw new DocumentStoreException("documento inexistente para atualizar: "
                            + document.id(), null);
                }
            }
            return findDocumentRow(connection, document.tenantId(), document.id())
                    .orElseThrow(() -> new DocumentStoreException("documento nao persistido", null));
        });
    }

    private Optional<Document> findDocumentRow(Connection connection, UUID tenantId, UUID id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, tenant_id, document_type_id, retention_class_id, data_classification,"
                        + " scan_status, original_filename, declared_content_type,"
                        + " detected_content_type, size_bytes, sha256, storage_bucket, storage_key,"
                        + " immutable, scanned_at, scanner, av_db_updated_at, threat,"
                        + " created_at, updated_at"
                        + " FROM documents WHERE tenant_id = ? AND id = ?")) {
            statement.setObject(1, tenantId);
            statement.setObject(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapDocument(rows));
            }
        }
    }

    private Document mapDocument(ResultSet rows) throws SQLException {
        return new Document(
                (UUID) rows.getObject("id"), (UUID) rows.getObject("tenant_id"),
                (UUID) rows.getObject("document_type_id"), (UUID) rows.getObject("retention_class_id"),
                DataClassification.valueOf(rows.getString("data_classification")),
                ScanStatus.valueOf(rows.getString("scan_status")),
                rows.getString("original_filename"), rows.getString("declared_content_type"),
                rows.getString("detected_content_type"), rows.getLong("size_bytes"),
                rows.getString("sha256"), rows.getString("storage_bucket"),
                rows.getString("storage_key"), rows.getBoolean("immutable"),
                toInstant(rows.getTimestamp("scanned_at")), rows.getString("scanner"),
                toInstant(rows.getTimestamp("av_db_updated_at")), rows.getString("threat"),
                toInstant(rows.getTimestamp("created_at")), toInstant(rows.getTimestamp("updated_at")));
    }

    private void bindDocument(PreparedStatement statement, Document document) throws SQLException {
        int i = 1;
        statement.setObject(i++, document.id());
        statement.setObject(i++, document.tenantId());
        statement.setObject(i++, document.documentTypeId());
        statement.setObject(i++, document.retentionClassId());
        statement.setString(i++, document.dataClassification().name());
        statement.setString(i++, document.scanStatus().name());
        statement.setString(i++, document.originalFilename());
        setNullableString(statement, i++, document.declaredContentType());
        statement.setString(i++, document.detectedContentType());
        statement.setLong(i++, document.sizeBytes());
        statement.setString(i++, document.sha256());
        statement.setString(i++, document.storageBucket());
        statement.setString(i++, document.storageKey());
        statement.setBoolean(i++, document.immutable());
        setNullableTimestamp(statement, i++, document.scannedAt());
        setNullableString(statement, i++, document.scanner());
        setNullableTimestamp(statement, i++, document.avDbUpdatedAt());
        setNullableString(statement, i, document.threat());
    }

    @Override
    public ScanEvent insertEvent(UUID tenantId, Optional<UUID> documentId, ScanEventType eventType,
                                 String threat, String detail) {
        return inTransaction(tenantId, connection -> {
            UUID id = UUID.randomUUID();
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO document_scan_events (id, tenant_id, document_id, event_type,"
                            + " threat, detail) VALUES (?, ?, ?, ?, ?, ?)")) {
                statement.setObject(1, id);
                statement.setObject(2, tenantId);
                if (documentId.isPresent()) {
                    statement.setObject(3, documentId.get());
                } else {
                    statement.setNull(3, Types.OTHER);
                }
                statement.setString(4, eventType.name());
                setNullableString(statement, 5, threat);
                setNullableString(statement, 6, detail);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, tenant_id, document_id, event_type, threat, detail, created_at"
                            + " FROM document_scan_events WHERE tenant_id = ? AND id = ?")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, id);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new DocumentStoreException("evento de varredura nao persistido", null);
                    }
                    UUID docId = (UUID) rows.getObject("document_id");
                    return new ScanEvent((UUID) rows.getObject("id"),
                            (UUID) rows.getObject("tenant_id"), Optional.ofNullable(docId),
                            ScanEventType.valueOf(rows.getString("event_type")),
                            rows.getString("threat"), rows.getString("detail"),
                            toInstant(rows.getTimestamp("created_at")));
                }
            }
        });
    }

    @Override
    public List<ScanEvent> listEvents(UUID tenantId, UUID documentId) {
        return inTransaction(tenantId, connection -> {
            List<ScanEvent> events = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, tenant_id, document_id, event_type, threat, detail, created_at"
                            + " FROM document_scan_events"
                            + " WHERE tenant_id = ? AND document_id = ? ORDER BY created_at ASC")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, documentId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        UUID docId = (UUID) rows.getObject("document_id");
                        events.add(new ScanEvent((UUID) rows.getObject("id"),
                                (UUID) rows.getObject("tenant_id"), Optional.ofNullable(docId),
                                ScanEventType.valueOf(rows.getString("event_type")),
                                rows.getString("threat"), rows.getString("detail"),
                                toInstant(rows.getTimestamp("created_at"))));
                    }
                }
            }
            return events;
        });
    }

    @Override
    public Optional<ScanEvent> latestEventByType(UUID tenantId, ScanEventType eventType) {
        return inTransaction(tenantId, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id, tenant_id, document_id, event_type, threat, detail, created_at"
                            + " FROM document_scan_events"
                            + " WHERE tenant_id = ? AND event_type = ?"
                            + " ORDER BY created_at DESC LIMIT 1")) {
                statement.setObject(1, tenantId);
                statement.setString(2, eventType.name());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    UUID docId = (UUID) rows.getObject("document_id");
                    return Optional.of(new ScanEvent((UUID) rows.getObject("id"),
                            (UUID) rows.getObject("tenant_id"), Optional.ofNullable(docId),
                            ScanEventType.valueOf(rows.getString("event_type")),
                            rows.getString("threat"), rows.getString("detail"),
                            toInstant(rows.getTimestamp("created_at"))));
                }
            }
        });
    }

    private static void setNullableString(PreparedStatement statement, int index, String value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableTimestamp(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
