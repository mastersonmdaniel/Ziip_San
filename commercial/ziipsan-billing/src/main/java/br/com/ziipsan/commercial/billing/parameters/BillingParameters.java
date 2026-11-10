package br.com.ziipsan.commercial.billing.parameters;

import br.com.ziipsan.commercial.billing.bill.LateFeePolicy;
import br.com.ziipsan.commercial.billing.tariff.FixedPartModel;
import br.com.ziipsan.commercial.billing.tariff.OptionalCategoryRule;
import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;
import br.com.ziipsan.commercial.billingrules.math.Money;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import br.com.ziipsan.core.parameters.ParameterLimits;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Leitura tipada dos parâmetros do tenant que o motor de faturamento consome
 * na P2.5 (PARAMETROS-DO-TENANT). O chamador resolve os valores vigentes e
 * aceitos (versões de {@code parameter_versions}, P0.19/P1.6) e entrega o
 * JSON de cada chave; esta classe só interpreta — <b>sempre</b> pela classe
 * {@code Json} do core — e valida.
 *
 * <p>Princípio do catálogo: parâmetro ausente é erro explícito
 * ({@link MissingParameterException}), nunca valor assumido em silêncio.
 * Nenhum acessor tem padrão de fábrica, exceto onde o acceptance declara um
 * (nenhuma chave da P2.5 declara; {@code bill.due_date_options} tem apenas a
 * trava legal de mínimo/ampliação, em {@link ParameterLimits}).
 *
 * <p>Formato JSON de cada chave (o mesmo que vai em {@code parameter_versions.value}):
 * <pre>
 *   bill.due_date_options          LIST_INT   [5, 10, 15, 20, 25, 28]
 *   bill.min_days_delivery_to_due  OBJECT     {"default": 5, "&lt;categoria&gt;": 7}
 *   bill.hold_on_anomaly           BOOLEAN    true
 *   bill.auto_review_value_bands   TABLE      [{"min": "0.00", "max": "50.00", "review": true}]
 *   bill.min_issue_amount          DECIMAL    5.00
 *   bill.below_min_rule            ENUM       "CARRY_OVER_TO_NEXT"
 *   bill.validity_months           INT        3
 *   bill.mail_extra_days           INT        3
 *   bill.delivery_address_fee      DECIMAL    2.50
 *   bill.inactive_economy_rule     ENUM       "CHARGE_FIXED_ONLY"
 *   bill.carry_over_unpaid         BOOLEAN    true
 *   bill.cut_connection_charge     BOOLEAN    true
 *   sewer.no_water_volume_rule     ENUM       "MINIMUM_VOLUME"
 *   credit.type_limits             OBJECT     {"SERVICE": {"maxAmount": "500.00", "maxInstallments": 12}}
 *   fee.fine_mode                  ENUM       "FLAT" | "PER_DAY"
 *   fee.charge_moment              ENUM       "AT_PAYMENT" | "ON_NEXT_BILL"
 *   fee.exemptions                 LIST_TEXT  ["&lt;categoria isenta&gt;"]
 *   tariff.fixed_part_model        ENUM       "MINIMUM_CHARGE_PER_ECONOMY" | "NONE"
 *   tariff.optional_categories     TABLE      [{"category": "ASSISTANCE_ENTITY", "criterion": "...",
 *                                               "proof": "...", "validityMonths": 12}]
 *   benefit.expiry_notice_days     INT        30
 * </pre>
 */
public final class BillingParameters {

    public static final String DUE_DATE_OPTIONS = "bill.due_date_options";
    public static final String MIN_DAYS_DELIVERY_TO_DUE = "bill.min_days_delivery_to_due";
    public static final String HOLD_ON_ANOMALY = "bill.hold_on_anomaly";
    public static final String AUTO_REVIEW_VALUE_BANDS = "bill.auto_review_value_bands";
    public static final String MIN_ISSUE_AMOUNT = "bill.min_issue_amount";
    public static final String BELOW_MIN_RULE = "bill.below_min_rule";
    public static final String VALIDITY_MONTHS = "bill.validity_months";
    public static final String MAIL_EXTRA_DAYS = "bill.mail_extra_days";
    public static final String DELIVERY_ADDRESS_FEE = "bill.delivery_address_fee";
    public static final String INACTIVE_ECONOMY_RULE = "bill.inactive_economy_rule";
    public static final String CARRY_OVER_UNPAID = "bill.carry_over_unpaid";
    public static final String CUT_CONNECTION_CHARGE = "bill.cut_connection_charge";
    public static final String NO_WATER_VOLUME_RULE = "sewer.no_water_volume_rule";
    public static final String CREDIT_TYPE_LIMITS = "credit.type_limits";
    public static final String FINE_MODE = "fee.fine_mode";
    public static final String CHARGE_MOMENT = "fee.charge_moment";
    public static final String FEE_EXEMPTIONS = "fee.exemptions";
    public static final String FIXED_PART_MODEL = "tariff.fixed_part_model";
    public static final String OPTIONAL_CATEGORIES = "tariff.optional_categories";
    public static final String EXPIRY_NOTICE_DAYS = "benefit.expiry_notice_days";

    /** O que fazer com conta abaixo de {@code bill.min_issue_amount}. */
    public enum BelowMinRule {
        ISSUE_ANYWAY,
        CARRY_OVER_TO_NEXT,
        HOLD_FOR_REVIEW
    }

    /** Regra da economia inativa/vaga (extensão pedida pela P1.41, CAD-68). */
    public enum InactiveEconomyRule {
        CHARGE_FIXED_ONLY,
        ESTIMATE_BY_AVERAGE,
        NO_BILL
    }

    /** Volume de esgoto quando não há volume de água medido. */
    public enum NoWaterVolumeRule {
        ALTERNATIVE_SOURCE_VOLUME,
        MINIMUM_VOLUME,
        NO_SEWER_CHARGE
    }

    /** Momento em que multa/juros entram na cobrança. */
    public enum ChargeMoment {
        AT_PAYMENT,
        ON_NEXT_BILL
    }

    /** Faixa de valor para revisão automática da conta. */
    public record ValueBand(BigDecimal min, BigDecimal max, boolean review) {
        public ValueBand {
            if (min == null || min.signum() < 0) {
                throw new IllegalArgumentException("bill.auto_review_value_bands: mínimo da faixa inválido: " + min);
            }
            if (max != null && max.compareTo(min) <= 0) {
                throw new IllegalArgumentException(
                        "bill.auto_review_value_bands: máximo deve ser maior que o mínimo: " + min + ".." + max);
            }
        }

        /** A faixa cobre {@code [min, max)}; {@code max == null} = faixa aberta. */
        public boolean contains(BigDecimal value) {
            return value.compareTo(min) >= 0 && (max == null || value.compareTo(max) < 0);
        }
    }

    /** Teto de um tipo de crédito a realizar (leitura da F5; o domínio de créditos é da F3). */
    public record CreditTypeLimit(BigDecimal maxAmount, int maxInstallments) {
        public CreditTypeLimit {
            if (maxAmount == null || maxAmount.signum() < 0) {
                throw new IllegalArgumentException("credit.type_limits: maxAmount inválido: " + maxAmount);
            }
            if (maxInstallments < 1) {
                throw new IllegalArgumentException(
                        "credit.type_limits: maxInstallments deve ser ao menos 1: " + maxInstallments);
            }
        }
    }

    private final Map<String, String> jsonByKey;

    private BillingParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    /** @param jsonByKey chave do parâmetro → JSON vigente e aceito (texto da coluna {@code value}) */
    public static BillingParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parâmetros é obrigatório");
        }
        return new BillingParameters(Map.copyOf(jsonByKey));
    }

    // ------------------------------------------------------------------------------------------
    // Vencimento (RAS-24)
    // ------------------------------------------------------------------------------------------

    /** {@code bill.due_date_options}, validado pela trava do produto (mínimo 6 p/ concessionária; só amplia). */
    public List<Integer> dueDateOptions(boolean concessionaire, Integer previousAcceptedCount) {
        JsonNode node = require(DUE_DATE_OPTIONS);
        if (!node.isArray()) {
            throw invalid(DUE_DATE_OPTIONS, "esperado array de dias");
        }
        List<Integer> days = new ArrayList<>();
        for (JsonNode day : node) {
            days.add(parseInt(DUE_DATE_OPTIONS, day));
        }
        ParameterLimits.validateDueDateOptions(days, concessionaire, previousAcceptedCount);
        return List.copyOf(days);
    }

    /**
     * {@code bill.min_days_delivery_to_due} <b>por categoria</b>: o objeto traz um
     * dia por chave de categoria e a chave {@code "default"} para as demais.
     * Categoria sem valor e sem {@code default} é erro explícito.
     */
    public int minDaysDeliveryToDue(String categoryKey) {
        Map<String, Object> map = requireMap(MIN_DAYS_DELIVERY_TO_DUE);
        Object value = categoryKey == null ? null : map.get(categoryKey);
        if (value == null) {
            value = map.get("default");
        }
        if (value == null) {
            throw new MissingParameterException(MIN_DAYS_DELIVERY_TO_DUE,
                    "sem dias para a categoria " + categoryKey + " e sem \"default\"");
        }
        int days = parseInt(MIN_DAYS_DELIVERY_TO_DUE, value);
        if (days < 0) {
            throw invalid(MIN_DAYS_DELIVERY_TO_DUE, "dias não podem ser negativos: " + days);
        }
        return days;
    }

    // ------------------------------------------------------------------------------------------
    // As 14 chaves da P2.5
    // ------------------------------------------------------------------------------------------

    /** {@code bill.hold_on_anomaly}: reter (não emitir) conta com anomalia de leitura/consumo. */
    public boolean holdOnAnomaly() {
        return parseBoolean(HOLD_ON_ANOMALY, require(HOLD_ON_ANOMALY));
    }

    /** {@code bill.auto_review_value_bands}: faixas de valor que mandam a conta para revisão automática. */
    public List<ValueBand> autoReviewValueBands() {
        JsonNode node = require(AUTO_REVIEW_VALUE_BANDS);
        if (!node.isArray()) {
            throw invalid(AUTO_REVIEW_VALUE_BANDS, "esperado array de faixas");
        }
        List<ValueBand> bands = new ArrayList<>();
        for (JsonNode band : node) {
            JsonNode max = band.get("max");
            bands.add(new ValueBand(
                    parseMoney(AUTO_REVIEW_VALUE_BANDS, band.get("min")),
                    max == null || max.isNull() ? null : parseMoney(AUTO_REVIEW_VALUE_BANDS, max),
                    parseBoolean(AUTO_REVIEW_VALUE_BANDS, band.get("review"))));
        }
        return List.copyOf(bands);
    }

    /** {@code bill.min_issue_amount}: valor mínimo para emitir a conta. */
    public BigDecimal minIssueAmount() {
        return parseMoney(MIN_ISSUE_AMOUNT, require(MIN_ISSUE_AMOUNT));
    }

    /** {@code bill.below_min_rule}: destino da conta abaixo do mínimo de emissão. */
    public BelowMinRule belowMinRule() {
        return parseEnum(BELOW_MIN_RULE, BelowMinRule.class, require(BELOW_MIN_RULE));
    }

    /** {@code bill.validity_months}: meses de validade da conta após o vencimento (≥ 0). */
    public int validityMonths() {
        return parseNonNegativeInt(VALIDITY_MONTHS, require(VALIDITY_MONTHS));
    }

    /** {@code bill.mail_extra_days}: dias extras de vencimento para entrega por correio (≥ 0). */
    public int mailExtraDays() {
        return parseNonNegativeInt(MAIL_EXTRA_DAYS, require(MAIL_EXTRA_DAYS));
    }

    /** {@code bill.delivery_address_fee}: taxa cobrada pela entrega em endereço alternativo. */
    public BigDecimal deliveryAddressFee() {
        return parseMoney(DELIVERY_ADDRESS_FEE, require(DELIVERY_ADDRESS_FEE));
    }

    /** {@code bill.inactive_economy_rule}: como faturar economia inativa/unidade vaga. */
    public InactiveEconomyRule inactiveEconomyRule() {
        return parseEnum(INACTIVE_ECONOMY_RULE, InactiveEconomyRule.class, require(INACTIVE_ECONOMY_RULE));
    }

    /** {@code bill.carry_over_unpaid}: saldo não pago da conta anterior vai para a próxima. */
    public boolean carryOverUnpaid() {
        return parseBoolean(CARRY_OVER_UNPAID, require(CARRY_OVER_UNPAID));
    }

    /** {@code sewer.no_water_volume_rule}: volume de esgoto sem volume de água medido. */
    public NoWaterVolumeRule noWaterVolumeRule() {
        return parseEnum(NO_WATER_VOLUME_RULE, NoWaterVolumeRule.class, require(NO_WATER_VOLUME_RULE));
    }

    /** {@code credit.type_limits}: teto de valor e de parcelas por tipo de crédito (só leitura; domínio é da F3). */
    public Map<String, CreditTypeLimit> creditTypeLimits() {
        Map<String, Object> map = requireMap(CREDIT_TYPE_LIMITS);
        Map<String, CreditTypeLimit> limits = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> rawLimit)) {
                throw invalid(CREDIT_TYPE_LIMITS, "limite do tipo " + entry.getKey() + " deve ser um objeto");
            }
            Map<String, Object> limit = new LinkedHashMap<>();
            for (Map.Entry<?, ?> field : rawLimit.entrySet()) {
                limit.put(String.valueOf(field.getKey()), field.getValue());
            }
            Object maxAmount = limit.get("maxAmount");
            Object maxInstallments = limit.get("maxInstallments");
            if (maxAmount == null || maxInstallments == null) {
                throw invalid(CREDIT_TYPE_LIMITS,
                        "limite do tipo " + entry.getKey() + " exige maxAmount e maxInstallments");
            }
            limits.put(entry.getKey(), new CreditTypeLimit(
                    parseMoney(CREDIT_TYPE_LIMITS, maxAmount), parseInt(CREDIT_TYPE_LIMITS, maxInstallments)));
        }
        return Map.copyOf(limits);
    }

    /** {@code fee.fine_mode}: modo da multa, no vocabulário que o {@link LateFeePolicy} já usa. */
    public LateFeePolicy.FineMode fineMode() {
        return parseEnum(FINE_MODE, LateFeePolicy.FineMode.class, require(FINE_MODE));
    }

    /** {@code fee.charge_moment}: quando multa/juros são cobrados. */
    public ChargeMoment chargeMoment() {
        return parseEnum(CHARGE_MOMENT, ChargeMoment.class, require(CHARGE_MOMENT));
    }

    /** {@code fee.exemptions}: chaves de categoria/situação isentas de multa e juros. */
    public Set<String> feeExemptions() {
        JsonNode node = require(FEE_EXEMPTIONS);
        if (!node.isArray()) {
            throw invalid(FEE_EXEMPTIONS, "esperado array de textos");
        }
        Set<String> exemptions = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw invalid(FEE_EXEMPTIONS, "isenção deve ser texto não vazio");
            }
            exemptions.add(item.asText());
        }
        return Set.copyOf(exemptions);
    }

    // ------------------------------------------------------------------------------------------
    // TFDI, categorias opcionais e benefício (RAS-22, RAS-13, CAD-88)
    // ------------------------------------------------------------------------------------------

    /** {@code tariff.fixed_part_model}: modelo do componente fixo (TFDI). */
    public FixedPartModel fixedPartModel() {
        return parseEnum(FIXED_PART_MODEL, FixedPartModel.class, require(FIXED_PART_MODEL));
    }

    /** {@code bill.cut_connection_charge}: cobrar a TFDI de unidade cortada/não conectada. */
    public boolean cutConnectionCharge() {
        return parseBoolean(CUT_CONNECTION_CHARGE, require(CUT_CONNECTION_CHARGE));
    }

    /** {@code tariff.optional_categories} (classe d): categorias NR 13 art. 6º com critério, prova e validade. */
    public List<OptionalCategoryRule> optionalCategories() {
        JsonNode node = require(OPTIONAL_CATEGORIES);
        if (!node.isArray()) {
            throw invalid(OPTIONAL_CATEGORIES, "esperado array de categorias");
        }
        List<OptionalCategoryRule> rules = new ArrayList<>();
        for (JsonNode item : node) {
            try {
                rules.add(new OptionalCategoryRule(
                        parseEnum(OPTIONAL_CATEGORIES, OptionalCategoryRule.OptionalCategory.class,
                                item.get("category")),
                        parseText(OPTIONAL_CATEGORIES, item.get("criterion")),
                        parseText(OPTIONAL_CATEGORIES, item.get("proof")),
                        parseInt(OPTIONAL_CATEGORIES, item.get("validityMonths"))));
            } catch (TariffConfigurationException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new TariffConfigurationException(OPTIONAL_CATEGORIES + ": " + e.getMessage());
            }
        }
        return List.copyOf(rules);
    }

    /** {@code benefit.expiry_notice_days} (classe d): antecedência, em dias, do aviso de vencimento do benefício. */
    public int expiryNoticeDays() {
        return parseNonNegativeInt(EXPIRY_NOTICE_DAYS, require(EXPIRY_NOTICE_DAYS));
    }

    // ------------------------------------------------------------------------------------------
    // Leitura e conversão (JSON só pela classe Json do core)
    // ------------------------------------------------------------------------------------------

    private JsonNode require(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue ao motor");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON é nulo");
        }
        return node;
    }

    private Map<String, Object> requireMap(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue ao motor");
        }
        return Json.readMap(json);
    }

    private static IllegalArgumentException invalid(String key, String detail) {
        return new IllegalArgumentException("Parâmetro '" + key + "' inválido: " + detail);
    }

    private static boolean parseBoolean(String key, JsonNode node) {
        if (node == null || !node.isBoolean()) {
            throw invalid(key, "esperado booleano");
        }
        return node.booleanValue();
    }

    private static String parseText(String key, JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalid(key, "esperado texto");
        }
        return node.asText();
    }

    private static <E extends Enum<E>> E parseEnum(String key, Class<E> type, JsonNode node) {
        String text = parseText(key, node);
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException e) {
            throw invalid(key, "valor fora do catálogo: " + text);
        }
    }

    private static int parseInt(String key, JsonNode node) {
        if (node == null) {
            throw invalid(key, "número ausente");
        }
        return parseInt(key, (Object) node.asText());
    }

    private static int parseInt(String key, Object value) {
        try {
            return new BigDecimal(String.valueOf(value)).intValueExact();
        } catch (RuntimeException e) {
            throw invalid(key, "esperado inteiro: " + value);
        }
    }

    private static int parseNonNegativeInt(String key, JsonNode node) {
        int value = parseInt(key, node);
        if (value < 0) {
            throw invalid(key, "não pode ser negativo: " + value);
        }
        return value;
    }

    private static BigDecimal parseMoney(String key, JsonNode node) {
        if (node == null) {
            throw invalid(key, "valor monetário ausente");
        }
        return parseMoney(key, (Object) node.asText());
    }

    private static BigDecimal parseMoney(String key, Object value) {
        try {
            BigDecimal amount = new BigDecimal(String.valueOf(value))
                    .setScale(Money.SCALE, RoundingMode.UNNECESSARY);
            if (amount.signum() < 0) {
                throw invalid(key, "valor não pode ser negativo: " + amount);
            }
            return amount;
        } catch (ArithmeticException | NumberFormatException e) {
            throw invalid(key, "esperado valor monetário com até 2 casas: " + value);
        }
    }
}
