package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.notification.ContactRegistry;
import br.com.ziipsan.platform.notification.DeliveryAttempt;
import br.com.ziipsan.platform.notification.DeliveryProofRule;
import br.com.ziipsan.platform.notification.DeliveryStatus;
import br.com.ziipsan.platform.notification.DeliveryStore;
import br.com.ziipsan.platform.notification.NotificationDispatchService;
import br.com.ziipsan.platform.notification.EmailGateway;
import br.com.ziipsan.platform.notification.MessageTemplateEngine;
import br.com.ziipsan.platform.notification.MessagingParameters;
import br.com.ziipsan.platform.notification.NotificationChannel;
import br.com.ziipsan.platform.notification.RecipientContact;
import br.com.ziipsan.platform.notification.RenderedMessage;
import br.com.ziipsan.platform.notification.SenderDomainStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fatura por e-mail e comunicado de débito (ED-41; POC-20 do roteiro
 * VISAN): o e-mail sai do domínio AUTENTICADO do tenant (SPF/DKIM/DMARC da
 * P3.5), com o aviso e o LINK AUTENTICADO ao portal, e o PDF anexo SOMENTE
 * nas hipóteses da {@link BillEmailRule} (opt-in do morador, ou padrão do
 * tenant com aceite formal — ADR-0028, l).
 *
 * <p>Guardas, em ordem: canal de e-mail ligado no tenant; <b>tenant não
 * pausado</b>; <b>taxa de reclamação do tenant abaixo de 0,10%</b>; contato
 * de e-mail verificado no cadastro da P1.3; <b>endereço fora da lista de
 * supressão do tenant</b>; domínio do tenant alinhado (a mesma regra
 * da P3.5); link autenticado do portal disponível (sem link, não há fatura
 * por e-mail no desenho do aceite — recusa nomeada, nunca link presumido);
 * decidindo anexar, o PDF precisa existir no faturamento (decisão de
 * anexar sem PDF = recusa {@code PDF_UNAVAILABLE}, nunca envio "sem o
 * anexo prometido" em silêncio). O envio grava a tentativa na MESMA porta
 * de prova da P3.5 ({@link DeliveryStore}), de modo que
 * delivered/bounced/complained e a prova de entrega valem também para a
 * fatura, e mede o uso ({@code MESSAGES}, sem custo atribuído ao e-mail
 * nesta entrega). Nesta porta, o {@code noticeId} da tentativa gravada é o
 * id da CONTA (a fatura é o próprio aviso) — declaração expressa, porque o
 * fluxo da fatura não nasce de uma mensagem da outbox como os demais
 * avisos.
 *
 * <p><b>CORREÇÃO PÓS-REVISÃO (defeito D2 da revisão da ED-41) — a fatura
 * HERDA as guardas de estado de envio da P3.5.</b> Como a fatura grava na
 * MESMA {@link DeliveryStore} e sai pelo MESMO canal de e-mail, ela passou a
 * consultar as guardas que o {@link NotificationDispatchService} da P3.5
 * aplica a todo e-mail do tenant, reusando as réguas/portas da P3.5 sem
 * duplicar regra: pausa do tenant ({@link DeliveryStore#sendingState}),
 * taxa de reclamação (contagens da mesma porta + regra pura
 * {@link DeliveryProofRule#complaintRateBreaches} — atingir o limite pausa
 * o tenant, como na P3.5) e supressão do endereço
 * ({@link DeliveryStore#isSuppressed}). Pausa e supressão são higiene de
 * reputação do tenant, não preferência do morador: tenant pausado ou
 * endereço suprimido NÃO recebem fatura por esta porta.
 *
 * <p><b>Decisão registrada (D2): a preferência da P3.36 NÃO é herdada.</b>
 * Fundamento: a fatura e o comunicado de débito são avisos da categoria de
 * cobrança — no piso da P3.36/{@code NotificationPolicy}, categoria
 * obrigatória está FORA do alcance da preferência (o morador não desliga o
 * canal da própria fatura; o que ele controla é o anexo do PDF, pela
 * {@link BillEmailRule}, e os canais promocionais/multicanal, pelo opt-in da
 * ED-41). Aplicar a preferência aqui desligaria a entrega da fatura sem
 * desligar a cobrança — por isso a isenção é estrutural, não omissão, e
 * fica declarada neste javadoc e no relatório (seção de correção D2).
 *
 * <p>As finalidades {@link #BILL_PURPOSE} e {@link #DEBIT_PURPOSE} são
 * códigos PROPOSTOS para o catálogo de finalidades da P1.3 (ele é o dono
 * dos códigos; a integração os registra ou substitui — declarado no
 * relatório).
 */
public class BillEmailService {

    /** Finalidade proposta da entrega da fatura (catálogo da P1.3). */
    public static final String BILL_PURPOSE = "BILL_DELIVERY";
    /** Finalidade proposta do comunicado de débito (catálogo da P1.3). */
    public static final String DEBIT_PURPOSE = "DEBIT_NOTICE";
    /** Variável do modelo com o link autenticado ao portal (nome desta porta, como na P3.5). */
    public static final String PORTAL_LINK_VARIABLE = "portalLink";

    public enum Outcome {
        SENT, CHANNEL_DISABLED, TENANT_PAUSED, COMPLAINT_RATE_BREACH, SUPPRESSED, NO_CONTACT,
        DOMAIN_NOT_ALIGNED, PORTAL_LINK_UNAVAILABLE, PDF_UNAVAILABLE
    }

    public record BillEmailResult(Outcome outcome, UUID attemptId, boolean pdfAttached,
                                  BillEmailRule.Basis basis, String reason) {
        public boolean sent() {
            return outcome == Outcome.SENT;
        }

        static BillEmailResult of(Outcome outcome, BillEmailRule.Basis basis, String reason) {
            return new BillEmailResult(outcome, null, false, basis, reason);
        }
    }

    private final MessagingParameters messagingParameters;
    private final EmailGateway emailGateway;
    private final ContactRegistry contacts;
    private final MessageTemplateEngine templateEngine;
    private final BillEmailPorts.BillEmailSender sender;
    private final BillEmailPorts.PortalLinkPort portalLinks;
    private final BillEmailPorts.BillPdfPort billPdfs;
    private final BillEmailPorts.BillPdfOptInReader optInReader;
    private final MultichannelParameters parameters;
    private final DeliveryStore deliveryStore;
    private final UsageStore usageStore;
    private final Clock clock;

    public BillEmailService(MessagingParameters messagingParameters, EmailGateway emailGateway,
                            ContactRegistry contacts, MessageTemplateEngine templateEngine,
                            BillEmailPorts.BillEmailSender sender, BillEmailPorts.PortalLinkPort portalLinks,
                            BillEmailPorts.BillPdfPort billPdfs, BillEmailPorts.BillPdfOptInReader optInReader,
                            MultichannelParameters parameters, DeliveryStore deliveryStore,
                            UsageStore usageStore, Clock clock) {
        if (messagingParameters == null || emailGateway == null || contacts == null || templateEngine == null
                || sender == null || portalLinks == null || billPdfs == null || optInReader == null
                || parameters == null || deliveryStore == null || usageStore == null || clock == null) {
            throw new IllegalArgumentException("parâmetros, gateway, cadastro, motor de modelos, remetente, "
                    + "portal, PDF, opt-in, prova, medição e relógio são obrigatórios");
        }
        this.messagingParameters = messagingParameters;
        this.emailGateway = emailGateway;
        this.contacts = contacts;
        this.templateEngine = templateEngine;
        this.sender = sender;
        this.portalLinks = portalLinks;
        this.billPdfs = billPdfs;
        this.optInReader = optInReader;
        this.parameters = parameters;
        this.deliveryStore = deliveryStore;
        this.usageStore = usageStore;
        this.clock = clock;
    }

    /** Fatura por e-mail: aviso + link autenticado; PDF anexo só pela {@link BillEmailRule}. */
    public BillEmailResult sendBillEmail(UUID tenantId, UUID customerId, UUID billId, String templateCode,
                                         Map<String, String> variables, String moduleCode) {
        return send(tenantId, customerId, billId, templateCode, variables, moduleCode, BILL_PURPOSE, true);
    }

    /** Comunicado de débito por e-mail: aviso + link autenticado, sem anexo de PDF. */
    public BillEmailResult sendDebitNotice(UUID tenantId, UUID customerId, UUID billId, String templateCode,
                                           Map<String, String> variables, String moduleCode) {
        return send(tenantId, customerId, billId, templateCode, variables, moduleCode, DEBIT_PURPOSE, false);
    }

    private BillEmailResult send(UUID tenantId, UUID customerId, UUID billId, String templateCode,
                                 Map<String, String> variables, String moduleCode,
                                 String purposeCode, boolean billFlow) {
        if (tenantId == null || customerId == null || billId == null || templateCode == null
                || templateCode.isBlank() || variables == null || moduleCode == null
                || moduleCode.isBlank()) {
            throw new IllegalArgumentException(
                    "tenant, consumidor, conta, modelo, variáveis e módulo são obrigatórios");
        }
        if (!messagingParameters.channelsEnabled(tenantId).contains(NotificationChannel.EMAIL)) {
            return BillEmailResult.of(Outcome.CHANNEL_DISABLED, null,
                    "canal de e-mail fora de msg.channels_enabled do tenant");
        }
        // Guardas de estado de envio herdadas da P3.5 (correção D2): a fatura
        // usa a mesma DeliveryStore e o mesmo canal dos demais e-mails do
        // tenant, logo respeita a pausa e a régua de reclamações dele — nas
        // mesmas posições relativas e com os mesmos efeitos do
        // NotificationDispatchService da P3.5 (a violação da taxa PAUSA o
        // tenant; os demais tenants não são afetados).
        DeliveryStore.TenantSendingState sendingState = deliveryStore.sendingState(tenantId);
        if (sendingState.paused()) {
            return BillEmailResult.of(Outcome.TENANT_PAUSED, null,
                    "envio do tenant pausado: " + sendingState.pauseReason());
        }
        if (complaintRateBreached(tenantId)) {
            deliveryStore.setPaused(tenantId, true, "complaint-rate");
            return BillEmailResult.of(Outcome.COMPLAINT_RATE_BREACH, null,
                    "taxa de reclamação do tenant atingiu 0,10%: tenant pausado, os demais não são afetados");
        }
        RecipientContact contact = contacts.resolveEmailContact(tenantId, customerId, null, purposeCode)
                .orElse(null);
        if (contact == null || !contact.verified()) {
            return BillEmailResult.of(Outcome.NO_CONTACT, null,
                    "sem contato de e-mail verificado no cadastro para o consumidor e a finalidade");
        }
        String address = contact.emailAddress().trim().toLowerCase(java.util.Locale.ROOT);
        // Supressão herdada da P3.5 (correção D2): endereço suprimido neste
        // tenant (bounce/reclamação anteriores) não recebe a fatura.
        if (deliveryStore.isSuppressed(tenantId, address)) {
            return BillEmailResult.of(Outcome.SUPPRESSED, null,
                    "endereço na lista de supressão do tenant");
        }
        String fromDomain = messagingParameters.emailFromDomain(tenantId);
        SenderDomainStatus domainStatus = emailGateway.domainStatus(tenantId, fromDomain);
        if (domainStatus == null) {
            return BillEmailResult.of(Outcome.DOMAIN_NOT_ALIGNED, null,
                    "o provedor não devolveu o estado de autenticação do domínio do tenant");
        }
        try {
            DeliveryProofRule.assertDomainAligned(fromDomain, domainStatus);
        } catch (ApiException.Validation e) {
            return BillEmailResult.of(Outcome.DOMAIN_NOT_ALIGNED, null, e.getMessage());
        }
        String portalLink = portalLinks.authenticatedBillLink(tenantId, customerId, billId);
        if (portalLink == null || portalLink.isBlank()) {
            return BillEmailResult.of(Outcome.PORTAL_LINK_UNAVAILABLE, null,
                    "o portal não devolveu o link autenticado da fatura");
        }

        BillEmailRule.Basis basis = null;
        Optional<BillEmailPorts.BillPdfAttachment> attachment = Optional.empty();
        if (billFlow) {
            BillEmailRule.AttachmentDecision decision = BillEmailRule.decide(
                    optInReader.residentPdfOptIn(tenantId, customerId),
                    parameters.billPdfTenantDefault(tenantId),
                    parameters.billPdfFormalAcceptance(tenantId));
            basis = decision.basis();
            if (decision.attachPdf()) {
                attachment = billPdfs.billPdf(tenantId, billId);
                if (attachment.isEmpty()) {
                    return BillEmailResult.of(Outcome.PDF_UNAVAILABLE, basis,
                            "a regra manda anexar o PDF, mas o faturamento não tem o documento desta conta");
                }
            }
        }

        Map<String, String> merged = new LinkedHashMap<>(variables);
        merged.put(PORTAL_LINK_VARIABLE, portalLink);
        RenderedMessage rendered = templateEngine.render(tenantId, templateCode,
                NotificationChannel.EMAIL, Map.copyOf(merged));
        String providerMessageId = sender.send(tenantId, fromDomain, address, rendered, attachment);
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new IllegalStateException("o remetente da fatura não devolveu o id da mensagem do provedor");
        }
        Instant sentAt = clock.instant();
        DeliveryAttempt attempt = new DeliveryAttempt(UUID.randomUUID(), tenantId, billId,
                customerId, null, contact.contactId(), NotificationChannel.EMAIL, address,
                rendered.templateCode(), rendered.templateVersion(), DeliveryStatus.SENT,
                providerMessageId, sentAt, null);
        deliveryStore.saveAttempt(attempt);
        usageStore.append(new UsageRecord(tenantId, moduleCode, UsageMetric.MESSAGES, 1,
                null, attempt.id(), sentAt));
        return new BillEmailResult(Outcome.SENT, attempt.id(), attachment.isPresent(), basis, null);
    }

    /**
     * Taxa de reclamação do tenant violada (≥ 0,10%)? MESMA régua da P3.5,
     * sem duplicação: as contagens vêm da {@link DeliveryStore} compartilhada
     * (histórico gravado do tenant, da época até agora) e o limite é a regra
     * pura {@link DeliveryProofRule#complaintRateBreaches}. Sem envios não há
     * taxa nem violação — como em
     * {@link NotificationDispatchService#complaintRateBreached}.
     */
    private boolean complaintRateBreached(UUID tenantId) {
        Instant now = clock.instant();
        long sent = deliveryStore.countSent(tenantId, Instant.EPOCH, now);
        if (sent == 0) {
            return false;
        }
        long complaints = deliveryStore.countComplaints(tenantId, Instant.EPOCH, now);
        return DeliveryProofRule.complaintRateBreaches(sent, complaints);
    }
}
