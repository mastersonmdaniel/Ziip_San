package br.com.ziipsan.platform.notification;

import br.com.ziipsan.core.api.ApiException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Central de notificações do usuário (P3.36): sino, lista, lido/não lido e o registro único
 * de preferências por canal e finalidade. Mesmo padrão transacional do
 * {@code TenantParameterService}: cada método roda em transação própria com
 * {@code app.current_tenant} local (as tabelas têm RLS forçada; sem a GUC a policy esconderia
 * as linhas) e o schema do tenant é explícito no nome qualificado da tabela.
 *
 * <p>Regras aplicadas aqui (as puras vivem em {@link NotificationPolicy} e
 * {@link NotificationIngestRule}, testadas sem banco):
 * ingestão só de evento do catálogo; ingestão IDEMPOTENTE pela chave da mensagem da outbox
 * ({@code source_message_id}); ingestão que APLICA as preferências do destinatário e o
 * {@code notify.digest_default} do tenant à linha IN_APP (correção pós-revisão — ver
 * {@link #planIngest}); preferência de categoria obrigatória não desliga; preferência do
 * CONSUMIDOR exige contato verificado + o consentimento EXATO gravado ({@code consentId})
 * vigente (porta da P1.3); preferência com sujeito ambíguo (usuário e consumidor juntos)
 * é recusada, nunca resolvida em silêncio; leitura é registrada uma única vez (o primeiro
 * {@code read_at} nunca é sobrescrito). Um aviso retido (resumo/silêncio) só aparece na
 * lista e no sino quando o seu {@code deliver_after} chega — reter não é descartar.
 */
public class NotificationCenterService {

    /** Aviso como a lista/sino o mostram. {@code readAt} nulo = não lido. */
    public record NotificationView(UUID id, String eventName, NotificationCategory category,
                                   String titleCode, String bodyCode, Instant createdAt, Instant readAt) {
    }

    /** Recorte da preferência que a ingestão consome: só o canal IN_APP, só a categoria do aviso. */
    record InAppPreference(boolean enabled, DigestMode digestMode, LocalTime silenceStart, LocalTime silenceEnd) {
    }

    /** Plano de ingestão: suprimido pela preferência (sem linha) ou a linha com o seu {@code deliver_after}. */
    record IngestPlan(boolean suppressed, Instant deliverAfter) {
    }

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;
    private final BusinessEventCatalog catalog;
    private final ConsentGate consentGate;
    private final NotificationParameters parameters;
    private final ZoneId tenantZone;
    private final Clock clock;

    public NotificationCenterService(DataSource dataSource, String schema, UUID tenantId,
                                     BusinessEventCatalog catalog, ConsentGate consentGate,
                                     NotificationParameters parameters, ZoneId tenantZone, Clock clock) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null
                || catalog == null || consentGate == null || parameters == null || tenantZone == null || clock == null) {
            throw new IllegalArgumentException("dataSource, schema, tenantId, catálogo, consentimento, parâmetros, fuso e relógio são obrigatórios");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
        this.catalog = catalog;
        this.consentGate = consentGate;
        this.parameters = parameters;
        this.tenantZone = tenantZone;
        this.clock = clock;
    }

    private String t(String table) {
        return "\"" + schema.replace("\"", "\"\"") + "\"." + table;
    }

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws Exception;
    }

    private <T> T inTx(Tx<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
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
                throw new IllegalStateException("Operação da central de notificações falhou: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Operação da central de notificações falhou: " + e.getMessage(), e);
        }
    }

    /**
     * Ingere uma mensagem da outbox (P1.9), aplicando as preferências do destinatário.
     * Recusa de catálogo/carga propaga a {@link ApiException} da regra — o worker marca a
     * mensagem conforme a sua política de posse/reenvio; a central não grava aviso parcial.
     *
     * <p>Fluxo (correção pós-revisão — antes a ingestão ignorava as preferências e gravava
     * sempre {@code deliver_after} = agora):
     * <ol>
     *   <li><b>Idempotência:</b> se já existe aviso com o {@code source_message_id} desta
     *       mensagem, devolve o id dele — a reentrega não duplica (o índice único parcial
     *       da V1_15 é a trava de banco; a corrida entre duas ingestões simultâneas resolve
     *       no {@code ON CONFLICT DO NOTHING} + releitura);</li>
     *   <li><b>Fiação das preferências:</b> lê as preferências IN_APP do destinatário para a
     *       categoria do aviso e monta o plano por {@link #planIngest} com a
     *       {@link NotificationPolicy}: canal desligado (categoria não obrigatória) SUPRIME
     *       a linha; resumo diário (da preferência ou do {@code notify.digest_default} do
     *       tenant) e silêncio vigente RETÊM a linha até o {@code deliver_after} calculado;
     *       categoria obrigatória ignora preferência, resumo e silêncio — entrega já;</li>
     *   <li>grava a linha IN_APP com o {@code deliver_after} do plano e a chave de dedup.</li>
     * </ol>
     * Devolve o id do aviso (o novo, ou o existente na reentrega), ou vazio quando a
     * preferência suprimiu a linha. Entregas externas (e-mail/push) NÃO são enviadas aqui:
     * a P3.5 lê as preferências deste registro e aplica a mesma política às suas linhas.
     */
    public Optional<UUID> ingest(OutboxMessage message) {
        NotificationIngestRule.NotificationDraft draft = NotificationIngestRule.draft(message, catalog);
        if (!tenantId.equals(draft.tenantId())) {
            throw new ApiException.Conflict("tenant-mismatch", "A mensagem da outbox não pertence a este tenant.");
        }
        Set<NotificationCategory> tenantMandatory = parameters.tenantMandatoryCategories(tenantId);
        DigestMode tenantDigestDefault = parameters.digestDefault(tenantId);
        Instant now = clock.instant();
        UUID newId = UUID.randomUUID();
        return inTx(c -> {
            UUID existing = findBySourceMessage(c, draft.sourceMessageId());
            if (existing != null) {
                return Optional.of(existing);
            }
            List<InAppPreference> preferences = loadInAppPreferences(c, draft);
            IngestPlan plan = planIngest(draft, preferences, tenantMandatory, tenantDigestDefault, now, tenantZone);
            if (plan.suppressed()) {
                return Optional.empty();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("notifications")
                            + " (id, tenant_id, recipient_user_id, recipient_customer_id, event_name, event_version,"
                            + " category, title_code, body_code, payload, channel, source_message_id, deliver_after, created_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'IN_APP', ?, ?, ?)"
                            + " ON CONFLICT (tenant_id, source_message_id) WHERE source_message_id IS NOT NULL DO NOTHING"
                            + " RETURNING id")) {
                ps.setObject(1, newId);
                ps.setObject(2, tenantId);
                ps.setObject(3, draft.recipientUserId());
                ps.setObject(4, draft.recipientCustomerId());
                ps.setString(5, draft.eventName());
                ps.setInt(6, draft.eventVersion());
                ps.setString(7, draft.category().name());
                ps.setString(8, draft.titleCode());
                ps.setString(9, draft.bodyCode());
                ps.setString(10, draft.payloadJson());
                ps.setObject(11, draft.sourceMessageId());
                ps.setTimestamp(12, Timestamp.from(plan.deliverAfter()));
                ps.setTimestamp(13, Timestamp.from(now));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(rs.getObject(1, UUID.class));
                    }
                }
            }
            // Corrida: outra ingestão da mesma mensagem gravou primeiro (ON CONFLICT segurou).
            UUID raced = findBySourceMessage(c, draft.sourceMessageId());
            if (raced == null) {
                throw new IllegalStateException("Ingestão não gravou nem encontrou aviso para a mensagem " + draft.sourceMessageId());
            }
            return Optional.of(raced);
        });
    }

    /** O aviso já gerado por esta mensagem da outbox, se houver (chave de dedup da ingestão). */
    private UUID findBySourceMessage(Connection connection, UUID sourceMessageId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM " + t("notifications")
                        + " WHERE tenant_id = ? AND source_message_id = ? AND channel = 'IN_APP'")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, sourceMessageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        }
    }

    /** Preferências IN_APP do destinatário do aviso, restritas à categoria dele (todas as finalidades). */
    private List<InAppPreference> loadInAppPreferences(Connection connection,
                                                       NotificationIngestRule.NotificationDraft draft) throws SQLException {
        List<InAppPreference> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT enabled, digest_mode, silence_start, silence_end FROM " + t("notification_preferences")
                        + " WHERE channel = 'IN_APP' AND category = ? AND (user_id = ? OR customer_id = ?)")) {
            ps.setString(1, draft.category().name());
            ps.setObject(2, draft.recipientUserId());
            ps.setObject(3, draft.recipientCustomerId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String digest = rs.getString(2);
                    result.add(new InAppPreference(rs.getBoolean(1),
                            digest == null ? null : DigestMode.valueOf(digest),
                            rs.getObject(3, LocalTime.class), rs.getObject(4, LocalTime.class)));
                }
            }
        }
        return result;
    }

    /**
     * O plano de ingestão da linha IN_APP, puro e testável sem banco — é ESTA função que o
     * {@link #ingest} executa (a fiação testada é a fiação real). A carga da outbox não
     * declara finalidade (convenção da porta, ver {@link OutboxMessage}), por isso as
     * preferências de TODAS as finalidades do destinatário para a categoria se combinam
     * pela regra MAIS RESTRITIVA, determinística: desligado vence ligado; {@code DAILY}
     * vence {@code IMMEDIATE}; entre silêncios vigentes, vale o término mais tarde.
     * Categoria obrigatória (piso ∪ tenant) ignora tudo e entrega agora — inclusive uma
     * preferência desligada que a guarda de gravação já teria recusado.
     */
    static IngestPlan planIngest(NotificationIngestRule.NotificationDraft draft, List<InAppPreference> preferences,
                                 Set<NotificationCategory> tenantMandatory, DigestMode tenantDigestDefault,
                                 Instant now, ZoneId zone) {
        if (NotificationPolicy.isMandatory(draft.category(), tenantMandatory)) {
            return new IngestPlan(false, now);
        }
        for (InAppPreference preference : preferences) {
            if (!preference.enabled()) {
                return new IngestPlan(true, null);
            }
        }
        DigestMode digestMode = tenantDigestDefault;
        boolean anyDaily = false;
        boolean anyImmediate = false;
        for (InAppPreference preference : preferences) {
            if (preference.digestMode() == DigestMode.DAILY) {
                anyDaily = true;
            } else if (preference.digestMode() == DigestMode.IMMEDIATE) {
                anyImmediate = true;
            }
        }
        if (anyDaily) {
            digestMode = DigestMode.DAILY;
        } else if (anyImmediate) {
            digestMode = DigestMode.IMMEDIATE;
        }
        Instant deliverAfter = NotificationPolicy.decide(now, draft.category(), tenantMandatory,
                digestMode, null, null, zone).deliverAfter();
        for (InAppPreference preference : preferences) {
            if (preference.silenceStart() != null && preference.silenceEnd() != null
                    && NotificationPolicy.isSilenced(now, preference.silenceStart(), preference.silenceEnd(), zone)) {
                Instant silenceEnd = NotificationPolicy.silenceEndsAt(now, preference.silenceStart(), preference.silenceEnd(), zone);
                if (silenceEnd.isAfter(deliverAfter)) {
                    deliverAfter = silenceEnd;
                }
            }
        }
        return new IngestPlan(false, deliverAfter);
    }

    /** Lista do usuário (sino), mais recentes primeiro. Só avisos IN_APP do próprio usuário JÁ entregues ({@code deliver_after} vencido). */
    public List<NotificationView> listForUser(UUID userId, int limit) {
        if (userId == null || limit < 1 || limit > 200) {
            throw ApiException.Validation.of(new ApiException.FieldError("limit", "range", "O limite da lista deve estar entre 1 e 200."));
        }
        return inTx(c -> {
            List<NotificationView> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, event_name, category, title_code, body_code, created_at, read_at FROM " + t("notifications")
                            + " WHERE recipient_user_id = ? AND channel = 'IN_APP' AND deliver_after <= ? ORDER BY created_at DESC LIMIT ?")) {
                ps.setObject(1, userId);
                ps.setTimestamp(2, Timestamp.from(clock.instant()));
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new NotificationView(rs.getObject(1, UUID.class), rs.getString(2),
                                NotificationCategory.valueOf(rs.getString(3)), rs.getString(4), rs.getString(5),
                                rs.getTimestamp(6).toInstant(),
                                rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant()));
                    }
                }
            }
            return result;
        });
    }

    /** Contador do sino: não lidos do usuário, entre os já entregues (retido ainda não conta). */
    public long unreadCount(UUID userId) {
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM " + t("notifications")
                            + " WHERE recipient_user_id = ? AND channel = 'IN_APP' AND read_at IS NULL AND deliver_after <= ?")) {
                ps.setObject(1, userId);
                ps.setTimestamp(2, Timestamp.from(clock.instant()));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }

    /**
     * Registra a leitura. Idempotente: reler não muda o primeiro {@code read_at}
     * (a leitura registrada é a PRIMEIRA). Aviso de outro usuário = 404 (não vaza existência).
     */
    public Instant markRead(UUID userId, UUID notificationId) {
        Instant now = clock.instant();
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("notifications") + " SET read_at = COALESCE(read_at, ?)"
                            + " WHERE id = ? AND recipient_user_id = ? RETURNING read_at")) {
                ps.setTimestamp(1, Timestamp.from(now));
                ps.setObject(2, notificationId);
                ps.setObject(3, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("Aviso não encontrado.");
                    }
                    return rs.getTimestamp(1).toInstant();
                }
            }
        });
    }

    /**
     * Grava (upsert) uma preferência. Guardas, nesta ordem: sujeito EXATO (usuário e
     * consumidor juntos — ou nenhum — é ambiguidade recusada com 422, nunca resolvida em
     * silêncio pulando o gate); categoria obrigatória não desliga (422); consumidor exige
     * contato verificado e o consentimento EXATO do {@code consentId} gravado vigente para
     * a finalidade (422 nomeando o campo); silêncio exige o par início/fim (422).
     */
    public void savePreference(NotificationPreference preference) {
        if (preference == null || preference.purposeCode() == null || preference.purposeCode().isBlank()
                || preference.category() == null || preference.channel() == null) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "purposeCode", "required", "Finalidade, categoria e canal são obrigatórios na preferência."));
        }
        boolean hasUser = preference.userId() != null;
        boolean hasCustomer = preference.customerId() != null;
        if (hasUser == hasCustomer) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "customerId", "ambiguous-subject",
                    "A preferência exige exatamente um sujeito: usuário OU consumidor (os dois juntos é ambiguidade recusada)."));
        }
        NotificationPreference.SubjectKind subjectKind = preference.subjectKind();
        if ((preference.silenceStart() == null) != (preference.silenceEnd() == null)) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "silenceStart", "pair-required", "O silêncio por período exige início e fim."));
        }
        Set<NotificationCategory> tenantMandatory = parameters.tenantMandatoryCategories(tenantId);
        if (!preference.enabled()) {
            NotificationPolicy.assertCanDisable(preference.category(), tenantMandatory);
        }
        if (subjectKind == NotificationPreference.SubjectKind.CONSUMER) {
            if (preference.contactId() == null || !consentGate.isVerifiedContact(tenantId, preference.contactId())) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        "contactId", "verified-contact-required", "A preferência do consumidor exige um contato verificado."));
            }
            if (preference.customerId() == null || preference.consentId() == null
                    || !consentGate.hasActiveConsent(tenantId, preference.customerId(), preference.contactId(),
                            preference.consentId(), preference.purposeCode())) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        "consentId", "active-consent-required", "A preferência do consumidor exige consentimento vigente para a finalidade."));
            }
        }
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("notification_preferences")
                            + " (id, tenant_id, user_id, customer_id, contact_id, consent_id, purpose_code, category, channel,"
                            + " enabled, digest_mode, silence_start, silence_end, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())"
                            + " ON CONFLICT (tenant_id, COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid),"
                            + " COALESCE(customer_id, '00000000-0000-0000-0000-000000000000'::uuid), purpose_code, channel)"
                            + " DO UPDATE SET enabled = EXCLUDED.enabled, digest_mode = EXCLUDED.digest_mode,"
                            + " silence_start = EXCLUDED.silence_start, silence_end = EXCLUDED.silence_end,"
                            + " contact_id = EXCLUDED.contact_id, consent_id = EXCLUDED.consent_id,"
                            + " category = EXCLUDED.category, updated_at = NOW()")) {
                ps.setObject(1, preference.id() != null ? preference.id() : UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, preference.userId());
                ps.setObject(4, preference.customerId());
                ps.setObject(5, preference.contactId());
                ps.setObject(6, preference.consentId());
                ps.setString(7, preference.purposeCode());
                ps.setString(8, preference.category().name());
                ps.setString(9, preference.channel().name());
                ps.setBoolean(10, preference.enabled());
                ps.setString(11, preference.digestMode() == null ? null : preference.digestMode().name());
                ps.setObject(12, preference.silenceStart());
                ps.setObject(13, preference.silenceEnd());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Preferências vigentes do usuário, para a tela e para os leitores (P3.5/ED-41 via mesma tabela). */
    public List<NotificationPreference> preferencesOfUser(UUID userId) {
        return inTx(c -> {
            List<NotificationPreference> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, customer_id, contact_id, consent_id, purpose_code, category, channel, enabled,"
                            + " digest_mode, silence_start, silence_end FROM " + t("notification_preferences")
                            + " WHERE user_id = ? ORDER BY purpose_code, channel")) {
                ps.setObject(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new NotificationPreference(rs.getObject(1, UUID.class), userId,
                                rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                                rs.getString(5), NotificationCategory.valueOf(rs.getString(6)),
                                NotificationChannel.valueOf(rs.getString(7)), rs.getBoolean(8),
                                rs.getString(9) == null ? null : DigestMode.valueOf(rs.getString(9)),
                                rs.getObject(10, java.time.LocalTime.class), rs.getObject(11, java.time.LocalTime.class)));
                    }
                }
            }
            return result;
        });
    }
}
