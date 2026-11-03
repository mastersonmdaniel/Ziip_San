package br.com.ziipsan.core.serviceorder;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tenant parameters of the work-order engine (ADR-0038: every normative
 * fork is a tenant parameter; P0.19: accepted and vigente versions in
 * {@code parameter_versions} win over the factory default).
 *
 * <p>Classes (ADR-0015, confirmed by the plan row and by 22b):
 * <ul>
 *   <li>{@code so.operational_types_enabled}: master switch of operational
 *       types (no factory class stated in the plan; the engine default is
 *       enabled, and the tenant may disable it).</li>
 *   <li>{@code so.deadline_hours_by_type}: per-type deadline override. It
 *       only REDUCES: an override above the type deadline (the agency
 *       deadline declared on the type) is refused by
 *       {@link #validateDeadlineOverride}.</li>
 *   <li>{@code sst.required_controls_by_service_type}: class c, the tenant
 *       only AMPLIFIES: {@link #validateRequiredControls} refuses a map
 *       that drops a factory control of a type.</li>
 *   <li>{@code sst.min_crew_confined_space}: class a, floor 2 (NR-33,
 *       33.3.4.4: no lone work, plus the entry-watch role). A value below
 *       2 is refused by {@link #validateMinCrewConfinedSpace}.</li>
 *   <li>{@code sst.training_validity_months}: factory default 12, the
 *       refresher period of NR-33 (33.3.5.3/4/6, cited in 22b); the tenant
 *       only REDUCES it ({@link #validateTrainingValidityMonths}). The
 *       value is consumed when a qualification is registered (P3.31 /
 *       ED-59); the engine checks the per-person validity date recorded
 *       on the crew row, and additionally consults a bound
 *       {@link CompetencyRegistry} when one exists.</li>
 * </ul>
 * <p>Where the factory default of a parameter is not stated by any norm
 * or owner decision, this class does not invent one: the reader returns
 * empty and the engine falls back to the type's own declaration.
 */
public final class ServiceOrderParameters {

    public static final String OPERATIONAL_TYPES_ENABLED = "so.operational_types_enabled";
    public static final String DEADLINE_HOURS_BY_TYPE = "so.deadline_hours_by_type";
    public static final String REQUIRED_CONTROLS_BY_SERVICE_TYPE = "sst.required_controls_by_service_type";
    public static final String MIN_CREW_CONFINED_SPACE = "sst.min_crew_confined_space";
    public static final String TRAINING_VALIDITY_MONTHS = "sst.training_validity_months";

    /** Factory default: operational types are enabled. */
    public static final boolean OPERATIONAL_TYPES_ENABLED_DEFAULT = true;
    /** Factory default: NR-33 floor, no lone work (33.3.4.4). */
    public static final int MIN_CREW_CONFINED_SPACE_DEFAULT = 2;
    /** Factory default: NR-33 refresher period in months (33.3.5.3/4/6). */
    public static final int TRAINING_VALIDITY_MONTHS_DEFAULT = 12;

    private ServiceOrderParameters() {
    }

    /** Refuses an override that exceeds the agency deadline declared on the type. */
    public static void validateDeadlineOverride(String typeCode, int typeDeadlineHours, int overrideHours) {
        if (overrideHours > typeDeadlineHours) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    DEADLINE_HOURS_BY_TYPE + "." + typeCode, "EXCEEDS_AGENCY_DEADLINE",
                    "O prazo do tenant para " + typeCode + " (" + overrideHours + " h) excede o prazo da "
                            + "agencia declarado no tipo (" + typeDeadlineHours + " h): o parametro so reduz."));
        }
        if (overrideHours <= 0) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    DEADLINE_HOURS_BY_TYPE + "." + typeCode, "INVALID",
                    "O prazo precisa ser maior que zero."));
        }
    }

    /** Effective deadline: the tenant override when present, otherwise the type deadline. */
    public static int effectiveDeadlineHours(int typeDeadlineHours, Integer overrideHours) {
        return overrideHours != null ? overrideHours : typeDeadlineHours;
    }

    /** Class a floor: the confined-space crew minimum is 2 or more, never below. */
    public static void validateMinCrewConfinedSpace(int value) {
        if (value < MIN_CREW_CONFINED_SPACE_DEFAULT) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    MIN_CREW_CONFINED_SPACE, "BELOW_NORM_FLOOR",
                    "A equipe minima em espaco confinado e 2 ou mais (NR-33, 33.3.4.4): "
                            + value + " e recusado."));
        }
    }

    /** The norm default may only be reduced, never extended. */
    public static void validateTrainingValidityMonths(int value) {
        if (value <= 0) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    TRAINING_VALIDITY_MONTHS, "INVALID",
                    "A validade da capacitacao precisa ser maior que zero."));
        }
        if (value > TRAINING_VALIDITY_MONTHS_DEFAULT) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    TRAINING_VALIDITY_MONTHS, "EXCEEDS_NORM_DEFAULT",
                    "A validade da capacitacao (" + value + " meses) excede o padrao da norma "
                            + "(" + TRAINING_VALIDITY_MONTHS_DEFAULT + " meses, NR-33): o parametro so reduz."));
        }
    }

    /**
     * Class c: the tenant map may only ADD controls to a type's factory
     * controls, never remove one. Unknown control codes are refused.
     */
    public static void validateRequiredControls(String typeCode, Set<ServiceOrderEnums.ControlCode> factory,
            Set<ServiceOrderEnums.ControlCode> tenant) {
        if (tenant == null) {
            return;
        }
        if (!tenant.containsAll(factory)) {
            var missing = new HashSet<>(factory);
            missing.removeAll(tenant);
            throw ApiException.Validation.of(new ApiException.FieldError(
                    REQUIRED_CONTROLS_BY_SERVICE_TYPE + "." + typeCode, "CONTROL_REMOVED",
                    "O tenant nao pode remover controles de fabrica do tipo " + typeCode + ": faltam " + missing + "."));
        }
    }

    /** Parses a {@code {typeCode: hours}} JSON object; non-integer values are refused. */
    public static Map<String, Integer> parseDeadlineOverrides(String json) {
        Map<String, Integer> out = new HashMap<>();
        JsonNode root = Json.readTree(json);
        if (root == null || root.isMissingNode() || root.isNull()) {
            return out;
        }
        if (!root.isObject()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    DEADLINE_HOURS_BY_TYPE, "INVALID", "O valor precisa ser um objeto JSON."));
        }
        for (String code : root.propertyNames()) {
            JsonNode value = root.path(code);
            if (!value.isIntegralNumber()) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        DEADLINE_HOURS_BY_TYPE + "." + code, "INVALID",
                        "O prazo precisa ser um numero inteiro de horas."));
            }
            out.put(code, value.intValue());
        }
        return out;
    }

    /** Parses a {@code {typeCode: [CONTROL, ...]}} JSON object; unknown codes are refused. */
    public static Map<String, Set<ServiceOrderEnums.ControlCode>> parseRequiredControls(String json) {
        Map<String, Set<ServiceOrderEnums.ControlCode>> out = new HashMap<>();
        JsonNode root = Json.readTree(json);
        if (root == null || root.isMissingNode() || root.isNull()) {
            return out;
        }
        if (!root.isObject()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    REQUIRED_CONTROLS_BY_SERVICE_TYPE, "INVALID", "O valor precisa ser um objeto JSON."));
        }
        for (String code : root.propertyNames()) {
            JsonNode value = root.path(code);
            if (!value.isArray()) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        REQUIRED_CONTROLS_BY_SERVICE_TYPE + "." + code, "INVALID",
                        "Os controles precisam ser uma lista."));
            }
            Set<ServiceOrderEnums.ControlCode> controls = new HashSet<>();
            List<String> unknown = new ArrayList<>();
            for (JsonNode item : value) {
                try {
                    controls.add(ServiceOrderEnums.ControlCode.valueOf(item.asText()));
                } catch (IllegalArgumentException e) {
                    unknown.add(item.asText());
                }
            }
            if (!unknown.isEmpty()) {
                throw ApiException.Validation.of(new ApiException.FieldError(
                        REQUIRED_CONTROLS_BY_SERVICE_TYPE + "." + code, "UNKNOWN_CONTROL",
                        "Controles desconhecidos: " + unknown + "."));
            }
            out.put(code, controls);
        }
        return out;
    }
}
