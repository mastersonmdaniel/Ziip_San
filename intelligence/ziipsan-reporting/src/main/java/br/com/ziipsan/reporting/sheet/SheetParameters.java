package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Parametros da ED-77, lidos do documento JSON de parametros do tenant pela
 * classe Json do core (precedente BenchmarkParameters da ED-48). As familias
 * de parametros DESTA classe sao apenas {@code loss.target_*} e
 * {@code perf.*}:
 * <ul>
 *   <li>{@code loss.target_*}: classe d (conforme a agencia), REG - exige
 *       origem e vigencia declaradas no proprio documento; sem elas, recusa
 *       nomeada. NENHUM valor de fabrica: em especial as metas de perdas da
 *       Portaria MCID 788/2024 so entram depois de conferidas contra o
 *       texto da portaria (aceite: "valores conferidos antes da carga") -
 *       pendencia P-ED77-03;</li>
 *   <li>{@code perf.*}: fatores da regulacao por incentivo definidos pela
 *       agencia (classe d, REG). Os fatores Q, P e U NAO sao presumidos:
 *       nao foram encontrados na CP 03/2026 da ANA (conferido em 22/10/2026,
 *       texto do acceptance) - fator ausente = simulacao recusada.</li>
 * </ul>
 * Metas de universalizacao NAO sao parametros {@code goal.*} desta classe e
 * nao tem leitor aqui: nascem como registros {@link UniversalizationGoal}
 * (origem e vigencia obrigatorias), persistidos na tabela
 * {@code universalization_goals} ({@code valid_from}/{@code valid_to} e os
 * CHECKs do piso federal).
 */
public final class SheetParameters {

    public static final String LOSS_TARGET_PREFIX = "loss.target_";
    public static final String PERF_PREFIX = "perf.";

    private SheetParameters() {
    }

    /** Meta de perdas declarada: valor + origem + vigencia, tudo obrigatorio. */
    public record DeclaredTarget(BigDecimal value, String origin, String validFrom, String validToOrNull) {
    }

    public static DeclaredTarget lossTarget(String parametersJson, String key) {
        Map<String, Object> params = Json.readMap(parametersJson);
        Object raw = params.get(key);
        if (raw == null) {
            throw new IndicatorValidationException("LOSS_TARGET_NOT_DECLARED",
                    "parametro " + key + " nao declarado pelo tenant/agencia: metas de perdas "
                            + "da Portaria MCID 788/2024 nao tem valor de fabrica nesta entrega "
                            + "[SEM FONTE - conferir antes da carga]");
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IndicatorValidationException("SHEET_PARAMETER_INVALID",
                    "parametro " + key + " deve ser objeto {value, origin, validFrom}");
        }
        Object value = map.get("value");
        Object origin = map.get("origin");
        Object validFrom = map.get("validFrom");
        if (value == null || origin == null || validFrom == null) {
            throw new IndicatorValidationException("SHEET_PARAMETER_ORIGIN_REQUIRED",
                    "parametro " + key + " exige value, origin e validFrom (classe d, REG - GES-03)");
        }
        Object validTo = map.get("validTo");
        return new DeclaredTarget(new BigDecimal(value.toString()), origin.toString(),
                validFrom.toString(), validTo == null ? null : validTo.toString());
    }

    /** Fator perf.* declarado pela agencia; ausente = recusa (Q, P e U nunca presumidos). */
    public static BigDecimal perfFactor(String parametersJson, String factorKey) {
        Map<String, Object> params = Json.readMap(parametersJson);
        Object raw = params.get(PERF_PREFIX + factorKey);
        if (raw == null) {
            throw new IndicatorValidationException("PERF_FACTOR_NOT_DECLARED",
                    "fator perf." + factorKey + " nao definido pela agencia: simulacao recusada; "
                            + "fatores Q, P e U nao presumidos (nao encontrados na CP 03/2026 da ANA)");
        }
        if (raw instanceof Map<?, ?> map) {
            Object v = map.get("value");
            if (v == null) {
                throw new IndicatorValidationException("PERF_FACTOR_NOT_DECLARED",
                        "fator perf." + factorKey + " sem value declarado pela agencia");
            }
            return new BigDecimal(v.toString());
        }
        return new BigDecimal(raw.toString());
    }
}
