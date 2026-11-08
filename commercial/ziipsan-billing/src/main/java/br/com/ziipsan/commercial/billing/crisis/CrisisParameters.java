package br.com.ziipsan.commercial.billing.crisis;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Leitura tipada dos parametros da ED-91 (crise hidrica e contingencia),
 * SEMPRE pela classe Json do core. Ausencia e erro explicito
 * (MissingParameterException), nunca padrao assumido em silencio.
 *
 * Chaves do acceptance:
 *   crisis.rotation_enabled                 BOOLEAN classe b
 *   crisis.tariff_flag_enabled              BOOLEAN classe b
 *   crisis.tanker_truck_min_chlorine_mg_l   DECIMAL padrao 0,5 mg/L, so amplia (classe c na pratica:
 *                                           reduzir abaixo de 0,5 e recusado)
 *   connection.temporary_max_days           INT     sem valor de fabrica [A DEFINIR] - ausencia e erro
 *   tariff.contingency_enabled              BOOLEAN classe b, com aceite (familia tariff.contingency_*)
 *   event.*                                 BOOLEAN classe b - um booleano por efeito da medida
 *                                           excepcional (ver ExceptionalMeasure.Effect)
 *
 * Percentuais de racionamento, gatilhos de crise e faixas NAO tem fabrica:
 * vivem no ato/componente registrado pelo tenant/agencia, nunca aqui.
 */
public final class CrisisParameters {

    public static final String ROTATION_ENABLED = "crisis.rotation_enabled";
    public static final String TARIFF_FLAG_ENABLED = "crisis.tariff_flag_enabled";
    public static final String TANKER_MIN_CHLORINE = "crisis.tanker_truck_min_chlorine_mg_l";
    public static final String TEMPORARY_MAX_DAYS = "connection.temporary_max_days";
    public static final String CONTINGENCY_ENABLED = "tariff.contingency_enabled";

    /** Minimo legal/de aceite do cloro residual livre do carro-pipa: 0,5 mg/L (OPE-10). */
    public static final BigDecimal CHLORINE_FLOOR_MG_L = new BigDecimal("0.5");

    private CrisisParameters() {}

    public static boolean rotationEnabled(String json) { return bool(json, ROTATION_ENABLED); }
    public static boolean tariffFlagEnabled(String json) { return bool(json, TARIFF_FLAG_ENABLED); }
    public static boolean contingencyEnabled(String json) { return bool(json, CONTINGENCY_ENABLED); }

    /** Efeito event.* (classe b): um booleano por efeito; ausencia e erro explicito. */
    public static boolean eventEffect(String json, String eventKey) {
        if (eventKey == null || !eventKey.startsWith("event."))
            throw new IllegalArgumentException("chave de efeito deve ser da familia event.*: " + eventKey);
        return bool(json, eventKey);
    }

    /** Cloro minimo: padrao 0,5 mg/L; valor menor e recusado (so amplia). */
    public static BigDecimal tankerMinChlorineMgL(String json) {
        JsonNode root = tree(json, TANKER_MIN_CHLORINE);
        if (!root.isNumber()) throw new IllegalStateException(TANKER_MIN_CHLORINE + " deve ser numero JSON");
        BigDecimal v = root.decimalValue();
        if (v.compareTo(CHLORINE_FLOOR_MG_L) < 0)
            throw new IllegalArgumentException(TANKER_MIN_CHLORINE + ": minimo de 0,5 mg/L (so amplia); recebidos: " + v);
        return v;
    }

    /** Vigencia maxima da ligacao temporaria, em dias: sem fabrica [A DEFINIR]; ausencia e erro. */
    public static int temporaryMaxDays(String json) {
        JsonNode root = tree(json, TEMPORARY_MAX_DAYS);
        if (!root.isIntegralNumber()) throw new IllegalStateException(TEMPORARY_MAX_DAYS + " deve ser inteiro JSON");
        int days = root.asInt();
        if (days < 1) throw new IllegalArgumentException(TEMPORARY_MAX_DAYS + " deve ser ao menos 1: " + days);
        return days;
    }

    private static boolean bool(String json, String key) {
        JsonNode root = tree(json, key);
        if (!root.isBoolean()) throw new IllegalStateException(key + " deve ser booleano JSON");
        return root.asBoolean();
    }

    private static JsonNode tree(String json, String key) {
        if (json == null || json.isBlank())
            throw new MissingParameterException(key, "conteudo JSON ausente para a ED-91");
        JsonNode root = Json.readTree(json);
        if (root.isMissingNode()) throw new MissingParameterException(key, "conteudo JSON vazio");
        return root;
    }
}
