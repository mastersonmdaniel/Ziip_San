package br.com.ziipsan.core.protocol;

import br.com.ziipsan.core.api.ApiException;
import tools.jackson.databind.JsonNode;

/**
 * The sac.* tenant parameters of P1.36 (definitions in core V9_9,
 * values in PARAMETROS-DO-TENANT 20d §5): keys, factory defaults and
 * fail-closed parsing of an accepted parameter value. An unknown value
 * in an accepted version is refused instead of silently falling back:
 * a deadline computed under a misspelled rule would be a wrong
 * deadline given to the user.
 */
public final class ProtocolParameters {

    public static final String DEADLINE_COUNTING_RULE = "sac.deadline_counting_rule";
    public static final String HOLIDAY_SCOPE = "sac.holiday_scope";
    public static final String EXTENSION_POLICY = "sac.extension_policy";

    public static final DeadlineCountingRule DEADLINE_COUNTING_RULE_DEFAULT = DeadlineCountingRule.CALENDAR;
    public static final HolidayScope HOLIDAY_SCOPE_DEFAULT = HolidayScope.NATIONAL;
    public static final ExtensionPolicy EXTENSION_POLICY_DEFAULT = ExtensionPolicy.NONE;

    private ProtocolParameters() {
    }

    public static DeadlineCountingRule parseCountingRule(String rawJson) {
        String value = textValue(DEADLINE_COUNTING_RULE, rawJson);
        if (value == null) {
            return DEADLINE_COUNTING_RULE_DEFAULT;
        }
        try {
            return DeadlineCountingRule.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.Validation.of(new ApiException.FieldError(DEADLINE_COUNTING_RULE, "INVALID_VALUE",
                    "Regra de contagem desconhecida: " + value + "."));
        }
    }

    public static HolidayScope parseHolidayScope(String rawJson) {
        String value = textValue(HOLIDAY_SCOPE, rawJson);
        if (value == null) {
            return HOLIDAY_SCOPE_DEFAULT;
        }
        try {
            return HolidayScope.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.Validation.of(new ApiException.FieldError(HOLIDAY_SCOPE, "INVALID_VALUE",
                    "Escopo de feriado desconhecido: " + value + "."));
        }
    }

    public static ExtensionPolicy parseExtensionPolicy(String rawJson) {
        String value = textValue(EXTENSION_POLICY, rawJson);
        if (value == null) {
            return EXTENSION_POLICY_DEFAULT;
        }
        try {
            return ExtensionPolicy.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.Validation.of(new ApiException.FieldError(EXTENSION_POLICY, "INVALID_VALUE",
                    "Politica de prorrogacao desconhecida: " + value + "."));
        }
    }

    /**
     * The text of an accepted parameter value (stored as JSONB, so a
     * string value arrives quoted). Null stays null (factory default
     * applies); a non-text JSON value is refused.
     */
    static String textValue(String key, String rawJson) {
        if (rawJson == null) {
            return null;
        }
        JsonNode node;
        try {
            node = br.com.ziipsan.core.json.Json.readTree(rawJson);
        } catch (RuntimeException e) {
            throw ApiException.Validation.of(new ApiException.FieldError(key, "INVALID_VALUE",
                    "Valor do parametro nao e um JSON valido."));
        }
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw ApiException.Validation.of(new ApiException.FieldError(key, "INVALID_VALUE",
                    "Valor do parametro precisa ser texto."));
        }
        return node.asString();
    }
}
