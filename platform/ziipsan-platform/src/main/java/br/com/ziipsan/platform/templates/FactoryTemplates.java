package br.com.ziipsan.platform.templates;

import java.util.List;
import java.util.Optional;

/**
 * Modelos de fábrica (P2.30): o tenant copia e ajusta — nunca edita a
 * fábrica. Como os perfis de fábrica da P1.37, vivem em código e são só
 * leitura: a cópia cria um modelo do tenant (com {@code factory_key}) em
 * DRAFT, que segue o fluxo normal de prévia, proposta e aprovação.
 *
 * <p>Os 14 modelos são exatamente os do aceite (comprovante de protocolo,
 * resposta ao usuário, aviso de corte, cartas de cobrança, termo de
 * parcelamento, auto de infração, certidões, CDA, OS impressa, laudo,
 * relatório de auditoria, e-mail, SMS e WhatsApp), e os 5 canais estão
 * todos cobertos (a OS impressa é o leiaute térmico da ED-19).
 *
 * <p>Conteúdo: texto genérico de fábrica, SEM conteúdo legal obrigatório —
 * os blocos de conteúdo legal vêm da P2.32 e ficam travados na edição. Por
 * isso nenhuma fábrica tem bloco LOCKED: travar texto de fábrica como se
 * fosse conteúdo legal seria inventar a P2.32. Os corpos usam só variáveis
 * do catálogo do próprio tipo (conferido em tempo de carga e por teste).
 */
public final class FactoryTemplates {

    /** Um modelo de fábrica, pronto para copiar. */
    public record FactoryTemplate(String key, TemplateDocumentType documentType, TemplateChannel channel,
                                  String title, String body,
                                  List<TemplateEngine.DeclaredVariable> variables) {
    }

    private static FactoryTemplate define(String key, TemplateDocumentType type, TemplateChannel channel,
                                          String title, String body) {
        List<TemplateEngine.DeclaredVariable> declared = TemplateEngine.references(body).stream()
                .map(ref -> new TemplateEngine.DeclaredVariable(ref,
                        TemplateVariableCatalog.variable(type, ref)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Modelo de fábrica '" + key
                                                + "' referencia variável fora do catálogo: " + ref))
                                .type()))
                .toList();
        return new FactoryTemplate(key, type, channel, title, body, declared);
    }

    public static final List<FactoryTemplate> ALL = List.of(
            define("protocol-receipt", TemplateDocumentType.PROTOCOL_RECEIPT, TemplateChannel.PDF,
                    "Comprovante de protocolo",
                    "COMPROVANTE DE PROTOCOLO\nProtocolo: {{protocol_number}}\nTitular: {{customer_name}}\n"
                            + "Solicitação: {{request_summary}}\nUnidade de atendimento: {{service_unit}}\n"),
            define("user-response", TemplateDocumentType.USER_RESPONSE, TemplateChannel.EMAIL,
                    "Resposta ao usuário",
                    "Prezado(a) {{customer_name}},\nReferente ao protocolo {{protocol_number}}: {{response_text}}\n"
                            + "Unidade de atendimento: {{service_unit}}\n"),
            define("cut-notice", TemplateDocumentType.CUT_NOTICE, TemplateChannel.PDF,
                    "Aviso de corte",
                    "AVISO DE CORTE\nTitular: {{customer_name}}\nMatrícula: {{registration_number}}\n"
                            + "Protocolo: {{protocol_number}}\nValor em aberto: R$ {{amount}}\nVencimento: {{due_date}}\n"),
            define("collection-letter", TemplateDocumentType.COLLECTION_LETTER, TemplateChannel.PDF,
                    "Carta de cobrança",
                    "CARTA DE COBRANÇA\nTitular: {{customer_name}}\nMatrícula: {{registration_number}}\n"
                            + "Protocolo: {{protocol_number}}\nValor: R$ {{amount}}\nVencimento: {{due_date}}\n"),
            define("installment-term", TemplateDocumentType.INSTALLMENT_TERM, TemplateChannel.PDF,
                    "Termo de parcelamento",
                    "TERMO DE PARCELAMENTO\nTitular: {{customer_name}}\nMatrícula: {{registration_number}}\n"
                            + "Protocolo: {{protocol_number}}\nValor total: R$ {{total_amount}}\nParcelas: {{installment_count}} x R$ {{installment_amount}}\n"
                            + "Primeiro vencimento: {{first_due_date}}\n"),
            define("infraction-notice", TemplateDocumentType.INFRACTION_NOTICE, TemplateChannel.PDF,
                    "Auto de infração",
                    "AUTO DE INFRAÇÃO\nTitular: {{customer_name}}\nMatrícula: {{registration_number}}\n"
                            + "Protocolo: {{protocol_number}}\nDescrição: {{infraction_description}}\nPrazo para defesa: {{appeal_days}} dias\n"),
            define("certificate", TemplateDocumentType.CERTIFICATE, TemplateChannel.PDF,
                    "Certidão",
                    "CERTIDÃO\nTitular: {{customer_name}}\nMatrícula: {{registration_number}}\n"
                            + "{{certificate_text}}\nData de emissão: {{issue_date}}\n"),
            define("cda", TemplateDocumentType.CDA, TemplateChannel.PDF,
                    "Certidão de dívida ativa",
                    "CERTIDÃO DE DÍVIDA ATIVA\nCDA: {{cda_number}}\nTitular: {{customer_name}}\n"
                            + "Matrícula: {{registration_number}}\nValor: R$ {{amount}}\nVencimento: {{due_date}}\n"),
            define("service-order-print", TemplateDocumentType.SERVICE_ORDER_PRINT, TemplateChannel.THERMAL,
                    "OS impressa",
                    "ORDEM DE SERVIÇO {{service_order_number}}\nMatrícula: {{registration_number}}\n"
                            + "Endereço: {{service_address}}\nServiço: {{service_description}}\n"),
            define("lab-report", TemplateDocumentType.LAB_REPORT, TemplateChannel.PDF,
                    "Laudo",
                    "LAUDO\nAmostra: {{sample_id}}\nResultado: {{result_text}}\nData de emissão: {{issue_date}}\n"),
            define("audit-report", TemplateDocumentType.AUDIT_REPORT, TemplateChannel.PDF,
                    "Relatório de auditoria",
                    "RELATÓRIO DE AUDITORIA\nPeríodo: {{audit_period}}\nResumo dos achados: {{finding_summary}}\n"
                            + "Data de emissão: {{issue_date}}\n"),
            define("email", TemplateDocumentType.EMAIL, TemplateChannel.EMAIL,
                    "E-mail",
                    "Prezado(a) {{customer_name}},\nMatrícula: {{registration_number}}\nProtocolo: {{protocol_number}}\n"
                            + "Valor: R$ {{amount}}\nVencimento: {{due_date}}\n"),
            define("sms", TemplateDocumentType.SMS, TemplateChannel.SMS,
                    "SMS",
                    "Aviso: {{customer_name}}, valor R$ {{amount}} com vencimento em {{due_date}}."),
            define("whatsapp", TemplateDocumentType.WHATSAPP, TemplateChannel.WHATSAPP,
                    "WhatsApp",
                    "Olá {{customer_name}}! Valor R$ {{amount}} com vencimento em {{due_date}}.")
    );

    private FactoryTemplates() {
    }

    /** A fábrica pela chave, ou vazio quando a chave não existe. */
    public static Optional<FactoryTemplate> byKey(String key) {
        return ALL.stream().filter(f -> f.key().equals(key)).findFirst();
    }
}
