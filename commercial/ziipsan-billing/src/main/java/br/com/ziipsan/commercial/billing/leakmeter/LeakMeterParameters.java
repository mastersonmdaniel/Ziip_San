package br.com.ziipsan.commercial.billing.leakmeter;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Leitura tipada dos parametros do tenant da revisao por vazamento oculto e
 * da afericao de hidrometro (P3.28), no mesmo desenho de
 * {@code MaintenanceParameters} (P2.20) e {@code BillingParameters} (P2.5):
 * o chamador resolve os valores vigentes e aceitos e entrega o JSON de cada
 * chave; esta classe interpreta -- sempre pela classe {@code Json} do core --
 * e valida.
 *
 * <p>Principio do catalogo: parametro ausente e erro explicito
 * ({@link MissingParameterException}), nunca valor assumido em silencio. As
 * unicas excecoes sao os padroes de fabrica que a fonte DECLARA:
 * <ul>
 *   <li>{@code leak.discount_rule} ausente = {@code MOST_FAVORABLE} --
 *       PARAMETROS-DO-TENANT, linha {@code leak.*}: "sem perfil, o mais
 *       favoravel ao usuario entre os tres (MOST_FAVORABLE, desconto maximo
 *       de 100%)";</li>
 *   <li>{@code meter.test_charge_only_if_normal} ausente = {@code true} --
 *       PARAMETROS-DO-TENANT, linha {@code meter.test_*}: o padrao de
 *       fabrica e "cobranca so se o hidrometro estiver normal";</li>
 *   <li>{@code leak.bills_limit_mode} ausente = {@code ANY_IN_12_MONTHS} --
 *       a contagem simples em 12 meses; o modo sequencial so vale quando o
 *       tenant o declara (e o que a nota do perfil AGIR da P2.31 registra).</li>
 * </ul>
 *
 * <p><b>{@code leak.max_bills_per_12_months} e chave OBRIGATORIA</b>
 * (correcao pos-revisao, achado M1): ausente lanca
 * {@link MissingParameterException}, como a chave
 * {@code bill.rectification_limits_against_user} da P2.20 -- la a CHAVE e
 * obrigatoria ({@code require()}) e so o CAMPO interno
 * {@code maxPerUnitAndYear} pode ser nulo. Aqui o desenho equivalente e:
 * chave entregue com <b>nulo JSON explicito</b> = sem teto declarado
 * (devolve {@code null}) -- e assim que o caso ARIS da P2.31, cuja nota de
 * {@code leak.max_discount_pct_water} nao registra limite de faturas, se
 * declara na ativacao. Ausencia da chave NAO e ausencia de teto: e erro de
 * ativacao, e erro explicito. Perfis ARESC/AGIR exigem a chave com o teto
 * (2) na receita de ativacao.
 *
 * <p><b>Valores de fabrica dos perfis (P2.31, RAS-01):</b> esta classe NAO
 * carrega nenhum numero de perfil. O valor de {@code
 * leak.max_discount_pct_water} (70 nas tres ERIs lidas -- ARIS RN 19/2019
 * art. 89, ARESC Res. 046/2016 art. 97, AGIR RN 001/2013 art. 107) chega
 * aqui pelo mapa de parametros do tenant, onde a ativacao do perfil da
 * P2.31 o gravou em {@code parameter_versions}; prazo, ancora e limite de
 * cada perfil constam das notas do valor nos perfis e sao informados pelo
 * tenant na ativacao (a P2.31 nao os carrega como chaves estruturadas --
 * ver RELATORIO-P3.28). Sem valor aceito e vigente, o acessor lanca
 * {@link MissingParameterException}: valor de parametro sem fonte e erro
 * explicito, nunca chute.
 *
 * <p>Formato JSON de cada chave (o mesmo de {@code parameter_versions.value}):
 * <pre>
 *   leak.discount_rule                  ENUM     "PCT_OF_EXCESS" | "AVERAGE_6M" | "MOST_FAVORABLE"
 *   leak.max_discount_pct_water         PERCENT  70
 *   leak.max_discount_pct_sewer         PERCENT  70
 *   leak.claim_deadline_days            INT      30
 *   leak.claim_deadline_anchor          ENUM     "READING_DATE" | "DUE_DATE"
 *   leak.max_bills_per_12_months        INT      2 (ou null JSON explicito = sem teto declarado; chave obrigatoria)
 *   leak.bills_limit_mode               ENUM     "ANY_IN_12_MONTHS" | "SEQUENTIAL_IN_12_MONTHS"
 *   leak.sewer_by_average               BOOLEAN  true
 *   meter.test_notice_days              INT      5
 *   meter.test_charge_only_if_normal    BOOLEAN  true
 *   meter.test_review_months            INT      6
 *   service_type.internal_approval      OBJECT   {"LEAK_REVIEW": "REQUIRED", "METER_TEST": "NONE"}
 * </pre>
 */
public final class LeakMeterParameters {

    public static final String LEAK_DISCOUNT_RULE = "leak.discount_rule";
    public static final String LEAK_MAX_DISCOUNT_PCT_WATER = "leak.max_discount_pct_water";
    public static final String LEAK_MAX_DISCOUNT_PCT_SEWER = "leak.max_discount_pct_sewer";
    public static final String LEAK_CLAIM_DEADLINE_DAYS = "leak.claim_deadline_days";
    public static final String LEAK_CLAIM_DEADLINE_ANCHOR = "leak.claim_deadline_anchor";
    public static final String LEAK_MAX_BILLS_PER_12_MONTHS = "leak.max_bills_per_12_months";
    public static final String LEAK_BILLS_LIMIT_MODE = "leak.bills_limit_mode";
    public static final String LEAK_SEWER_BY_AVERAGE = "leak.sewer_by_average";
    public static final String METER_TEST_NOTICE_DAYS = "meter.test_notice_days";
    public static final String METER_TEST_CHARGE_ONLY_IF_NORMAL = "meter.test_charge_only_if_normal";
    public static final String METER_TEST_REVIEW_MONTHS = "meter.test_review_months";
    public static final String INTERNAL_APPROVAL = "service_type.internal_approval";

    /** Regra de desconto do vazamento (PARAMETROS-DO-TENANT, linha leak.*; 20b §5.1). */
    public enum DiscountRule {
        /** Fatura-se pela media (agua e esgoto): o volume revisto e a media do periodo. */
        AVERAGE_6M,
        /** Desconto de um percentual sobre o volume ACIMA da media, agua e esgoto. */
        PCT_OF_EXCESS,
        /** A mais favoravel ao usuario entre AVERAGE_6M e PCT_OF_EXCESS (padrao sem perfil). */
        MOST_FAVORABLE
    }

    /**
     * Ancora do prazo de reclamacao do vazamento. Sao as duas atestadas nas
     * notas dos perfis da P2.31: ARIS conta "da leitura"; ARESC e AGIR, "do
     * vencimento". Ancora nova exige opcao nova aqui, com fonte.
     */
    public enum DeadlineAnchor {
        READING_DATE,
        DUE_DATE
    }

    /** Como o limite de faturas em 12 meses conta (notas dos perfis da P2.31). */
    public enum BillsLimitMode {
        /** Quaisquer faturas beneficiadas dentro da janela de 12 meses (ARESC). */
        ANY_IN_12_MONTHS,
        /** So faturas de referencias consecutivas contam como o bloco beneficiado (AGIR). */
        SEQUENTIAL_IN_12_MONTHS
    }

    /** Exigencia de aprovacao de um tipo de OS (service_type.internal_approval). */
    public enum ApprovalRequirement {
        REQUIRED,
        NONE
    }

    private final java.util.Map<String, String> jsonByKey;

    private LeakMeterParameters(java.util.Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    public static LeakMeterParameters fromValues(java.util.Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new LeakMeterParameters(java.util.Map.copyOf(jsonByKey));
    }

    // ------------------------------------------------------------------
    // Vazamento: desconto
    // ------------------------------------------------------------------

    /** {@code leak.discount_rule}: ausente = MOST_FAVORABLE (padrao declarado, ver cabecalho). */
    public DiscountRule discountRule() {
        String json = jsonByKey.get(LEAK_DISCOUNT_RULE);
        if (json == null || json.isBlank()) {
            return DiscountRule.MOST_FAVORABLE;
        }
        return parseEnum(LEAK_DISCOUNT_RULE, DiscountRule.class, require(LEAK_DISCOUNT_RULE));
    }

    /**
     * {@code leak.max_discount_pct_water}: teto do desconto sobre o volume de
     * agua acima da media, em percentual (0 a 100). Obrigatorio: o valor de
     * fabrica (70 nas ERIs lidas de SC) vem do perfil ativado da P2.31, nao
     * desta classe.
     */
    public BigDecimal maxDiscountPctWater() {
        return parsePercent(LEAK_MAX_DISCOUNT_PCT_WATER, require(LEAK_MAX_DISCOUNT_PCT_WATER));
    }

    /**
     * {@code leak.max_discount_pct_sewer}: idem para o esgoto. So e lido
     * quando a regra de desconto alcanca o esgoto por percentual (ver
     * {@link LeakDiscountRule}); com {@code leak.sewer_by_average} ligado ele
     * nao e consultado.
     */
    public BigDecimal maxDiscountPctSewer() {
        return parsePercent(LEAK_MAX_DISCOUNT_PCT_SEWER, require(LEAK_MAX_DISCOUNT_PCT_SEWER));
    }

    /**
     * {@code leak.sewer_by_average}: o esgoto da fatura revisada e cobrado
     * pela media (e nao pelo volume medido nem por percentual do excesso).
     * Obrigatorio: e escolha do tenant (classe b), sem padrao declarado.
     */
    public boolean sewerByAverage() {
        return parseBoolean(LEAK_SEWER_BY_AVERAGE, require(LEAK_SEWER_BY_AVERAGE));
    }

    // ------------------------------------------------------------------
    // Vazamento: prazo e limite
    // ------------------------------------------------------------------

    /** {@code leak.claim_deadline_days}: prazo de reclamacao em dias (> 0), contado da ancora. */
    public int claimDeadlineDays() {
        return parsePositiveInt(LEAK_CLAIM_DEADLINE_DAYS, require(LEAK_CLAIM_DEADLINE_DAYS));
    }

    /** {@code leak.claim_deadline_anchor}: de qual data o prazo corre. Obrigatorio. */
    public DeadlineAnchor claimDeadlineAnchor() {
        return parseEnum(LEAK_CLAIM_DEADLINE_ANCHOR, DeadlineAnchor.class, require(LEAK_CLAIM_DEADLINE_ANCHOR));
    }

    /**
     * {@code leak.max_bills_per_12_months}: teto de faturas beneficiadas em
     * 12 meses. A chave e OBRIGATORIA (ausente =
     * {@link MissingParameterException}, precedente real da P2.20: a chave
     * {@code bill.rectification_limits_against_user} e exigida e so o campo
     * interno pode ser nulo). Devolve {@code null} SOMENTE quando a chave
     * foi entregue com nulo JSON explicito -- a declaracao deliberada de
     * "sem teto" (caso ARIS da P2.31). Ver o cabecalho da classe.
     */
    public Integer maxBillsPer12Months() {
        String json = jsonByKey.get(LEAK_MAX_BILLS_PER_12_MONTHS);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(LEAK_MAX_BILLS_PER_12_MONTHS,
                    "nenhum valor entregue ao servico de vazamento/afericao: a chave e obrigatoria"
                            + " (sem teto se declara com nulo JSON explicito, nunca pela ausencia)");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode()) {
            throw new MissingParameterException(LEAK_MAX_BILLS_PER_12_MONTHS, "valor JSON ausente");
        }
        if (node.isNull()) {
            return null;
        }
        int value = parseInt(LEAK_MAX_BILLS_PER_12_MONTHS, node);
        if (value < 1) {
            throw invalid(LEAK_MAX_BILLS_PER_12_MONTHS, "teto deve ser ao menos 1: " + value);
        }
        return value;
    }

    /** {@code leak.bills_limit_mode}: ausente = ANY_IN_12_MONTHS (padrao declarado, ver cabecalho). */
    public BillsLimitMode billsLimitMode() {
        String json = jsonByKey.get(LEAK_BILLS_LIMIT_MODE);
        if (json == null || json.isBlank()) {
            return BillsLimitMode.ANY_IN_12_MONTHS;
        }
        return parseEnum(LEAK_BILLS_LIMIT_MODE, BillsLimitMode.class, require(LEAK_BILLS_LIMIT_MODE));
    }

    // ------------------------------------------------------------------
    // Afericao de hidrometro (meter.test_*)
    // ------------------------------------------------------------------

    /** {@code meter.test_notice_days}: antecedencia minima, em dias, do aviso da afericao (>= 0). */
    public int meterTestNoticeDays() {
        return parseNonNegativeInt(METER_TEST_NOTICE_DAYS, require(METER_TEST_NOTICE_DAYS));
    }

    /**
     * {@code meter.test_charge_only_if_normal}: a afericao so e cobrada
     * quando o laudo conclui hidrometro NORMAL. Ausente = {@code true}
     * (padrao de fabrica declarado em PARAMETROS-DO-TENANT).
     */
    public boolean meterTestChargeOnlyIfNormal() {
        String json = jsonByKey.get(METER_TEST_CHARGE_ONLY_IF_NORMAL);
        if (json == null || json.isBlank()) {
            return true;
        }
        return parseBoolean(METER_TEST_CHARGE_ONLY_IF_NORMAL, require(METER_TEST_CHARGE_ONLY_IF_NORMAL));
    }

    /** {@code meter.test_review_months}: meses de faturas sujeitos a revisao se o hidrometro estiver irregular (> 0). */
    public int meterTestReviewMonths() {
        return parsePositiveInt(METER_TEST_REVIEW_MONTHS, require(METER_TEST_REVIEW_MONTHS));
    }

    // ------------------------------------------------------------------
    // Aprovacao dos tipos (mesma chave da P1.31/P2.20)
    // ------------------------------------------------------------------

    /**
     * {@code service_type.internal_approval} para um tipo desta tarefa:
     * {@code null} quando o mapa nao cobre o tipo (vale a fabrica do tipo).
     * A chave e a mesma lida por {@code MaintenanceParameters} (P2.20) -- o
     * catalogo e um so; cada frente le as entradas dos seus tipos.
     */
    public ApprovalRequirement internalApproval(LeakMeterOrderType type) {
        String json = jsonByKey.get(INTERNAL_APPROVAL);
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode node = Json.readTree(json);
        if (!node.isObject()) {
            throw invalid(INTERNAL_APPROVAL, "esperado objeto {tipo: \"REQUIRED\"|\"NONE\"}");
        }
        JsonNode value = node.get(type.code());
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalid(INTERNAL_APPROVAL, "exigencia do tipo " + type.code() + " deve ser texto");
        }
        try {
            return ApprovalRequirement.valueOf(value.asText());
        } catch (IllegalArgumentException e) {
            throw invalid(INTERNAL_APPROVAL, "valor fora do catalogo para " + type.code() + ": " + value.asText());
        }
    }

    /** Aprovacao efetiva: a sobreposicao do tenant vence; sem ela, a fabrica do tipo. */
    public boolean approvalRequired(LeakMeterOrderType type, boolean typeFactoryRequiresApproval) {
        ApprovalRequirement override = internalApproval(type);
        if (override != null) {
            return override == ApprovalRequirement.REQUIRED;
        }
        return typeFactoryRequiresApproval;
    }

    // ------------------------------------------------------------------
    // Leitura (JSON so pela classe Json do core)
    // ------------------------------------------------------------------

    private JsonNode require(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue ao servico de vazamento/afericao");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON e nulo");
        }
        return node;
    }

    private static IllegalArgumentException invalid(String key, String detail) {
        return new IllegalArgumentException("Parametro '" + key + "' invalido: " + detail);
    }

    private static <E extends Enum<E>> E parseEnum(String key, Class<E> type, JsonNode node) {
        if (!node.isTextual()) {
            throw invalid(key, "esperado texto com um valor de " + type.getSimpleName());
        }
        try {
            return Enum.valueOf(type, node.asText());
        } catch (IllegalArgumentException e) {
            throw invalid(key, "valor fora do catalogo: " + node.asText());
        }
    }

    private static BigDecimal parsePercent(String key, JsonNode node) {
        if (!node.isNumber() && !node.isTextual()) {
            throw invalid(key, "esperado numero percentual");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(node.asText().trim());
        } catch (NumberFormatException e) {
            throw invalid(key, "percentual nao numerico: " + node.asText());
        }
        if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw invalid(key, "percentual fora de 0..100: " + value);
        }
        return value;
    }

    private static boolean parseBoolean(String key, JsonNode node) {
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual() && ("true".equals(node.asText()) || "false".equals(node.asText()))) {
            return Boolean.parseBoolean(node.asText());
        }
        throw invalid(key, "esperado booleano");
    }

    private static int parseInt(String key, JsonNode node) {
        if (node == null || !node.isIntegralNumber()) {
            throw invalid(key, "esperado inteiro");
        }
        return node.intValue();
    }

    private static int parsePositiveInt(String key, JsonNode node) {
        int value = parseInt(key, node);
        if (value <= 0) {
            throw invalid(key, "deve ser maior que zero: " + value);
        }
        return value;
    }

    private static int parseNonNegativeInt(String key, JsonNode node) {
        int value = parseInt(key, node);
        if (value < 0) {
            throw invalid(key, "nao pode ser negativo: " + value);
        }
        return value;
    }
}
