package br.com.ziipsan.platform.observability;

/**
 * P3.11: burn rate e orçamento de erro de um SLO, calculados à mão e
 * conferidos nos testes com os mesmos números deste javadoc.
 *
 * <p>Definições (SRE Workbook, multi-window burn rate):
 * <ul>
 *   <li>taxa de erro observada = eventos ruins / eventos totais;</li>
 *   <li>burn rate = taxa observada / taxa permitida pelo SLO.
 *       Burn 1 consome o orçamento exatamente no ritmo da janela;
 *       burn 2, na metade do tempo;</li>
 *   <li>orçamento da janela = taxa permitida × eventos totais da janela;</li>
 *   <li>fração consumida = eventos ruins / orçamento.</li>
 * </ul>
 *
 * <p>Exemplo calculado à mão (repetido no teste): SLO 99,9% (permitido
 * 0,001), 100.000 eventos, 250 ruins → taxa 0,0025 → burn 2,5; orçamento
 * 100 eventos; consumido 250/100 = 250%.
 *
 * <p>Ausência de dado é erro explícito: janela sem eventos lança
 * {@link IllegalArgumentException}. Burn rate "zero" inventado a partir de
 * janela vazia esconderia exatamente a indisponibilidade que o alerta
 * existe para mostrar (mesma regra da {@code V9_1} para o watchdog:
 * ausência é "nunca coletou", não zero).
 */
public final class BurnRateCalculator {

    /** Limiares multi-janela usuais (SRE Workbook): página e ticket. */
    public enum AlertTier { NONE, TICKET_FAST, PAGE_SLOW, TICKET_SLOW, PAGE_FAST }

    public record Result(
            double observedErrorRate,
            double burnRate,
            double errorBudgetEvents,
            double consumedFraction,
            double remainingFraction) {
    }

    private BurnRateCalculator() {
    }

    public static Result calculate(SloDefinition slo, long totalEvents, long badEvents) {
        if (slo == null) {
            throw new IllegalArgumentException("SLO é obrigatório para calcular burn rate");
        }
        if (totalEvents <= 0) {
            throw new IllegalArgumentException(
                    "Sem eventos na janela não há burn rate calculável (dado ausente, não zero): total="
                            + totalEvents);
        }
        if (badEvents < 0 || badEvents > totalEvents) {
            throw new IllegalArgumentException(
                    "Eventos ruins fora do intervalo [0, total]: bad=" + badEvents + ", total=" + totalEvents);
        }
        double observed = (double) badEvents / (double) totalEvents;
        double burn = observed / slo.allowedErrorRate();
        double budget = slo.allowedErrorRate() * totalEvents;
        double consumed = badEvents / budget;
        return new Result(observed, burn, budget, consumed, 1.0 - consumed);
    }

    /**
     * Dias até esgotar o orçamento restante no ritmo observado.
     * Sem consumo observado, devolve {@link Double#POSITIVE_INFINITY}
     * (não esgota neste ritmo) — valor matemático, não dado inventado.
     */
    public static double daysToExhaustion(SloDefinition slo, long totalEvents, long badEvents,
                                            double elapsedDays) {
        if (elapsedDays <= 0) {
            throw new IllegalArgumentException("Dias decorridos devem ser > 0");
        }
        Result r = calculate(slo, totalEvents, badEvents);
        if (r.remainingFraction() <= 0) {
            return 0.0;
        }
        if (badEvents == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double remainingEvents = r.errorBudgetEvents() * r.remainingFraction();
        double badPerDay = badEvents / elapsedDays;
        return remainingEvents / badPerDay;
    }

    /**
     * Classificação multi-janela: burn ≥ 14,4 (janela curta) pagina na hora;
     * ≥ 6 pagina; ≥ 3 abre ticket rápido; ≥ 1 abre ticket lento.
     */
    public static AlertTier tierFor(double burnRateShortWindow, double burnRateLongWindow) {
        if (!Double.isFinite(burnRateShortWindow) || !Double.isFinite(burnRateLongWindow)
                || burnRateShortWindow < 0 || burnRateLongWindow < 0) {
            throw new IllegalArgumentException("Burn rates devem ser finitos e >= 0");
        }
        if (burnRateShortWindow >= 14.4) {
            return AlertTier.PAGE_FAST;
        }
        if (burnRateShortWindow >= 6.0) {
            return AlertTier.PAGE_SLOW;
        }
        if (burnRateLongWindow >= 3.0) {
            return AlertTier.TICKET_FAST;
        }
        if (burnRateLongWindow >= 1.0) {
            return AlertTier.TICKET_SLOW;
        }
        return AlertTier.NONE;
    }
}
