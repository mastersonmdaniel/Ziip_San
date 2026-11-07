package br.com.ziipsan.commercial.collection.parameters;

import br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parametros do tenant consumidos pelo Pix (P3.2), no mesmo padrao de
 * {@code CollectionParameters} (P3.1): JSON lido SOMENTE pela classe
 * {@link Json} do core e ausencia = {@link MissingParameterException},
 * nunca valor presumido.
 *
 * <p>Formato JSON de cada chave (o mesmo de {@code parameter_versions.value}):
 * <pre>
 *   pay.auto_debit.retry_policy  OBJETO  {"maxRetries":3,"windowDays":7,"foreseenInAuthorization":true}
 *   pay.auto_debit.notice_days   INTEIRO 2   (dias de antecedencia da notificacao ao pagador)
 *   pay.auto_debit.incentive     TEXTO   "NONE" (incentivo declarado; sem padrao presumido)
 *   pay.channels                 ARRAY   ["PIX","OPEN_FINANCE_JSR","PIX_PROXIMITY"]
 *   pay.hybrid_offer_enabled     BOOLEAN false (oferta da cobranca hibrida, facultativa por tenant)
 * </pre>
 *
 * <p>{@code pay.channels} declara CAPACIDADES DO PROVEDOR (o PSP escolhido
 * declara suporte a Open Finance JSR e Pix por aproximacao); nem o Ziip SAN
 * nem o tenant se tornam participantes por essa declaracao.
 *
 * <p>Os tres {@code pay.auto_debit.*} sao classe d: registram as regras do
 * Pix Automatico (ver {@link PixAutoDebitRules}); a execucao e da P4.5/ED-27.
 */
public final class PixParameters {

    public static final String AUTO_DEBIT_RETRY_POLICY = "pay.auto_debit.retry_policy";
    public static final String AUTO_DEBIT_NOTICE_DAYS = "pay.auto_debit.notice_days";
    public static final String AUTO_DEBIT_INCENTIVE = "pay.auto_debit.incentive";
    public static final String CHANNELS = "pay.channels";
    public static final String HYBRID_OFFER_ENABLED = "pay.hybrid_offer_enabled";

    public static final String CHANNEL_OPEN_FINANCE_JSR = "OPEN_FINANCE_JSR";
    public static final String CHANNEL_PIX_PROXIMITY = "PIX_PROXIMITY";

    private final Map<String, String> jsonByKey;

    private PixParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    public static PixParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new PixParameters(Map.copyOf(jsonByKey));
    }

    /** Politica de retentativa do Pix Automatico, validada contra o teto normativo. */
    public PixAutoDebitRules.RetryPolicy autoDebitRetryPolicy() {
        JsonNode node = required(AUTO_DEBIT_RETRY_POLICY);
        if (!node.isObject()) {
            throw new IllegalStateException(AUTO_DEBIT_RETRY_POLICY + ": esperado objeto, veio "
                    + node.getNodeType());
        }
        JsonNode max = node.get("maxRetries");
        JsonNode win = node.get("windowDays");
        JsonNode fore = node.get("foreseenInAuthorization");
        if (max == null || !max.isIntegralNumber() || win == null || !win.isIntegralNumber()
                || fore == null || !fore.isBoolean()) {
            throw new IllegalStateException(AUTO_DEBIT_RETRY_POLICY
                    + ": exige maxRetries e windowDays inteiros e foreseenInAuthorization booleano");
        }
        return new PixAutoDebitRules.RetryPolicy(max.intValue(), win.intValue(), fore.booleanValue());
    }

    /** Dias de antecedencia da notificacao ao pagador (>= 0). */
    public int autoDebitNoticeDays() {
        JsonNode node = required(AUTO_DEBIT_NOTICE_DAYS);
        if (!node.isIntegralNumber()) {
            throw new IllegalStateException(AUTO_DEBIT_NOTICE_DAYS + ": esperado inteiro, veio "
                    + node.getNodeType());
        }
        int days = node.intValue();
        if (days < 0) {
            throw new IllegalStateException(AUTO_DEBIT_NOTICE_DAYS + ": nao pode ser negativo: " + days);
        }
        return days;
    }

    /** Incentivo do Pix Automatico declarado pelo tenant (texto; ausencia e erro). */
    public String autoDebitIncentive() {
        JsonNode node = required(AUTO_DEBIT_INCENTIVE);
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalStateException(AUTO_DEBIT_INCENTIVE + ": esperado texto nao vazio");
        }
        return node.textValue();
    }

    /** Canais/capacidades declarados do provedor (pay.channels). */
    public Set<String> channels() {
        JsonNode node = required(CHANNELS);
        if (!node.isArray()) {
            throw new IllegalStateException(CHANNELS + ": esperado array, veio " + node.getNodeType());
        }
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.textValue().isBlank()) {
                throw new IllegalStateException(CHANNELS + ": item deve ser texto nao vazio");
            }
            out.add(item.textValue());
        }
        return Set.copyOf(out);
    }

    public boolean providerDeclaresOpenFinanceJsr() {
        return channels().contains(CHANNEL_OPEN_FINANCE_JSR);
    }

    public boolean providerDeclaresPixProximity() {
        return channels().contains(CHANNEL_PIX_PROXIMITY);
    }

    /** Oferta da cobranca hibrida ligada para este tenant? Ausencia e erro explicito. */
    public boolean hybridOfferEnabled() {
        JsonNode node = required(HYBRID_OFFER_ENABLED);
        if (!node.isBoolean()) {
            throw new IllegalStateException(HYBRID_OFFER_ENABLED + ": esperado booleano, veio "
                    + node.getNodeType());
        }
        return node.booleanValue();
    }

    private JsonNode required(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue ao Pix");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON e nulo");
        }
        return node;
    }
}
