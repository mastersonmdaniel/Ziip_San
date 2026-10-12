package br.com.sigsan.operations.metering.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consumo médio (relatório 02, 3.1; casos A-01 e A-02). Valores FICTÍCIOS, recalculados à mão.
 * Regra de arredondamento sob teste: a média é o TRUNCAMENTO (DOWN) de soma / meses considerados.
 */
class AverageConsumptionTest {

    private static final YearMonth REF = YearMonth.of(2026, 10);

    private static HistoryEntry real(int back, int consumption) {
        return new HistoryEntry(REF.minusMonths(back), consumption, ConsumptionType.REAL, null);
    }

    @Test
    @DisplayName("A-01: 20+18+22+25+19+21 = 125; 125/6 = 20,83 -> trunca para 20 (HALF_UP daria 21)")
    void a01_truncatesTheAverage() {
        // set, ago, jul, jun, mai, abr
        List<HistoryEntry> history = List.of(real(1, 20), real(2, 18), real(3, 22), real(4, 25), real(5, 19),
                real(6, 21));
        AverageResult result = AverageConsumption.compute(REF, history, Fixtures.averagePolicy(), 10);
        assertThat(result.average()).isEqualTo(20);
        assertThat(result.monthsConsidered()).isEqualTo(6);
        assertThat(result.source()).isEqualTo(AverageResult.Source.HISTORY);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("A-02: sem histórico, a média é o consumo mínimo da ligação (1 mês) e há aviso")
    void a02_noHistoryFallsBackToTheConnectionMinimumWithAWarning() {
        AverageResult result = AverageConsumption.compute(REF, List.of(), Fixtures.averagePolicy(), 10);
        assertThat(result.average()).isEqualTo(10);
        assertThat(result.monthsConsidered()).isEqualTo(1);
        assertThat(result.source()).isEqualTo(AverageResult.Source.CONNECTION_MINIMUM);
        assertThat(result.warnings()).extracting(CalculationWarning::code)
                .containsExactly(WarningCode.AVERAGE_FROM_CONNECTION_MINIMUM);
    }

    @Test
    @DisplayName("mês sem registro retroage: o mínimo de 6 meses é buscado mais atrás (até 12)")
    void gapsLookFurtherBack() {
        // Faltam jul (3) e fev (8). Elegíveis: set 20, ago 18, jun 25, mai 19, abr 21, mar 24 = 127 em 6 meses
        // (varre 7 meses: set..mar). 127/6 = 21,17 -> 21.
        List<HistoryEntry> history = List.of(real(1, 20), real(2, 18), real(4, 25), real(5, 19), real(6, 21),
                real(7, 24), real(9, 99));
        AverageResult result = AverageConsumption.compute(REF, history, Fixtures.averagePolicy(), 10);
        assertThat(result.average()).isEqualTo(21);
        assertThat(result.monthsConsidered()).isEqualTo(6);
    }

    @Test
    @DisplayName("o teto de retroação limita: com 6 meses de varredura e 2 buracos, a média usa só 4 meses")
    void lookbackLimitStopsTheScan() {
        AveragePolicy policy = new AveragePolicy(6, 6, EnumSet.allOf(ConsumptionType.class),
                EnumSet.allOf(ConsumptionAnomalyKind.class));
        // Varre set..abr (6 meses). Faltam jul e mai. Entram set 20, ago 18, jun 25, abr 21 = 84; 84/4 = 21.
        List<HistoryEntry> history = List.of(real(1, 20), real(2, 18), real(4, 25), real(6, 21), real(7, 500));
        AverageResult result = AverageConsumption.compute(REF, history, policy, 10);
        assertThat(result.average()).isEqualTo(21);
        assertThat(result.monthsConsidered()).isEqualTo(4);
    }

    @Test
    @DisplayName("tipo que não entra na média é ignorado e gasta a retroação")
    void ineligibleTypesAreSkipped() {
        AveragePolicy policy = new AveragePolicy(3, 6, Set.of(ConsumptionType.REAL),
                EnumSet.allOf(ConsumptionAnomalyKind.class));
        List<HistoryEntry> history = List.of(
                new HistoryEntry(REF.minusMonths(1), 99, ConsumptionType.ESTIMATED, null),
                real(2, 10), real(3, 20), real(4, 30), real(5, 40));
        // set (ESTIMATED) é ignorado; entram ago 10, jul 20, jun 30 = 60; 60/3 = 20.
        AverageResult result = AverageConsumption.compute(REF, history, policy, 10);
        assertThat(result.average()).isEqualTo(20);
        assertThat(result.monthsConsidered()).isEqualTo(3);
    }

    @Test
    @DisplayName("anormalidade que não entra na média exclui o mês; a que entra, não")
    void anomalyEligibility() {
        AveragePolicy policy = new AveragePolicy(2, 6, EnumSet.allOf(ConsumptionType.class),
                Set.of(ConsumptionAnomalyKind.MINIMUM_FIXED));
        List<HistoryEntry> history = List.of(
                new HistoryEntry(REF.minusMonths(1), 100, ConsumptionType.REAL, ConsumptionAnomalyKind.BURST),
                new HistoryEntry(REF.minusMonths(2), 12, ConsumptionType.REAL, ConsumptionAnomalyKind.MINIMUM_FIXED),
                real(3, 18), real(4, 500));
        // set (BURST) não entra; ago 12 (anomalia que entra) e jul 18: 30/2 = 15.
        AverageResult result = AverageConsumption.compute(REF, history, policy, 10);
        assertThat(result.average()).isEqualTo(15);
    }

    @Test
    @DisplayName("todo o histórico inelegível dentro do teto cai no mínimo da ligação, com aviso")
    void allIneligibleFallsBack() {
        AveragePolicy policy = new AveragePolicy(2, 3, Set.of(ConsumptionType.REAL),
                EnumSet.allOf(ConsumptionAnomalyKind.class));
        List<HistoryEntry> history = List.of(
                new HistoryEntry(REF.minusMonths(1), 50, ConsumptionType.ESTIMATED, null),
                real(4, 50)); // fora do teto de 3 meses
        AverageResult result = AverageConsumption.compute(REF, history, policy, 7);
        assertThat(result.average()).isEqualTo(7);
        assertThat(result.source()).isEqualTo(AverageResult.Source.CONNECTION_MINIMUM);
    }

    @Test
    @DisplayName("histórico incoerente é recusado, não corrigido")
    void invalidHistory() {
        assertThatThrownBy(() -> AverageConsumption.compute(REF, List.of(real(0, 10)), Fixtures.averagePolicy(), 10))
                .isInstanceOfSatisfying(MeteringInputException.class,
                        e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.HISTORY_NOT_BEFORE_REFERENCE));
        assertThatThrownBy(() -> AverageConsumption.compute(REF, List.of(real(1, 10), real(1, 12)),
                Fixtures.averagePolicy(), 10))
                .isInstanceOfSatisfying(MeteringInputException.class,
                        e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.DUPLICATE_HISTORY_MONTH));
        assertThatThrownBy(() -> new HistoryEntry(REF.minusMonths(1), -1, ConsumptionType.REAL, null))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("a política exige parâmetros válidos")
    void policyValidation() {
        Set<ConsumptionType> types = EnumSet.allOf(ConsumptionType.class);
        Set<ConsumptionAnomalyKind> kinds = EnumSet.allOf(ConsumptionAnomalyKind.class);
        assertThatThrownBy(() -> new AveragePolicy(0, 6, types, kinds)).isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new AveragePolicy(6, 5, types, kinds)).isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new AveragePolicy(6, 12, null, kinds)).isInstanceOf(MeteringInputException.class);
    }
}
