package br.com.ziipsan.platform.audit;

import br.com.ziipsan.platform.infra.storage.ObjectStorage;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * P1.7 — exportação contínua da trilha do tenant para o SIEM do cliente.
 *
 * <p>Formato: JSON por linha ou CEF, conforme {@code log.export_format}
 * (classe d); desligada por padrão ({@code log.export_enabled}, classe d).
 * A entrega implementada aqui é em armazenamento do cliente (o bucket do
 * {@link ObjectStorage} — a implementação local da P0.45; o fluxo HTTPS
 * assinado para o SIEM é pendência, pois exige endpoint e chaves do
 * cliente, decisão do dono).
 *
 * <p>RNF-52 — sem dado pessoal em claro: a exportação leva SÓ metadados do
 * evento (instante, tenant, usuário, entidade, ação, correlação, resultado,
 * origem). Nunca: ip_address, user_agent, request_id, old/new/changed
 * values (payloads), purpose ou document_hash. A trilha em si já é redigida
 * pelo trigger (core V2), mas a exportação não depende disso: ela nem lê
 * essas colunas.
 *
 * <p>Fontes: public.audit_log (sempre) e, quando o schema do tenant é
 * informado, <schema>.personal_data_access_log (P0.11) — buscas por
 * documento, leituras de cadastro e revelações, também só em metadados.
 * Toda consulta leva limites de período, para podar as partições mensais.
 */
public class AuditTrailExportService {

    public enum Format {
        JSONL, CEF
    }

    /** Um evento exportável: metadados apenas, nunca payload ou identificador. */
    public record ExportRow(OffsetDateTime occurredAt, UUID tenantId, UUID userId,
                            String entityType, UUID entityId, String action,
                            UUID correlationId, boolean success, String source) {
    }

    private static final Pattern SCHEMA_NAME = Pattern.compile("[a-z][a-z0-9_]{0,62}");

    private final DataSource dataSource;

    public AuditTrailExportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Lê a trilha do tenant no período. {@code tenantSchema} null exporta só
     * audit_log; informado, inclui personal_data_access_log do tenant.
     */
    public List<ExportRow> readTrail(UUID tenantId, String tenantSchema,
                                     OffsetDateTime from, OffsetDateTime to) throws SQLException {
        if (tenantSchema != null && !SCHEMA_NAME.matcher(tenantSchema).matches()) {
            throw new IllegalArgumentException("schema de tenant invalido: " + tenantSchema);
        }
        return TenantScopedConnection.run(dataSource, tenantId, connection -> {
            List<ExportRow> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT created_at, tenant_id, user_id, entity_type, entity_id, action,"
                            + " correlation_id, success FROM public.audit_log"
                            + " WHERE tenant_id = ? AND created_at >= ? AND created_at < ?"
                            + " ORDER BY created_at, id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, Timestamp.from(from.toInstant()));
                ps.setObject(3, Timestamp.from(to.toInstant()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new ExportRow(
                                rs.getObject(1, OffsetDateTime.class),
                                (UUID) rs.getObject(2),
                                (UUID) rs.getObject(3),
                                rs.getString(4),
                                (UUID) rs.getObject(5),
                                rs.getString(6),
                                (UUID) rs.getObject(7),
                                rs.getBoolean(8),
                                "audit_log"));
                    }
                }
            }
            if (tenantSchema != null) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT accessed_at, tenant_id, user_id, entity_type, entity_id,"
                                + " access_type, result FROM " + tenantSchema + ".personal_data_access_log"
                                + " WHERE tenant_id = ? AND accessed_at >= ? AND accessed_at < ?"
                                + " ORDER BY accessed_at, id")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, Timestamp.from(from.toInstant()));
                    ps.setObject(3, Timestamp.from(to.toInstant()));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(new ExportRow(
                                    rs.getObject(1, OffsetDateTime.class),
                                    (UUID) rs.getObject(2),
                                    (UUID) rs.getObject(3),
                                    rs.getString(4),
                                    (UUID) rs.getObject(5),
                                    rs.getString(6),
                                    null,
                                    "FOUND".equals(rs.getString(7)),
                                    "personal_data_access_log"));
                        }
                    }
                }
            }
            rows.sort((a, b) -> a.occurredAt().compareTo(b.occurredAt()));
            return rows;
        });
    }

    /** Formata as linhas no formato pedido. Puro e testável sem banco. */
    public String format(List<ExportRow> rows, Format format) {
        StringBuilder out = new StringBuilder();
        for (ExportRow row : rows) {
            if (format == Format.JSONL) {
                out.append('{')
                        .append("\"occurred_at\":").append(jsonString(row.occurredAt().toString())).append(',')
                        .append("\"tenant_id\":").append(jsonString(row.tenantId().toString())).append(',')
                        .append("\"user_id\":").append(row.userId() == null ? "null" : jsonString(row.userId().toString())).append(',')
                        .append("\"entity_type\":").append(jsonString(nullToEmpty(row.entityType()))).append(',')
                        .append("\"entity_id\":").append(row.entityId() == null ? "null" : jsonString(row.entityId().toString())).append(',')
                        .append("\"action\":").append(jsonString(nullToEmpty(row.action()))).append(',')
                        .append("\"correlation_id\":").append(row.correlationId() == null ? "null" : jsonString(row.correlationId().toString())).append(',')
                        .append("\"success\":").append(row.success()).append(',')
                        .append("\"source\":").append(jsonString(row.source()))
                        .append("}\n");
            } else {
                out.append("CEF:0|Ziip|ZiipSAN|1.0|AUDIT|").append(cef(row.action()))
                        .append("|5|rt=").append(row.occurredAt().toInstant().toEpochMilli())
                        .append(" tenantId=").append(cef(row.tenantId().toString()))
                        .append(" userId=").append(row.userId() == null ? "-" : cef(row.userId().toString()))
                        .append(" entityType=").append(cef(nullToEmpty(row.entityType())))
                        .append(" entityId=").append(row.entityId() == null ? "-" : cef(row.entityId().toString()))
                        .append(" correlationId=").append(row.correlationId() == null ? "-" : cef(row.correlationId().toString()))
                        .append(" success=").append(row.success())
                        .append(" source=").append(cef(row.source()))
                        .append('\n');
            }
        }
        return out.toString();
    }

    /**
     * Entrega a exportação no armazenamento do cliente. Desligada
     * ({@code enabled=false}): recusa, nunca exporta em silêncio.
     */
    public String deliver(UUID tenantId, String tenantSchema, OffsetDateTime from, OffsetDateTime to,
                          Format format, boolean enabled, ObjectStorage storage, String bucket)
            throws SQLException {
        if (!enabled) {
            throw new IllegalStateException(
                    "audit_log: exportacao desligada (log.export_enabled); entrega recusada");
        }
        List<ExportRow> rows = readTrail(tenantId, tenantSchema, from, to);
        String key = "siem/" + tenantId + "/" + from.toLocalDate() + "_" + to.toLocalDate()
                + "." + (format == Format.JSONL ? "jsonl" : "cef");
        byte[] bytes = format(rows, format).getBytes(StandardCharsets.UTF_8);
        storage.put(bucket, key, new ByteArrayInputStream(bytes),
                format == Format.JSONL ? "application/x-ndjson" : "text/plain");
        return key;
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String cef(String value) {
        return value.replace("\\", "\\\\").replace("|", "\\|").replace("=", "\\=");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
