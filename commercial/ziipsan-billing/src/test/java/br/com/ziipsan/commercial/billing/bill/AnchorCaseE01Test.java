package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.tariff.SewerFactorPolicy;
import br.com.ziipsan.commercial.billing.tariff.SewerParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.res;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1Schedule;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caso-âncora da spec da P2.5, conferido de ponta a ponta pelo {@link BillCalculator}:
 * leitura anterior 1000, leitura atual 1027 (27 m³), ocorrência E-01 do relatório 02
 * (pcesgoto 80%, pccoleta 100%) resulta em 201,60 com a tarifa FICTÍCIA T1.
 *
 * <p>Recalculado à mão: água RES 1 economia, 27 m³ = mínima 30,00 + 10 m³ x 4,00 (até 20)
 * + 7 m³ x 6,00 (até 50) = 30,00 + 40,00 + 42,00 = 112,00. Esgoto: volume 27 x 100% = 27;
 * valor 112,00 x 0,80 = 89,60. Total = 112,00 + 89,60 = 201,60.
 *
 * <p>Esta frente (F4) não altera o {@code BillCalculator}: o teste existe para travar o
 * caso-âncora contra qualquer regressão das regras novas de período parcial/média — o
 * período usado é um ciclo regular de 30 dias (2026-09-05 a 2026-10-04), dentro da faixa
 * de 27–33 dias, portanto sem ajuste de período parcial.
 */
class AnchorCaseE01Test {

    @Test
    @DisplayName("âncora P2.5: 1000 -> 1027 (27 m³) com E-01 resulta em 201,60 (água 112,00 + esgoto 89,60)")
    void anchorCaseRemainsGreen() {
        int previousReading = 1000;
        int currentReading = 1027;
        int consumption = currentReading - previousReading;
        assertThat(consumption).isEqualTo(27);

        SewerParameters sewer = new SewerParameters(d("80"), d("100"), null,
                SewerFactorPolicy.EXACT, false);
        BillInput input = new BillInput(t1Schedule(), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 4),
                List.of(res(1)), consumption, new BillInput.SewerBilling(consumption, sewer),
                List.of(), List.of(), null);

        BillResult result = BillCalculator.calculate(input);
        assertThat(result.waterValue()).isEqualByComparingTo("112.00");
        assertThat(result.sewerValue()).isEqualByComparingTo("89.60");
        assertThat(result.total()).isEqualByComparingTo("201.60");
    }
}
