package br.com.ziipsan.platform.omnichannel;

import br.com.ziipsan.core.sac.AssignmentChange;
import br.com.ziipsan.platform.notification.NotificationChannel;
import br.com.ziipsan.platform.notification.multichannel.ChannelDelivery;
import br.com.ziipsan.platform.notification.multichannel.ChannelDeliveryStatus;
import br.com.ziipsan.platform.notification.multichannel.UsageMetric;
import br.com.ziipsan.platform.notification.multichannel.UsageRecord;
import br.com.ziipsan.platform.notification.multichannel.UsageReportService;
import br.com.ziipsan.platform.notification.multichannel.WhatsAppCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OmnichannelReplyAndUsageTest {

    private static final Clock CLOCK = Clock.fixed(OmnichannelTestSupport.NOW, ZoneOffset.UTC);

    static final class Fixture {
        final OmnichannelTestSupport.FakeParameters parameters = new OmnichannelTestSupport.FakeParameters();
        final OmnichannelTestSupport.FakeProviderRegistry dpa = new OmnichannelTestSupport.FakeProviderRegistry();
        final OmnichannelTestSupport.InMemoryConversationStore store =
                new OmnichannelTestSupport.InMemoryConversationStore();
        final OmnichannelTestSupport.FakeGateway gateway = new OmnichannelTestSupport.FakeGateway();
        final OmnichannelTestSupport.FakeOptIn optIn = new OmnichannelTestSupport.FakeOptIn();
        final OmnichannelTestSupport.InMemoryIntegrity integrity =
                new OmnichannelTestSupport.InMemoryIntegrity();
        final OmnichannelTestSupport.FakeMultichannelParameters multiParams =
                new OmnichannelTestSupport.FakeMultichannelParameters();
        final OmnichannelTestSupport.FakePricing pricing = new OmnichannelTestSupport.FakePricing();
        final OmnichannelTestSupport.InMemoryUsage usage = new OmnichannelTestSupport.InMemoryUsage();
        final OmnichannelReplyService service = new OmnichannelReplyService(
                parameters, dpa, store, gateway, optIn, integrity, multiParams, pricing, usage, CLOCK);
        final Conversation conversation;

        Fixture(OmnichannelChannel channel) {
            conversation = Conversation.open(UUID.randomUUID(), OmnichannelTestSupport.TENANT,
                    OmnichannelTestSupport.PROTOCOL, OmnichannelTestSupport.UNIT,
                    OmnichannelTestSupport.CUSTOMER, channel, "thread-1", false,
                    OmnichannelTestSupport.NOW);
            store.save(conversation);
        }
    }

    @Test
    void whatsAppTemplateReplyCostsByEd41Category() {
        Fixture f = new Fixture(OmnichannelChannel.WHATSAPP);
        OmnichannelReplyService.ReplyResult result = f.service.reply(
                OmnichannelTestSupport.TENANT, f.conversation.id(), OmnichannelTestSupport.ATTENDANT,
                "Sua fatura foi revisada.", "bill-template");
        assertThat(result.sent()).isTrue();
        assertThat(result.category()).isEqualTo(WhatsAppCategory.UTILITY);
        assertThat(result.cost()).isEqualByComparingTo("0.35");
        assertThat(f.store.messages).hasSize(1);
        assertThat(f.store.findById(OmnichannelTestSupport.TENANT, f.conversation.id()))
                .get().extracting(Conversation::status).isEqualTo(Conversation.Status.WAITING_CUSTOMER);
        assertThat(f.usage.records).hasSize(1);
        assertThat(f.usage.records.get(0).cost()).isEqualByComparingTo("0.35");
        assertThat(IntegrityLog.verifyChain(f.integrity.entries)).isTrue();
    }

    @Test
    void whatsAppFreeTextReplyHasNoTemplateCost() {
        Fixture f = new Fixture(OmnichannelChannel.WHATSAPP);
        OmnichannelReplyService.ReplyResult result = f.service.reply(
                OmnichannelTestSupport.TENANT, f.conversation.id(), OmnichannelTestSupport.ATTENDANT,
                "Pode me contar mais?", null);
        assertThat(result.sent()).isTrue();
        assertThat(result.cost()).isNull();
        assertThat(result.category()).isNull();
    }

    @Test
    void unclassifiedTemplateAndMissingPriceBlock() {
        Fixture f = new Fixture(OmnichannelChannel.WHATSAPP);
        assertThat(f.service.reply(OmnichannelTestSupport.TENANT, f.conversation.id(),
                OmnichannelTestSupport.ATTENDANT, "oi", "modelo-desconhecido").outcome())
                .isEqualTo(OmnichannelReplyService.Outcome.TEMPLATE_NOT_CLASSIFIED);
        f.pricing.price = Optional.empty();
        assertThat(f.service.reply(OmnichannelTestSupport.TENANT, f.conversation.id(),
                OmnichannelTestSupport.ATTENDANT, "oi", "bill-template").outcome())
                .isEqualTo(OmnichannelReplyService.Outcome.PRICE_NOT_CONFIGURED);
        assertThat(f.gateway.calls).isZero();
    }

    @Test
    void optInMissingAndRevokedBlockExternalReplyButPortalReplies() {
        Fixture f = new Fixture(OmnichannelChannel.TELEGRAM);
        f.optIn.rows = List.of();
        assertThat(f.service.reply(OmnichannelTestSupport.TENANT, f.conversation.id(),
                OmnichannelTestSupport.ATTENDANT, "oi", null).outcome())
                .isEqualTo(OmnichannelReplyService.Outcome.OPT_IN_MISSING);
        f.optIn.rows = List.of(true, false);
        assertThat(f.service.reply(OmnichannelTestSupport.TENANT, f.conversation.id(),
                OmnichannelTestSupport.ATTENDANT, "oi", null).outcome())
                .isEqualTo(OmnichannelReplyService.Outcome.OPT_IN_REVOKED);
        assertThat(f.gateway.calls).isZero();

        Fixture portal = new Fixture(OmnichannelChannel.PORTAL_CHAT);
        portal.optIn.rows = List.of();
        assertThat(portal.service.reply(OmnichannelTestSupport.TENANT, portal.conversation.id(),
                OmnichannelTestSupport.ATTENDANT, "oi", null).sent()).isTrue();
    }

    @Test
    void closedOrMissingConversationBlocks() {
        Fixture f = new Fixture(OmnichannelChannel.EMAIL);
        assertThat(f.service.reply(OmnichannelTestSupport.TENANT, UUID.randomUUID(),
                OmnichannelTestSupport.ATTENDANT, "oi", null).outcome())
                .isEqualTo(OmnichannelReplyService.Outcome.CONVERSATION_NOT_FOUND);
        f.store.update(f.conversation.close(OmnichannelTestSupport.NOW.plusSeconds(10)));
        assertThat(f.service.reply(OmnichannelTestSupport.TENANT, f.conversation.id(),
                OmnichannelTestSupport.ATTENDANT, "oi", null).outcome())
                .isEqualTo(OmnichannelReplyService.Outcome.CONVERSATION_CLOSED);
    }

    @Test
    void replyEarlyRefusalsAreRecordedInIntegrityChain() {
        // D-01 (lado da resposta): inexistente, encerrada, canal
        // desabilitado e provedor fora do DPA lançam REFUSAL_RECORDED.
        Fixture f = new Fixture(OmnichannelChannel.WHATSAPP);
        f.service.reply(OmnichannelTestSupport.TENANT, UUID.randomUUID(),
                OmnichannelTestSupport.ATTENDANT, "oi", null);
        f.parameters.enabled = java.util.EnumSet.of(OmnichannelChannel.EMAIL);
        f.service.reply(OmnichannelTestSupport.TENANT, f.conversation.id(),
                OmnichannelTestSupport.ATTENDANT, "oi", null);
        f.parameters.enabled = java.util.EnumSet.allOf(OmnichannelChannel.class);
        f.dpa.approved = false;
        f.service.reply(OmnichannelTestSupport.TENANT, f.conversation.id(),
                OmnichannelTestSupport.ATTENDANT, "oi", null);
        f.dpa.approved = true;
        f.store.update(f.conversation.close(OmnichannelTestSupport.NOW.plusSeconds(10)));
        f.service.reply(OmnichannelTestSupport.TENANT, f.conversation.id(),
                OmnichannelTestSupport.ATTENDANT, "oi", null);
        assertThat(f.integrity.entries).hasSize(4)
                .allSatisfy(e -> assertThat(e.eventType())
                        .isEqualTo(IntegrityLog.EventType.REFUSAL_RECORDED));
        assertThat(f.integrity.entries).extracting(IntegrityLog.Entry::reference)
                .anySatisfy(r -> assertThat(r).contains("CONVERSATION_NOT_FOUND"))
                .anySatisfy(r -> assertThat(r).contains("CHANNEL_DISABLED"))
                .anySatisfy(r -> assertThat(r).contains("PROVIDER_NOT_IN_DPA"))
                .anySatisfy(r -> assertThat(r).contains("CONVERSATION_CLOSED"));
        assertThat(IntegrityLog.verifyChain(f.integrity.entries)).isTrue();
        assertThat(f.gateway.calls).isZero();
    }

    @Test
    void transferUsesEd35TrailWithMandatoryReason() {
        Fixture f = new Fixture(OmnichannelChannel.WHATSAPP);
        f.store.update(f.conversation.assign(OmnichannelTestSupport.ATTENDANT));
        AssignmentChange.TrailEntry trail = f.service.transfer(OmnichannelTestSupport.TENANT,
                f.conversation.id(), OmnichannelTestSupport.ATTENDANT, OmnichannelTestSupport.ATTENDANT_2,
                "morador pediu humano", OmnichannelTestSupport.ATTENDANT);
        assertThat(trail.targetType()).isEqualTo(AssignmentChange.TargetType.PROTOCOL);
        assertThat(trail.targetId()).isEqualTo(OmnichannelTestSupport.PROTOCOL);
        assertThat(trail.reason()).isEqualTo("morador pediu humano");
        assertThat(f.store.findById(OmnichannelTestSupport.TENANT, f.conversation.id()))
                .get().extracting(Conversation::assignedTo).isEqualTo(OmnichannelTestSupport.ATTENDANT_2);
        assertThatThrownBy(() -> f.service.transfer(OmnichannelTestSupport.TENANT, f.conversation.id(),
                OmnichannelTestSupport.ATTENDANT_2, OmnichannelTestSupport.ATTENDANT, " ",
                OmnichannelTestSupport.ATTENDANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usageReadFromEd41AndCostByCategoryAggregatesEd41Deliveries() {
        OmnichannelTestSupport.InMemoryUsage store = new OmnichannelTestSupport.InMemoryUsage();
        store.append(new UsageRecord(OmnichannelTestSupport.TENANT, "ED-42", UsageMetric.MESSAGES,
                2, new BigDecimal("0.70"), UUID.randomUUID(), OmnichannelTestSupport.NOW));
        OmnichannelUsageService service = new OmnichannelUsageService(new UsageReportService(store));
        UsageReportService.UsageReport report = service.usage(OmnichannelTestSupport.TENANT,
                OmnichannelTestSupport.NOW.minusSeconds(60), OmnichannelTestSupport.NOW.plusSeconds(60));
        assertThat(report.modules()).hasSize(1);
        assertThat(report.modules().get(0).quantities()).containsEntry(UsageMetric.MESSAGES, 2L);
        assertThat(report.modules().get(0).totalCost()).isEqualByComparingTo("0.70");

        ChannelDelivery utility = delivery(WhatsAppCategory.UTILITY, "0.35");
        ChannelDelivery marketing = delivery(WhatsAppCategory.MARKETING, "1.20");
        ChannelDelivery utility2 = delivery(WhatsAppCategory.UTILITY, "0.35");
        Map<WhatsAppCategory, BigDecimal> totals =
                OmnichannelUsageService.costByTemplateCategory(List.of(utility, marketing, utility2));
        assertThat(totals).containsEntry(WhatsAppCategory.UTILITY, new BigDecimal("0.70"))
                .containsEntry(WhatsAppCategory.MARKETING, new BigDecimal("1.20"));
    }

    @Test
    void emailDeliverabilityConsumesP35AndEd41Rules() {
        // P3.5: 1 reclamação em 1000 envios = 0,10% -> atinge o teto (regra: abaixo de 0,10%).
        assertThat(OmnichannelUsageService.emailComplaintRateBreaches(1000, 1)).isTrue();
        assertThat(OmnichannelUsageService.emailComplaintRateBreaches(10000, 1)).isFalse();
        var snapshot = OmnichannelUsageService.deliverability(OmnichannelTestSupport.TENANT,
                NotificationChannel.EMAIL, OmnichannelTestSupport.NOW, OmnichannelTestSupport.NOW.plusSeconds(3600),
                200, 190, 7, 3);
        assertThat(snapshot.deliveryRate().orElseThrow()).isEqualByComparingTo("0.9500");
        assertThat(snapshot.failureRate().orElseThrow()).isEqualByComparingTo("0.0500");
    }

    private static ChannelDelivery delivery(WhatsAppCategory category, String cost) {
        return new ChannelDelivery(UUID.randomUUID(), OmnichannelTestSupport.TENANT, UUID.randomUUID(),
                OmnichannelTestSupport.CUSTOMER, null, UUID.randomUUID(), NotificationChannel.WHATSAPP,
                "+5548999990000", "bill-template", 1, category, ChannelDeliveryStatus.DELIVERED,
                "pm-1", new BigDecimal(cost), OmnichannelTestSupport.NOW, OmnichannelTestSupport.NOW);
    }
}
