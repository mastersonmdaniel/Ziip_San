package br.com.ziipsan.core.access;

import br.com.ziipsan.core.audit.UuidV7;
import br.com.ziipsan.core.json.Json;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência do acesso assistido e do acesso de emergência (P3.17;
 * ADR-0020; tabelas da core {@code V10_3}), no padrão transacional do
 * {@code TicketService}: cada operação roda em transação própria com o
 * contexto RLS do tenant ({@code app.current_tenant}).
 *
 * <p>Este serviço é a implementação JDBC das portas do {@link AccessGuard}
 * ({@link AccessAuditPort}, {@link IncidentSessionRegistry} e
 * {@link EmergencyNoticePort}):
 * <ul>
 *   <li>{@code audit_log} do tenant: {@code public.audit_log}, com
 *       {@code entity_type = 'ACCESS_SESSION'}, a ação mapeada da operação
 *       e o {@code metadata} em JSON pela classe {@code Json} do core —
 *       com o {@code consentId} no assistido e o {@code incidentId} na
 *       emergência; negação grava {@code success = false} e o código do
 *       motivo em {@code error_message};</li>
 *   <li>{@code audit_event} da plataforma: {@code platform.audit_event},
 *       só emergência, com os mesmos detalhes JSON;</li>
 *   <li>registro no incidente: a sessão de emergência é persistida com o
 *       {@code incident_id} e lida de volta por
 *       {@link #sessionsByIncident(UUID)} — é essa consulta que materializa
 *       "a sessão no registro do incidente" enquanto a P3.10 não confirma
 *       tabela própria do registro (ver a {@code V10_3});</li>
 *   <li>aviso ao tenant: linha em {@code public.outbox} (P1.9) na mesma
 *       transação da abertura, {@code type = 'support.emergency-access-notice'},
 *       {@code dedup_key = 'emergency-access-notice:<sessionId>'} e payload
 *       SEM dado pessoal (só ids e instantes, pela classe {@code Json}).
 *       O envio é imediato na abertura — muito antes do teto de 24 h
 *       ({@code noticeDueAt} da sessão); {@code notice_sent_at} registra o
 *       instante da gravação na outbox.</li>
 * </ul>
 *
 * <p>Limites declarados (não omissões silenciosas):
 * <ul>
 *   <li>a DECISÃO não mora aqui: quem autoriza é o {@link AccessGuard}
 *       com a {@link AccessPolicy} pura. Este serviço grava consentimento,
 *       sessão, revogação e trilhas; ele não reinterpreta a regra;</li>
 *   <li>o encerramento das sessões marcadas com {@code must_end_by}
 *       (revogação + 1 min) é varredura de job: {@link #closeDueSessions()}
 *       fecha as vencidas e devolve quantas fechou, mas AGENDAR a
 *       varredura (a cada minuto, para o teto valer) é da integração —
 *       o portão nega desde a revogação mesmo sem a varredura;</li>
 *   <li>{@link SecurityIncidentLookup} NÃO é implementado aqui: o registro
 *       do incidente da P3.10 não tem tabela confirmada, e consultar uma
 *       tabela inventada seria pior que declarar a porta. A fiação lê o
 *       registro real na integração;</li>
 *   <li>a revogação grava {@code must_end_by} nas sessões abertas do
 *       consentimento na mesma transação em que revoga o consentimento.</li>
 * </ul>
 */
public final class AccessSessionService implements AccessAuditPort, IncidentSessionRegistry,
        EmergencyNoticePort {

    /** Tipo da mensagem de aviso ao tenant na outbox (P1.9). */
    public static final String EMERGENCY_NOTICE_TYPE = "support.emergency-access-notice";

    /** Fila da outbox do aviso de emergência. */
    public static final String EMERGENCY_NOTICE_QUEUE = "notifications";

    private final DataSource dataSource;
    private final UUID tenantId;
    private final Clock clock;

    public AccessSessionService(DataSource dataSource, UUID tenantId, Clock clock) {
        if (dataSource == null || tenantId == null || clock == null) {
            throw new IllegalArgumentException("dataSource, tenantId e clock sao obrigatorios");
        }
        this.dataSource = dataSource;
        this.tenantId = tenantId;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Consentimento
    // ------------------------------------------------------------------

    /** Grava a concessão do consentimento (a concessão em si é ato do tenant, fora do portão). */
    public void saveConsent(AssistedConsent consent) {
        Objects.requireNonNull(consent, "consent é obrigatório");
        requireTenant(consent.tenantId());
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO support.assisted_consent (id, tenant_id, ticket_id, granted_by,"
                            + " beneficiary_id, scope, granted_at, expires_at, revoked_at, revoked_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, consent.id());
                ps.setObject(2, tenantId);
                setUuid(ps, 3, consent.ticketId());
                ps.setObject(4, consent.grantedBy());
                ps.setObject(5, consent.beneficiaryId());
                ps.setString(6, consent.scope());
                ps.setTimestamp(7, Timestamp.from(consent.grantedAt()));
                ps.setTimestamp(8, Timestamp.from(consent.expiresAt()));
                setInstant(ps, 9, consent.revokedAt());
                setUuid(ps, 10, consent.revokedBy());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Lê o consentimento vigente (ou revogado) pelo id, no contexto do tenant. */
    public Optional<AssistedConsent> findConsent(UUID consentId) {
        Objects.requireNonNull(consentId, "consentId é obrigatório");
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, tenant_id, ticket_id, granted_by, beneficiary_id, scope,"
                            + " granted_at, expires_at, revoked_at, revoked_by"
                            + " FROM support.assisted_consent WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, consentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.<AssistedConsent>empty();
                    }
                    return Optional.of(new AssistedConsent(
                            rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                            rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                            rs.getObject(5, UUID.class), rs.getString(6),
                            rs.getTimestamp(7).toInstant(), rs.getTimestamp(8).toInstant(),
                            toInstant(rs.getTimestamp(9)), rs.getObject(10, UUID.class)));
                }
            }
        });
    }

    /**
     * Revoga o consentimento e marca as sessões abertas dele com
     * {@code must_end_by = revokedAt + 1 min}, na mesma transação.
     * Devolve o consentimento revogado (cópia imutável de
     * {@link AssistedConsent#revoke(Instant, UUID)}).
     */
    public AssistedConsent revokeConsent(UUID consentId, UUID revokedBy) {
        AssistedConsent current = findConsent(consentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "consentimento nao localizado neste tenant: " + consentId));
        Instant now = clock.instant();
        AssistedConsent revoked = current.revoke(now, revokedBy);
        Instant mustEndBy = now.plus(AccessSession.REVOCATION_CLOSE_LIMIT);
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE support.assisted_consent SET revoked_at = ?, revoked_by = ?,"
                            + " updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.setObject(2, revokedBy);
                ps.setObject(3, tenantId);
                ps.setObject(4, consentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE support.access_session SET must_end_by = ?"
                            + " WHERE tenant_id = ? AND consent_id = ? AND ended_at IS NULL")) {
                ps.setTimestamp(1, Timestamp.from(mustEndBy));
                ps.setObject(2, tenantId);
                ps.setObject(3, consentId);
                ps.executeUpdate();
            }
            return null;
        });
        return revoked;
    }

    // ------------------------------------------------------------------
    // Sessões
    // ------------------------------------------------------------------

    /** Persiste a sessão aberta pelo {@link AccessGuard}. */
    public void saveSession(AccessSession session) {
        Objects.requireNonNull(session, "session é obrigatória");
        requireTenant(session.tenantId());
        inTx(c -> {
            insertSession(c, session);
            return null;
        });
    }

    /** Sessões de emergência registradas para o incidente (a visão do registro do incidente). */
    public List<AccessSession> sessionsByIncident(UUID incidentId) {
        Objects.requireNonNull(incidentId, "incidentId é obrigatório");
        return inTx(c -> {
            List<AccessSession> sessions = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, tenant_id, mode, actor_id, actor_role, consent_id, incident_id,"
                            + " ticket_id, started_at, mfa_step_up_at, ended_at, end_reason,"
                            + " must_end_by, notice_due_at, notice_sent_at"
                            + " FROM support.access_session"
                            + " WHERE tenant_id = ? AND incident_id = ? ORDER BY started_at")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, incidentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        sessions.add(readSession(rs));
                    }
                }
            }
            return List.copyOf(sessions);
        });
    }

    /**
     * Fecha as sessões cujo encerramento já é devido ({@code must_end_by}
     * alcançado, ex.: revogação + 1 min). Devolve quantas fechou. A
     * varredura em si é chamada por job da integração (ver javadoc).
     */
    public int closeDueSessions() {
        Instant now = clock.instant();
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE support.access_session SET ended_at = ?, end_reason = 'REVOCATION_LIMIT'"
                            + " WHERE tenant_id = ? AND ended_at IS NULL AND must_end_by IS NOT NULL"
                            + " AND must_end_by <= ?")) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.setObject(2, tenantId);
                ps.setTimestamp(3, Timestamp.from(now));
                return ps.executeUpdate();
            }
        });
    }

    // ------------------------------------------------------------------
    // Portas do guard: auditoria, registro no incidente, aviso
    // ------------------------------------------------------------------

    @Override
    public void appendTenantAudit(AccessAuditEntry entry) {
        requireTenant(entry.tenantId());
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO public.audit_log (id, tenant_id, entity_type, entity_id, action,"
                            + " user_id, metadata, success, error_message, created_at)"
                            + " VALUES (?, ?, 'ACCESS_SESSION', ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)")) {
                ps.setObject(1, UuidV7.generate());
                ps.setObject(2, tenantId);
                ps.setObject(3, entry.sessionId() != null ? entry.sessionId() : entry.requestId());
                ps.setString(4, auditAction(entry.operation()));
                ps.setObject(5, entry.actorId());
                ps.setString(6, entry.detailsJson());
                ps.setBoolean(7, entry.allowed());
                setText(ps, 8, entry.allowed() ? null : entry.decisionCode());
                ps.setTimestamp(9, Timestamp.from(entry.occurredAt()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void appendPlatformAudit(AccessAuditEntry entry) {
        requireTenant(entry.tenantId());
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO platform.audit_event (tenant_id, actor_id, actor_role, action, details)"
                            + " VALUES (?, ?, ?, ?, CAST(? AS jsonb))")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, entry.actorId());
                ps.setString(3, entry.actorRole());
                ps.setString(4, entry.allowed()
                        ? "EMERGENCY_ACCESS" : "EMERGENCY_ACCESS_DENIED");
                ps.setString(5, entry.detailsJson());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Registro da sessão no incidente: persiste a sessão com o
     * {@code incident_id} (a leitura é {@link #sessionsByIncident(UUID)}).
     */
    @Override
    public void registerSessionInIncident(AccessSession session) {
        saveSession(session);
    }

    /**
     * Aviso ao tenant pela outbox, na abertura: grava a linha em
     * {@code public.outbox} e marca {@code notice_sent_at} da sessão com o
     * instante da gravação. O payload não tem dado pessoal: só ids,
     * o modo e os instantes (JSON pela classe {@code Json} do core).
     */
    @Override
    public void scheduleTenantNotice(AccessSession session) {
        Objects.requireNonNull(session, "session é obrigatória");
        requireTenant(session.tenantId());
        Instant now = clock.instant();
        String payloadJson = emergencyNoticePayload(session);
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT public.outbox_ensure_partition((NOW() AT TIME ZONE 'UTC')::DATE)")) {
                ps.executeQuery().close();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO public.outbox (id, tenant_id, queue, type, dedup_key, payload,"
                            + " status, attempts, available_at, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), 'PENDING', 0, ?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setString(3, EMERGENCY_NOTICE_QUEUE);
                ps.setString(4, EMERGENCY_NOTICE_TYPE);
                ps.setString(5, "emergency-access-notice:" + session.id());
                ps.setString(6, payloadJson);
                ps.setTimestamp(7, Timestamp.from(now));
                ps.setTimestamp(8, Timestamp.from(now));
                ps.setTimestamp(9, Timestamp.from(now));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE support.access_session SET notice_sent_at = ?"
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.setObject(2, tenantId);
                ps.setObject(3, session.id());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Payload do aviso de emergência (puro, para o teste conferir sem banco). */
    public static String emergencyNoticePayload(AccessSession session) {
        Objects.requireNonNull(session, "session é obrigatória");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.id().toString());
        payload.put("incidentId", session.incidentId() == null ? null : session.incidentId().toString());
        payload.put("actorRole", session.actorRole());
        payload.put("startedAt", session.startedAt().toString());
        payload.put("noticeDueAt", session.noticeDueAt() == null ? null : session.noticeDueAt().toString());
        return Json.write(payload);
    }

    /** Chave de deduplicação do aviso na outbox: uma por sessão. */
    public static String emergencyNoticeDedupKey(AccessSession session) {
        Objects.requireNonNull(session, "session é obrigatória");
        return "emergency-access-notice:" + session.id();
    }

    // ------------------------------------------------------------------
    // Infraestrutura (padrao TicketService/NotificationCenterService)
    // ------------------------------------------------------------------

    /** Ação do audit_log (domínio de AuditLog.AuditAction) por operação. */
    static String auditAction(AccessOperation operation) {
        return switch (operation) {
            case READ, REVEAL_DOCUMENT -> "READ";
            case WRITE -> "UPDATE";
            case EXPORT -> "EXPORT";
        };
    }

    private void insertSession(Connection c, AccessSession session) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO support.access_session (id, tenant_id, mode, actor_id, actor_role,"
                        + " consent_id, incident_id, ticket_id, started_at, mfa_step_up_at,"
                        + " ended_at, end_reason, must_end_by, notice_due_at, notice_sent_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, session.id());
            ps.setObject(2, tenantId);
            ps.setString(3, session.mode().name());
            ps.setObject(4, session.actorId());
            ps.setString(5, session.actorRole());
            setUuid(ps, 6, session.consentId());
            setUuid(ps, 7, session.incidentId());
            setUuid(ps, 8, session.ticketId());
            ps.setTimestamp(9, Timestamp.from(session.startedAt()));
            setInstant(ps, 10, session.mfaStepUpAt());
            setInstant(ps, 11, session.endedAt());
            setText(ps, 12, session.endReason());
            setInstant(ps, 13, session.mustEndBy());
            setInstant(ps, 14, session.noticeDueAt());
            setInstant(ps, 15, session.noticeSentAt());
            ps.executeUpdate();
        }
    }

    private AccessSession readSession(ResultSet rs) throws SQLException {
        return new AccessSession(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                AccessMode.valueOf(rs.getString(3)), rs.getObject(4, UUID.class), rs.getString(5),
                rs.getObject(6, UUID.class), rs.getObject(7, UUID.class), rs.getObject(8, UUID.class),
                rs.getTimestamp(9).toInstant(), toInstant(rs.getTimestamp(10)),
                toInstant(rs.getTimestamp(11)), rs.getString(12), toInstant(rs.getTimestamp(13)),
                toInstant(rs.getTimestamp(14)), toInstant(rs.getTimestamp(15)));
    }

    private void requireTenant(UUID other) {
        if (!tenantId.equals(other)) {
            throw new IllegalArgumentException("tenant divergente do servico: " + other);
        }
    }

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws SQLException;
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
            } catch (RuntimeException | SQLException e) {
                connection.rollback();
                throw e instanceof RuntimeException re ? re
                        : new IllegalStateException("Operacao de acesso falhou: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Operacao de acesso falhou: " + e.getMessage(), e);
        }
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setTimestamp(index, Timestamp.from(value));
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
