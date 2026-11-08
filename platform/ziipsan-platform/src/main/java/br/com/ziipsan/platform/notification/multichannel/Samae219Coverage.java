package br.com.ziipsan.platform.notification.multichannel;

import java.util.List;

/**
 * Cobertura do item 2.19 do SAMAE no recorte da ED-41 (notificações). O item
 * 2.19 (atendimento/comunicação) se reparte entre a ED-35 (atendimento) e
 * esta entrega (a parte de notificações); este registro cobre SOMENTE a
 * fatia da ED-41.
 *
 * <p>MARCADOR HONESTO (mesmo padrão da ED-30): os códigos SG individuais da
 * fatia de notificações NÃO foram confirmados nas fontes disponíveis nesta
 * entrega — nenhum código SG é inventado aqui; as linhas usam as exigências
 * do aceite da ED-41 e a atribuição de códigos SG fica PENDENTE de
 * conferência contra o roteiro do SAMAE.
 */
public final class Samae219Coverage {

    private Samae219Coverage() {
    }

    public record Entry(String requirement, String feature) {
    }

    public static final List<Entry> ED41_SLICE = List.of(
            new Entry("Fatura por e-mail com aviso e link autenticado ao portal (POC-20)",
                    "BillEmailService.sendBillEmail + PortalLinkPort"),
            new Entry("Comunicado de débito por e-mail (POC-20)",
                    "BillEmailService.sendDebitNotice"),
            new Entry("PDF anexo só com opt-in do morador ou padrão do tenant com aceite formal (ADR-0028, l)",
                    "BillEmailRule (5 bases nomeadas) + BillPdfOptInReader"),
            new Entry("SMS, WhatsApp e push com opt-in por canal e finalidade (P3.36 + P1.3)",
                    "OptInRule + ConsentGate (consentimento exato) + ChannelContactRegistry"),
            new Entry("Mensagens pelos modelos da P2.30, disparadas por eventos do catálogo da P1.38",
                    "NotificationDispatchRule (P3.5) + MessageTemplateEngine"),
            new Entry("E-mail com remetente autenticado por tenant (P3.5)",
                    "DeliveryProofRule.assertDomainAligned reutilizada no BillEmailService"),
            new Entry("Remetente de SMS e conta de WhatsApp por tenant, com entregabilidade medida",
                    "MultichannelParameters (msg.sms_sender_id/msg.whatsapp_account_id) + DeliverabilityRule"),
            new Entry("Modelo de WhatsApp classificado por categoria de preço, com estimativa e alerta na aprovação",
                    "TemplateCategoryRule + msg.template_category_map + TemplateApprovalRegistry"),
            new Entry("Teto de gasto mensal por tenant com alertas a 80% e 100% (ADR-0019)",
                    "MonthlyCapRule + CapAlertSink (msg.monthly_cap)"),
            new Entry("Uso medido por tenant e por módulo exposto ao console da P1.6",
                    "UsageStore/UsageReportService (MESSAGES, SMS, AI_TOKENS, STORAGE, GIS)"),
            new Entry("Provedores no DPA",
                    "DpaProviderRegistry (lista nominal é valor aberto da P3.9)"));

    public static List<String> requirements() {
        return ED41_SLICE.stream().map(Entry::requirement).toList();
    }
}
