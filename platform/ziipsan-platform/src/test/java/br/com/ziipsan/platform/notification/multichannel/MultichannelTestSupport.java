package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.BusinessEventCatalog;
import br.com.ziipsan.platform.notification.CatalogEvent;
import br.com.ziipsan.platform.notification.ConsentGate;
import br.com.ziipsan.platform.notification.ContactRegistry;
import br.com.ziipsan.platform.notification.DeliveryAttempt;
import br.com.ziipsan.platform.notification.DeliveryStore;
import br.com.ziipsan.platform.notification.EmailEventType;
import br.com.ziipsan.platform.notification.EmailGateway;
import br.com.ziipsan.platform.notification.MessageTemplateEngine;
import br.com.ziipsan.platform.notification.MessagingParameters;
import br.com.ziipsan.platform.notification.NotificationCategory;
import br.com.ziipsan.platform.notification.NotificationChannel;
import br.com.ziipsan.platform.notification.NotificationParameters;
import br.com.ziipsan.platform.notification.NotificationPreference;
import br.com.ziipsan.platform.notification.OutboxMessage;
import br.com.ziipsan.platform.notification.PreferenceReader;
import br.com.ziipsan.platform.notification.RecipientContact;
import br.com.ziipsan.platform.notification.RenderedMessage;
import br.com.ziipsan.platform.notification.SenderDomainStatus;
import br.com.ziipsan.platform.notification.SuppressionPolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Fakes em memória das portas vizinhas (P3.36/P3.5/P1.3/P2.30) e das portas
 * da ED-41 — a fiação testada é a dos serviços reais sobre estes fakes,
 * como nos testes da P3.5.
 */
final class MultichannelTestSupport {

    static final Instant NOW = Instant.parse("2026-03-10T13:00:00Z");
    static final UUID TENANT = UUID.randomUUID();
    static final UUID CUSTOMER = UUID.randomUUID();
    static final UUID CONTACT_ID = UUID.randomUUID();
    static final UUID CONSENT_ID = UUID.randomUUID();

    private MultichannelTestSupport() {
    }

    static OutboxMessage message(String type, String purpose, String template, UUID customerId) {
        String payload = "{\"eventVersion\":1,\"purposeCode\":\"" + purpose + "\",\"templateCode\":\""
                + template + "\",\"recipientCustomerId\":\"" + customerId
                + "\",\"variables\":{\"billId\":\"" + UUID.randomUUID() + "\"}}";
        return new OutboxMessage(UUID.randomUUID(), TENANT, type, payload, NOW);
    }

    static NotificationPreference preference(UUID customerId, String purpose, NotificationCategory category,
                                             NotificationChannel channel, boolean enabled) {
        return new NotificationPreference(UUID.randomUUID(), null, customerId, CONTACT_ID, CONSENT_ID,
                purpose, category, channel, enabled, null, null, null);
    }

    static final class FakeCatalog implements BusinessEventCatalog {
        public Optional<CatalogEvent> find(String eventName, int eventVersion) {
            if (eventVersion != 1) {
                return Optional.empty();
            }
            return switch (eventName) {
                case "bill.issued" -> Optional.of(new CatalogEvent(eventName, 1, NotificationCategory.BILLING));
                case "legal.notice" -> Optional.of(new CatalogEvent(eventName, 1, NotificationCategory.LEGAL_NOTICE));
                case "os.enroute" -> Optional.of(new CatalogEvent(eventName, 1, NotificationCategory.SERVICE_ORDER));
                default -> Optional.empty();
            };
        }
    }

    static final class FakeMessaging implements MessagingParameters {
        Set<NotificationChannel> channels = EnumSet.of(NotificationChannel.EMAIL, NotificationChannel.SMS,
                NotificationChannel.WHATSAPP, NotificationChannel.PUSH);
        String domain = "envio.example.com";

        public String emailFromDomain(UUID tenantId) { return domain; }
        public SuppressionPolicy suppressionPolicy(UUID tenantId) { return new SuppressionPolicy(true, true); }
        public Set<NotificationChannel> channelsEnabled(UUID tenantId) { return channels; }
        public BigDecimal highConsumptionPct(UUID tenantId) { return new BigDecimal("50"); }
        public Set<NotificationChannel> alertChannels(UUID tenantId) { return Set.of(NotificationChannel.EMAIL); }
    }

    static final class FakePreferences implements PreferenceReader {
        final List<NotificationPreference> rows = new ArrayList<>();

        public List<NotificationPreference> find(UUID tenantId, UUID customerId, UUID userId,
                                                 String purposeCode, NotificationCategory category,
                                                 NotificationChannel channel) {
            return rows.stream()
                    .filter(r -> r.purposeCode().equals(purposeCode) && r.category() == category
                            && r.channel() == channel)
                    .toList();
        }
    }

    static final class FakeConsent implements ConsentGate {
        boolean active = true;

        public boolean isVerifiedContact(UUID tenantId, UUID contactId) { return true; }
        public boolean hasActiveConsent(UUID tenantId, UUID customerId, UUID contactId, UUID consentId,
                                        String purposeCode) { return active; }
    }

    static final class FakeNotificationParameters implements NotificationParameters {
        public br.com.ziipsan.platform.notification.DigestMode digestDefault(UUID tenantId) {
            return br.com.ziipsan.platform.notification.DigestMode.IMMEDIATE;
        }
        public Set<NotificationCategory> tenantMandatoryCategories(UUID tenantId) { return Set.of(); }
    }

    static final class FakeChannelContacts implements ChannelContactRegistry {
        Optional<ChannelContact> phone = Optional.of(
                new ChannelContact(CONTACT_ID, CUSTOMER, null, "+5548999990000", true));
        Optional<ChannelContact> push = Optional.of(
                new ChannelContact(CONTACT_ID, CUSTOMER, null, "device-token-1", true));
        final List<UUID> invalidated = new ArrayList<>();

        public Optional<ChannelContact> resolvePhoneContact(UUID t, UUID c, UUID u, String p) { return phone; }
        public Optional<ChannelContact> resolvePushContact(UUID t, UUID c, UUID u, String p) { return push; }
        public void markContactInvalid(UUID t, UUID contactId, String reason) { invalidated.add(contactId); }
    }

    static final class FakeTemplateEngine implements MessageTemplateEngine {
        String body = "Sua fatura chegou. Veja no portal.";
        Map<String, String> lastVariables = Map.of();

        public RenderedMessage render(UUID tenantId, String templateCode, NotificationChannel channel,
                                      Map<String, String> variables) {
            lastVariables = variables;
            return new RenderedMessage("Aviso", body, templateCode, 3);
        }
    }

    static final class FakeGateways {
        final List<String> sends = new ArrayList<>();
        int sequence;

        final ChannelGateways.SmsGateway sms = (t, sender, dest, msg) -> {
            sends.add("SMS|" + sender + "|" + dest);
            return "sms-" + (++sequence);
        };
        final ChannelGateways.WhatsAppGateway whatsapp = (t, account, dest, msg, category) -> {
            sends.add("WA|" + account + "|" + dest + "|" + category);
            return "wa-" + (++sequence);
        };
        final ChannelGateways.PushGateway push = (t, token, msg) -> {
            sends.add("PUSH|" + token);
            return "push-" + (++sequence);
        };
    }

    static final class InMemoryChannelStore implements ChannelDeliveryStore {
        final List<ChannelDelivery> deliveries = new ArrayList<>();
        final Set<String> processedEvents = new HashSet<>();

        public void save(ChannelDelivery d) { deliveries.add(d); }

        public void update(ChannelDelivery d) {
            for (int i = 0; i < deliveries.size(); i++) {
                if (deliveries.get(i).id().equals(d.id())) {
                    deliveries.set(i, d);
                    return;
                }
            }
            throw new IllegalStateException("tentativa desconhecida");
        }

        public Optional<ChannelDelivery> findByProviderMessageId(UUID t, String providerMessageId) {
            return deliveries.stream().filter(d -> d.tenantId().equals(t)
                    && providerMessageId.equals(d.providerMessageId())).findFirst();
        }

        public List<ChannelDelivery> attemptsForNotice(UUID t, UUID noticeId) {
            return deliveries.stream().filter(d -> d.tenantId().equals(t)
                    && d.noticeId().equals(noticeId)).toList();
        }

        public BigDecimal sumEstimatedCost(UUID t, Instant from, Instant to) {
            return deliveries.stream()
                    .filter(d -> d.tenantId().equals(t) && !d.sentAt().isBefore(from) && !d.sentAt().isAfter(to)
                            && d.estimatedCost() != null)
                    .map(ChannelDelivery::estimatedCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public long countSent(UUID t, NotificationChannel ch, Instant from, Instant to) {
            return deliveries.stream().filter(d -> d.tenantId().equals(t) && d.channel() == ch
                    && !d.sentAt().isBefore(from) && !d.sentAt().isAfter(to)).count();
        }

        public long countWithStatus(UUID t, NotificationChannel ch, ChannelDeliveryStatus status,
                                    Instant from, Instant to) {
            // Coorte ÚNICA por sentAt (correção D4): a janela é a do ENVIO,
            // como em countSent — a situação atual da tentativa é o estado da
            // coorte, nunca filtrada por resolvedAt. Com resolvedAt, uma
            // mensagem enviada antes da janela e resolvida dentro dela
            // inflaria as resolvidas acima dos envios e a regra lançava.
            return deliveries.stream().filter(d -> d.tenantId().equals(t) && d.channel() == ch
                    && d.status() == status
                    && !d.sentAt().isBefore(from) && !d.sentAt().isAfter(to)).count();
        }

        public boolean eventAlreadyProcessed(UUID t, String providerEventId) {
            return processedEvents.contains(t + "|" + providerEventId);
        }

        public void markEventProcessed(UUID t, String providerEventId, Instant at) {
            processedEvents.add(t + "|" + providerEventId);
        }
    }

    static final class FakePricing implements ChannelPricing {
        final Map<String, BigDecimal> prices = new HashMap<>();

        FakePricing with(NotificationChannel channel, WhatsAppCategory category, String price) {
            prices.put(channel + "|" + category, new BigDecimal(price));
            return this;
        }

        public Optional<BigDecimal> unitPrice(UUID t, NotificationChannel channel, WhatsAppCategory category) {
            return Optional.ofNullable(prices.get(channel + "|" + category));
        }
    }

    static final class FakeParams implements MultichannelParameters {
        Optional<BigDecimal> cap = Optional.of(new BigDecimal("1000.00"));
        Map<String, WhatsAppCategory> categoryMap = Map.of();
        Optional<String> smsSender = Optional.of("ZIIPSAN");
        Optional<String> whatsappAccount = Optional.of("wa-account-1");
        boolean billPdfDefault;
        boolean billPdfFormal;

        public Optional<BigDecimal> monthlyCap(UUID t) { return cap; }
        public Map<String, WhatsAppCategory> templateCategoryMap(UUID t) { return categoryMap; }
        public Optional<String> smsSenderId(UUID t) { return smsSender; }
        public Optional<String> whatsappAccountId(UUID t) { return whatsappAccount; }
        public boolean billPdfTenantDefault(UUID t) { return billPdfDefault; }
        public boolean billPdfFormalAcceptance(UUID t) { return billPdfFormal; }
    }

    static final class InMemoryUsageStore implements UsageStore {
        final List<UsageRecord> records = new ArrayList<>();

        public void append(UsageRecord record) { records.add(record); }

        public List<UsageRecord> query(UUID tenantId, Instant from, Instant to) {
            return records.stream().filter(r -> r.tenantId().equals(tenantId)
                    && !r.occurredAt().isBefore(from) && !r.occurredAt().isAfter(to)).toList();
        }
    }

    static final class FakeCapSink implements CapAlertSink {
        final List<MonthlyCapRule.CapLevel> alerts = new ArrayList<>();

        public void onCapAlert(UUID t, MonthlyCapRule.CapLevel level, BigDecimal projected,
                               BigDecimal cap, Instant at) { alerts.add(level); }
    }

    // ---------- fakes da fatura por e-mail (portas da P3.5 e da ED-41) ----------

    static final class FakeEmailContacts implements ContactRegistry {
        Optional<RecipientContact> contact = Optional.of(
                new RecipientContact(CONTACT_ID, CUSTOMER, null, "Titular@Example.com", true));

        public Optional<RecipientContact> resolveEmailContact(UUID t, UUID c, UUID u, String p) { return contact; }
        public void markContactInvalid(UUID t, UUID contactId, String reason) { }
        public UUID openInvalidContactPendency(UUID t, UUID c, UUID contactId, String reason) {
            return UUID.randomUUID();
        }
    }

    static final class FakeEmailGateway implements EmailGateway {
        SenderDomainStatus status = new SenderDomainStatus("envio.example.com", true, true, true);

        public String send(UUID t, String fromDomain, String to, RenderedMessage m) { return "mail-1"; }
        public SenderDomainStatus domainStatus(UUID t, String fromDomain) {
            return new SenderDomainStatus(fromDomain, status.spfAligned(), status.dkimAligned(),
                    status.dmarcAligned());
        }
    }

    static final class InMemoryDeliveryStore implements DeliveryStore {
        final List<DeliveryAttempt> attempts = new ArrayList<>();
        // Estado de envio configurável (correção D2): pausa, supressão e as
        // contagens da taxa de reclamação, para os testes das guardas que a
        // fatura herdou da P3.5. sentOverride/complaintsOverride < 0 = usar o
        // comportamento original (tentativas gravadas / zero reclamações).
        boolean paused;
        String pauseReason;
        String recordedPauseReason;
        final Set<String> suppressed = new HashSet<>();
        long sentOverride = -1;
        long complaintsOverride = -1;

        public void saveAttempt(DeliveryAttempt a) { attempts.add(a); }
        public void updateAttempt(DeliveryAttempt a) { }
        public Optional<DeliveryAttempt> findByProviderMessageId(UUID t, String id) { return Optional.empty(); }
        public List<DeliveryAttempt> attemptsForNotice(UUID t, UUID noticeId) { return List.of(); }
        public boolean isSuppressed(UUID t, String email) { return suppressed.contains(email); }
        public void addSuppression(UUID t, String email, EmailEventType cause, Instant at) {
            suppressed.add(email);
        }
        public TenantSendingState sendingState(UUID t) {
            return paused ? new TenantSendingState(true, pauseReason) : TenantSendingState.ACTIVE;
        }
        public void setPaused(UUID t, boolean paused, String reason) {
            this.paused = paused;
            this.pauseReason = reason;
            this.recordedPauseReason = reason;
        }
        public boolean eventAlreadyProcessed(UUID t, String id) { return false; }
        public void markEventProcessed(UUID t, String id, Instant at) { }
        public long countSent(UUID t, Instant from, Instant to) {
            return sentOverride >= 0 ? sentOverride : attempts.size();
        }
        public long countComplaints(UUID t, Instant from, Instant to) {
            return complaintsOverride >= 0 ? complaintsOverride : 0;
        }
    }

    static final class FakeBillSender implements BillEmailPorts.BillEmailSender {
        final List<Optional<BillEmailPorts.BillPdfAttachment>> attachments = new ArrayList<>();

        public String send(UUID t, String fromDomain, String to, RenderedMessage m,
                           Optional<BillEmailPorts.BillPdfAttachment> attachment) {
            attachments.add(attachment);
            return "bill-mail-" + attachments.size();
        }
    }
}
