package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.notification.NotificationCategory;
import br.com.ziipsan.platform.notification.NotificationChannel;
import br.com.ziipsan.platform.notification.OutboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static br.com.ziipsan.platform.notification.multichannel.MultichannelTestSupport.CUSTOMER;
import static br.com.ziipsan.platform.notification.multichannel.MultichannelTestSupport.NOW;
import static br.com.ziipsan.platform.notification.multichannel.MultichannelTestSupport.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serviço multicanal sobre fakes das portas — guardas na ordem declarada.
 * Contas à mão: SMS a 0,30 com teto 1.000,00; travessia de 80% com gasto
 * prévio de 750,00 e SMS de 50,00 (= 800,00).
 */
class MultichannelDispatchServiceTest {

    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String MODULE = "commercial.billing";

    private MultichannelTestSupport.FakeMessaging messaging;
    private MultichannelTestSupport.FakePreferences preferences;
    private MultichannelTestSupport.FakeConsent consent;
    private MultichannelTestSupport.FakeChannelContacts contacts;
    private MultichannelTestSupport.FakeTemplateEngine engine;
    private MultichannelTestSupport.FakeGateways gateways;
    private MultichannelTestSupport.InMemoryChannelStore store;
    private MultichannelTestSupport.FakePricing pricing;
    private MultichannelTestSupport.FakeParams params;
    private MultichannelTestSupport.FakeCapSink capSink;
    private MultichannelTestSupport.InMemoryUsageStore usage;
    private boolean dpaApproved;
    private boolean marketingApproved;
    private MultichannelDispatchService service;

    @BeforeEach
    void setUp() {
        messaging = new MultichannelTestSupport.FakeMessaging();
        preferences = new MultichannelTestSupport.FakePreferences();
        consent = new MultichannelTestSupport.FakeConsent();
        contacts = new MultichannelTestSupport.FakeChannelContacts();
        engine = new MultichannelTestSupport.FakeTemplateEngine();
        gateways = new MultichannelTestSupport.FakeGateways();
        store = new MultichannelTestSupport.InMemoryChannelStore();
        pricing = new MultichannelTestSupport.FakePricing()
                .with(NotificationChannel.SMS, null, "0.30")
                .with(NotificationChannel.WHATSAPP, WhatsAppCategory.UTILITY, "0.20");
        params = new MultichannelTestSupport.FakeParams();
        capSink = new MultichannelTestSupport.FakeCapSink();
        usage = new MultichannelTestSupport.InMemoryUsageStore();
        dpaApproved = true;
        marketingApproved = false;
        service = new MultichannelDispatchService(new MultichannelTestSupport.FakeCatalog(), messaging,
                preferences, consent, new MultichannelTestSupport.FakeNotificationParameters(), contacts,
                engine, gateways.sms, gateways.whatsapp, gateways.push, store, pricing, params,
                (t, ch) -> dpaApproved, (t, code, v) -> marketingApproved, capSink, usage, CLOCK);
    }

    private void optIn(NotificationCategory category, NotificationChannel channel, boolean enabled) {
        preferences.rows.add(MultichannelTestSupport.preference(CUSTOMER, "BILLING", category, channel, enabled));
    }

    private OutboxMessage billMessage(String template) {
        return MultichannelTestSupport.message("bill.issued", "BILLING", template, CUSTOMER);
    }

    private void seedSpend(String amount) {
        store.save(new ChannelDelivery(UUID.randomUUID(), TENANT, UUID.randomUUID(), CUSTOMER, null,
                MultichannelTestSupport.CONTACT_ID, NotificationChannel.SMS, "+5548999990000",
                "bill.sms", 3, null, ChannelDeliveryStatus.SENT, "seed-" + amount,
                new BigDecimal(amount), NOW.minusSeconds(3600), null));
    }

    @Test
    @DisplayName("SMS feliz: envia, grava a tentativa e mede MESSAGES + SMS com o custo")
    void smsHappyPath() {
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        MultichannelDispatchService.MultichannelDispatchResult r =
                service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE);
        assertThat(r.outcome()).isEqualTo(MultichannelDispatchService.Outcome.SENT);
        assertThat(r.estimatedCost()).isEqualByComparingTo("0.30");
        assertThat(store.deliveries).hasSize(1);
        assertThat(gateways.sends).containsExactly("SMS|ZIIPSAN|+5548999990000");
        assertThat(usage.records).extracting(UsageRecord::metric)
                .containsExactly(UsageMetric.MESSAGES, UsageMetric.SMS);
    }

    @Test
    @DisplayName("canal fora de msg.channels_enabled não dispara")
    void channelDisabled() {
        messaging.channels.remove(NotificationChannel.SMS);
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        assertThat(service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE).outcome())
                .isEqualTo(MultichannelDispatchService.Outcome.CHANNEL_DISABLED);
        assertThat(gateways.sends).isEmpty();
    }

    @Test
    @DisplayName("sem provedor aprovado no DPA não dispara")
    void providerNotInDpa() {
        dpaApproved = false;
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        assertThat(service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE).outcome())
                .isEqualTo(MultichannelDispatchService.Outcome.PROVIDER_NOT_IN_DPA);
    }

    @Test
    @DisplayName("sem opt-in registrado não dispara (opt-in não se presume)")
    void optInMissing() {
        assertThat(service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE).outcome())
                .isEqualTo(MultichannelDispatchService.Outcome.OPT_IN_MISSING);
        assertThat(gateways.sends).isEmpty();
    }

    @Test
    @DisplayName("preferência desligada não dispara")
    void preferenceOff() {
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, false);
        assertThat(service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE).outcome())
                .isEqualTo(MultichannelDispatchService.Outcome.PREFERENCE_OFF);
    }

    @Test
    @DisplayName("categoria obrigatória envia mesmo com a preferência desligada")
    void mandatoryBypassesPreferenceOff() {
        preferences.rows.add(MultichannelTestSupport.preference(CUSTOMER, "LEGAL",
                NotificationCategory.LEGAL_NOTICE, NotificationChannel.SMS, false));
        OutboxMessage legal = MultichannelTestSupport.message("legal.notice", "LEGAL", "legal.sms", CUSTOMER);
        assertThat(service.dispatch(legal, NotificationChannel.SMS, MODULE).outcome())
                .isEqualTo(MultichannelDispatchService.Outcome.SENT);
    }

    @Test
    @DisplayName("consentimento exato da preferência inativo na P1.3 bloqueia")
    void consentInactive() {
        consent.active = false;
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        assertThat(service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE).outcome())
                .isEqualTo(MultichannelDispatchService.Outcome.CONSENT_INACTIVE);
    }

    @Test
    @DisplayName("sem contato verificado do canal não dispara")
    void noContact() {
        contacts.phone = Optional.empty();
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        assertThat(service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE).outcome())
                .isEqualTo(MultichannelDispatchService.Outcome.NO_CONTACT);
    }

    @Test
    @DisplayName("sem remetente autenticado de SMS do tenant não dispara")
    void noSenderIdentity() {
        params.smsSender = Optional.empty();
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        assertThat(service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE).outcome())
                .isEqualTo(MultichannelDispatchService.Outcome.NO_SENDER_IDENTITY);
    }

    @Test
    @DisplayName("WhatsApp sem classificação no mapa não dispara")
    void whatsappTemplateNotClassified() {
        optIn(NotificationCategory.BILLING, NotificationChannel.WHATSAPP, true);
        assertThat(service.dispatch(billMessage("bill.whatsapp"), NotificationChannel.WHATSAPP, MODULE)
                .outcome()).isEqualTo(MultichannelDispatchService.Outcome.TEMPLATE_NOT_CLASSIFIED);
    }

    @Test
    @DisplayName("modelo de cobrança utilidade com texto promocional: alerta na aprovação e bloqueio")
    void whatsappMarketingRiskBlocked() {
        params.categoryMap = Map.of("bill.whatsapp", WhatsAppCategory.UTILITY);
        engine.body = "Sua fatura chegou com desconto especial para você";
        optIn(NotificationCategory.BILLING, NotificationChannel.WHATSAPP, true);
        MultichannelDispatchService.MultichannelDispatchResult r =
                service.dispatch(billMessage("bill.whatsapp"), NotificationChannel.WHATSAPP, MODULE);
        assertThat(r.outcome()).isEqualTo(MultichannelDispatchService.Outcome.MARKETING_APPROVAL_REQUIRED);
        assertThat(r.category()).isEqualTo(WhatsAppCategory.UTILITY);
        assertThat(gateways.sends).isEmpty();
    }

    @Test
    @DisplayName("com a aprovação do risco registrada, o WhatsApp de cobrança envia")
    void whatsappMarketingRiskApprovedSends() {
        params.categoryMap = Map.of("bill.whatsapp", WhatsAppCategory.UTILITY);
        engine.body = "Sua fatura chegou com desconto especial para você";
        marketingApproved = true;
        optIn(NotificationCategory.BILLING, NotificationChannel.WHATSAPP, true);
        MultichannelDispatchService.MultichannelDispatchResult r =
                service.dispatch(billMessage("bill.whatsapp"), NotificationChannel.WHATSAPP, MODULE);
        assertThat(r.outcome()).isEqualTo(MultichannelDispatchService.Outcome.SENT);
        assertThat(gateways.sends).containsExactly("WA|wa-account-1|+5548999990000|UTILITY");
    }

    @Test
    @DisplayName("sem preço vigente do provedor não há estimativa: não dispara")
    void priceNotConfigured() {
        pricing.prices.clear();
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        assertThat(service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE).outcome())
                .isEqualTo(MultichannelDispatchService.Outcome.PRICE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("sem msg.monthly_cap configurado o disparo pago é recusado")
    void monthlyCapNotConfigured() {
        params.cap = Optional.empty();
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        assertThat(service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE).outcome())
                .isEqualTo(MultichannelDispatchService.Outcome.MONTHLY_CAP_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("gasto do mês já no teto: o próximo envio é bloqueado pelo teto")
    void monthlyCapReachedBlocks() {
        seedSpend("1000.00");
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        MultichannelDispatchService.MultichannelDispatchResult r =
                service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE);
        assertThat(r.outcome()).isEqualTo(MultichannelDispatchService.Outcome.MONTHLY_CAP_REACHED);
        assertThat(gateways.sends).isEmpty();
    }

    @Test
    @DisplayName("travessia de 80% no envio emite o alerta de 80% e envia")
    void capAlert80EmittedOnCrossing() {
        seedSpend("750.00");
        pricing.with(NotificationChannel.SMS, null, "50.00");
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        MultichannelDispatchService.MultichannelDispatchResult r =
                service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE);
        assertThat(r.outcome()).isEqualTo(MultichannelDispatchService.Outcome.SENT);
        assertThat(capSink.alerts).containsExactly(MonthlyCapRule.CapLevel.ALERT_80);
        assertThat(r.capDecision().projectedSpend()).isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("D3: envio BLOQUEADO pelo teto cruzando 80% e 100% na projeção não emite alerta nenhum")
    void capBlockedCrossingEmitsNoAlert() {
        seedSpend("950.00");
        pricing.with(NotificationChannel.SMS, null, "100.00");
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        MultichannelDispatchService.MultichannelDispatchResult r =
                service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE);
        assertThat(r.outcome()).isEqualTo(MultichannelDispatchService.Outcome.MONTHLY_CAP_REACHED);
        assertThat(r.capDecision().projectedSpend()).isEqualByComparingTo("1050.00");
        assertThat(capSink.alerts).isEmpty();
        assertThat(gateways.sends).isEmpty();
        assertThat(store.deliveries).hasSize(1);
    }

    @Test
    @DisplayName("D3: falha do gateway na travessia de 80% não deixa alerta sem gasto")
    void capGatewayFailureEmitsNoAlert() {
        seedSpend("750.00");
        pricing.with(NotificationChannel.SMS, null, "50.00");
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        MultichannelDispatchService failing = new MultichannelDispatchService(
                new MultichannelTestSupport.FakeCatalog(), messaging, preferences, consent,
                new MultichannelTestSupport.FakeNotificationParameters(), contacts, engine,
                (t, sender, dest, msg) -> { throw new IllegalStateException("gateway indisponível"); },
                gateways.whatsapp, gateways.push, store, pricing, params,
                (t, ch) -> dpaApproved, (t, code, v) -> marketingApproved, capSink, usage, CLOCK);
        assertThatThrownBy(() -> failing.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE))
                .isInstanceOf(IllegalStateException.class);
        assertThat(capSink.alerts).isEmpty();
        assertThat(store.deliveries).hasSize(1);
    }

    @Test
    @DisplayName("D3: travessia de 100% com envio admitido (pousa no teto) emite ALERT_80 e REACHED_100 após o envio")
    void capAlert100EmittedAfterCommittedSend() {
        seedSpend("750.00");
        pricing.with(NotificationChannel.SMS, null, "250.00");
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        MultichannelDispatchService.MultichannelDispatchResult r =
                service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE);
        assertThat(r.outcome()).isEqualTo(MultichannelDispatchService.Outcome.SENT);
        assertThat(capSink.alerts).containsExactly(MonthlyCapRule.CapLevel.ALERT_80,
                MonthlyCapRule.CapLevel.REACHED_100);
        assertThat(store.deliveries).hasSize(2);
    }

    @Test
    @DisplayName("push feliz: envia sem preço/teto e mede só MESSAGES")
    void pushHappyPath() {
        optIn(NotificationCategory.BILLING, NotificationChannel.PUSH, true);
        MultichannelDispatchService.MultichannelDispatchResult r =
                service.dispatch(billMessage("bill.push"), NotificationChannel.PUSH, MODULE);
        assertThat(r.outcome()).isEqualTo(MultichannelDispatchService.Outcome.SENT);
        assertThat(r.estimatedCost()).isNull();
        assertThat(usage.records).extracting(UsageRecord::metric)
                .containsExactly(UsageMetric.MESSAGES);
    }

    @Test
    @DisplayName("e-mail volta à P3.5 e IN_APP volta à central da P3.36")
    void emailAndInAppDelegated() {
        assertThat(service.dispatch(billMessage("bill.mail"), NotificationChannel.EMAIL, MODULE).outcome())
                .isEqualTo(MultichannelDispatchService.Outcome.DELEGATED_TO_P35);
        assertThat(service.dispatch(billMessage("bill.app"), NotificationChannel.IN_APP, MODULE).outcome())
                .isEqualTo(MultichannelDispatchService.Outcome.DELEGATED_TO_CENTRAL);
    }

    @Test
    @DisplayName("evento fora do catálogo da P1.38 é recusado e nenhum disparo nasce")
    void eventOutsideCatalogRefused() {
        OutboxMessage unknown = MultichannelTestSupport.message("unknown.event", "BILLING", "x", CUSTOMER);
        assertThatThrownBy(() -> service.dispatch(unknown, NotificationChannel.SMS, MODULE))
                .isInstanceOf(ApiException.Conflict.class);
        assertThat(gateways.sends).isEmpty();
    }

    @Test
    @DisplayName("evento delivered resolve a tentativa; reentrega do mesmo evento é idempotente")
    void channelEventDeliveredAndIdempotent() {
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE);
        ChannelDeliveryEvent event = new ChannelDeliveryEvent("evt-1", TENANT, "sms-1",
                ChannelDeliveryEvent.ChannelEventType.DELIVERED, NOW.plusSeconds(60), null);
        ChannelDelivery resolved = service.onChannelEvent(event);
        assertThat(resolved.status()).isEqualTo(ChannelDeliveryStatus.DELIVERED);
        ChannelDelivery again = service.onChannelEvent(event);
        assertThat(again.status()).isEqualTo(ChannelDeliveryStatus.DELIVERED);
        assertThat(contacts.invalidated).isEmpty();
    }

    @Test
    @DisplayName("evento rejected marca o contato como inválido no cadastro")
    void channelEventRejectedMarksContactInvalid() {
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE);
        service.onChannelEvent(new ChannelDeliveryEvent("evt-9", TENANT, "sms-1",
                ChannelDeliveryEvent.ChannelEventType.REJECTED, NOW.plusSeconds(30), "invalid-number"));
        assertThat(store.deliveries.get(0).status()).isEqualTo(ChannelDeliveryStatus.REJECTED);
        assertThat(contacts.invalidated).containsExactly(MultichannelTestSupport.CONTACT_ID);
    }

    @Test
    @DisplayName("D4: janela mista — enviada ANTES da janela e entregue DENTRO dela não entra em contagem nenhuma (coorte por sentAt)")
    void deliverabilityMixedWindowUsesSentAtCohort() {
        // Enviada 2h antes do relógio, resolvida AGORA (dentro da janela de ±1h):
        // com a base mista antiga (sentAt × resolvedAt) ela contaria como
        // entregue sem envio correspondente e a regra lançaria exceção.
        store.save(new ChannelDelivery(UUID.randomUUID(), TENANT, UUID.randomUUID(), CUSTOMER, null,
                MultichannelTestSupport.CONTACT_ID, NotificationChannel.SMS, "+5548999990000",
                "bill.sms", 3, null, ChannelDeliveryStatus.DELIVERED, "old-1",
                new BigDecimal("0.30"), NOW.minusSeconds(7200), NOW));
        // Enviada DENTRO da janela, ainda não resolvida: é o único envio da coorte.
        store.save(new ChannelDelivery(UUID.randomUUID(), TENANT, UUID.randomUUID(), CUSTOMER, null,
                MultichannelTestSupport.CONTACT_ID, NotificationChannel.SMS, "+5548999990000",
                "bill.sms", 3, null, ChannelDeliveryStatus.SENT, "new-1",
                new BigDecimal("0.30"), NOW.minusSeconds(1800), null));
        DeliverabilityRule.DeliverabilitySnapshot s = service.deliverability(TENANT,
                NotificationChannel.SMS, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        assertThat(s.sent()).isEqualTo(1);
        assertThat(s.delivered()).isEqualTo(0);
        assertThat(s.deliveryRate().get()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("entregabilidade do tenant sai das tentativas registradas")
    void deliverabilitySnapshot() {
        optIn(NotificationCategory.BILLING, NotificationChannel.SMS, true);
        service.dispatch(billMessage("bill.sms"), NotificationChannel.SMS, MODULE);
        service.onChannelEvent(new ChannelDeliveryEvent("evt-1", TENANT, "sms-1",
                ChannelDeliveryEvent.ChannelEventType.DELIVERED, NOW.plusSeconds(60), null));
        DeliverabilityRule.DeliverabilitySnapshot s = service.deliverability(TENANT,
                NotificationChannel.SMS, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        assertThat(s.sent()).isEqualTo(1);
        assertThat(s.delivered()).isEqualTo(1);
        assertThat(s.deliveryRate().get()).isEqualByComparingTo("1.0000");
    }
}
