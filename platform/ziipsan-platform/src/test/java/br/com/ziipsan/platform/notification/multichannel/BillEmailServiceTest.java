package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.NotificationChannel;
import br.com.ziipsan.platform.notification.RecipientContact;
import br.com.ziipsan.platform.notification.SenderDomainStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static br.com.ziipsan.platform.notification.multichannel.MultichannelTestSupport.CUSTOMER;
import static br.com.ziipsan.platform.notification.multichannel.MultichannelTestSupport.NOW;
import static br.com.ziipsan.platform.notification.multichannel.MultichannelTestSupport.TENANT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fatura por e-mail (POC-20) sobre fakes: por padrão só o link autenticado;
 * o PDF só entra nas hipóteses da regra (ADR-0028, l).
 */
class BillEmailServiceTest {

    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID BILL = UUID.randomUUID();
    private static final String MODULE = "commercial.billing";

    private MultichannelTestSupport.FakeMessaging messaging;
    private MultichannelTestSupport.FakeEmailGateway gateway;
    private MultichannelTestSupport.FakeEmailContacts contacts;
    private MultichannelTestSupport.FakeTemplateEngine engine;
    private MultichannelTestSupport.FakeBillSender sender;
    private MultichannelTestSupport.InMemoryDeliveryStore deliveryStore;
    private MultichannelTestSupport.InMemoryUsageStore usage;
    private MultichannelTestSupport.FakeParams params;
    private Optional<Boolean> residentOptIn;
    private Optional<BillEmailPorts.BillPdfAttachment> pdf;
    private String portalLink;
    private BillEmailService service;

    @BeforeEach
    void setUp() {
        messaging = new MultichannelTestSupport.FakeMessaging();
        gateway = new MultichannelTestSupport.FakeEmailGateway();
        contacts = new MultichannelTestSupport.FakeEmailContacts();
        engine = new MultichannelTestSupport.FakeTemplateEngine();
        sender = new MultichannelTestSupport.FakeBillSender();
        deliveryStore = new MultichannelTestSupport.InMemoryDeliveryStore();
        usage = new MultichannelTestSupport.InMemoryUsageStore();
        params = new MultichannelTestSupport.FakeParams();
        residentOptIn = Optional.empty();
        pdf = Optional.of(new BillEmailPorts.BillPdfAttachment("doc-ref-1", "fatura.pdf"));
        portalLink = "portal-link-assinado";
        service = new BillEmailService(messaging, gateway, contacts, engine, sender,
                (t, c, b) -> portalLink,
                (t, b) -> pdf,
                (t, c) -> residentOptIn,
                params, deliveryStore, usage, CLOCK);
    }

    private BillEmailService.BillEmailResult sendBill() {
        return service.sendBillEmail(TENANT, CUSTOMER, BILL, "bill.email", Map.of("billId", BILL.toString()),
                MODULE);
    }

    @Test
    @DisplayName("padrão: fatura vai com aviso e link autenticado, sem PDF anexo")
    void linkOnlyByDefault() {
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.outcome()).isEqualTo(BillEmailService.Outcome.SENT);
        assertThat(r.pdfAttached()).isFalse();
        assertThat(r.basis()).isEqualTo(BillEmailRule.Basis.LINK_ONLY_NO_OPT_IN);
        assertThat(sender.attachments).containsExactly(Optional.empty());
        assertThat(engine.lastVariables).containsEntry(BillEmailService.PORTAL_LINK_VARIABLE, portalLink);
        assertThat(deliveryStore.attempts).hasSize(1);
        assertThat(deliveryStore.attempts.get(0).channel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(usage.records).extracting(UsageRecord::metric).containsExactly(UsageMetric.MESSAGES);
    }

    @Test
    @DisplayName("opt-in do morador: PDF anexo")
    void attachWithResidentOptIn() {
        residentOptIn = Optional.of(true);
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.pdfAttached()).isTrue();
        assertThat(r.basis()).isEqualTo(BillEmailRule.Basis.RESIDENT_OPT_IN);
        assertThat(sender.attachments.get(0)).isPresent();
    }

    @Test
    @DisplayName("padrão do tenant com aceite formal: PDF anexo")
    void attachWithTenantDefaultAndFormalAcceptance() {
        params.billPdfDefault = true;
        params.billPdfFormal = true;
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.pdfAttached()).isTrue();
        assertThat(r.basis()).isEqualTo(BillEmailRule.Basis.TENANT_DEFAULT_WITH_FORMAL_ACCEPTANCE);
    }

    @Test
    @DisplayName("padrão do tenant sem aceite formal: só link")
    void tenantDefaultWithoutFormalAcceptanceStaysLinkOnly() {
        params.billPdfDefault = true;
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.pdfAttached()).isFalse();
        assertThat(r.basis()).isEqualTo(BillEmailRule.Basis.LINK_ONLY_NO_FORMAL_ACCEPTANCE);
    }

    @Test
    @DisplayName("opt-out do morador vence o padrão do tenant com aceite formal")
    void residentOptOutBeatsTenantDefault() {
        residentOptIn = Optional.of(false);
        params.billPdfDefault = true;
        params.billPdfFormal = true;
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.pdfAttached()).isFalse();
        assertThat(r.basis()).isEqualTo(BillEmailRule.Basis.LINK_ONLY_RESIDENT_OPT_OUT);
    }

    @Test
    @DisplayName("regra manda anexar mas o faturamento não tem o PDF: recusa nomeada, sem envio")
    void attachDecidedButPdfMissingRefuses() {
        residentOptIn = Optional.of(true);
        pdf = Optional.empty();
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.outcome()).isEqualTo(BillEmailService.Outcome.PDF_UNAVAILABLE);
        assertThat(sender.attachments).isEmpty();
    }

    @Test
    @DisplayName("domínio do tenant sem DKIM alinhado não envia (regra da P3.5)")
    void domainNotAlignedBlocks() {
        gateway.status = new SenderDomainStatus("envio.example.com", true, false, true);
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.outcome()).isEqualTo(BillEmailService.Outcome.DOMAIN_NOT_ALIGNED);
        assertThat(sender.attachments).isEmpty();
    }

    @Test
    @DisplayName("contato de e-mail não verificado não recebe a fatura")
    void noVerifiedContactBlocks() {
        contacts.contact = Optional.of(new RecipientContact(
                MultichannelTestSupport.CONTACT_ID, CUSTOMER, null, "titular@example.com", false));
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.outcome()).isEqualTo(BillEmailService.Outcome.NO_CONTACT);
    }

    @Test
    @DisplayName("sem link autenticado do portal não há fatura por e-mail")
    void portalLinkUnavailableBlocks() {
        portalLink = " ";
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.outcome()).isEqualTo(BillEmailService.Outcome.PORTAL_LINK_UNAVAILABLE);
        assertThat(sender.attachments).isEmpty();
    }

    @Test
    @DisplayName("D2: tenant pausado na P3.5 não envia fatura por e-mail")
    void tenantPausedBlocksBill() {
        deliveryStore.paused = true;
        deliveryStore.pauseReason = "manual-hold";
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.outcome()).isEqualTo(BillEmailService.Outcome.TENANT_PAUSED);
        assertThat(sender.attachments).isEmpty();
        assertThat(deliveryStore.attempts).isEmpty();
    }

    @Test
    @DisplayName("D2: destinatário suprimido no tenant não recebe a fatura")
    void suppressedRecipientBlocksBill() {
        deliveryStore.suppressed.add("titular@example.com");
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.outcome()).isEqualTo(BillEmailService.Outcome.SUPPRESSED);
        assertThat(sender.attachments).isEmpty();
        assertThat(deliveryStore.attempts).isEmpty();
    }

    @Test
    @DisplayName("D2: taxa de reclamação no limite (1/1000 = 0,10%) bloqueia e pausa o tenant")
    void complaintRateBreachBlocksAndPauses() {
        deliveryStore.sentOverride = 1000;
        deliveryStore.complaintsOverride = 1;
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.outcome()).isEqualTo(BillEmailService.Outcome.COMPLAINT_RATE_BREACH);
        assertThat(sender.attachments).isEmpty();
        assertThat(deliveryStore.paused).isTrue();
        assertThat(deliveryStore.recordedPauseReason).isEqualTo("complaint-rate");
    }

    @Test
    @DisplayName("D2: taxa de reclamação abaixo do limite (1/10000 = 0,01%) não bloqueia a fatura")
    void complaintRateBelowLimitSends() {
        deliveryStore.sentOverride = 10000;
        deliveryStore.complaintsOverride = 1;
        BillEmailService.BillEmailResult r = sendBill();
        assertThat(r.outcome()).isEqualTo(BillEmailService.Outcome.SENT);
        assertThat(deliveryStore.paused).isFalse();
    }

    @Test
    @DisplayName("comunicado de débito envia com link e sem anexo, com tentativa gravada")
    void debitNoticeSendsWithoutAttachment() {
        BillEmailService.BillEmailResult r = service.sendDebitNotice(TENANT, CUSTOMER, BILL,
                "debit.email", Map.of("billId", BILL.toString()), MODULE);
        assertThat(r.outcome()).isEqualTo(BillEmailService.Outcome.SENT);
        assertThat(r.pdfAttached()).isFalse();
        assertThat(r.basis()).isNull();
        assertThat(sender.attachments).containsExactly(Optional.empty());
        assertThat(deliveryStore.attempts).hasSize(1);
    }
}
