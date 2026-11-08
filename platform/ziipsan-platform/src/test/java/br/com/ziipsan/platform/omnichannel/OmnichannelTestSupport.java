package br.com.ziipsan.platform.omnichannel;

import br.com.ziipsan.platform.notification.multichannel.UsageRecord;
import br.com.ziipsan.platform.notification.multichannel.UsageStore;
import br.com.ziipsan.platform.notification.multichannel.WhatsAppCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Fakes em memória das portas da ED-42 e das portas da ED-41 consumidas. */
final class OmnichannelTestSupport {

    static final Instant NOW = Instant.parse("2026-03-10T13:00:00Z");
    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID PROTOCOL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID UNIT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID CUSTOMER = UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final UUID ATTENDANT = UUID.fromString("55555555-5555-5555-5555-555555555555");
    static final UUID ATTENDANT_2 = UUID.fromString("66666666-6666-6666-6666-666666666666");
    static final String META_SECRET = "meta-app-secret-de-teste";
    static final String TELEGRAM_SECRET = "telegram-secret-de-teste";
    static final String EMAIL_SECRET = "email-provider-secret-de-teste";

    private OmnichannelTestSupport() {
    }

    static OmnichannelPorts.InboundEnvelope envelope(String eventId, String thread, String body) {
        return new OmnichannelPorts.InboundEnvelope(eventId, thread, "ext-msg-" + eventId,
                PROTOCOL, UNIT, CUSTOMER, false, false, body, NOW);
    }

    static final class FakeParameters implements OmnichannelParameters {
        Set<OmnichannelChannel> enabled = EnumSet.allOf(OmnichannelChannel.class);

        public Set<OmnichannelChannel> channelsEnabled(UUID tenantId) { return enabled; }
    }

    static final class FakeProviderRegistry implements OmnichannelPorts.OmniProviderRegistry {
        boolean approved = true;

        public boolean providerApproved(UUID tenantId, OmnichannelChannel channel) { return approved; }
    }

    static final class FakeVault implements OmnichannelPorts.CredentialVaultPort {
        final Map<OmnichannelChannel, String> secrets = new EnumMap<>(OmnichannelChannel.class);

        FakeVault() {
            secrets.put(OmnichannelChannel.WHATSAPP, META_SECRET);
            secrets.put(OmnichannelChannel.MESSENGER, META_SECRET);
            secrets.put(OmnichannelChannel.INSTAGRAM, META_SECRET);
            secrets.put(OmnichannelChannel.TELEGRAM, TELEGRAM_SECRET);
            secrets.put(OmnichannelChannel.EMAIL, EMAIL_SECRET);
        }

        public Optional<String> webhookSecret(UUID tenantId, OmnichannelChannel channel) {
            return Optional.ofNullable(secrets.get(channel));
        }

        public Optional<String> credentialReference(UUID tenantId, OmnichannelChannel channel) {
            return Optional.of("vault://omni/" + channel);
        }
    }

    static final class InMemoryConversationStore implements OmnichannelPorts.ConversationStore {
        final Map<UUID, Conversation> conversations = new HashMap<>();
        final List<ConversationMessage> messages = new ArrayList<>();

        public void save(Conversation c) { conversations.put(c.id(), c); }

        public void update(Conversation c) { conversations.put(c.id(), c); }

        public Optional<Conversation> findById(UUID t, UUID id) { return Optional.ofNullable(conversations.get(id)); }

        public Optional<Conversation> findOpenByThread(UUID t, OmnichannelChannel ch, String thread) {
            return conversations.values().stream()
                    .filter(c -> c.isOpen() && c.channel() == ch && c.externalThreadId().equals(thread))
                    .findFirst();
        }

        public void appendMessage(ConversationMessage m) { messages.add(m); }

        public List<ConversationMessage> messages(UUID t, UUID conversationId) {
            return messages.stream().filter(m -> m.conversationId().equals(conversationId)).toList();
        }
    }

    static final class InMemoryWebhookEvents implements OmnichannelPorts.WebhookEventStore {
        final Set<String> processed = new java.util.HashSet<>();

        public boolean alreadyProcessed(UUID t, OmnichannelChannel ch, String eventId) {
            return processed.contains(ch + ":" + eventId);
        }

        public void markProcessed(UUID t, OmnichannelChannel ch, String eventId, Instant at) {
            processed.add(ch + ":" + eventId);
        }
    }

    static final class InMemoryIntegrity implements OmnichannelPorts.IntegrityLogStore {
        final List<IntegrityLog.Entry> entries = new ArrayList<>();

        public long nextSequence(UUID t) { return entries.size() + 1L; }

        public String currentHash(UUID t) {
            return entries.isEmpty() ? IntegrityLog.GENESIS_HASH : entries.get(entries.size() - 1).hash();
        }

        public void append(IntegrityLog.Entry entry) { entries.add(entry); }

        public List<IntegrityLog.Entry> entries(UUID t) { return List.copyOf(entries); }
    }

    static final class InMemoryUsage implements UsageStore {
        final List<UsageRecord> records = new ArrayList<>();

        public void append(UsageRecord record) { records.add(record); }

        public List<UsageRecord> query(UUID tenantId, Instant from, Instant to) {
            return records.stream()
                    .filter(r -> r.tenantId().equals(tenantId)
                            && !r.occurredAt().isBefore(from) && r.occurredAt().isBefore(to))
                    .toList();
        }
    }

    static final class FakeGateway implements OmnichannelPorts.ConversationGateway {
        int calls;

        public String send(UUID tenantId, OmnichannelChannel channel, String thread, String body) {
            calls++;
            return "provider-msg-" + calls;
        }
    }

    static final class FakeOptIn implements OmnichannelPorts.OptInPort {
        List<Boolean> rows = List.of(true);

        public List<Boolean> optInRows(UUID t, UUID customerId, OmnichannelChannel channel) { return rows; }
    }

    static final class FakeMultichannelParameters
            implements br.com.ziipsan.platform.notification.multichannel.MultichannelParameters {
        Map<String, WhatsAppCategory> categoryMap = Map.of("bill-template", WhatsAppCategory.UTILITY);

        public Optional<BigDecimal> monthlyCap(UUID t) { return Optional.empty(); }

        public Map<String, WhatsAppCategory> templateCategoryMap(UUID t) { return categoryMap; }

        public Optional<String> smsSenderId(UUID t) { return Optional.empty(); }

        public Optional<String> whatsappAccountId(UUID t) { return Optional.of("wa-account-1"); }

        public boolean billPdfTenantDefault(UUID t) { return false; }

        public boolean billPdfFormalAcceptance(UUID t) { return false; }
    }

    static final class FakePricing implements br.com.ziipsan.platform.notification.multichannel.ChannelPricing {
        Optional<BigDecimal> price = Optional.of(new BigDecimal("0.35"));

        public Optional<BigDecimal> unitPrice(UUID t,
                br.com.ziipsan.platform.notification.NotificationChannel channel, WhatsAppCategory category) {
            return price;
        }
    }
}
