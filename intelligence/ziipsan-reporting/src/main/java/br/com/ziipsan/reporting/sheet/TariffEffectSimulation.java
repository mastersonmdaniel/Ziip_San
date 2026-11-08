package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Simulacao do efeito tarifario de bonus ou penalidade (GES-30): efeito =
 * base tarifaria declarada x soma dos fatores perf.* DECLARADOS pela agencia
 * (percentuais). Fator nao declarado recusa a simulacao inteira - em
 * particular Q, P e U nunca entram por presuncao (texto do acceptance:
 * nao encontrados na CP 03/2026 da ANA, conferido em 22/10/2026).
 * Simulacao nao e cobranca: nenhum valor aqui toca a conta (item 32 do
 * DECISOES-DO-DONO observado - a proposta tarifaria nao se executa).
 */
public final class TariffEffectSimulation {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    public record Simulation(BigDecimal baseAmount, List<String> factorKeys,
                             BigDecimal totalFactorPercent, BigDecimal effectAmount) {
    }

    private TariffEffectSimulation() {
    }

    public static Simulation simulate(String parametersJson, BigDecimal baseAmount,
                                      List<String> factorKeys) {
        if (baseAmount == null) {
            throw new IndicatorValidationException("SIMULATION_BASE_REQUIRED",
                    "simulacao sem base tarifaria declarada");
        }
        if (factorKeys == null || factorKeys.isEmpty()) {
            throw new IndicatorValidationException("SIMULATION_FACTORS_REQUIRED",
                    "simulacao sem nenhum fator perf.* nomeado pela agencia");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (String key : factorKeys) {
            total = total.add(SheetParameters.perfFactor(parametersJson, key));
        }
        BigDecimal effect = baseAmount.multiply(total).movePointLeft(2)
                .round(new MathContext(20, RoundingMode.HALF_UP));
        return new Simulation(baseAmount, List.copyOf(factorKeys), total, effect);
    }
}
