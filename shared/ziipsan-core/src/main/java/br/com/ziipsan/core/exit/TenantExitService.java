package br.com.ziipsan.core.exit;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.support.TicketRetentionService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Processo de saida de tenant (P3.16; core {@code V10_5}): abertura,
 * exportacao, consulta somente leitura, carencia, contestacao, ordem
 * judicial, agendamento, execucao com crypto-shredding e certificado.
 * As regras de prazo e de portao sao as puras de
 * {@link TenantExitCalculator}; este servico so le/grava o estado nas
 * tabelas {@code platform.tenant_exit*} e aplica os portoes ANTES de
 * qualquer escrita, no mesmo padrao do {@code TicketRetentionService}
 * (P3.19): transacao com {@code set_config('app.current_tenant', ...)}
 * e recusas nomeadas, sem dado inventado.
 *
 * <p>Limites declarados:
 * <ul>
 *   <li>a destruicao da chave (crypto-shredding) e executada pela
 *       porta {@link KeyDestruction}: a implementacao de producao e o
 *       provedor de chaves da P0.28 (core {@code V6}, tabela
 *       {@code public.tenant_key}); sem confirmacao dela
 *       ({@code destroyedAt}), a execucao recusa e nada e marcado
 *       como eliminado;</li>
 *   <li>a geracao fisica dos arquivos da exportacao (linhas do schema
 *       do tenant) e do chamador: este servico registra formato, hash
 *       e downloads; o conteudo e montado com
 *       {@link TenantExitExport} a partir das linhas e dos metadados
 *       de {@code information_schema} lidos pelo chamador;</li>
 *   <li>o alerta da Central da contestacao e materializado por
 *       {@link #contestationsDueForAlert()} + registro em
 *       {@code alert_sent_at}: quem varre e agenda o alerta na Central
 *       (V9_1) e o job da plataforma; aqui fica a regra com relogio
 *       injetavel e a prova por teste puro no Calculator;</li>
 *   <li>datas do contrato sao DATE (V10_1): os instantes do processo
 *       usam 00:00 UTC da data, como a P3.19 faz com o
 *       {@code purge_at}.</li>
 * </ul>
 */
public final class TenantExitService {

    /** Porta do crypto-shredding (P0.28): destroi a DEK e devolve o instante confirmado. */
    @FunctionalInterface
    public interface KeyDestruction {
        OffsetDateTime destroy(UUID tenantId);
    }

    /** Fotografia do processo de saida, como gravada na V10_5. */
    public record ExitState(UUID exitId, String status, TenantExitCalculator.Level level,
                            LocalDate contractEnd, OffsetDateTime exportDueAt,
                            OffsetDateTime graceStartsAt, OffsetDateTime graceEndsAt,
                            OffsetDateTime certificateDueAt) {
    }

    /** Contestacao aberta devida para alerta da Central. */
    public record ContestationAlert(UUID contestationId, OffsetDateTime responseDueAt, boolean overdue) {
    }

    private final DataSource dataSource;
    private final UUID tenantId;
    private final Clock clock;

    public TenantExitService(DataSource dataSource, UUID tenantId, Clock clock) {
        if (dataSource == null || tenantId == null || clock == null) {
            throw new IllegalArgumentException("dataSource, tenantId e clock sao obrigatorios");
        }
        this.dataSource = dataSource;
        this.tenantId = tenantId;
        this.clock = clock;
    }

    private static OffsetDateTime startOfDayUtc(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    // ------------------------------------------------------------------
    // Abertura do processo e consulta somente leitura
    // ------------------------------------------------------------------

    /**
     * Abre o processo de saida a partir do contrato (inicio e fim da
     * vigencia) e, no nivel PUBLIC_SECTOR, o periodo de consulta
     * somente leitura. Todos os vencimentos sao calculados por
     * {@link TenantExitCalculator} na abertura e gravados — o processo
     * nao recalcula prazo em leitura.
     */
    public ExitState openExit(TenantExitCalculator.Level level, LocalDate contractStart,
                              LocalDate contractEnd, Set<LocalDate> holidays, UUID actorId) {
        if (level == null || contractStart == null || contractEnd == null) {
            throw new IllegalArgumentException("level, contractStart e contractEnd sao obrigatorios");
        }
        OffsetDateTime contractEndAt = startOfDayUtc(contractEnd);
        OffsetDateTime exportDueAt = startOfDayUtc(
                TenantExitCalculator.exportDueDate(level, contractEnd, holidays));
        OffsetDateTime readonlyEndsAt = level == TenantExitCalculator.Level.PUBLIC_SECTOR
                ? TenantExitCalculator.readonlyEndsAt(contractStart, contractEndAt) : null;
        OffsetDateTime graceStartsAt = TenantExitCalculator.graceStartsAt(level, contractEndAt, readonlyEndsAt);
        OffsetDateTime graceEndsAt = TenantExitCalculator.graceEndsAt(graceStartsAt);
        OffsetDateTime certificateDueAt = TenantExitCalculator.certificateDueAt(
                graceStartsAt, graceEndsAt, null);
        String status = level == TenantExitCalculator.Level.PUBLIC_SECTOR
                ? "IN_CONSULTATION" : "EXPORT_PENDING";
        return inTx(connection -> {
            UUID exitId = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO platform.tenant_exit (id, tenant_id, contract_end, level, status,"
                            + " export_due_at, grace_starts_at, grace_ends_at, certificate_due_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, exitId);
                ps.setObject(2, tenantId);
                ps.setDate(3, Date.valueOf(contractEnd));
                ps.setString(4, level.name());
                ps.setString(5, status);
                ps.setTimestamp(6, Timestamp.from(exportDueAt.toInstant()));
                ps.setTimestamp(7, Timestamp.from(graceStartsAt.toInstant()));
                ps.setTimestamp(8, Timestamp.from(graceEndsAt.toInstant()));
                ps.setTimestamp(9, Timestamp.from(certificateDueAt.toInstant()));
                ps.executeUpdate();
            }
            if (readonlyEndsAt != null) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO platform.tenant_readonly_period (tenant_id, exit_id, starts_at,"
                                + " ends_at, status, created_by) VALUES (?, ?, ?, ?, 'ACTIVE', ?)")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, exitId);
                    ps.setTimestamp(3, Timestamp.from(contractEndAt.toInstant()));
                    ps.setTimestamp(4, Timestamp.from(readonlyEndsAt.toInstant()));
                    ps.setObject(5, actorId);
                    ps.executeUpdate();
                }
            }
            return new ExitState(exitId, status, level, contractEnd, exportDueAt,
                    graceStartsAt, graceEndsAt, certificateDueAt);
        });
    }

    /** Portao de escrita durante a consulta: le o periodo ativo e aplica a regra pura. */
    public void requireWriteAllowedNow() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT starts_at, ends_at FROM platform.tenant_readonly_period"
                            + " WHERE tenant_id = ? AND status = 'ACTIVE'")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        TenantExitCalculator.requireWriteAllowed(
                                rs.getTimestamp(1).toInstant().atOffset(ZoneOffset.UTC),
                                rs.getTimestamp(2).toInstant().atOffset(ZoneOffset.UTC), now);
                    }
                }
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Exportacao: registro da geracao e dos downloads
    // ------------------------------------------------------------------

    public void registerExportGenerated(TenantExitExport.Format format, String exportHash) {
        if (format == null || exportHash == null || exportHash.isBlank()) {
            throw new IllegalArgumentException("format e exportHash sao obrigatorios");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE platform.tenant_exit SET export_format = ?, export_generated_at = ?,"
                            + " export_hash = ?, updated_at = NOW() WHERE tenant_id = ?")) {
                ps.setString(1, format.name());
                ps.setTimestamp(2, Timestamp.from(now.toInstant()));
                ps.setString(3, exportHash);
                ps.setObject(4, tenantId);
                requireUpdated(ps.executeUpdate(), "tenant_exit");
            }
            return null;
        });
    }

    /** Download da exportacao registrado (aceite: download e contestacao registradas). */
    public void recordDownload(UUID downloadedBy) {
        inTx(connection -> {
            UUID exitId = exitId(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO platform.tenant_exit_download (exit_id, tenant_id, downloaded_by)"
                            + " VALUES (?, ?, ?)")) {
                ps.setObject(1, exitId);
                ps.setObject(2, tenantId);
                ps.setObject(3, downloadedBy);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Contestacao e ordem judicial
    // ------------------------------------------------------------------

    /** Registra contestacao na carencia; fora dela, recusa pela regra pura. Devolve o vencimento da resposta. */
    public OffsetDateTime registerContestation(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject da contestacao e obrigatorio");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        return inTx(connection -> {
            ExitState state = loadState(connection);
            TenantExitCalculator.requireContestationInGrace(
                    state.graceStartsAt(), state.graceEndsAt(), now);
            OffsetDateTime dueAt = TenantExitCalculator.contestationResponseDueAt(now);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO platform.tenant_exit_contestation (exit_id, tenant_id, registered_at,"
                            + " subject, response_due_at) VALUES (?, ?, ?, ?, ?)")) {
                ps.setObject(1, state.exitId());
                ps.setObject(2, tenantId);
                ps.setTimestamp(3, Timestamp.from(now.toInstant()));
                ps.setString(4, subject);
                ps.setTimestamp(5, Timestamp.from(dueAt.toInstant()));
                ps.executeUpdate();
            }
            return dueAt;
        });
    }

    /** Resposta do Ziip a contestacao; recalcula o vencimento do certificado com a ultima resposta. */
    public void respondContestation(UUID contestationId, String responseRef) {
        if (contestationId == null || responseRef == null || responseRef.isBlank()) {
            throw new IllegalArgumentException("contestationId e responseRef sao obrigatorios");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE platform.tenant_exit_contestation SET responded_at = ?, response_ref = ?"
                            + " WHERE id = ? AND tenant_id = ? AND responded_at IS NULL")) {
                ps.setTimestamp(1, Timestamp.from(now.toInstant()));
                ps.setString(2, responseRef);
                ps.setObject(3, contestationId);
                ps.setObject(4, tenantId);
                requireUpdated(ps.executeUpdate(), "tenant_exit_contestation");
            }
            ExitState state = loadState(connection);
            OffsetDateTime lastResponse = lastContestationResponse(connection);
            OffsetDateTime dueAt = TenantExitCalculator.certificateDueAt(
                    state.graceStartsAt(), state.graceEndsAt(), lastResponse);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE platform.tenant_exit SET certificate_due_at = ?, updated_at = NOW()"
                            + " WHERE tenant_id = ?")) {
                ps.setTimestamp(1, Timestamp.from(dueAt.toInstant()));
                ps.setObject(2, tenantId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Contestacoes abertas cujo alerta da Central esta devido no relogio injetado. */
    public List<ContestationAlert> contestationsDueForAlert() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return inTx(connection -> {
            List<ContestationAlert> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, response_due_at FROM platform.tenant_exit_contestation"
                            + " WHERE tenant_id = ? AND responded_at IS NULL AND alert_sent_at IS NULL"
                            + " ORDER BY response_due_at")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        OffsetDateTime dueAt = rs.getTimestamp(2).toInstant().atOffset(ZoneOffset.UTC);
                        if (TenantExitCalculator.contestationAlertDue(now, dueAt, false)) {
                            out.add(new ContestationAlert((UUID) rs.getObject(1), dueAt,
                                    TenantExitCalculator.contestationOverdue(now, dueAt, false)));
                        }
                    }
                }
            }
            return out;
        });
    }

    public void markContestationAlertSent(UUID contestationId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE platform.tenant_exit_contestation SET alert_sent_at = ?"
                            + " WHERE id = ? AND tenant_id = ?")) {
                ps.setTimestamp(1, Timestamp.from(now.toInstant()));
                ps.setObject(2, contestationId);
                ps.setObject(3, tenantId);
                requireUpdated(ps.executeUpdate(), "tenant_exit_contestation");
            }
            return null;
        });
    }

    /** Ordem judicial registrada: suspende a execucao enquanto nao levantada. */
    public void registerCourtOrder(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference da ordem judicial e obrigatoria");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        inTx(connection -> {
            UUID exitId = exitId(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO platform.tenant_exit_court_order (exit_id, tenant_id, reference,"
                            + " registered_at) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, exitId);
                ps.setObject(2, tenantId);
                ps.setString(3, reference);
                ps.setTimestamp(4, Timestamp.from(now.toInstant()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void liftCourtOrder(UUID courtOrderId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE platform.tenant_exit_court_order SET lifted_at = ?"
                            + " WHERE id = ? AND tenant_id = ? AND lifted_at IS NULL")) {
                ps.setTimestamp(1, Timestamp.from(now.toInstant()));
                ps.setObject(2, courtOrderId);
                ps.setObject(3, tenantId);
                requireUpdated(ps.executeUpdate(), "tenant_exit_court_order");
            }
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Agendamento, cancelamento e execucao
    // ------------------------------------------------------------------

    /** Agendamento: portao de papel/MFA ANTES de abrir a transacao (padrao P3.19). */
    public void scheduleElimination(String actorRole, boolean mfaVerified, UUID actorId) {
        TenantExitCalculator.requireSchedulingAllowed(actorRole, mfaVerified);
        OffsetDateTime now = OffsetDateTime.now(clock);
        inTx(connection -> {
            TenantExitCalculator.requireSchedulingStateAllowed(loadState(connection).status());
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE platform.tenant_exit SET status = 'ELIMINATION_SCHEDULED',"
                            + " scheduled_by = ?, scheduled_at = ?, scheduled_mfa = TRUE,"
                            + " updated_at = NOW() WHERE tenant_id = ?")) {
                ps.setObject(1, actorId);
                ps.setTimestamp(2, Timestamp.from(now.toInstant()));
                ps.setObject(3, tenantId);
                requireUpdated(ps.executeUpdate(), "tenant_exit");
            }
            return null;
        });
    }

    /** Cancelamento do agendamento: so durante a carencia. */
    public void cancelElimination(UUID actorId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        inTx(connection -> {
            ExitState state = loadState(connection);
            TenantExitCalculator.requireEliminationScheduled(state.status());
            TenantExitCalculator.requireCancellationAllowed(
                    state.graceStartsAt(), state.graceEndsAt(), now);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE platform.tenant_exit SET status = 'CANCELLED', cancelled_at = ?,"
                            + " cancelled_by = ?, updated_at = NOW() WHERE tenant_id = ?")) {
                ps.setTimestamp(1, Timestamp.from(now.toInstant()));
                ps.setObject(2, actorId);
                ps.setObject(3, tenantId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Execucao da eliminacao: (1) portao arquivistico da P3.19 para
     * natureza publica, ANTES de qualquer escrita; (2) portao de
     * carencia/suspensoes; (3) crypto-shredding confirmado pela porta
     * da P0.28; (4) certificado com hashes e expiracoes e o ciclo de
     * vida marcado DELETED com o hash do certificado (V9).
     * Devolve o JSON do certificado (via {@code Json} do core).
     */
    public String executeElimination(String legalNature,
                                     TicketRetentionService.ArchivalApproval approval,
                                     List<TenantExitExport.ManifestEntry> manifest,
                                     OffsetDateTime backupExpiresAt,
                                     OffsetDateTime immutableCopyExpiresAt,
                                     KeyDestruction keyDestruction) {
        TicketRetentionService.requirePurgeAllowed(legalNature, approval);
        if (keyDestruction == null || backupExpiresAt == null || immutableCopyExpiresAt == null
                || manifest == null || manifest.isEmpty()) {
            throw new IllegalArgumentException("keyDestruction, backupExpiresAt, "
                    + "immutableCopyExpiresAt e manifesto com hash por arquivo sao obrigatorios: "
                    + "sem expiracao conhecida e sem hashes nao ha certificado");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        return inTx(connection -> {
            ExitState state = loadState(connection);
            TenantExitCalculator.requireEliminationScheduled(state.status());
            TenantExitCalculator.Holds holds = new TenantExitCalculator.Holds(
                    hasOpenContestation(connection), hasActiveCourtOrder(connection));
            TenantExitCalculator.requireExecutionAllowed(state.graceEndsAt(), now, holds);
            String exportHash = exportHash(connection);
            if (exportHash == null) {
                throw new TenantExitCalculator.ExitRefusedException("export_missing",
                        "Execucao recusada: exportacao de fim de contrato nao foi gerada/registrada.");
            }
            OffsetDateTime destroyedAt = keyDestruction.destroy(tenantId);
            if (destroyedAt == null) {
                throw new TenantExitCalculator.ExitRefusedException("crypto_shredding_unconfirmed",
                        "Execucao recusada: provedor de chaves nao confirmou a destruicao da DEK.");
            }
            var certificate = TenantExitExport.certificate(tenantId.toString(), state.contractEnd(),
                    exportHash, manifest, destroyedAt, backupExpiresAt, immutableCopyExpiresAt, now);
            String certificateJson = Json.write(certificate);
            String certificateHash = TenantExitExport.sha256Hex(certificateJson);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE platform.tenant_exit SET status = 'ELIMINATED', executed_at = ?,"
                            + " key_destroyed_at = ?, backup_expires_at = ?,"
                            + " immutable_copy_expires_at = ?, certificate_hash = ?,"
                            + " certificate_issued_at = ?, updated_at = NOW() WHERE tenant_id = ?")) {
                ps.setTimestamp(1, Timestamp.from(now.toInstant()));
                ps.setTimestamp(2, Timestamp.from(destroyedAt.toInstant()));
                ps.setTimestamp(3, Timestamp.from(backupExpiresAt.toInstant()));
                ps.setTimestamp(4, Timestamp.from(immutableCopyExpiresAt.toInstant()));
                ps.setString(5, certificateHash);
                ps.setTimestamp(6, Timestamp.from(now.toInstant()));
                ps.setObject(7, tenantId);
                requireUpdated(ps.executeUpdate(), "tenant_exit");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE platform.tenant_lifecycle SET status = 'DELETED',"
                            + " deletion_certificate_hash = ?, deletion_certificate_issued_at = ?,"
                            + " updated_at = NOW() WHERE tenant_id = ?")) {
                ps.setString(1, certificateHash);
                ps.setTimestamp(2, Timestamp.from(now.toInstant()));
                ps.setObject(3, tenantId);
                ps.executeUpdate();
            }
            return certificateJson;
        });
    }

    // ------------------------------------------------------------------
    // Infra interna (mesmo padrao do TicketRetentionService)
    // ------------------------------------------------------------------

    private static void requireUpdated(int updated, String table) {
        if (updated != 1) {
            throw new TenantExitCalculator.ExitRefusedException("exit_not_found",
                    "Processo de saida nao encontrado ou nao atualizavel em " + table
                            + " para o tenant.");
        }
    }

    private UUID exitId(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM platform.tenant_exit WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new TenantExitCalculator.ExitRefusedException("exit_not_found",
                            "Sem processo de saida aberto para o tenant.");
                }
                return (UUID) rs.getObject(1);
            }
        }
    }

    private ExitState loadState(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, status, level, contract_end, export_due_at, grace_starts_at,"
                        + " grace_ends_at, certificate_due_at FROM platform.tenant_exit"
                        + " WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new TenantExitCalculator.ExitRefusedException("exit_not_found",
                            "Sem processo de saida aberto para o tenant.");
                }
                return new ExitState((UUID) rs.getObject(1), rs.getString(2),
                        TenantExitCalculator.Level.valueOf(rs.getString(3)),
                        rs.getDate(4).toLocalDate(),
                        rs.getTimestamp(5).toInstant().atOffset(ZoneOffset.UTC),
                        rs.getTimestamp(6).toInstant().atOffset(ZoneOffset.UTC),
                        rs.getTimestamp(7).toInstant().atOffset(ZoneOffset.UTC),
                        rs.getTimestamp(8).toInstant().atOffset(ZoneOffset.UTC));
            }
        }
    }

    private boolean hasOpenContestation(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM platform.tenant_exit_contestation"
                        + " WHERE tenant_id = ? AND responded_at IS NULL")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        }
    }

    private boolean hasActiveCourtOrder(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM platform.tenant_exit_court_order"
                        + " WHERE tenant_id = ? AND lifted_at IS NULL")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        }
    }

    private OffsetDateTime lastContestationResponse(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT MAX(responded_at) FROM platform.tenant_exit_contestation WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                Timestamp ts = rs.getTimestamp(1);
                return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
            }
        }
    }

    private String exportHash(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT export_hash FROM platform.tenant_exit WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
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
                throw new IllegalStateException("Tenant exit operation failed: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Tenant exit operation failed: " + e.getMessage(), e);
        }
    }
}
