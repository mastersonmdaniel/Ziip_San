package br.com.ziipsan.core.cutoff;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tenant parameters of cutoff and reconnection (P3.4; G9 and the option
 * tables of PARAMETROS-DO-TENANT). Values arrive as the canonical JSON of
 * the accepted {@code parameter_versions} (P0.19 envelope; the agency
 * values come from the P2.31 profiles) — this class only parses and
 * validates them, following the {@code ServiceOrderParameters} pattern.
 *
 * <p>Where a parameter has no factory default in the sources, absence is
 * an explicit state of {@link Values}, never a silently assumed value:
 * e.g. {@code cut.social_tariff_allowed} without an accepted value means
 * the social-tariff class stays protected, and
 * {@code cut.post_cut_notice_hours} without a value makes the post-cut
 * notice deadline uncomputable (the notice rule throws, it does not
 * guess an hour count).
 */
public final class CutoffParameters {

    public static final String MIN_NOTICE_DAYS = "cut.min_notice_days";
    public static final String NOTICE_VALIDITY_DAYS = "cut.notice_validity_days";
    public static final String MIN_OVERDUE_DAYS = "cut.min_overdue_days";
    public static final String MAX_DEBT_AGE_DAYS = "cut.max_debt_age_days";
    public static final String MIN_DEBT_AMOUNT = "cut.min_debt_amount";
    public static final String POST_CUT_NOTICE_HOURS = "cut.post_cut_notice_hours";
    public static final String PROTECTED_CLASSES = "cut.protected_classes";
    public static final String SOCIAL_TARIFF_ALLOWED = "cut.social_tariff_allowed";
    public static final String SEWER_ALLOWED = "cut.sewer_allowed";
    public static final String REASONS = "cut.reasons";
    public static final String FORBIDDEN_WINDOW = "cut.forbidden_window";
    public static final String SLA_HOURS = "rec.sla_hours";
    public static final String URGENT_FEE = "rec.urgent_fee";
    public static final String RELEASE_CONDITION = "rec.release_condition";
    public static final String AUTO_ORDER_ON_PAYMENT = "rec.auto_order_on_payment";
    public static final String UNDUE_CUT_COMPENSATION = "rec.undue_cut_compensation";

    /** Legal floor of the notice period: 30 days (Lei 11.445/2007, art. 40, §2º; class c, only widens). */
    public static final int MIN_NOTICE_DAYS_FLOOR = 30;

    /** Factory default of {@code rec.release_condition} (class b). */
    public static final CutoffEnums.ReleaseCondition RELEASE_CONDITION_DEFAULT =
            CutoffEnums.ReleaseCondition.MOTIVATING_DEBT_PAID_OR_AGREED;
    /** Factory default of {@code rec.auto_order_on_payment} (class b). */
    public static final boolean AUTO_ORDER_ON_PAYMENT_DEFAULT = true;
    /** Factory default of {@code cut.sewer_allowed} (class d). */
    public static final boolean SEWER_ALLOWED_DEFAULT = false;
    /** Locked minimum list of {@code cut.protected_classes} (class c, only adds). */
    public static final Set<CutoffEnums.ProtectedClass> PROTECTED_CLASSES_MINIMUM = EnumSet.of(
            CutoffEnums.ProtectedClass.ESSENTIAL_USER,
            CutoffEnums.ProtectedClass.HEALTH_DEPENDENT,
            CutoffEnums.ProtectedClass.SOCIAL_TARIFF);

    private CutoffParameters() {
    }

    /** Reconnection SLA of {@code rec.sla_hours}, as carried by the P2.31 profiles. */
    public record SlaHours(Integer normalHours, Integer urgentHours, Integer undueHours) {

        /** The hour count of a reconnection kind; a missing kind is an explicit error. */
        public int hoursFor(boolean urgent, boolean undue) {
            Integer value = undue ? undueHours : urgent ? urgentHours : normalHours;
            if (value == null) {
                throw new IllegalStateException("rec.sla_hours sem a hora do tipo de religacao pedido"
                        + " (urgente=" + urgent + ", indevida=" + undue + "): a ERI/tenant precisa declarar.");
            }
            return value;
        }
    }

    /**
     * One row of {@code cut.reasons}: whether the reason follows the
     * temporal bans, and the norm cited when it does not. The factory
     * default (class b) is that EVERY reason follows the bans, except
     * technical emergency with justification and photo; the DEFAULT
     * (debt) reason always follows them in full (class a).
     */
    public record ReasonRule(boolean followsTemporalBans, String normCitation) {
    }

    /** Parsed, validated parameter set of a tenant. Null components are declared absences. */
    public record Values(
            Integer minNoticeDays,
            Integer noticeValidityDays,
            Integer minOverdueDays,
            Integer maxDebtAgeDays,
            BigDecimal minDebtAmount,
            Integer postCutNoticeHours,
            Set<CutoffEnums.ProtectedClass> protectedClasses,
            Boolean socialTariffAllowed,
            boolean sewerAllowed,
            Map<CutoffEnums.Reason, ReasonRule> reasonRules,
            SlaHours slaHours,
            BigDecimal urgentFee,
            CutoffEnums.ReleaseCondition releaseCondition,
            boolean autoOrderOnPayment,
            CutoffEnums.UndueCompensationFormula undueCompensationFormula) {

        /** Effective notice days: the accepted value, never below the legal floor of 30. */
        public int effectiveMinNoticeDays() {
            if (minNoticeDays == null) {
                return MIN_NOTICE_DAYS_FLOOR;
            }
            return minNoticeDays;
        }

        /** Whether the social-tariff class may be cut: only an accepted {@code true} allows it. */
        public boolean socialTariffCutAllowed() {
            return Boolean.TRUE.equals(socialTariffAllowed);
        }

        /**
         * Whether a reason follows the temporal bans. Factory default:
         * all follow, except technical emergency (which is gated by
         * justification + photo instead). DEFAULT always follows.
         */
        public boolean reasonFollowsTemporalBans(CutoffEnums.Reason reason) {
            if (reason == CutoffEnums.Reason.DEFAULT) {
                return true;
            }
            ReasonRule rule = reasonRules == null ? null : reasonRules.get(reason);
            if (rule != null) {
                return rule.followsTemporalBans();
            }
            return reason != CutoffEnums.Reason.TECHNICAL_EMERGENCY;
        }
    }

    /** Builds {@link Values} from the accepted raw JSON values by key (missing key = absent). */
    public static Values parse(Map<String, String> rawByKey) {
        Map<String, String> raw = rawByKey == null ? Map.of() : rawByKey;
        return new Values(
                parseInt(raw.get(MIN_NOTICE_DAYS), MIN_NOTICE_DAYS),
                parseInt(raw.get(NOTICE_VALIDITY_DAYS), NOTICE_VALIDITY_DAYS),
                parseInt(raw.get(MIN_OVERDUE_DAYS), MIN_OVERDUE_DAYS),
                parseInt(raw.get(MAX_DEBT_AGE_DAYS), MAX_DEBT_AGE_DAYS),
                parseDecimal(raw.get(MIN_DEBT_AMOUNT), MIN_DEBT_AMOUNT),
                parseInt(raw.get(POST_CUT_NOTICE_HOURS), POST_CUT_NOTICE_HOURS),
                parseProtectedClasses(raw.get(PROTECTED_CLASSES)),
                parseBoolean(raw.get(SOCIAL_TARIFF_ALLOWED), SOCIAL_TARIFF_ALLOWED),
                parseBooleanOrDefault(raw.get(SEWER_ALLOWED), SEWER_ALLOWED, SEWER_ALLOWED_DEFAULT),
                parseReasonRules(raw.get(REASONS)),
                parseSlaHours(raw.get(SLA_HOURS)),
                parseDecimal(raw.get(URGENT_FEE), URGENT_FEE),
                parseReleaseCondition(raw.get(RELEASE_CONDITION)),
                parseBooleanOrDefault(raw.get(AUTO_ORDER_ON_PAYMENT), AUTO_ORDER_ON_PAYMENT,
                        AUTO_ORDER_ON_PAYMENT_DEFAULT),
                parseUndueFormula(raw.get(UNDUE_CUT_COMPENSATION)));
    }

    /** Class c floor: a notice period below 30 days is refused (Lei 11.445/2007, art. 40, §2º). */
    public static void validateMinNoticeDays(int days) {
        if (days < MIN_NOTICE_DAYS_FLOOR) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    MIN_NOTICE_DAYS, "BELOW_LEGAL_FLOOR",
                    "Aviso previo de " + days + " dias abaixo do piso de " + MIN_NOTICE_DAYS_FLOOR
                            + " dias (Lei 11.445/2007, art. 40, §2o): o parametro so amplia."));
        }
    }

    private static Integer parseInt(String json, String key) {
        if (json == null) {
            return null;
        }
        JsonNode node = Json.readTree(json);
        if (!node.isIntegralNumber()) {
            throw new IllegalStateException("Parametro aceito '" + key + "' com valor nao inteiro: " + json);
        }
        int value = node.intValue();
        if (key.equals(MIN_NOTICE_DAYS)) {
            validateMinNoticeDays(value);
        }
        if (value <= 0) {
            throw new IllegalStateException("Parametro aceito '" + key + "' precisa ser maior que zero: " + json);
        }
        return value;
    }

    private static BigDecimal parseDecimal(String json, String key) {
        if (json == null) {
            return null;
        }
        JsonNode node = Json.readTree(json);
        if (!node.isNumber()) {
            throw new IllegalStateException("Parametro aceito '" + key + "' com valor nao numerico: " + json);
        }
        return node.decimalValue();
    }

    private static Boolean parseBoolean(String json, String key) {
        if (json == null) {
            return null;
        }
        JsonNode node = Json.readTree(json);
        if (!node.isBoolean()) {
            throw new IllegalStateException("Parametro aceito '" + key + "' com valor nao booleano: " + json);
        }
        return node.booleanValue();
    }

    private static boolean parseBooleanOrDefault(String json, String key, boolean fallback) {
        Boolean value = parseBoolean(json, key);
        return value == null ? fallback : value;
    }

    private static Set<CutoffEnums.ProtectedClass> parseProtectedClasses(String json) {
        if (json == null) {
            return PROTECTED_CLASSES_MINIMUM;
        }
        JsonNode node = Json.readTree(json);
        if (!node.isArray()) {
            throw new IllegalStateException("Parametro aceito '" + PROTECTED_CLASSES
                    + "' precisa ser uma lista: " + json);
        }
        Set<CutoffEnums.ProtectedClass> classes = EnumSet.noneOf(CutoffEnums.ProtectedClass.class);
        for (JsonNode item : node) {
            try {
                classes.add(CutoffEnums.ProtectedClass.valueOf(item.asText()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Classe protegida desconhecida em '" + PROTECTED_CLASSES
                        + "': " + item.asText());
            }
        }
        // Class c: the tenant list may only ADD to the locked minimum.
        if (!classes.containsAll(PROTECTED_CLASSES_MINIMUM)) {
            var missing = EnumSet.copyOf(PROTECTED_CLASSES_MINIMUM);
            missing.removeAll(classes);
            throw ApiException.Validation.of(new ApiException.FieldError(
                    PROTECTED_CLASSES, "PROTECTED_CLASS_REMOVED",
                    "A lista de classes protegidas so acrescenta (classe c): faltam " + missing + "."));
        }
        return classes;
    }

    private static Map<CutoffEnums.Reason, ReasonRule> parseReasonRules(String json) {
        if (json == null) {
            return Map.of();
        }
        JsonNode node = Json.readTree(json);
        if (!node.isObject()) {
            throw new IllegalStateException("Parametro aceito '" + REASONS + "' precisa ser um objeto: " + json);
        }
        Map<CutoffEnums.Reason, ReasonRule> rules = new LinkedHashMap<>();
        for (String code : node.propertyNames()) {
            CutoffEnums.Reason reason;
            try {
                reason = CutoffEnums.Reason.valueOf(code);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Motivo desconhecido em '" + REASONS + "': " + code);
            }
            JsonNode row = node.path(code);
            boolean follows = row.path("followsTemporalBans").asBoolean(true);
            String citation = row.hasNonNull("normCitation") ? row.path("normCitation").asText() : null;
            if (!follows && reason == CutoffEnums.Reason.DEFAULT) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        REASONS + "." + code, "CLASS_A_LOCKED",
                        "Inadimplencia segue sempre a regra integral das vedacoes (classe a)."));
            }
            if (!follows && reason != CutoffEnums.Reason.TECHNICAL_EMERGENCY
                    && (citation == null || citation.isBlank())) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        REASONS + "." + code, "NORM_CITATION_REQUIRED",
                        "Excluir o motivo " + code + " das vedacoes exige aceite formal com norma citada."));
            }
            rules.put(reason, new ReasonRule(follows, citation));
        }
        return rules;
    }

    private static SlaHours parseSlaHours(String json) {
        if (json == null) {
            return null;
        }
        JsonNode node = Json.readTree(json);
        if (!node.isObject()) {
            throw new IllegalStateException("Parametro aceito '" + SLA_HOURS + "' precisa ser um objeto: " + json);
        }
        // The AGR-Tubarao profile carries an unmapped {reconnectionHours}
        // shape: it is NOT guessed into normal/urgent/undue here -- the
        // tenant declares the mapping at activation (P2.31 report).
        return new SlaHours(
                intOrNull(node, "normalHours"),
                intOrNull(node, "urgentHours"),
                intOrNull(node, "undueHours"));
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() ? value.intValue() : null;
    }

    private static CutoffEnums.ReleaseCondition parseReleaseCondition(String json) {
        if (json == null) {
            return RELEASE_CONDITION_DEFAULT;
        }
        JsonNode node = Json.readTree(json);
        try {
            return CutoffEnums.ReleaseCondition.valueOf(node.asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Valor desconhecido de '" + RELEASE_CONDITION + "': " + json);
        }
    }

    private static CutoffEnums.UndueCompensationFormula parseUndueFormula(String json) {
        if (json == null) {
            // PARAMETROS-DO-TENANT: without an agency profile, the
            // ARESC/AGIR formula is the factory default.
            return CutoffEnums.UndueCompensationFormula.GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE;
        }
        JsonNode node = Json.readTree(json);
        try {
            return CutoffEnums.UndueCompensationFormula.valueOf(node.asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Valor desconhecido de '" + UNDUE_CUT_COMPENSATION + "': " + json);
        }
    }
}
