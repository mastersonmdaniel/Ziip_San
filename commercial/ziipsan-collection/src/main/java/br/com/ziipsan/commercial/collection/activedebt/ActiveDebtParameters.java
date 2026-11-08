package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Parametros da divida ativa (ED-31), no padrao de DunningParameters (P4.1)
 * e ArrecadacaoParameters (ED-26): JSON sempre pela classe Json do core.
 *
 * <ul>
 *   <li>{@code da.min_filing_amount} — valor minimo para ajuizar, "pela lei
 *       do ente". SEM padrao de fabrica e SEM valor neste codigo: a lei do
 *       ente nao esta nas fontes desta entrega (MARCADOR DECLARADO);
 *       ausencia e erro explicito, nunca um minimo chutado;</li>
 *   <li>{@code da.protest_enabled} — protesto de CDA (Lei 9.492/1997, art. 1o,
 *       paragrafo unico). Nasce DESLIGADO (decisao PD-10): ausencia vale
 *       false, declarado. Ligar o parametro NAO basta: o portao do protesto
 *       exige tambem o parecer juridico favoravel registrado (PD-10), que
 *       NAO existe nas fontes — ver ProtestControl;</li>
 *   <li>{@code da.prescription_alert_days} — antecedencia (dias) do alerta de
 *       prescricao. Sem padrao na spec: ausencia e erro explicito;</li>
 *   <li>{@code da.max_installments} / {@code da.min_installment} —
 *       parcelamento de divida ativa: sem padrao na spec, ausencia e erro;</li>
 *   <li>{@code da.cda_template_code}, {@code da.petition_template_code},
 *       {@code da.handoff_template_code} — codigos de modelo da P2.30
 *       (JOR-06): sao configuracao do tenant; as MINUTAS nao existem nas
 *       fontes (mesma pendencia das minutas da P3.9), logo ausencia e erro
 *       explicito — nenhum codigo de modelo e inventado aqui.</li>
 * </ul>
 */
public final class ActiveDebtParameters {

    public static final String MIN_FILING_AMOUNT = "da.min_filing_amount";
    public static final String PROTEST_ENABLED = "da.protest_enabled";
    public static final String PRESCRIPTION_ALERT_DAYS = "da.prescription_alert_days";
    public static final String MAX_INSTALLMENTS = "da.max_installments";
    public static final String MIN_INSTALLMENT = "da.min_installment";
    public static final String CDA_TEMPLATE_CODE = "da.cda_template_code";
    public static final String PETITION_TEMPLATE_CODE = "da.petition_template_code";
    public static final String HANDOFF_TEMPLATE_CODE = "da.handoff_template_code";

    private final Map<String, String> jsonByKey;

    private ActiveDebtParameters(Map<String, String> jsonByKey) { this.jsonByKey = jsonByKey; }

    public static ActiveDebtParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        return new ActiveDebtParameters(Map.copyOf(jsonByKey));
    }

    /** Valor minimo para ajuizar (lei do ente): obrigatorio, sem constante neste codigo. */
    public BigDecimal minFilingAmount() { return requiredDecimal(MIN_FILING_AMOUNT); }

    /** Protesto ligado? DESLIGADO por padrao (PD-10): ausencia = false, declarado. */
    public boolean protestEnabled() {
        String json = jsonByKey.get(PROTEST_ENABLED);
        if (json == null || json.isBlank()) return false;
        JsonNode node = Json.readTree(json);
        if (!node.isBoolean()) throw new IllegalStateException(PROTEST_ENABLED + ": esperado booleano, veio " + node.getNodeType());
        return node.booleanValue();
    }

    public int prescriptionAlertDays() { return requiredInt(PRESCRIPTION_ALERT_DAYS); }
    public int maxInstallments() { return requiredInt(MAX_INSTALLMENTS); }
    public BigDecimal minInstallment() { return requiredDecimal(MIN_INSTALLMENT); }
    public String cdaTemplateCode() { return requiredText(CDA_TEMPLATE_CODE); }
    public String petitionTemplateCode() { return requiredText(PETITION_TEMPLATE_CODE); }
    public String handoffTemplateCode() { return requiredText(HANDOFF_TEMPLATE_CODE); }

    private JsonNode required(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) throw new MissingParameterException(key, "nenhum valor entregue a divida ativa");
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) throw new MissingParameterException(key, "valor JSON e nulo");
        return node;
    }
    private BigDecimal requiredDecimal(String key) {
        JsonNode node = required(key);
        if (!node.isNumber()) throw new IllegalStateException(key + ": esperado numero decimal, veio " + node.getNodeType());
        BigDecimal v = node.decimalValue();
        if (v.signum() < 0) throw new IllegalStateException(key + ": valor nao pode ser negativo: " + v);
        return v;
    }
    private int requiredInt(String key) {
        JsonNode node = required(key);
        if (!node.isIntegralNumber()) throw new IllegalStateException(key + ": esperado inteiro, veio " + node.getNodeType());
        long v = node.longValue();
        if (v < 0 || v > Integer.MAX_VALUE) throw new IllegalStateException(key + ": inteiro fora da faixa: " + v);
        return (int) v;
    }
    private String requiredText(String key) {
        JsonNode node = required(key);
        if (!node.isTextual() || node.asText().isBlank()) throw new IllegalStateException(key + ": esperado texto nao vazio");
        return node.asText();
    }
}
