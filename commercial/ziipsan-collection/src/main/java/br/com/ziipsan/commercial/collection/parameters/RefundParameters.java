package br.com.ziipsan.commercial.collection.parameters;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Parametros do tenant consumidos pela devolucao (P3.29), no mesmo padrao de
 * {@code CollectionParameters} (P3.1) e {@code PixParameters} (P3.2): JSON lido
 * SOMENTE pela classe {@link Json} do core; ausencia = {@link MissingParameterException},
 * nunca valor presumido.
 *
 * <p>Formato JSON de cada chave (o mesmo de {@code parameter_versions.value};
 * opcoes e padroes de fabrica do catalogo PARAMETROS-DO-TENANT):
 * <pre>
 *   pay.refund_mode                  TEXTO  "USER_CHOICE" | "CREDIT_NEXT_BILL" | "BANK_TRANSFER"
 *                                    (classe b; padrao de fabrica USER_CHOICE - a leitura
 *                                    NAO aplica o padrao: quem resolve entrega o valor vigente)
 *   pay.refund_destination           TEXTO  "PIX_RETURN_TO_PAYER" | "PIX_KEY" | "BANK_ACCOUNT"
 *                                    (classe b) - COM RESSALVA (correcao pos-revisao M4):
 *                                    esta chave NAO consta do catalogo
 *                                    PARAMETROS-DO-TENANT.md; a chave e nomeada pelo
 *                                    acceptance da P3.29 e os literais PIX_KEY/BANK_ACCOUNT
 *                                    vem da prosa dele (so PIX_RETURN_TO_PAYER e nomeado
 *                                    como constante). NENHUMA linha de catalogo foi
 *                                    inventada aqui: a catalogacao da chave e pendencia
 *                                    registrada para a integracao/tarefa do catalogo
 *                                    (ver RELATORIO-P3.29.md, pendencia P-5).
 *   bill.undue_charge_double_refund  OBJETO {"enabled":true}
 *                                    ou {"enabled":false,"normCitation":"..."}
 *                                    (classe b; desligar EXIGE a norma citada)
 * </pre>
 *
 * <p>A ALCADA da aprovacao dupla NAO e lida aqui: o catalogo nao tem chave
 * propria de alcada de devolucao, e inventar chave ou valor violaria a regra
 * do projeto. A alcada vigente do tenant chega a
 * {@code RefundApprovalRule} como entrada obrigatoria da fiacao (politica de
 * aprovacao do tenant, caixa da P2.28); ver o relatorio da P3.29.
 */
public final class RefundParameters {

    public static final String REFUND_MODE = "pay.refund_mode";
    public static final String REFUND_DESTINATION = "pay.refund_destination";
    public static final String UNDUE_CHARGE_DOUBLE_REFUND = "bill.undue_charge_double_refund";

    public enum RefundMode { USER_CHOICE, CREDIT_NEXT_BILL, BANK_TRANSFER }

    public enum RefundDestination { PIX_RETURN_TO_PAYER, PIX_KEY, BANK_ACCOUNT }

    /** @param normCitation norma citada que sustenta o desligamento; nula quando ligado */
    public record DoubleRefundSetting(boolean enabled, String normCitation) {
    }

    private final Map<String, String> jsonByKey;

    private RefundParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    public static RefundParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new RefundParameters(Map.copyOf(jsonByKey));
    }

    /** Modo de encerramento da devolucao ({@code pay.refund_mode}). */
    public RefundMode refundMode() {
        JsonNode node = required(REFUND_MODE);
        if (!node.isTextual()) {
            throw new IllegalStateException(REFUND_MODE + ": esperado texto, veio " + node.getNodeType());
        }
        try {
            return RefundMode.valueOf(node.textValue());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(REFUND_MODE + ": opcao desconhecida: " + node.textValue(), e);
        }
    }

    /** Destino da devolucao bancaria ({@code pay.refund_destination}, classe b). */
    public RefundDestination refundDestination() {
        JsonNode node = required(REFUND_DESTINATION);
        if (!node.isTextual()) {
            throw new IllegalStateException(REFUND_DESTINATION + ": esperado texto, veio "
                    + node.getNodeType());
        }
        try {
            return RefundDestination.valueOf(node.textValue());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(REFUND_DESTINATION + ": opcao desconhecida: "
                    + node.textValue(), e);
        }
    }

    /**
     * Dobro da cobranca indevida ({@code bill.undue_charge_double_refund}).
     * Desligado SEM norma citada e erro explicito: o catalogo so admite
     * desligar "com norma citada" (CDC, art. 42, paragrafo unico).
     */
    public DoubleRefundSetting undueChargeDoubleRefund() {
        JsonNode node = required(UNDUE_CHARGE_DOUBLE_REFUND);
        if (!node.isObject()) {
            throw new IllegalStateException(UNDUE_CHARGE_DOUBLE_REFUND + ": esperado objeto, veio "
                    + node.getNodeType());
        }
        JsonNode enabled = node.get("enabled");
        if (enabled == null || !enabled.isBoolean()) {
            throw new IllegalStateException(UNDUE_CHARGE_DOUBLE_REFUND
                    + ": exige 'enabled' booleano");
        }
        JsonNode citation = node.get("normCitation");
        String citationText = citation != null && citation.isTextual() && !citation.textValue().isBlank()
                ? citation.textValue() : null;
        if (!enabled.booleanValue() && citationText == null) {
            throw new IllegalStateException(UNDUE_CHARGE_DOUBLE_REFUND
                    + ": desligar o dobro exige a norma citada (normCitation)");
        }
        return new DoubleRefundSetting(enabled.booleanValue(), citationText);
    }

    private JsonNode required(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue a devolucao");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON e nulo");
        }
        return node;
    }
}
