package br.com.ziipsan.operations.metering.rules;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Média e teto de ciclos estimados lidos dos parâmetros do tenant (RAS-14, P2.5), testados
 * com os valores convergentes das ERIs de SC: {@code meter.average_months} = 6,
 * {@code meter.max_estimated_cycles} = 3, e a regra posterior nas duas divergências que o
 * plano registra (ARIS/AGIR: mínimo; ARESC: saldo parcelado em 3 vezes).
 *
 * <p>Valores de histórico FICTÍCIOS, recalculados à mão: 20+18+22+25+19+21 = 125;
 * 125/6 = 20,83, truncado (regra da média, {@link AverageConsumption}) = 20.
 */
class MeteringParametersTest {

    private static final YearMonth REF = YearMonth.of(2026, 10);

    private static List<HistoryEntry> history125() {
        List<HistoryEntry> list = new java.util.ArrayList<>();
        int[] values = {20, 18, 22, 25, 19, 21};
        for (int i = 0; i < values.length; i++) {
            list.add(new HistoryEntry(REF.minusMonths(i + 1), values[i], ConsumptionType.REAL, null));
        }
        return list;
    }

    @Test
    @DisplayName("SC convergente: a política sai dos parâmetros com 6 meses e a média de 125/6 trunca para 20")
    void scConvergentAverageFromParameters() {
        MeteringParameters params = MeteringParameters.scConvergent(AfterCapRule.BILL_MINIMUM);
        assertThat(params.averageMonths()).isEqualTo(6);
        assertThat(params.maxEstimatedCycles()).isEqualTo(3);

        AveragePolicy policy = params.averagePolicy(12, EnumSet.allOf(ConsumptionType.class),
                EnumSet.allOf(ConsumptionAnomalyKind.class));
        assertThat(policy.months()).isEqualTo(6);

        AverageResult result = AverageConsumption.compute(REF, history125(), policy, 10);
        assertThat(result.average()).isEqualTo(20);
        assertThat(result.monthsConsidered()).isEqualTo(6);
        assertThat(result.source()).isEqualTo(AverageResult.Source.HISTORY);
    }

    @Test
    @DisplayName("valores do tenant em JSON (número ou texto) montam os mesmos parâmetros de SC")
    void fromTenantJsonValues() {
        MeteringParameters fromNumbers = MeteringParameters.fromTenantValues("6", "3", "\"BILL_MINIMUM\"");
        MeteringParameters fromTexts = MeteringParameters.fromTenantValues("\"6\"", "\"3\"",
                "\"SETTLE_BALANCE_IN_3_INSTALLMENTS\"");

        assertThat(fromNumbers).isEqualTo(MeteringParameters.scConvergent(AfterCapRule.BILL_MINIMUM));
        assertThat(fromTexts.averageMonths()).isEqualTo(6);
        assertThat(fromTexts.maxEstimatedCycles()).isEqualTo(3);
        assertThat(fromTexts.afterCapRule()).isEqualTo(AfterCapRule.SETTLE_BALANCE_IN_3_INSTALLMENTS);
    }

    @Test
    @DisplayName("parâmetro sem valor aceito é erro explícito que nomeia a chave (nunca assume 6/3 em silêncio)")
    void missingValueIsExplicit() {
        assertThatThrownBy(() -> MeteringParameters.fromTenantValues(null, "3", "\"BILL_MINIMUM\""))
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("meter.average_months");
        assertThatThrownBy(() -> MeteringParameters.fromTenantValues("6", " ", "\"BILL_MINIMUM\""))
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("meter.max_estimated_cycles");
        assertThatThrownBy(() -> MeteringParameters.fromTenantValues("6", "3", null))
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("meter.after_cap_rule");
    }

    @Test
    @DisplayName("limites do core (NR 11, art. 35): meses 0 e ciclos 0 são recusados; regra posterior fora do catálogo também")
    void coreLimitsAreEnforced() {
        assertThatThrownBy(() -> MeteringParameters.fromTenantValues("0", "3", "\"BILL_MINIMUM\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("meter.average_months");
        assertThatThrownBy(() -> MeteringParameters.fromTenantValues("6", "0", "\"BILL_MINIMUM\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("meter.max_estimated_cycles");
        assertThatThrownBy(() -> MeteringParameters.fromTenantValues("6", "3", "\"REGRA_INVENTADA\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("meter.after_cap_rule");
        assertThatThrownBy(() -> new MeteringParameters(6, 3, null))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("retroação menor que os meses da média é recusada pela AveragePolicy")
    void lookbackBelowMonthsIsRefused() {
        MeteringParameters params = MeteringParameters.scConvergent(AfterCapRule.BILL_MINIMUM);
        assertThatThrownBy(() -> params.averagePolicy(5, EnumSet.allOf(ConsumptionType.class),
                EnumSet.allOf(ConsumptionAnomalyKind.class)))
                .isInstanceOf(MeteringInputException.class)
                .hasMessageContaining("maxLookbackMonths");
    }

    @Test
    @DisplayName("teto SC = 3: os ciclos estimados 1, 2 e 3 saem pela média (usados 0, 1, 2 na entrada)")
    void withinTheCapEstimatesByAverage() {
        MeteringParameters params = MeteringParameters.scConvergent(AfterCapRule.BILL_MINIMUM);
        assertThat(params.decideWithoutReading(0)).isEqualTo(MeteringParameters.EstimatedDecision.ESTIMATE_BY_AVERAGE);
        assertThat(params.decideWithoutReading(1)).isEqualTo(MeteringParameters.EstimatedDecision.ESTIMATE_BY_AVERAGE);
        assertThat(params.decideWithoutReading(2)).isEqualTo(MeteringParameters.EstimatedDecision.ESTIMATE_BY_AVERAGE);
    }

    @Test
    @DisplayName("após o teto SC (usados = 3, o 4º ciclo sem leitura): ARIS/AGIR faturam o mínimo")
    void afterTheCapBillsMinimum() {
        MeteringParameters params = MeteringParameters.scConvergent(AfterCapRule.BILL_MINIMUM);
        assertThat(params.decideWithoutReading(3)).isEqualTo(MeteringParameters.EstimatedDecision.BILL_BY_MINIMUM);
        assertThat(params.decideWithoutReading(4)).isEqualTo(MeteringParameters.EstimatedDecision.BILL_BY_MINIMUM);
    }

    @Test
    @DisplayName("após o teto SC (usados = 3): ARESC sinaliza o saldo parcelado em 3 vezes, sem estimar de novo")
    void afterTheCapSettlesBalanceInThreeInstallments() {
        MeteringParameters params = MeteringParameters.scConvergent(
                AfterCapRule.SETTLE_BALANCE_IN_3_INSTALLMENTS);
        assertThat(params.decideWithoutReading(2)).isEqualTo(MeteringParameters.EstimatedDecision.ESTIMATE_BY_AVERAGE);
        assertThat(params.decideWithoutReading(3))
                .isEqualTo(MeteringParameters.EstimatedDecision.SETTLE_BALANCE_IN_3_INSTALLMENTS);
    }

    @Test
    @DisplayName("ciclos estimados já usados negativo é recusado")
    void negativeUsedCyclesIsRefused() {
        MeteringParameters params = MeteringParameters.scConvergent(AfterCapRule.BILL_MINIMUM);
        assertThatThrownBy(() -> params.decideWithoutReading(-1))
                .isInstanceOf(MeteringInputException.class);
    }
}
