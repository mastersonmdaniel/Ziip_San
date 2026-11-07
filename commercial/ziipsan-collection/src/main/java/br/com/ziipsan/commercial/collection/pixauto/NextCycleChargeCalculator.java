package br.com.ziipsan.commercial.collection.pixauto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Multa e juros do debito nao liquidado entram na instrucao do CICLO SEGUINTE
 * (GES-15, registrada na P3.2): a instrucao seguinte soma, ao principal do novo
 * ciclo, a multa e os juros calculados sobre o principal NAO pago do ciclo
 * falho.
 *
 * <p>As aliquotas NAO sao presumidas aqui: multa e taxa diaria de juros sao
 * entradas explicitas de quem chama (no produto, os parametros do tenant
 * {@code fee.fine_pct} e a taxa de juros simples sem capitalizacao fixados na
 * P0.20/P2.5 - padrao legal de multa de 2% e juros simples, sem capitalizacao).
 * Juros sao SIMPLES: principal x taxa diaria x dias, sem juros sobre juros.
 * Arredondamento monetario HALF_UP em 2 casas, por componente.
 */
public final class NextCycleChargeCalculator {

    /** Componentes da instrucao do ciclo seguinte. */
    public record NextCycleCharge(BigDecimal nextPrincipal, BigDecimal fine, BigDecimal interest,
                                  BigDecimal total) {
    }

    private NextCycleChargeCalculator() {
    }

    /**
     * @param unpaidPrincipal principal do ciclo falho que nao foi pago (> 0)
     * @param nextPrincipal   principal do ciclo seguinte (>= 0)
     * @param finePct         percentual de multa sobre o principal nao pago (>= 0)
     * @param dailyInterestPct percentual de juros simples AO DIA (>= 0)
     * @param daysLate        dias de atraso do principal nao pago (>= 0)
     */
    public static NextCycleCharge compute(BigDecimal unpaidPrincipal, BigDecimal nextPrincipal,
                                          BigDecimal finePct, BigDecimal dailyInterestPct, int daysLate) {
        if (unpaidPrincipal == null || unpaidPrincipal.signum() <= 0) {
            throw new IllegalArgumentException("principal nao pago deve ser > 0");
        }
        if (nextPrincipal == null || nextPrincipal.signum() < 0) {
            throw new IllegalArgumentException("principal do ciclo seguinte nao pode ser negativo");
        }
        if (finePct == null || finePct.signum() < 0) {
            throw new IllegalArgumentException("percentual de multa nao pode ser negativo");
        }
        if (dailyInterestPct == null || dailyInterestPct.signum() < 0) {
            throw new IllegalArgumentException("taxa diaria de juros nao pode ser negativa");
        }
        if (daysLate < 0) {
            throw new IllegalArgumentException("dias de atraso nao podem ser negativos");
        }
        BigDecimal fine = unpaidPrincipal.multiply(finePct)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal interest = unpaidPrincipal.multiply(dailyInterestPct)
                .multiply(BigDecimal.valueOf(daysLate))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal total = nextPrincipal.add(fine).add(interest).setScale(2, RoundingMode.HALF_UP);
        return new NextCycleCharge(nextPrincipal.setScale(2, RoundingMode.HALF_UP), fine, interest, total);
    }
}
