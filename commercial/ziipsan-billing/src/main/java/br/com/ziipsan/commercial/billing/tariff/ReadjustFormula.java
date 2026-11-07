package br.com.ziipsan.commercial.billing.tariff;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fórmula do reajuste tarifário (parâmetro {@code tariff.readjust_formula},
 * classe d, aprovação REG; P2.29, NR 10 e NR 6 da ANA):
 * <ul>
 *   <li>{@link #INDEX_ONLY}: reajuste = índice acumulado desde a data-base
 *       (ex.: IPCA do período);</li>
 *   <li>{@link #INDEX_MINUS_FACTOR_X}: fórmula paramétrica, reajuste =
 *       índice − fator X (o fator de produtividade da NR 6, que a agência
 *       fixa por prestador).</li>
 * </ul>
 * Nenhuma outra fórmula é aceita: uma fórmula sem cálculo implementado e
 * testado seria aplicada errado em silêncio.
 */
public enum ReadjustFormula {

    INDEX_ONLY,
    INDEX_MINUS_FACTOR_X;

    /**
     * Percentual aplicado do reajuste, com 4 casas (a mesma precisão dos
     * percentuais gravados em {@code tariff_versions}, NUMERIC(9,4)).
     *
     * @param indexPct  índice do período desde a data-base, em %; obrigatório,
     *                  maior que −100 (deflação é admitida; −100 ou menos
     *                  zeraria a tarifa)
     * @param factorXPct fator X em %; obrigatório e ≥ 0 na fórmula paramétrica,
     *                  e deve ser nulo ou zero em {@link #INDEX_ONLY} — fator X
     *                  informado com fórmula por índice é incoerência, não é
     *                  ignorado em silêncio
     */
    public BigDecimal appliedPct(BigDecimal indexPct, BigDecimal factorXPct) {
        if (indexPct == null || indexPct.compareTo(new BigDecimal("-100")) <= 0) {
            throw new TariffConfigurationException(
                    "Índice do reajuste deve ser informado e maior que -100%: " + indexPct);
        }
        return switch (this) {
            case INDEX_ONLY -> {
                if (factorXPct != null && factorXPct.signum() != 0) {
                    throw new TariffConfigurationException(
                            "Fator X informado (" + factorXPct + ") com a fórmula INDEX_ONLY: "
                                    + "ou a fórmula é paramétrica, ou o fator X não entra no reajuste");
                }
                yield indexPct.setScale(4, RoundingMode.HALF_UP);
            }
            case INDEX_MINUS_FACTOR_X -> {
                if (factorXPct == null || factorXPct.signum() < 0) {
                    throw new TariffConfigurationException(
                            "A fórmula paramétrica exige o fator X informado e não negativo: " + factorXPct);
                }
                yield indexPct.subtract(factorXPct).setScale(4, RoundingMode.HALF_UP);
            }
        };
    }
}
