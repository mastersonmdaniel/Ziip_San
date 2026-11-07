package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.util.EnumSet;
import java.util.Set;

/**
 * Leitura tipada dos parametros do tenant da manutencao de conta (P2.20),
 * no mesmo desenho de {@code BillingParameters} (P2.5): o chamador resolve
 * os valores vigentes e aceitos e entrega o JSON de cada chave; esta classe
 * interpreta -- sempre pela classe {@code Json} do core -- e valida.
 *
 * <p>Principio do catalogo: parametro ausente e erro explicito
 * ({@link MissingParameterException}), nunca valor assumido em silencio.
 * A unica excecao e {@code bill.review_effects}, cujo <b>padrao
 * SUSPEND_ALL o proprio aceite declara</b>: chave ausente devolve
 * SUSPEND_ALL, e o corte continua vedado em qualquer cenario (ver
 * {@link ReviewSuspensionRule}).
 *
 * <p>Formato JSON de cada chave (o mesmo de {@code parameter_versions.value}):
 * <pre>
 *   bill.review_effects                     ENUM|LIST  "SUSPEND_ALL" ou ["COLLECTION","AUTO_DEBIT",...]
 *   bill.review_max_days                    INT        30
 *   bill.rectification_limits_against_user  OBJECT     {"maxPerUnitAndYear": 3}
 *   bill.due_change_max_per_year            INT        2
 *   bill.due_change_min_days_before_due     INT        5
 *   service_type.internal_approval          OBJECT     {"BILL_CANCELLATION": "REQUIRED", "BILL_REVIEW": "NONE"}
 * </pre>
 * {@code bill.rectification_limits_against_user}: {@code maxPerUnitAndYear}
 * ausente ou nulo no objeto = sem teto numerico configurado (a regra de
 * direcao -- so contra o usuario -- continua valendo; ver
 * {@link RectificationLimitRule}).
 */
public final class MaintenanceParameters {

    public static final String REVIEW_EFFECTS = "bill.review_effects";
    public static final String REVIEW_MAX_DAYS = "bill.review_max_days";
    public static final String RECTIFICATION_LIMITS_AGAINST_USER = "bill.rectification_limits_against_user";
    public static final String DUE_CHANGE_MAX_PER_YEAR = "bill.due_change_max_per_year";
    public static final String DUE_CHANGE_MIN_DAYS_BEFORE_DUE = "bill.due_change_min_days_before_due";
    public static final String INTERNAL_APPROVAL = "service_type.internal_approval";

    /** Efeitos que uma revisao aberta pode suspender (aceite da P2.20). */
    public enum ReviewEffect {
        COLLECTION,
        LATE_FEES,
        NEGATIVE_LISTING,
        CUT,
        AUTO_DEBIT
    }

    /** Exigencia de aprovacao de um tipo de OS interna (service_type.internal_approval). */
    public enum ApprovalRequirement {
        REQUIRED,
        NONE
    }

    private final java.util.Map<String, String> jsonByKey;

    private MaintenanceParameters(java.util.Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    public static MaintenanceParameters fromValues(java.util.Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new MaintenanceParameters(java.util.Map.copyOf(jsonByKey));
    }

    /**
     * {@code bill.review_effects}: o conjunto de efeitos suspensos por uma
     * revisao aberta. Aceita o literal {@code "SUSPEND_ALL"} (todos) ou uma
     * lista de efeitos. Chave ausente = SUSPEND_ALL (padrao do aceite).
     */
    public Set<ReviewEffect> reviewEffects() {
        String json = jsonByKey.get(REVIEW_EFFECTS);
        if (json == null || json.isBlank()) {
            return EnumSet.allOf(ReviewEffect.class);
        }
        JsonNode node = Json.readTree(json);
        if (node.isTextual()) {
            if ("SUSPEND_ALL".equals(node.asText())) {
                return EnumSet.allOf(ReviewEffect.class);
            }
            return EnumSet.of(parseEffect(node.asText()));
        }
        if (node.isArray()) {
            EnumSet<ReviewEffect> effects = EnumSet.noneOf(ReviewEffect.class);
            for (JsonNode item : node) {
                if (!item.isTextual()) {
                    throw invalid(REVIEW_EFFECTS, "efeito deve ser texto: " + item);
                }
                if ("SUSPEND_ALL".equals(item.asText())) {
                    return EnumSet.allOf(ReviewEffect.class);
                }
                effects.add(parseEffect(item.asText()));
            }
            return Set.copyOf(effects);
        }
        throw invalid(REVIEW_EFFECTS, "esperado \"SUSPEND_ALL\" ou lista de efeitos");
    }

    /** {@code bill.review_max_days}: teto, em dias, de uma revisao aberta (> 0). */
    public int reviewMaxDays() {
        return parsePositiveInt(REVIEW_MAX_DAYS, require(REVIEW_MAX_DAYS));
    }

    /**
     * {@code bill.rectification_limits_against_user}: teto de retificacoes
     * contra o usuario por unidade e ano civil; {@code null} = objeto sem
     * teto numerico (a chave em si e obrigatoria: ausencia e erro explicito).
     */
    public Integer rectificationMaxPerUnitAndYear() {
        JsonNode node = require(RECTIFICATION_LIMITS_AGAINST_USER);
        if (!node.isObject()) {
            throw invalid(RECTIFICATION_LIMITS_AGAINST_USER, "esperado objeto {\"maxPerUnitAndYear\": n}");
        }
        JsonNode max = node.get("maxPerUnitAndYear");
        if (max == null || max.isNull()) {
            return null;
        }
        int value = parseInt(RECTIFICATION_LIMITS_AGAINST_USER, max);
        if (value < 0) {
            throw invalid(RECTIFICATION_LIMITS_AGAINST_USER, "teto nao pode ser negativo: " + value);
        }
        return value;
    }

    /** {@code bill.due_change_max_per_year}: maximo de alteracoes de vencimento por ano (>= 0). */
    public int dueChangeMaxPerYear() {
        return parseNonNegativeInt(DUE_CHANGE_MAX_PER_YEAR, require(DUE_CHANGE_MAX_PER_YEAR));
    }

    /** {@code bill.due_change_min_days_before_due}: antecedencia minima, em dias, para mudar o vencimento (>= 0). */
    public int dueChangeMinDaysBeforeDue() {
        return parseNonNegativeInt(DUE_CHANGE_MIN_DAYS_BEFORE_DUE, require(DUE_CHANGE_MIN_DAYS_BEFORE_DUE));
    }

    /**
     * {@code service_type.internal_approval}: a exigencia de aprovacao do
     * tipo, quando o tenant a declarou; {@code null} quando o mapa nao cobre
     * o tipo (vale o {@code requires_approval} de fabrica do tipo, coluna de
     * {@code service_types} -- ADR-0039, item 8). Chave ausente como um todo
     * tambem devolve {@code null}: este parametro e, por desenho da P1.31,
     * uma <i>sobreposicao</i> opcional sobre o padrao de fabrica do tipo,
     * nao um valor sem o qual a operacao nao exista.
     */
    public ApprovalRequirement internalApproval(BillOrderType type) {
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

    /**
     * Aprovacao efetiva: a sobreposicao do tenant vence; sem ela, o padrao
     * de fabrica do tipo. ATENCAO ao consumidor (correcao G3): o motor da
     * P1.31 nao le esta chave -- ele decide pelo {@code requires_approval}
     * DECLARADO no tipo. Este metodo resolve a exigencia que o servico
     * grava na declaracao do tipo (tipo ausente) e confere contra a fabrica
     * declarada (tipo existente; divergencia e erro explicito no servico).
     */
    public boolean approvalRequired(BillOrderType type, boolean typeFactoryRequiresApproval) {
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
            throw new MissingParameterException(key, "nenhum valor entregue ao servico de manutencao");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON e nulo");
        }
        return node;
    }

    private static ReviewEffect parseEffect(String text) {
        try {
            return ReviewEffect.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw invalid(REVIEW_EFFECTS, "efeito fora do catalogo: " + text);
        }
    }

    private static IllegalArgumentException invalid(String key, String detail) {
        return new IllegalArgumentException("Parametro '" + key + "' invalido: " + detail);
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
