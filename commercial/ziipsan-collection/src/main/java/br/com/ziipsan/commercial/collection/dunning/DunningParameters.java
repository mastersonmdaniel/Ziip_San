package br.com.ziipsan.commercial.collection.dunning;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Leitura tipada dos parametros do tenant consumidos pela cobranca (P4.1),
 * no padrao de {@code CollectionParameters} (P3.1) e {@code BillingParameters}
 * (P2.5): o chamador resolve os valores vigentes e aceitos (P0.19) e entrega
 * o JSON de cada chave; esta classe interpreta — sempre pela classe
 * {@link Json} do core — e valida.
 *
 * <p>Principios, todos do aceite da P4.1:
 * <ul>
 *   <li>parametro sem padrao de fabrica na spec: ausencia e erro explicito
 *       ({@link MissingParameterException}), nunca valor assumido. E o caso
 *       do minimo existencial e do perfil de parcelamento;</li>
 *   <li>o MINIMO EXISTENCIAL e parametro versionado
 *       ({@code dun.existential_minimum}): o valor vigente inicial
 *       (Decreto 11.567/2023) vive como padrao de fabrica da DEFINICAO do
 *       parametro no catalogo (ver o SQL pendente desta entrega) e em
 *       {@code parameter_versions} — NUNCA como constante neste codigo.
 *       Este codigo so conhece a CHAVE; sem valor entregue, recusa;</li>
 *   <li>dois interruptores nascem DESLIGADOS porque a spec assim o fixa, e
 *       so nesses a ausencia vale "desligado" (declarado, nao presumido):
 *       {@code ai.collection_scoring_enabled} ("desligado por padrao") e
 *       {@code dun.federal_assignment_enabled} (cessao a programa federal
 *       "desligada" ate parecer juridico favoravel — e mesmo ligada, o
 *       parecer e exigido a parte, ver {@code DebtAssignmentGate});</li>
 *   <li>{@code inst.income_based} segue o precedente de
 *       {@code pay.withholding_enabled} (P3.1): sem valor aceito, erro
 *       explicito — presumir desligado esconderia parametro nao publicado
 *       e presumir ligado ativaria acordo por renda sem decisao do ente.</li>
 * </ul>
 *
 * <p>Formato JSON de cada chave (o mesmo de {@code parameter_versions.value}):
 * <pre>
 *   inst.min_down_payment_pct   DECIMAL  20        (% minimo da entrada sobre o total)
 *   inst.min_down_payment       DECIMAL  50.00     (minimo absoluto da entrada, R$)
 *   inst.entry_due_days         INT      7         (vencimento da entrada nao inferior a este prazo em dias)
 *   inst.max_installments       INT      24
 *   inst.min_installment        DECIMAL  30.00     (prestacao minima, R$)
 *   inst.max_discount_pct       DECIMAL  30        (teto de abatimento total, %)
 *   inst.principal_discount_limit_pct DECIMAL 0    (teto do abatimento do principal, %)
 *   dun.existential_minimum     DECIMAL  (valor versionado; fabrica inicial do Decreto 11.567/2023)
 *   dun.repactuation_effects    JSON     ["SUSPEND_CHARGES","SUSPEND_NEGATIVATION","SUSPEND_ACTIONS","SUSPEND_CUTOFF"]
 *   dun.ruler                   JSON     objeto da regua (ver DunningRuler)
 *   dun.federal_assignment_enabled BOOLEAN false  (desligado ate parecer favoravel)
 *   ai.collection_scoring_enabled  BOOLEAN false  (desligado por padrao, GES-24)
 *   inst.income_based           BOOLEAN  (sem padrao: ausencia e erro explicito)
 * </pre>
 */
public final class DunningParameters {

    public static final String MIN_DOWN_PAYMENT_PCT = "inst.min_down_payment_pct";
    public static final String MIN_DOWN_PAYMENT = "inst.min_down_payment";
    public static final String ENTRY_DUE_DAYS = "inst.entry_due_days";
    public static final String MAX_INSTALLMENTS = "inst.max_installments";
    public static final String MIN_INSTALLMENT = "inst.min_installment";
    public static final String MAX_DISCOUNT_PCT = "inst.max_discount_pct";
    public static final String PRINCIPAL_DISCOUNT_LIMIT_PCT = "inst.principal_discount_limit_pct";
    public static final String EXISTENTIAL_MINIMUM = "dun.existential_minimum";
    public static final String REPACTUATION_EFFECTS = "dun.repactuation_effects";
    public static final String RULER = "dun.ruler";
    public static final String FEDERAL_ASSIGNMENT_ENABLED = "dun.federal_assignment_enabled";
    public static final String COLLECTION_SCORING_ENABLED = "ai.collection_scoring_enabled";
    public static final String INCOME_BASED = "inst.income_based";

    private final Map<String, String> jsonByKey;

    private DunningParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    public static DunningParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new DunningParameters(Map.copyOf(jsonByKey));
    }

    /** Minimo existencial vigente (R$): parametro versionado, sem constante neste codigo. */
    public BigDecimal existentialMinimum() {
        return requiredDecimal(EXISTENTIAL_MINIMUM);
    }

    /** Acordo por capacidade de pagamento ligado? Ausencia e erro explicito (precedente P3.1). */
    public boolean incomeBasedEnabled() {
        return requiredBoolean(INCOME_BASED);
    }

    /** Escore de cobranca ligado? Desligado por padrao (GES-24): ausencia vale false, declarado. */
    public boolean collectionScoringEnabled() {
        String json = jsonByKey.get(COLLECTION_SCORING_ENABLED);
        if (json == null || json.isBlank()) {
            return false;
        }
        JsonNode node = Json.readTree(json);
        if (!node.isBoolean()) {
            throw new IllegalStateException(COLLECTION_SCORING_ENABLED + ": esperado booleano, veio "
                    + node.getNodeType());
        }
        return node.booleanValue();
    }

    /** Interruptor da cessao federal: desligado ate ligacao expressa E parecer (DebtAssignmentGate). */
    public boolean federalAssignmentEnabled() {
        String json = jsonByKey.get(FEDERAL_ASSIGNMENT_ENABLED);
        if (json == null || json.isBlank()) {
            return false;
        }
        JsonNode node = Json.readTree(json);
        if (!node.isBoolean()) {
            throw new IllegalStateException(FEDERAL_ASSIGNMENT_ENABLED + ": esperado booleano, veio "
                    + node.getNodeType());
        }
        return node.booleanValue();
    }

    /** JSON cru vigente de uma chave obrigatoria (a interpretacao e da regra dona da chave). */
    public JsonNode requiredNode(String key) {
        return required(key);
    }

    public BigDecimal requiredDecimal(String key) {
        JsonNode node = required(key);
        if (!node.isNumber()) {
            throw new IllegalStateException(key + ": esperado numero decimal, veio " + node.getNodeType());
        }
        BigDecimal value = node.decimalValue();
        if (value.signum() < 0) {
            throw new IllegalStateException(key + ": valor nao pode ser negativo: " + value);
        }
        return value;
    }

    public int requiredInt(String key) {
        JsonNode node = required(key);
        if (!node.isIntegralNumber()) {
            throw new IllegalStateException(key + ": esperado inteiro, veio " + node.getNodeType());
        }
        long value = node.longValue();
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new IllegalStateException(key + ": inteiro fora da faixa: " + value);
        }
        return (int) value;
    }

    public boolean requiredBoolean(String key) {
        JsonNode node = required(key);
        if (!node.isBoolean()) {
            throw new IllegalStateException(key + ": esperado booleano, veio " + node.getNodeType());
        }
        return node.booleanValue();
    }

    private JsonNode required(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue a cobranca");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON e nulo");
        }
        return node;
    }
}
