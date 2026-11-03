package br.com.ziipsan.platform.audit;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.infra.storage.ObjectStorage;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * P1.7 — prova de integridade da trilha do tenant.
 *
 * <p>Modelo: raiz diária por tenant (e, por construção, por partição mensal —
 * o dia pertence a um único mês UTC) sobre encadeamento por hash das linhas
 * de audit_log daquele dia, ordenadas por (created_at, id). A raiz é gravada
 * FORA do banco, via {@link ObjectStorage} (implementação local da P0.45;
 * S3 com Object Lock a partir da LC.1), e conferida por tarefa agendada
 * (o wiring no worker/job_runs é pendência — ver relatório da frente).
 * Qualquer alteração posterior de uma linha por acesso privilegiado — UPDATE,
 * DELETE ou INSERT retroativo no dia — muda a raiz recomputada e a
 * conferência seguinte acusa MISMATCH.
 *
 * <p>Regras de segurança do desenho:
 * <ul>
 *   <li>Seal recusa sobrescrever raiz existente (semântica append-only em
 *       cima de um storage que, sozinho, permitiria overwrite). Re-selar um
 *       dia selado é erro, nunca correção silenciosa.</li>
 *   <li>Dia sem linhas também tem raiz determinística (o seed): selar um dia
 *       vazio prova ausência de linhas naquele dia, e um INSERT posterior
 *       no dia passa a acusar MISMATCH.</li>
 *   <li>Leitura sempre com tenant_id explícito + contexto RLS (defesa em
 *       profundidade) e com limites de created_at — o que também poda as
 *       partições mensais para o mês do dia.</li>
 * </ul>
 *
 * <p>Fora do escopo desta frente: selar personal_data_access_log (vive no
 * schema do tenant, não em public — precisa da resolução schema → tenant,
 * que é trabalho do provisionamento) e o armazenamento com retenção
 * imutável de verdade (WORM) — o LocalFileObjectStorage não o garante.
 */
public class AuditTrailIntegrityService {

    private static final String SEED_PREFIX = "ziipsan-audit-root-v1";

    private final DataSource dataSource;

    public AuditTrailIntegrityService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Uma linha da trilha, na forma canônica usada pelo encadeamento. */
    public record AuditRow(UUID id, OffsetDateTime createdAt, String entityType, UUID entityId,
                           String action, UUID userId, UUID correlationId,
                           String oldValues, String newValues, String changedFields,
                           boolean success) {
    }

    public record SealedRoot(UUID tenantId, LocalDate day, String partition,
                             int rowCount, String rootHash) {
    }

    public enum VerificationStatus {
        MATCH, MISMATCH, MISSING_ROOT
    }

    public record Verification(UUID tenantId, LocalDate day, String partition,
                               Integer storedRowCount, int computedRowCount,
                               String storedRoot, String computedRoot,
                               VerificationStatus status) {
        public boolean matches() {
            return status == VerificationStatus.MATCH;
        }
    }

    /** Raiz do encadeamento, pura e testável sem banco. */
    public static String chainRoot(UUID tenantId, LocalDate day, List<AuditRow> rows) {
        List<AuditRow> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.comparing(AuditRow::createdAt).thenComparing(AuditRow::id));
        byte[] previous = sha256((SEED_PREFIX + "\n" + tenantId + "\n" + day).getBytes(StandardCharsets.UTF_8));
        for (AuditRow row : ordered) {
            String canonical = String.join("\n",
                    nullToEmpty(row.id()), nullToEmpty(row.createdAt()), nullToEmpty(row.entityType()),
                    nullToEmpty(row.entityId()), nullToEmpty(row.action()), nullToEmpty(row.userId()),
                    nullToEmpty(row.correlationId()), nullToEmpty(row.oldValues()),
                    nullToEmpty(row.newValues()), nullToEmpty(row.changedFields()),
                    Boolean.toString(row.success()));
            byte[] step = new byte[previous.length + 1 + canonical.getBytes(StandardCharsets.UTF_8).length];
            System.arraycopy(previous, 0, step, 0, previous.length);
            step[previous.length] = '\n';
            System.arraycopy(canonical.getBytes(StandardCharsets.UTF_8), 0, step,
                    previous.length + 1, canonical.getBytes(StandardCharsets.UTF_8).length);
            previous = sha256(step);
        }
        return HexFormat.of().formatHex(previous);
    }

    /** Linhas de audit_log do tenant no dia (UTC), ordenadas por (created_at, id). */
    public List<AuditRow> readDay(UUID tenantId, LocalDate day) throws SQLException {
        OffsetDateTime from = day.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = day.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return TenantScopedConnection.run(dataSource, tenantId, connection -> {
            List<AuditRow> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, created_at, entity_type, entity_id, action, user_id, correlation_id,"
                            + " old_values, new_values, changed_fields, success"
                            + " FROM public.audit_log"
                            + " WHERE tenant_id = ? AND created_at >= ? AND created_at < ?"
                            + " ORDER BY created_at, id")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, Timestamp.from(from.toInstant()));
                ps.setObject(3, Timestamp.from(to.toInstant()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new AuditRow(
                                (UUID) rs.getObject(1),
                                rs.getObject(2, OffsetDateTime.class),
                                rs.getString(3),
                                (UUID) rs.getObject(4),
                                rs.getString(5),
                                (UUID) rs.getObject(6),
                                (UUID) rs.getObject(7),
                                rs.getString(8),
                                rs.getString(9),
                                rs.getString(10),
                                rs.getBoolean(11)));
                    }
                }
            }
            return rows;
        });
    }

    /** Sela a raiz do dia. Dia já selado: erro, nunca overwrite. */
    public SealedRoot sealDay(UUID tenantId, LocalDate day, ObjectStorage storage, String bucket)
            throws SQLException {
        String key = rootKey(tenantId, day);
        if (storage.exists(bucket, key)) {
            throw new IllegalStateException(
                    "audit_log: raiz do dia " + day + " do tenant " + tenantId + " ja selada; re-selar e recusado");
        }
        List<AuditRow> rows = readDay(tenantId, day);
        String root = chainRoot(tenantId, day, rows);
        String partition = String.format("%04d_%02d", day.getYear(), day.getMonthValue());
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("version", 1);
        document.put("tenant_id", tenantId.toString());
        document.put("date", day.toString());
        document.put("partition", "audit_log_" + partition);
        document.put("row_count", rows.size());
        document.put("root_hash", root);
        document.put("algorithm", "SHA-256-chain-v1");
        byte[] bytes = Json.write(document).getBytes(StandardCharsets.UTF_8);
        storage.put(bucket, key, new ByteArrayInputStream(bytes), "application/json");
        return new SealedRoot(tenantId, day, "audit_log_" + partition, rows.size(), root);
    }

    /** Recomputa a raiz do dia e compara com a selada. */
    public Verification verifyDay(UUID tenantId, LocalDate day, ObjectStorage storage, String bucket)
            throws SQLException {
        List<AuditRow> rows = readDay(tenantId, day);
        String computed = chainRoot(tenantId, day, rows);
        String partition = String.format("audit_log_%04d_%02d", day.getYear(), day.getMonthValue());
        var stored = storage.get(bucket, rootKey(tenantId, day));
        if (stored.isEmpty()) {
            return new Verification(tenantId, day, partition, null, rows.size(), null, computed,
                    VerificationStatus.MISSING_ROOT);
        }
        String json;
        try (var in = stored.get()) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("audit_log: falha ao ler raiz selada de " + day, e);
        }
        var tree = Json.readTree(json);
        String storedRoot = tree.get("root_hash").asString();
        int storedCount = tree.get("row_count").asInt();
        VerificationStatus status = storedRoot.equals(computed) && storedCount == rows.size()
                ? VerificationStatus.MATCH : VerificationStatus.MISMATCH;
        return new Verification(tenantId, day, partition, storedCount, rows.size(),
                storedRoot, computed, status);
    }

    static String rootKey(UUID tenantId, LocalDate day) {
        return "audit-roots/" + tenantId + "/" + day + ".json";
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
