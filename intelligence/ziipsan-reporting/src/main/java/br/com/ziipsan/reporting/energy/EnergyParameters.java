package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Parametros da ED-90 nomeados no aceite, lidos do documento JSON de
 * parametros do tenant pela classe Json do core (precedentes
 * BenchmarkParameters da ED-48 e SheetParameters da ED-77):
 * <ul>
 *   <li>{@code energy.demand_alert_pct} (OPE-08): percentual de
 *       tolerancia sobre a demanda contratada para o alerta de multa
 *       por ultrapassagem. O aceite nomeia a chave e NAO fixa valor:
 *       sem valor do tenant, a ultrapassagem nao e avaliada (declarado
 *       em {@link EnergyEfficiency}) — nenhum percentual de fabrica;</li>
 *   <li>{@code energy.pf_min} (OPE-08): fator de potencia minimo para o
 *       alerta de multa por fator de potencia baixo. Idem: sem valor,
 *       sem avaliacao — nenhum minimo de fabrica embutido;</li>
 *   <li>{@code esg.emission_factors_version} (GES-21): versao do
 *       catalogo global de fatores de emissao que o inventario usa.
 *       Sem versao declarada, o inventario recusa (nunca "a ultima"
 *       presumida);</li>
 *   <li>{@code esg.report_profile} (GES-21): perfil declarado pelo
 *       tenant para a exportacao VOLUNTARIA do relatorio de
 *       sustentabilidade. Sem perfil, nada e exportado — e o
 *       inventario nao depende dele (sem obrigacao de relatorio
 *       identificada para autarquia municipal, texto do aceite).</li>
 * </ul>
 * Os valores podem vir como escalar ou como objeto {@code {value: ...}}
 * (mesma tolerancia dos leitores vizinhos); tipo errado e recusa
 * nomeada, nunca coercao silenciosa.
 */
public final class EnergyParameters {

    public static final String DEMAND_ALERT_PCT = "energy.demand_alert_pct";
    public static final String PF_MIN = "energy.pf_min";
    public static final String EMISSION_FACTORS_VERSION = "esg.emission_factors_version";
    public static final String REPORT_PROFILE = "esg.report_profile";

    private EnergyParameters() {
    }

    private static Object rawValue(String parametersJson, String key) {
        Map<String, Object> params = Json.readMap(parametersJson);
        Object raw = params.get(key);
        if (raw instanceof Map<?, ?> map) {
            return map.get("value");
        }
        return raw;
    }

    private static Optional<BigDecimal> decimalParam(String parametersJson, String key) {
        Object raw = rawValue(parametersJson, key);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(raw.toString()));
        } catch (NumberFormatException e) {
            throw new IndicatorValidationException("ENERGY_PARAMETER_INVALID",
                    "parametro " + key + " nao e numerico: " + raw);
        }
    }

    /** Tolerancia de demanda (% sobre a contratada); vazia sem valor do tenant. */
    public static Optional<BigDecimal> demandAlertPct(String parametersJson) {
        Optional<BigDecimal> value = decimalParam(parametersJson, DEMAND_ALERT_PCT);
        value.ifPresent(pct -> {
            if (pct.signum() < 0) {
                throw new IndicatorValidationException("ENERGY_PARAMETER_INVALID",
                        DEMAND_ALERT_PCT + " nao pode ser negativo: " + pct);
            }
        });
        return value;
    }

    /** Fator de potencia minimo; vazio sem valor do tenant. Faixa valida (0, 1]. */
    public static Optional<BigDecimal> pfMin(String parametersJson) {
        Optional<BigDecimal> value = decimalParam(parametersJson, PF_MIN);
        value.ifPresent(pf -> {
            if (pf.signum() <= 0 || pf.compareTo(BigDecimal.ONE) > 0) {
                throw new IndicatorValidationException("ENERGY_PARAMETER_INVALID",
                        PF_MIN + " deve estar em (0, 1]: " + pf);
            }
        });
        return value;
    }

    /** Versao do catalogo de fatores de emissao declarada pelo tenant; vazia sem declaracao. */
    public static Optional<String> emissionFactorsVersion(String parametersJson) {
        Object raw = rawValue(parametersJson, EMISSION_FACTORS_VERSION);
        if (raw == null || raw.toString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(raw.toString().trim());
    }

    /** Perfil de exportacao voluntaria declarado pelo tenant; vazio sem declaracao. */
    public static Optional<String> reportProfile(String parametersJson) {
        Object raw = rawValue(parametersJson, REPORT_PROFILE);
        if (raw == null || raw.toString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(raw.toString().trim());
    }
}
