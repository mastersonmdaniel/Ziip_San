package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Verificacao anual e trajetoria da universalizacao (aceite da ED-77):
 * cumprimento contado em janela de 5 anos - a meta anual so se considera
 * cumprida na janela com pelo menos 3 de 5 anos atendidos; e trajetoria de
 * ligacoes/domicilios a incorporar por ano = (alvo - atual) / anos restantes,
 * com teto (ceil) para nao prometer fracao de domicilio. Aritmetica pura
 * sobre numeros declarados; nenhum numero de fabrica alem da regra 3-de-5
 * do proprio acceptance.
 */
public final class UniversalizationAssessment {

    public record YearResult(int year, BigDecimal achievedPercent, BigDecimal targetPercent) {
        public boolean met() { return achievedPercent.compareTo(targetPercent) >= 0; }
    }

    public record WindowVerdict(int yearsMet, int windowSize, boolean fulfilled) {
    }

    private UniversalizationAssessment() {
    }

    public static WindowVerdict verifyFiveYearWindow(List<YearResult> years) {
        if (years == null || years.size() != 5) {
            throw new IndicatorValidationException("UNIVERSALIZATION_WINDOW_SIZE",
                    "verificacao exige a janela de 5 anos do acceptance, recebidos: "
                            + (years == null ? 0 : years.size()));
        }
        int met = (int) years.stream().filter(YearResult::met).count();
        return new WindowVerdict(met, 5, met >= 3);
    }

    /** Domicilios a incorporar por ano (teto). Alvo ja atingido = 0, nunca negativo. */
    public static long trajectoryPerYear(long targetHouseholds, long currentHouseholds,
                                         int remainingYears) {
        if (remainingYears <= 0) {
            throw new IndicatorValidationException("TRAJECTORY_YEARS_INVALID",
                    "trajetoria sem anos restantes: prazo vencido e declarado, nao dividido");
        }
        long remaining = targetHouseholds - currentHouseholds;
        if (remaining <= 0) {
            return 0L;
        }
        return (remaining + remainingYears - 1L) / remainingYears;
    }
}
