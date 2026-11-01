package br.com.ziipsan.commercial.billing.tariff;

import java.math.BigDecimal;

/**
 * Resultado da cobrança de esgoto.
 *
 * @param baseVolume    volume de água (ou do poço) de que o esgoto parte
 * @param volume        volume de esgoto faturado: {@code baseVolume × pccoleta}, arredondado ao m³ (HALF_UP)
 * @param effectivePct  percentual de esgoto efetivamente usado (o alternativo, se valeu)
 * @param factor        fator multiplicador do valor
 */
public record SewerCharge(int baseVolume, int volume, BigDecimal effectivePct, BigDecimal factor,
                          TariffCharge charge) {

    public BigDecimal net() {
        return charge.net();
    }
}
