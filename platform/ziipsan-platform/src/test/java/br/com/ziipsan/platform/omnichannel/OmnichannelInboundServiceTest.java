package br.com.ziipsan.platform.omnichannel;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OmnichannelInboundServiceTest {

    private static final Clock CLOCK = Clock.fixed(OmnichannelTestSupport.NOW, ZoneOffset.UTC);

    static final class Fixture {
        final OmnichannelTestSupport.FakeParameters parameters = new OmnichannelTestSupport.FakeParameters();
        final OmnichannelTestSupport.FakeProviderRegistry dpa = new OmnichannelTestSupport.FakeProviderRegistry();
        final OmnichannelTestSupport.FakeVault vault = new OmnichannelTestSupport.FakeVault();
        final OmnichannelTestSupport.InMemoryConversationStore store =
                new OmnichannelTestSupport.InMemoryConversationStore();
        final OmnichannelTestSupport.InMemoryWebhookEvents events =
                new OmnichannelTestSupport.InMemoryWebhookEvents();
        final OmnichannelTestSupport.InMemoryIntegrity integrity =
                new OmnichannelTestSupport.InMemoryIntegrity();
        final OmnichannelTestSupport.InMemoryUsage usage = new OmnichannelTestSupport.InMemoryUsage();
        final OmnichannelInboundService service = new OmnichannelInboundService(
                parameters, dpa, vault, store, events, integrity, usage, CLOCK);
    }

    private static String metaHeader(String secret, byte[] payload) {
        return "sha256=" + WebhookSecurity.hmacSha256Hex(secret, payload);
    }

    @Test
    void whatsAppVerifiedOpensConversationOnProtocolAndMeasures() {
        Fixture f = new Fixture();
        byte[] payload = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        OmnichannelInboundService.InboundResult result = f.service.accept(
                OmnichannelTestSupport.TENANT, OmnichannelChannel.WHATSAPP, payload,
                metaHeader(OmnichannelTestSupport.META_SECRET, payload), null,
                OmnichannelTestSupport.envelope("evt-1", "thread-wa-1", "minha conta veio errada"));
        assertThat(result.accepted()).isTrue();
        assertThat(f.store.conversations).hasSize(1);
        Conversation conversation = f.store.conversations.values().iterator().next();
        assertThat(conversation.protocolId()).isEqualTo(OmnichannelTestSupport.PROTOCOL);
        assertThat(conversation.consumerUnitId()).isEqualTo(OmnichannelTestSupport.UNIT);
        assertThat(conversation.channel().protocolChannel())
                .isEqualTo(br.com.ziipsan.core.protocol.ProtocolChannel.WHATSAPP);
        assertThat(f.store.messages).hasSize(1);
        assertThat(f.usage.records).hasSize(1);
        assertThat(f.usage.records.get(0).moduleCode()).isEqualTo("ED-42");
        assertThat(IntegrityLog.verifyChain(f.integrity.entries)).isTrue();
    }

    @Test
    void badSignatureRejectedLoggedAndNoConversation() {
        Fixture f = new Fixture();
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        OmnichannelInboundService.InboundResult result = f.service.accept(
                OmnichannelTestSupport.TENANT, OmnichannelChannel.MESSENGER, payload,
                "sha256=0000", null, OmnichannelTestSupport.envelope("evt-2", "thread-m-1", "oi"));
        assertThat(result.outcome()).isEqualTo(OmnichannelInboundService.Outcome.WEBHOOK_REJECTED);
        assertThat(f.store.conversations).isEmpty();
        assertThat(f.store.messages).isEmpty();
        assertThat(f.usage.records).isEmpty();
        assertThat(f.integrity.entries).anySatisfy(e -> assertThat(e.eventType())
                .isEqualTo(IntegrityLog.EventType.WEBHOOK_REJECTED));
        assertThat(IntegrityLog.verifyChain(f.integrity.entries)).isTrue();
    }

    @Test
    void duplicateWebhookIgnored() {
        Fixture f = new Fixture();
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        String header = metaHeader(OmnichannelTestSupport.META_SECRET, payload);
        f.service.accept(OmnichannelTestSupport.TENANT, OmnichannelChannel.INSTAGRAM, payload,
                header, null, OmnichannelTestSupport.envelope("evt-3", "thread-ig-1", "oi"));
        OmnichannelInboundService.InboundResult second = f.service.accept(
                OmnichannelTestSupport.TENANT, OmnichannelChannel.INSTAGRAM, payload,
                header, null, OmnichannelTestSupport.envelope("evt-3", "thread-ig-1", "oi de novo"));
        assertThat(second.outcome()).isEqualTo(OmnichannelInboundService.Outcome.DUPLICATE_IGNORED);
        assertThat(f.store.messages).hasSize(1);
    }

    @Test
    void secondMessageReusesOpenConversation() {
        Fixture f = new Fixture();
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        String header = metaHeader(OmnichannelTestSupport.META_SECRET, payload);
        f.service.accept(OmnichannelTestSupport.TENANT, OmnichannelChannel.WHATSAPP, payload,
                header, null, OmnichannelTestSupport.envelope("evt-4", "thread-wa-9", "primeira"));
        f.service.accept(OmnichannelTestSupport.TENANT, OmnichannelChannel.WHATSAPP, payload,
                header, null, OmnichannelTestSupport.envelope("evt-5", "thread-wa-9", "segunda"));
        assertThat(f.store.conversations).hasSize(1);
        assertThat(f.store.messages).hasSize(2);
    }

    @Test
    void telegramSecretAndPortalSessionModes() {
        Fixture f = new Fixture();
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        OmnichannelInboundService.InboundResult telegram = f.service.accept(
                OmnichannelTestSupport.TENANT, OmnichannelChannel.TELEGRAM, payload,
                null, OmnichannelTestSupport.TELEGRAM_SECRET,
                OmnichannelTestSupport.envelope("evt-6", "thread-tg-1", "oi telegram"));
        assertThat(telegram.accepted()).isTrue();
        OmnichannelInboundService.InboundResult telegramBad = f.service.accept(
                OmnichannelTestSupport.TENANT, OmnichannelChannel.TELEGRAM, payload,
                null, "errado", OmnichannelTestSupport.envelope("evt-7", "thread-tg-2", "oi"));
        assertThat(telegramBad.outcome()).isEqualTo(OmnichannelInboundService.Outcome.WEBHOOK_REJECTED);

        OmnichannelPorts.InboundEnvelope portalAnon = new OmnichannelPorts.InboundEnvelope(
                "evt-8", "thread-portal-1", "m1", OmnichannelTestSupport.PROTOCOL,
                OmnichannelTestSupport.UNIT, OmnichannelTestSupport.CUSTOMER, false, false,
                "oi portal", OmnichannelTestSupport.NOW);
        assertThat(f.service.accept(OmnichannelTestSupport.TENANT, OmnichannelChannel.PORTAL_CHAT,
                payload, null, null, portalAnon).outcome())
                .isEqualTo(OmnichannelInboundService.Outcome.WEBHOOK_REJECTED);
        OmnichannelPorts.InboundEnvelope portalAuth = new OmnichannelPorts.InboundEnvelope(
                "evt-9", "thread-portal-1", "m1", OmnichannelTestSupport.PROTOCOL,
                OmnichannelTestSupport.UNIT, OmnichannelTestSupport.CUSTOMER, false, true,
                "oi portal", OmnichannelTestSupport.NOW);
        assertThat(f.service.accept(OmnichannelTestSupport.TENANT, OmnichannelChannel.PORTAL_CHAT,
                payload, null, null, portalAuth).accepted()).isTrue();
    }

    @Test
    void channelDisabledProviderNotInDpaAndMissingSecretFailClosed() {
        Fixture f = new Fixture();
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        String header = metaHeader(OmnichannelTestSupport.META_SECRET, payload);
        f.parameters.enabled = EnumSet.of(OmnichannelChannel.EMAIL);
        assertThat(f.service.accept(OmnichannelTestSupport.TENANT, OmnichannelChannel.WHATSAPP,
                payload, header, null, OmnichannelTestSupport.envelope("e1", "t", "oi")).outcome())
                .isEqualTo(OmnichannelInboundService.Outcome.CHANNEL_DISABLED);
        f.parameters.enabled = EnumSet.allOf(OmnichannelChannel.class);
        f.dpa.approved = false;
        assertThat(f.service.accept(OmnichannelTestSupport.TENANT, OmnichannelChannel.WHATSAPP,
                payload, header, null, OmnichannelTestSupport.envelope("e2", "t", "oi")).outcome())
                .isEqualTo(OmnichannelInboundService.Outcome.PROVIDER_NOT_IN_DPA);
        f.dpa.approved = true;
        f.vault.secrets.remove(OmnichannelChannel.WHATSAPP);
        assertThat(f.service.accept(OmnichannelTestSupport.TENANT, OmnichannelChannel.WHATSAPP,
                payload, header, null, OmnichannelTestSupport.envelope("e3", "t", "oi")).outcome())
                .isEqualTo(OmnichannelInboundService.Outcome.WEBHOOK_SECRET_MISSING);
        assertThat(f.store.conversations).isEmpty();
    }

    @Test
    void earlyRefusalsAreRecordedInIntegrityChain() {
        // D-01: canal desabilitado, provedor fora do DPA e segredo ausente
        // lançam REFUSAL_RECORDED na cadeia — com o motivo na referência e
        // NUNCA o valor de segredo algum.
        Fixture f = new Fixture();
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        String header = metaHeader(OmnichannelTestSupport.META_SECRET, payload);
        f.parameters.enabled = EnumSet.of(OmnichannelChannel.EMAIL);
        f.service.accept(OmnichannelTestSupport.TENANT, OmnichannelChannel.WHATSAPP,
                payload, header, null, OmnichannelTestSupport.envelope("r1", "t", "oi"));
        f.parameters.enabled = EnumSet.allOf(OmnichannelChannel.class);
        f.dpa.approved = false;
        f.service.accept(OmnichannelTestSupport.TENANT, OmnichannelChannel.WHATSAPP,
                payload, header, null, OmnichannelTestSupport.envelope("r2", "t", "oi"));
        f.dpa.approved = true;
        f.vault.secrets.remove(OmnichannelChannel.WHATSAPP);
        f.service.accept(OmnichannelTestSupport.TENANT, OmnichannelChannel.WHATSAPP,
                payload, header, null, OmnichannelTestSupport.envelope("r3", "t", "oi"));
        assertThat(f.integrity.entries).hasSize(3)
                .allSatisfy(e -> assertThat(e.eventType())
                        .isEqualTo(IntegrityLog.EventType.REFUSAL_RECORDED));
        assertThat(f.integrity.entries).extracting(IntegrityLog.Entry::reference)
                .anySatisfy(r -> assertThat(r).contains("CHANNEL_DISABLED"))
                .anySatisfy(r -> assertThat(r).contains("PROVIDER_NOT_IN_DPA"))
                .anySatisfy(r -> assertThat(r).contains("WEBHOOK_SECRET_MISSING"))
                .allSatisfy(r -> assertThat(r)
                        .doesNotContain(OmnichannelTestSupport.META_SECRET)
                        .doesNotContain(OmnichannelTestSupport.TELEGRAM_SECRET));
        assertThat(IntegrityLog.verifyChain(f.integrity.entries)).isTrue();
        assertThat(f.store.conversations).isEmpty();
    }

    @Test
    void unresolvedIdentityRefused() {
        Fixture f = new Fixture();
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        String header = metaHeader(OmnichannelTestSupport.EMAIL_SECRET, payload);
        OmnichannelPorts.InboundEnvelope orphan = new OmnichannelPorts.InboundEnvelope(
                "evt-10", "thread-mail-1", "m1", null, null, null, false, false,
                "oi", OmnichannelTestSupport.NOW);
        assertThat(f.service.accept(OmnichannelTestSupport.TENANT, OmnichannelChannel.EMAIL,
                payload, header, null, orphan).outcome())
                .isEqualTo(OmnichannelInboundService.Outcome.IDENTITY_UNRESOLVED);
        assertThat(f.store.conversations).isEmpty();
        assertThat(List.of()).isEmpty();
    }
}
