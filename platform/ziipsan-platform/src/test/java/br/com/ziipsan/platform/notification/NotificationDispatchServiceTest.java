package br.com.ziipsan.platform.notification;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serviço de disparo e prova (P3.5) sobre fakes em memória das portas — a fiação
 * testada é a do serviço real. Instantes e contas calculados à mão:
 * reclamação que pausa o tenant: 99 entregas gravadas + 1 envio = 100 envios;
 * 1 reclamação ÷ 100 = 1% ≥ 0,10% → pausa só daquele tenant.
 * Alerta de consumo: média 10, pct 50 → limite 15; 16 dispara, 15 e 14 não.
 */
class NotificationDispatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-10T13:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID CONTACT = UUID.randomUUID();
    private static final String ADDRESS = "Titular@Example.com";
    private static final String ADDRESS_NORM = "titular@example.com";

    // ---------- fakes das portas ----------

    static final class InMemoryStore implements DeliveryStore {
        final Map<UUID, List<DeliveryAttempt>> attempts = new HashMap<>();
        final Map<UUID, Set<String>> suppressions = new HashMap<>();
        final Map<UUID, TenantSendingState> states = new HashMap<>();
        final Map<UUID, Set<String>> processedEvents = new HashMap<>();

        public void saveAttempt(DeliveryAttempt a) {
            attempts.computeIfAbsent(a.tenantId(), k -> new ArrayList<>()).add(a);
        }

        public void updateAttempt(DeliveryAttempt a) {
            List<DeliveryAttempt> list = attempts.getOrDefault(a.tenantId(), List.of());
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id().equals(a.id())) {
                    list.set(i, a);
                    return;
                }
            }
            throw new IllegalStateException("tentativa desconhecida");
        }

        public Optional<DeliveryAttempt> findByProviderMessageId(UUID tenantId, String providerMessageId) {
            return attempts.getOrDefault(tenantId, List.of()).stream()
                    .filter(a -> providerMessageId.equals(a.providerMessageId())).findFirst();
        }

        public List<DeliveryAttempt> attemptsForNotice(UUID tenantId, UUID noticeId) {
            return attempts.getOrDefault(tenantId, List.of()).stream()
                    .filter(a -> a.noticeId().equals(noticeId)).toList();
        }

        public boolean isSuppressed(UUID tenantId, String emailAddress) {
            return suppressions.getOrDefault(tenantId, Set.of()).contains(emailAddress);
        }

        public void addSuppression(UUID tenantId, String emailAddress, EmailEventType cause, Instant at) {
            suppressions.computeIfAbsent(tenantId, k -> new java.util.HashSet<>()).add(emailAddress);
        }

        public TenantSendingState sendingState(UUID tenantId) {
            return states.getOrDefault(tenantId, TenantSendingState.ACTIVE);
        }

        public void setPaused(UUID tenantId, boolean paused, String reason) {
            states.put(tenantId, new TenantSendingState(paused, reason));
        }

        public boolean eventAlreadyProcessed(UUID tenantId, String providerEventId) {
            return processedEvents.getOrDefault(tenantId, Set.of()).contains(providerEventId);
        }

        public void markEventProcessed(UUID tenantId, String providerEventId, Instant at) {
            processedEvents.computeIfAbsent(tenantId, k -> new java.util.HashSet<>()).add(providerEventId);
        }

        public long countSent(UUID tenantId, Instant from, Instant to) {
            return attempts.getOrDefault(tenantId, List.of()).size();
        }

        public long countComplaints(UUID tenantId, Instant from, Instant to) {
            return attempts.getOrDefault(tenantId, List.of()).stream()
                    .filter(a -> a.status() == DeliveryStatus.COMPLAINED).count();
        }
    }

    static final class FakeGateway implements EmailGateway {
        final List<String> sends = new ArrayList<>();
        SenderDomainStatus status = new SenderDomainStatus("envio.example.com", true, true, true);
        int sequence;

        public String send(UUID tenantId, String fromDomain, String toAddress, RenderedMessage message) {
            sends.add(tenantId + "|" + toAddress);
            return "provider-" + (++sequence);
        }

        public SenderDomainStatus domainStatus(UUID tenantId, String fromDomain) {
            return new SenderDomainStatus(fromDomain, status.spfAligned(), status.dkimAligned(), status.dmarcAligned());
        }
    }

    static final class FakeTemplates implements MessageTemplateEngine {
        Map<String, String> lastVariables = Map.of();

        public RenderedMessage render(UUID tenantId, String templateCode, NotificationChannel channel,
                                      Map<String, String> variables) {
            lastVariables = variables;
            return new RenderedMessage("assunto", "corpo", templateCode, 3);
        }
    }

    static final class FakeCatalog implements BusinessEventCatalog {
        public Optional<CatalogEvent> find(String eventName, int eventVersion) {
            if (eventVersion != 1) {
                return Optional.empty();
            }
            return switch (eventName) {
                case "bill.cutoff-notice" -> Optional.of(new CatalogEvent(eventName, 1, NotificationCategory.LEGAL_NOTICE));
                case "bill.due" -> Optional.of(new CatalogEvent(eventName, 1, NotificationCategory.BILLING));
                default -> Optional.empty();
            };
        }
    }

    static final class FakePreferences implements PreferenceReader {
        final List<NotificationPreference> rows = new ArrayList<>();

        public List<NotificationPreference> find(UUID tenantId, UUID customerId, UUID userId,
                                                 String purposeCode, NotificationCategory category,
                                                 NotificationChannel channel) {
            return rows.stream()
                    .filter(p -> p.purposeCode().equals(purposeCode) && p.category() == category
                            && p.channel() == channel
                            && java.util.Objects.equals(p.customerId(), customerId)
                            && java.util.Objects.equals(p.userId(), userId))
                    .toList();
        }
    }

    static final class FakeContacts implements ContactRegistry {
        boolean hasContact = true;
        int invalidMarks;
        int pendencies;

        public Optional<RecipientContact> resolveEmailContact(UUID tenantId, UUID customerId, UUID userId,
                                                              String purposeCode) {
            if (!hasContact) {
                return Optional.empty();
            }
            return Optional.of(new RecipientContact(CONTACT, customerId, userId, ADDRESS, true));
        }

        public void markContactInvalid(UUID tenantId, UUID contactId, String reason) {
            invalidMarks++;
        }

        public UUID openInvalidContactPendency(UUID tenantId, UUID customerId, UUID contactId, String reason) {
            pendencies++;
            return UUID.randomUUID();
        }
    }

    static final class FakeParameters implements MessagingParameters {
        Set<NotificationChannel> channels = Set.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP);
        Set<NotificationChannel> alertChannels = Set.of(NotificationChannel.EMAIL);

        public String emailFromDomain(UUID tenantId) {
            return "envio.example.com";
        }

        public SuppressionPolicy suppressionPolicy(UUID tenantId) {
            return new SuppressionPolicy(true, true);
        }

        public Set<NotificationChannel> channelsEnabled(UUID tenantId) {
            return channels;
        }

        public BigDecimal highConsumptionPct(UUID tenantId) {
            return new BigDecimal("50");
        }

        public Set<NotificationChannel> alertChannels(UUID tenantId) {
            return alertChannels;
        }
    }

    static final class FakeNotificationParameters implements NotificationParameters {
        public DigestMode digestDefault(UUID tenantId) {
            return DigestMode.IMMEDIATE;
        }

        public Set<NotificationCategory> tenantMandatoryCategories(UUID tenantId) {
            return Set.of();
        }
    }

    // ---------- montagem ----------

    private final InMemoryStore store = new InMemoryStore();
    private final FakeGateway gateway = new FakeGateway();
    private final FakeTemplates templates = new FakeTemplates();
    private final FakePreferences preferences = new FakePreferences();
    private final FakeContacts contacts = new FakeContacts();
    private final FakeParameters parameters = new FakeParameters();
    private final NotificationDispatchService service = new NotificationDispatchService(
            store, gateway, templates, new FakeCatalog(), preferences, contacts, parameters,
            new FakeNotificationParameters(), CLOCK);

    private static OutboxMessage message(UUID tenantId, String event, String purpose, String template) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventVersion", 1);
        payload.put("recipientCustomerId", CUSTOMER.toString());
        payload.put("purposeCode", purpose);
        payload.put("templateCode", template);
        payload.put("variables", Map.of("billId", "123"));
        return new OutboxMessage(UUID.randomUUID(), tenantId, event, Json.write(payload), NOW);
    }

    private DispatchResult dispatchDue(UUID tenantId) {
        return service.dispatch(message(tenantId, "bill.due", "BILLING_NOTICE", "bill.due"),
                NotificationChannel.EMAIL);
    }

    // ---------- disparo ----------

    @Test
    @DisplayName("caminho feliz: monta pelo modelo, envia pelo gateway do tenant e grava a tentativa SENT com o id do provedor")
    void happyPathSendsAndRecordsAttempt() {
        DispatchResult result = dispatchDue(TENANT_A);
        assertThat(result.sent()).isTrue();
        assertThat(gateway.sends).hasSize(1);
        List<DeliveryAttempt> attempts = store.attempts.get(TENANT_A);
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(attempts.get(0).providerMessageId()).isEqualTo("provider-1");
        assertThat(attempts.get(0).templateVersion()).isEqualTo(3);
        // O endereço gravado é o do contato resolvido, normalizado.
        assertThat(attempts.get(0).emailAddress()).isEqualTo(ADDRESS_NORM);
    }

    @Test
    @DisplayName("evento fora do catálogo da P1.38 é recusado (409) e nenhum disparo nasce")
    void eventOutsideCatalogRefused() {
        assertThatThrownBy(() -> service.dispatch(message(TENANT_A, "unknown.event", "X", "t"),
                NotificationChannel.EMAIL)).isInstanceOf(ApiException.Conflict.class);
        assertThat(gateway.sends).isEmpty();
    }

    @Test
    @DisplayName("canal fora de msg.channels_enabled não dispara; PUSH é fronteira da ED-41 e IN_APP é da central da P3.36")
    void channelGuards() {
        parameters.channels = Set.of(NotificationChannel.IN_APP);
        assertThat(dispatchDue(TENANT_A).outcome()).isEqualTo(DispatchResult.Outcome.CHANNEL_DISABLED);
        parameters.channels = Set.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP);
        OutboxMessage msg = message(TENANT_A, "bill.due", "BILLING_NOTICE", "bill.due");
        assertThat(service.dispatch(msg, NotificationChannel.PUSH).outcome())
                .isEqualTo(DispatchResult.Outcome.FRONTIER_ED41);
        assertThat(service.dispatch(msg, NotificationChannel.IN_APP).outcome())
                .isEqualTo(DispatchResult.Outcome.DELEGATED_TO_CENTRAL);
        assertThat(gateway.sends).isEmpty();
    }

    @Test
    @DisplayName("preferência da P3.36 desliga o e-mail da finalidade; aviso LEGAL ignora a preferência (fora do alcance dela) e envia")
    void preferenceOffAndLegalNoticeOverride() {
        preferences.rows.add(new NotificationPreference(UUID.randomUUID(), null, CUSTOMER, CONTACT,
                UUID.randomUUID(), "BILLING_NOTICE", NotificationCategory.BILLING,
                NotificationChannel.EMAIL, false, null, null, null));
        assertThat(dispatchDue(TENANT_A).outcome()).isEqualTo(DispatchResult.Outcome.PREFERENCE_OFF);
        // Mesmo com uma linha desligada gravada para o aviso legal, ele está fora do alcance.
        preferences.rows.add(new NotificationPreference(UUID.randomUUID(), null, CUSTOMER, CONTACT,
                UUID.randomUUID(), "CUTOFF_NOTICE", NotificationCategory.LEGAL_NOTICE,
                NotificationChannel.EMAIL, false, null, null, null));
        DispatchResult legal = service.dispatch(message(TENANT_A, "bill.cutoff-notice", "CUTOFF_NOTICE",
                "bill.cutoff-notice"), NotificationChannel.EMAIL);
        assertThat(legal.sent()).isTrue();
    }

    @Test
    @DisplayName("TESTE DO ACEITE (isolamento): tenant A pausado não envia; o tenant B, com tudo igual, envia normalmente")
    void pausedTenantDoesNotAffectOthers() {
        store.setPaused(TENANT_A, true, "manual");
        assertThat(dispatchDue(TENANT_A).outcome()).isEqualTo(DispatchResult.Outcome.TENANT_PAUSED);
        assertThat(dispatchDue(TENANT_B).sent()).isTrue();
        assertThat(store.attempts.getOrDefault(TENANT_A, List.of())).isEmpty();
        assertThat(store.attempts.get(TENANT_B)).hasSize(1);
    }

    @Test
    @DisplayName("domínio sem DKIM alinhado não envia (requisito de remetente em massa); suprimido não envia; sem contato verificado não envia")
    void domainSuppressionAndContactGuards() {
        gateway.status = new SenderDomainStatus("envio.example.com", true, false, true);
        assertThat(dispatchDue(TENANT_A).outcome()).isEqualTo(DispatchResult.Outcome.DOMAIN_NOT_ALIGNED);
        gateway.status = new SenderDomainStatus("envio.example.com", true, true, true);
        store.addSuppression(TENANT_A, ADDRESS_NORM, EmailEventType.BOUNCED, NOW);
        assertThat(dispatchDue(TENANT_A).outcome()).isEqualTo(DispatchResult.Outcome.SUPPRESSED);
        // A supressão é do tenant A: o B envia para o mesmo endereço.
        assertThat(dispatchDue(TENANT_B).sent()).isTrue();
        contacts.hasContact = false;
        assertThat(dispatchDue(TENANT_B).outcome()).isEqualTo(DispatchResult.Outcome.NO_CONTACT);
    }

    // ---------- eventos e prova ----------

    @Test
    @DisplayName("REGRA DO ACEITE: sem prova, assertCutoffAllowed bloqueia; com o evento delivered, a prova existe e o corte é liberado")
    void deliveredEventGrantsProof() {
        OutboxMessage msg = message(TENANT_A, "bill.cutoff-notice", "CUTOFF_NOTICE", "bill.cutoff-notice");
        DispatchResult sent = service.dispatch(msg, NotificationChannel.EMAIL);
        assertThat(sent.sent()).isTrue();
        assertThat(service.hasDeliveryProof(TENANT_A, msg.id())).isFalse();
        assertThatThrownBy(() -> service.assertCutoffAllowed(TENANT_A, msg.id()))
                .isInstanceOf(ApiException.Conflict.class);
        service.onEmailEvent(new EmailDeliveryEvent("evt-1", TENANT_A, "provider-1",
                EmailEventType.DELIVERED, NOW, null));
        assertThat(service.hasDeliveryProof(TENANT_A, msg.id())).isTrue();
        service.assertCutoffAllowed(TENANT_A, msg.id());
    }

    @Test
    @DisplayName("bounce: contato marcado inválido, pendência aberta, endereço suprimido, aviso NÃO entregue — e o evento repetido não repete efeito")
    void bouncedEventEffectsAreIdempotent() {
        OutboxMessage msg = message(TENANT_A, "bill.cutoff-notice", "CUTOFF_NOTICE", "bill.cutoff-notice");
        service.dispatch(msg, NotificationChannel.EMAIL);
        EmailDeliveryEvent bounce = new EmailDeliveryEvent("evt-b1", TENANT_A, "provider-1",
                EmailEventType.BOUNCED, NOW, "mailbox-not-found");
        service.onEmailEvent(bounce);
        assertThat(contacts.invalidMarks).isEqualTo(1);
        assertThat(contacts.pendencies).isEqualTo(1);
        assertThat(store.isSuppressed(TENANT_A, ADDRESS_NORM)).isTrue();
        assertThat(service.hasDeliveryProof(TENANT_A, msg.id())).isFalse();
        assertThatThrownBy(() -> service.assertCutoffAllowed(TENANT_A, msg.id()))
                .isInstanceOf(ApiException.Conflict.class);
        // Reentrega do MESMO evento pelo provedor: nenhum efeito novo.
        service.onEmailEvent(bounce);
        assertThat(contacts.invalidMarks).isEqualTo(1);
        assertThat(contacts.pendencies).isEqualTo(1);
    }

    @Test
    @DisplayName("CORREÇÃO DA REVISÃO: delivered TARDIO depois do bounce NÃO fabrica prova — a situação fica BOUNCED, sem efeito novo no cadastro")
    void deliveredAfterBounceDoesNotCreateProof() {
        OutboxMessage msg = message(TENANT_A, "bill.cutoff-notice", "CUTOFF_NOTICE", "bill.cutoff-notice");
        service.dispatch(msg, NotificationChannel.EMAIL);
        service.onEmailEvent(new EmailDeliveryEvent("evt-b1", TENANT_A, "provider-1",
                EmailEventType.BOUNCED, NOW, "mailbox-not-found"));
        DeliveryAttempt afterLate = service.onEmailEvent(new EmailDeliveryEvent("evt-d2", TENANT_A,
                "provider-1", EmailEventType.DELIVERED, NOW, null));
        assertThat(afterLate.status()).isEqualTo(DeliveryStatus.BOUNCED);
        assertThat(contacts.invalidMarks).isEqualTo(1);
        assertThat(contacts.pendencies).isEqualTo(1);
        assertThat(service.hasDeliveryProof(TENANT_A, msg.id())).isFalse();
        assertThatThrownBy(() -> service.assertCutoffAllowed(TENANT_A, msg.id()))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("CORREÇÃO DA REVISÃO: bounce tardio depois do delivered é ignorado — a prova fica e nenhum efeito nasce no cadastro")
    void bounceAfterDeliveredIsIgnored() {
        OutboxMessage msg = message(TENANT_A, "bill.cutoff-notice", "CUTOFF_NOTICE", "bill.cutoff-notice");
        service.dispatch(msg, NotificationChannel.EMAIL);
        service.onEmailEvent(new EmailDeliveryEvent("evt-d1", TENANT_A, "provider-1",
                EmailEventType.DELIVERED, NOW, null));
        DeliveryAttempt afterLate = service.onEmailEvent(new EmailDeliveryEvent("evt-b2", TENANT_A,
                "provider-1", EmailEventType.BOUNCED, NOW, "late-bounce"));
        assertThat(afterLate.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(contacts.invalidMarks).isEqualTo(0);
        assertThat(contacts.pendencies).isEqualTo(0);
        assertThat(service.hasDeliveryProof(TENANT_A, msg.id())).isTrue();
    }

    @Test
    @DisplayName("CORREÇÃO DA REVISÃO: reclamação DEPOIS da entrega vale — apaga a prova, marca o contato e abre pendência uma única vez")
    void complaintAfterDeliveryAppliesEffectsAndErasesProof() {
        OutboxMessage msg = message(TENANT_A, "bill.cutoff-notice", "CUTOFF_NOTICE", "bill.cutoff-notice");
        service.dispatch(msg, NotificationChannel.EMAIL);
        service.onEmailEvent(new EmailDeliveryEvent("evt-d1", TENANT_A, "provider-1",
                EmailEventType.DELIVERED, NOW, null));
        assertThat(service.hasDeliveryProof(TENANT_A, msg.id())).isTrue();
        DeliveryAttempt resolved = service.onEmailEvent(new EmailDeliveryEvent("evt-c2", TENANT_A,
                "provider-1", EmailEventType.COMPLAINED, NOW, "spam"));
        assertThat(resolved.status()).isEqualTo(DeliveryStatus.COMPLAINED);
        assertThat(contacts.invalidMarks).isEqualTo(1);
        assertThat(contacts.pendencies).isEqualTo(1);
        assertThat(service.hasDeliveryProof(TENANT_A, msg.id())).isFalse();
    }

    @Test
    @DisplayName("complaint: 99 entregas + este envio = 100 envios, 1 reclamação = 1% ≥ 0,10% → tenant pausado; o outro tenant segue enviando")
    void complaintBreachingRatePausesOnlyThatTenant() {
        for (int i = 0; i < 99; i++) {
            store.saveAttempt(new DeliveryAttempt(UUID.randomUUID(), TENANT_A, UUID.randomUUID(),
                    CUSTOMER, null, CONTACT, NotificationChannel.EMAIL, ADDRESS_NORM,
                    "bill.due", 1, DeliveryStatus.DELIVERED, "seed-" + i, NOW, NOW));
        }
        DispatchResult sent = dispatchDue(TENANT_A);
        assertThat(sent.sent()).isTrue();
        service.onEmailEvent(new EmailDeliveryEvent("evt-c1", TENANT_A, "provider-1",
                EmailEventType.COMPLAINED, NOW, "spam"));
        assertThat(contacts.invalidMarks).isEqualTo(1);
        assertThat(store.sendingState(TENANT_A).paused()).isTrue();
        assertThat(dispatchDue(TENANT_A).outcome()).isEqualTo(DispatchResult.Outcome.TENANT_PAUSED);
        assertThat(dispatchDue(TENANT_B).sent()).isTrue();
    }

    @Test
    @DisplayName("evento de mensagem desconhecida neste tenant é erro explícito (404), nunca ignorado")
    void unknownProviderMessageIsExplicitError() {
        assertThatThrownBy(() -> service.onEmailEvent(new EmailDeliveryEvent("evt-x", TENANT_A,
                "provider-999", EmailEventType.DELIVERED, NOW, null)))
                .isInstanceOf(ApiException.NotFound.class);
    }

    // ---------- alerta de consumo anormal ----------

    @Test
    @DisplayName("TESTE DO ACEITE (crítica disparada): consumo 16, média 10, pct 50 → alerta enviado antes da fatura, com orientação de vazamento e atalho de revisão")
    void highConsumptionAlertFires() {
        Map<NotificationChannel, DispatchResult> results = service.onReadingClosed(
                TENANT_A, CUSTOMER, UUID.randomUUID(), new BigDecimal("16"), new BigDecimal("10"));
        assertThat(results.get(NotificationChannel.EMAIL).sent()).isTrue();
        assertThat(templates.lastVariables)
                .containsEntry("leakTestGuidanceCode", NotificationDispatchService.LEAK_TEST_GUIDANCE_CODE)
                .containsEntry("billReviewActionCode", NotificationDispatchService.BILL_REVIEW_ACTION_CODE)
                .containsEntry("excessPct", "60.00");
    }

    @Test
    @DisplayName("TESTE DO ACEITE (sem disparo): consumo 14 e consumo igual ao limite (15) não geram aviso nenhum")
    void highConsumptionAlertDoesNotFire() {
        assertThat(service.onReadingClosed(TENANT_A, CUSTOMER, UUID.randomUUID(),
                new BigDecimal("14"), new BigDecimal("10"))).isEmpty();
        assertThat(service.onReadingClosed(TENANT_A, CUSTOMER, UUID.randomUUID(),
                new BigDecimal("15"), new BigDecimal("10"))).isEmpty();
        assertThat(gateway.sends).isEmpty();
    }
}
