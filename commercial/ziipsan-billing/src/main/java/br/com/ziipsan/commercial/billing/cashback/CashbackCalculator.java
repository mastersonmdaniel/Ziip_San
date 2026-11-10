package br.com.ziipsan.commercial.billing.cashback;

import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Cálculo puro da devolução do cashback (P2.34), por domicílio e por
 * período de aferição:
 *
 * <pre>
 *   devolução de CBS = 100% do CBS sobre o consumo do período, se o período
 *                      é de 01/2027 em diante; senão 0,00
 *   devolução de IBS =  20% do IBS sobre o consumo do período, se o período
 *                      é de 01/2029 em diante; senão 0,00
 * </pre>
 *
 * (LC 214/2025, arts. 112 a 118; vigências em {@link CashbackVigencia}.)
 *
 * <p>Os valores de CBS/IBS entram como <b>entradas já resolvidas</b> pelo
 * chamador a partir da tabela de alíquotas com vigência da P2.4 (V4_5) e
 * dos itens da conta: esta classe <b>não conhece alíquota nenhuma</b> e
 * não duplica a tabela da P2.4. Valor ausente, negativo ou com mais de 2
 * casas é erro explícito — nunca zero presumido.
 *
 * <p>Arredondamento: o percentual é aplicado com
 * {@link Money#percentOf} em {@link RoundingMode#HALF_UP} (o modo dos
 * valores de tarifa do módulo). A parcela de CBS a 100% é exata (sem
 * arredondamento além da normalização de escala).
 *
 * <p>A unicidade da devolução (uma por domicílio no período) <b>não</b> é
 * desta classe pura: é garantida pelo {@link CashbackService} + constraint
 * {@code uq_cashback_return_domicile_period} da V4_12.
 */
public final class CashbackCalculator {

    /** Resultado do cálculo de um período (valores em reais, escala 2). */
    public record Calculo(BigDecimal cbsDevolvida, BigDecimal ibsDevolvida, BigDecimal totalDevolvido,
                          boolean cbsNaVigencia, boolean ibsNaVigencia) {
        public Calculo {
            Objects.requireNonNull(cbsDevolvida, "cbsDevolvida é obrigatória");
            Objects.requireNonNull(ibsDevolvida, "ibsDevolvida é obrigatória");
            Objects.requireNonNull(totalDevolvido, "totalDevolvido é obrigatório");
        }

        /** Há algo a creditar na fatura neste período? */
        public boolean temDevolucao() {
            return totalDevolvido.signum() > 0;
        }
    }

    private CashbackCalculator() {
    }

    /**
     * @param periodoAfericao período de aferição do domicílio (mês)
     * @param cbsSobreConsumo CBS cobrada sobre o consumo no período (resolvida da P2.4)
     * @param ibsSobreConsumo IBS cobrado sobre o consumo no período (resolvido da P2.4)
     */
    public static Calculo calcular(YearMonth periodoAfericao, BigDecimal cbsSobreConsumo,
                                   BigDecimal ibsSobreConsumo) {
        Objects.requireNonNull(periodoAfericao, "Período de aferição é obrigatório");
        BigDecimal cbs = requireMoney("CBS sobre o consumo", cbsSobreConsumo);
        BigDecimal ibs = requireMoney("IBS sobre o consumo", ibsSobreConsumo);
        boolean cbsVigente = CashbackVigencia.cbsNaVigencia(periodoAfericao);
        boolean ibsVigente = CashbackVigencia.ibsNaVigencia(periodoAfericao);
        BigDecimal cbsDevolvida = cbsVigente
                ? Money.percentOf(cbs, CashbackVigencia.CBS_RETURN_PCT, RoundingMode.HALF_UP)
                : Money.ZERO;
        BigDecimal ibsDevolvida = ibsVigente
                ? Money.percentOf(ibs, CashbackVigencia.IBS_RETURN_PCT, RoundingMode.HALF_UP)
                : Money.ZERO;
        return new Calculo(cbsDevolvida, ibsDevolvida, cbsDevolvida.add(ibsDevolvida),
                cbsVigente, ibsVigente);
    }

    private static BigDecimal requireMoney(String nome, BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException(nome + " é obrigatório: ausência é erro, nunca zero presumido");
        }
        if (valor.signum() < 0) {
            throw new IllegalArgumentException(nome + " não pode ser negativo: " + valor);
        }
        try {
            return valor.setScale(Money.SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(nome + " deve ter até 2 casas decimais: " + valor);
        }
    }
}
