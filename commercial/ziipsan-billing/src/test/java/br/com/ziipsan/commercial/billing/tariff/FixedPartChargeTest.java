package br.com.ziipsan.commercial.billing.tariff;

import br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAS-22 (P2.5/F5): TFDI de {@code tariff.fixed_part_model} +
 * {@code bill.cut_connection_charge}. Tarifa fictícia T1: RES tem parcela
 * fixa de 30,00 por economia — valores conferidos à mão.
 */
class FixedPartChargeTest {

    @Test
    @DisplayName("unidade NÃO conectada paga SÓ a TFDI: parcela fixa 30,00 × 3 economias = 90,00, sem consumo nem faixas")
    void disconnectedUnitPaysOnlyFixedPart() {
        FixedPartCharge charge = FixedPartCharge.calculate(false,
                FixedPartModel.MINIMUM_CHARGE_PER_ECONOMY, true, FictitiousTariffs.t1Res(), 3);
        assertThat(charge.charged()).isTrue();
        assertThat(charge.amount()).isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("unidade não conectada com bill.cut_connection_charge desligado: sem TFDI")
    void disconnectedUnitWithoutCutChargePaysNothing() {
        FixedPartCharge charge = FixedPartCharge.calculate(false,
                FixedPartModel.MINIMUM_CHARGE_PER_ECONOMY, false, FictitiousTariffs.t1Res(), 3);
        assertThat(charge.charged()).isFalse();
        assertThat(charge.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("modelo NONE: sem componente fixo avulso, mesmo com cut ligado")
    void modelNoneChargesNothing() {
        FixedPartCharge charge = FixedPartCharge.calculate(false,
                FixedPartModel.NONE, true, FictitiousTariffs.t1Res(), 3);
        assertThat(charge.charged()).isFalse();
        assertThat(charge.amount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("unidade conectada: TFDI avulsa não se aplica (a parte fixa já está na tarifa normal)")
    void connectedUnitHasNoSeparateFixedPart() {
        FixedPartCharge charge = FixedPartCharge.calculate(true,
                FixedPartModel.MINIMUM_CHARGE_PER_ECONOMY, true, FictitiousTariffs.t1Res(), 3);
        assertThat(charge.charged()).isFalse();
        assertThat(charge.amount()).isEqualByComparingTo("0.00");
    }
}
