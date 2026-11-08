package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.util.Map;

/**
 * Leitura tipada do parametro {@code bench.cross_tenant_opt_in} (aceite da
 * ED-48/GES-29: classe b, PADRAO DESLIGADO). O documento de parametros e
 * lido so pela classe {@code Json} do core. Ausencia do parametro =
 * desligado (o padrao); valor que nao seja booleano = recusa nomeada,
 * nunca interpretacao silenciosa.
 */
public final class BenchmarkParameters {

    public static final String CROSS_TENANT_OPT_IN_KEY = "bench.cross_tenant_opt_in";

    private BenchmarkParameters() {
    }

    public static boolean crossTenantOptInEnabled(String parametersJson) {
        Map<String, Object> parameters = Json.readMap(parametersJson);
        Object value = parameters.get(CROSS_TENANT_OPT_IN_KEY);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean enabled) {
            return enabled;
        }
        throw new IndicatorValidationException("BENCHMARK_PARAMETER_INVALID",
                "parametro " + CROSS_TENANT_OPT_IN_KEY + " deve ser booleano, recebido: " + value);
    }
}
