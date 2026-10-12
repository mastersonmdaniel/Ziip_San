package br.com.sigsan.commercial.billing.bill;

import br.com.sigsan.commercial.billing.math.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Retenção de impostos na conta (relatório 02, seção 4.6; relatório 11, R46).
 *
 * <pre>
 *   base       = água + esgoto + débitos − créditos      truncada a 2 casas (DOWN)
 *   imposto(i) = base × alíquota(i) / 100                 HALF_DOWN, 2 casas
 *   total      = base × Σalíquotas / 100                  HALF_DOWN, 2 casas
 *   resíduo    = total − Σ imposto(i)                     o ÚLTIMO imposto absorve o resíduo
 * </pre>
 * Garante {@code Σ impostos = total} por construção.
 *
 * <p><b>Divergência corrigida em relação à especificação do legado.</b> A fórmula do legado ({@code
 * último = total − Σ anteriores}) dá valor <b>negativo</b> para o último imposto em bases pequenas: com
 * quatro tributos de 1,20 / 1,00 / 3,00 / 0,65%, uma base de R$ 0,55 dá 0,01 + 0,01 + 0,02 = 0,04
 * contra um total de 0,03, e o último sairia −0,01 (vale para as bases de 0,51 a 0,59). Aqui, quando o
 * resíduo é negativo e maior que o valor do último imposto, o excedente passa ao imposto anterior, e
 * assim por diante: nunca há imposto negativo e a soma continua igual ao total. Quando o resíduo cabe
 * no último imposto, o resultado é idêntico ao da fórmula do legado.
 *
 * <p>O modo HALF_DOWN vem do levantamento do legado; nenhuma norma lida define arredondamento de
 * retenção (relatório 08, E-20). Está isolado nas constantes abaixo para ser trocado em um só lugar.
 */
public final class TaxWithholding {

    /** Truncamento da base de cálculo. */
    static final RoundingMode BASE_ROUNDING = RoundingMode.DOWN;
    /** Arredondamento de cada imposto e do total. */
    static final RoundingMode TAX_ROUNDING = RoundingMode.HALF_DOWN;

    private TaxWithholding() {
    }

    public static List<TaxLine> withhold(BigDecimal baseBeforeTruncation, TaxWithholdingPolicy policy) {
        BigDecimal base = baseBeforeTruncation.setScale(Money.SCALE, BASE_ROUNDING);
        List<TaxRate> rates = policy.rates();
        int n = rates.size();
        BigDecimal[] amounts = new BigDecimal[n];
        BigDecimal direct = Money.ZERO;
        if (base.signum() > 0) {
            for (int i = 0; i < n; i++) {
                amounts[i] = base.multiply(rates.get(i).ratePct()).divide(Money.HUNDRED, Money.SCALE, TAX_ROUNDING);
                direct = direct.add(amounts[i]);
            }
            BigDecimal total = base.multiply(policy.totalRatePct()).divide(Money.HUNDRED, Money.SCALE, TAX_ROUNDING);
            BigDecimal residue = total.subtract(direct);
            if (residue.signum() > 0) {
                amounts[n - 1] = amounts[n - 1].add(residue);
            } else if (residue.signum() < 0) {
                BigDecimal toTakeBack = residue.negate();
                for (int i = n - 1; i >= 0 && toTakeBack.signum() > 0; i--) {
                    BigDecimal take = Money.min(amounts[i], toTakeBack);
                    amounts[i] = amounts[i].subtract(take);
                    toTakeBack = toTakeBack.subtract(take);
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                amounts[i] = Money.ZERO;
            }
        }

        List<TaxLine> lines = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            lines.add(new TaxLine(rates.get(i).code(), rates.get(i).ratePct(),
                    base.signum() > 0 ? base : Money.ZERO, amounts[i], i + 1));
        }
        return lines;
    }
}
