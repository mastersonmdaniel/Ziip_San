package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.reading.FalseRangeReport.Entry;
import br.com.ziipsan.operations.metering.reading.ReaderProductivityReport.DailyFact;
import br.com.ziipsan.operations.metering.reading.ResamplingService.ResampleOrder;
import br.com.ziipsan.operations.metering.rules.ExpectedReadingRange;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResamplingAndReportsTest {

    private static List<UUID> units(int n) {
        return IntStream.range(0, n)
                .mapToObj(i -> UUID.fromString(String.format("00000000-0000-0000-0000-%012d", i + 1)))
                .toList();
    }

    // ---- Releitura amostral -------------------------------------------------

    @Test
    @DisplayName("releitura amostral: 10% de 20 leituras gera 2 OS seletivas, determinísticas")
    void resampleTenPercentOfTwenty() {
        List<UUID> read = units(20);
        List<ResampleOrder> orders = ResamplingService.select(read, new BigDecimal("10"), "OS-RELEITURA");
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(ResampleOrder::unitId)
                .containsExactly(read.get(0), read.get(10));
        assertThat(orders).allSatisfy(o -> assertThat(o.serviceOrderTypeCode()).isEqualTo("OS-RELEITURA"));
        // determinístico: repetir devolve a mesma amostra
        assertThat(ResamplingService.select(read, new BigDecimal("10"), "OS-RELEITURA")).isEqualTo(orders);
    }

    @Test
    @DisplayName("releitura amostral: pct 0 não amostra nada; pct 100 amostra todas; percentual positivo amostra ao menos 1")
    void resampleEdges() {
        List<UUID> read = units(7);
        assertThat(ResamplingService.select(read, BigDecimal.ZERO, "OS-RELEITURA")).isEmpty();
        assertThat(ResamplingService.select(read, new BigDecimal("100"), "OS-RELEITURA")).hasSize(7);
        assertThat(ResamplingService.select(read, new BigDecimal("1"), "OS-RELEITURA")).hasSize(1);
    }

    @Test
    @DisplayName("releitura amostral com unidade duplicada na lista é recusada (OS repetida)")
    void resampleDuplicateUnitRejected() {
        List<UUID> read = units(3);
        assertThatThrownBy(() -> ResamplingService.select(
                List.of(read.get(0), read.get(0), read.get(1)), new BigDecimal("50"), "OS-RELEITURA"))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("releitura amostral sem tipo de OS (catálogo P1.31) é recusada")
    void resampleWithoutServiceOrderTypeRejected() {
        assertThatThrownBy(() -> ResamplingService.select(units(3), new BigDecimal("10"), " "))
                .isInstanceOf(MeteringInputException.class);
    }

    // ---- Relatório de faixa falsa -------------------------------------------

    private static final UUID READER_1 = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID READER_2 = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final ExpectedReadingRange RANGE = new ExpectedReadingRange(1000, 1100, 5);

    @Test
    @DisplayName("relatório de faixa falsa lista só as fora da faixa e totaliza por leiturista")
    void falseRangeListsOnlyOutOfRange() {
        UUID u1 = units(1).get(0);
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID u3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        FalseRangeReport.Report report = FalseRangeReport.build(List.of(
                new Entry(u1, READER_1, 1050, RANGE),   // dentro
                new Entry(u2, READER_1, 1300, RANGE),   // falsa
                new Entry(u3, READER_2, 900, RANGE)));  // falsa
        assertThat(report.total()).isEqualTo(2);
        assertThat(report.falseRangeEntries()).extracting(Entry::unitId).containsExactly(u2, u3);
        assertThat(report.countByReader()).containsEntry(READER_1, 1L).containsEntry(READER_2, 1L);
    }

    // ---- Impedimento e produtividade ----------------------------------------

    private static final UUID ROUTE_1 = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID ROUTE_2 = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 9);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 11, 10);

    @Test
    @DisplayName("taxa de impedimento por leiturista e por rota, e produtividade por dia")
    void impedimentRatesAndProductivity() {
        ReaderProductivityReport.Report report = ReaderProductivityReport.build(List.of(
                // leiturista 1, rota 1, dia 1: 18 lidas, 2 impedidas -> 10%
                new DailyFact(READER_1, ROUTE_1, DAY_1, 22, 18, 2),
                // leiturista 1, rota 2, dia 2: 10 lidas, 0 impedidas
                new DailyFact(READER_1, ROUTE_2, DAY_2, 10, 10, 0),
                // leiturista 2, rota 1, dia 1: 6 lidas, 4 impedidas -> rota 1 total: 6/30 = 20%
                new DailyFact(READER_2, ROUTE_1, DAY_1, 12, 6, 4)));
        // por leiturista: L1 = 2/30 = 6,67%; L2 = 4/10 = 40,00%
        assertThat(report.impedimentRatePctByReader().get(READER_1)).isEqualByComparingTo("6.67");
        assertThat(report.impedimentRatePctByReader().get(READER_2)).isEqualByComparingTo("40.00");
        // por rota: R1 = 6/30 = 20,00%; R2 = 0/10 = 0,00%
        assertThat(report.impedimentRatePctByRoute().get(ROUTE_1)).isEqualByComparingTo("20.00");
        assertThat(report.impedimentRatePctByRoute().get(ROUTE_2)).isEqualByComparingTo("0.00");
        // produtividade por dia: dia 1 = 18+6 = 24; dia 2 = 10
        assertThat(report.readingsByDay()).containsEntry(DAY_1, 24L).containsEntry(DAY_2, 10L);
        assertThat(report.readingsByReaderAndDay().get(READER_1)).containsEntry(DAY_1, 18L)
                .containsEntry(DAY_2, 10L);
    }

    @Test
    @DisplayName("fato diário incoerente (planejado menor que lidas + impedidas) é recusado")
    void incoherentDailyFactRejected() {
        assertThatThrownBy(() -> new DailyFact(READER_1, ROUTE_1, DAY_1, 5, 4, 2))
                .isInstanceOf(MeteringInputException.class);
    }
}
