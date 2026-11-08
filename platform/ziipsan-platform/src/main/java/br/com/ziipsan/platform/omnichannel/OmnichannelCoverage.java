package br.com.ziipsan.platform.omnichannel;

import java.util.List;

/**
 * Cobertura dos roteiros/itens externos nomeados no aceite da ED-42
 * (POC-70, POC-75, TR 21.1 da VISAN, item 4 do lote do SAMAE, SAMAE 2.22
 * e SG-655 a SG-661), no padrão honesto da ED-30/ED-41: os ROTEIROS
 * (VISAN/SAMAE) não estão nesta VM — nenhum ponto, código SG individual
 * ou critério de ensaio é inventado aqui. Cada linha liga a EXIGÊNCIA
 * nomeada no aceite à peça desta entrega que a implementa; o ENSAIO com
 * o roteiro real e a conta de teste real é pendência externa categoria B
 * nomeada no relatório (não é presumido como passado: o aceite diz
 * "passam", e passar de verdade só se prova no ensaio).
 */
public final class OmnichannelCoverage {

    private OmnichannelCoverage() {
    }

    public record Entry(String requirement, String feature, String evidenceKind) {
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry("POC-70/POC-75 — WhatsApp oficial atendido na tela do Ziip SAN, conversa no protocolo da matrícula",
                    "OmnichannelInboundService + OmnichannelReplyService (canal WHATSAPP)",
                    "núcleo provado sobre portas; ensaio com conta de teste = pendência B"),
            new Entry("POC-70/POC-75 — e-mail atendido na tela do Ziip SAN, conversa no protocolo da matrícula",
                    "OmnichannelInboundService + OmnichannelReplyService (canal EMAIL, webhook HMAC do provedor)",
                    "núcleo provado sobre portas; ensaio com conta de teste = pendência B"),
            new Entry("POC-70/POC-75 — chat do portal atendido na tela do Ziip SAN, conversa no protocolo da matrícula",
                    "OmnichannelInboundService (canal PORTAL_CHAT, sessão autenticada)",
                    "núcleo provado sobre portas; ensaio no portal = pendência B"),
            new Entry("Messenger pela API oficial da Meta, no mesmo modelo",
                    "OmnichannelChannel.MESSENGER (família META, HMAC-SHA-256) + mesmos serviços",
                    "núcleo provado sobre portas; ensaio com conta de teste Meta = pendência B"),
            new Entry("Instagram pela API oficial da Meta, no mesmo modelo",
                    "OmnichannelChannel.INSTAGRAM (família META, HMAC-SHA-256) + mesmos serviços",
                    "núcleo provado sobre portas; ensaio com conta de teste Meta = pendência B"),
            new Entry("Telegram pela Bot API, no mesmo modelo",
                    "OmnichannelChannel.TELEGRAM (segredo da Bot API) + mesmos serviços",
                    "núcleo provado sobre portas; ensaio com bot de teste = pendência B"),
            new Entry("Mesmo modelo em todos os canais: conversa no protocolo, opt-in por canal, credenciais no cofre, webhook conferido, provedor no DPA",
                    "Conversation (protocolo/matrícula obrigatórios) + OmniOptInRule + CredentialVaultPort + WebhookSecurity + OmniProviderRegistry",
                    "provado por testes do núcleo"),
            new Entry("TR 21.1 da VISAN atendido",
                    "todas as peças acima (atendimento omnichannel sobre o protocolo único)",
                    "ensaio com o roteiro VISAN real = pendência B [SEM FONTE nesta VM]"),
            new Entry("Item 4 do lote do SAMAE atendido / SAMAE 2.22 sem falha",
                    "todas as peças acima (atendimento omnichannel sobre o protocolo único)",
                    "ensaio com o roteiro SAMAE real = pendência B [SEM FONTE nesta VM]"),
            new Entry("Logs com integridade (SG-655 a SG-661)",
                    "IntegrityLog (cadeia SHA-256 append-only verificável) + lançamentos dos dois serviços",
                    "mecanismo provado por testes; atribuição código-a-código SG = pendência de conferência [SEM FONTE]"),
            new Entry("Mensagens medidas por tenant, custo por categoria de template e uso lidos da ED-41 (GES-19)",
                    "UsageStore/UsageReportService da ED-41 (módulo ED-42) + OmnichannelUsageService.costByTemplateCategory",
                    "provado por testes contra as classes reais da ED-41"),
            new Entry("E-mail com a entregabilidade por tenant da P3.5 (PLT-10)",
                    "OmnichannelUsageService consumindo DeliveryProofRule (P3.5) e DeliverabilityRule (ED-41)",
                    "provado por testes contra as classes reais da P3.5/ED-41"));

    public static List<String> requirements() {
        return ENTRIES.stream().map(Entry::requirement).toList();
    }
}
