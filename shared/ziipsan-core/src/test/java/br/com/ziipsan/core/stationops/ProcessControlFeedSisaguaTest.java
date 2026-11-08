package br.com.ziipsan.core.stationops;

import br.com.ziipsan.core.quality.MonthlyClosing;
import br.com.ziipsan.core.quality.NormativeCatalog;
import br.com.ziipsan.core.quality.SamplingPlan;
import br.com.ziipsan.core.quality.SisaguaExport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JOR-21 (teste exigido pelo aceite): o arquivo SISAGUA gerado pela
 * ED-50 a partir das entradas convertidas do diario e comparado,
 * linha a linha, com os lancamentos do diario — sem redigitacao, o
 * arquivo carrega exatamente os valores lancados (turbidez por
 * filtro, cloro e pH na saida), nem um a mais, nem um a menos.
 */
class ProcessControlFeedSisaguaTest {

    @Test
    void sisaguaFileMatchesDiaryEntriesExactly() {
        StationUnit eta = TestSupport.etaRapida();
        ShiftLog log = new ShiftLog(UUID.randomUUID(), eta, LocalDate.of(2026, 3, 10),
                "DIA", "operador-1", TestSupport.T0);
        // Lancamentos do diario (massa sintetica): processo + uma leitura
        // operacional que NAO e de processo (LEVEL nao vai ao SISAGUA).
        log.addReading(new DiaryReading(DiaryReading.Kind.TURBIDITY, "F1",
                new BigDecimal("0.40"), "uT", TestSupport.T0));
        log.addReading(new DiaryReading(DiaryReading.Kind.TURBIDITY, "F1",
                new BigDecimal("0.55"), "uT", TestSupport.T0.plusSeconds(2 * 3600)));
        log.addReading(new DiaryReading(DiaryReading.Kind.TURBIDITY, "F2",
                new BigDecimal("0.61"), "uT", TestSupport.T0.plusSeconds(3600)));
        log.addReading(new DiaryReading(DiaryReading.Kind.CHLORINE, null,
                new BigDecimal("0.80"), "mg/L", TestSupport.T0.plusSeconds(1800)));
        log.addReading(new DiaryReading(DiaryReading.Kind.PH, null,
                new BigDecimal("7.10"), "pH", TestSupport.T0.plusSeconds(1800)));
        log.addReading(new DiaryReading(DiaryReading.Kind.LEVEL, null,
                new BigDecimal("3.20"), "m", TestSupport.T0));

        UUID pointId = UUID.randomUUID();
        // Catalogo vazio da ED-50: veredito INDETERMINATE (nunca presumido).
        NormativeCatalog catalog = NormativeCatalog.empty("CAT-TESTE");
        List<MonthlyClosing.Entry> entries = ProcessControlFeed.toEntries(log, pointId, catalog, "validador-1");
        assertThat(entries).hasSize(5);

        SamplingPlan plan = new SamplingPlan(UUID.randomUUID(), "SISTEMA-A",
                YearMonth.of(2026, 3), YearMonth.of(2026, 3), "CAT-TESTE",
                List.of(new SamplingPlan.PlanLine(pointId, "TURBIDITY", 3),
                        new SamplingPlan.PlanLine(pointId, "CHLORINE", 1),
                        new SamplingPlan.PlanLine(pointId, "PH", 1)));
        List<br.com.ziipsan.core.quality.Sample> samples = entries.stream().map(MonthlyClosing.Entry::sample).toList();
        MonthlyClosing closing = MonthlyClosing.assemble(UUID.randomUUID(), "SISTEMA-A",
                YearMonth.of(2026, 3), plan, samples, entries);
        closing.close("responsavel-tecnico-1", TestSupport.T0.plusSeconds(24 * 3600));

        SisaguaExport.SisaguaFile file = SisaguaExport.generate(closing, entries, "LEIAUTE-TESTE");
        assertThat(file.rowCount()).isEqualTo(5);

        // Comparacao com o DIARIO: cada linha do arquivo corresponde a um
        // lancamento de processo do diario (parametro;valor;etiqueta).
        List<String> fileFacts = file.content().lines()
                .filter(l -> !l.startsWith("#"))
                .map(l -> {
                    String[] c = l.split(";");
                    return c[4] + ";" + c[5] + ";" + c[3];
                })
                .sorted()
                .toList();
        List<String> diaryFacts = log.processReadings().stream()
                .map(r -> r.parameterKey() + ";" + r.value().toPlainString() + ";"
                        + ProcessControlFeed.labelFor(log, r))
                .sorted()
                .toList();
        assertThat(fileFacts).isEqualTo(diaryFacts);
        // E a conformidade mensal da ED-50 contou os mesmos lancamentos:
        // TURBIDITY analisado = 3, todos indeterminados sem catalogo revisado.
        MonthlyClosing.ParameterLine turb = closing.lines().stream()
                .filter(l -> l.parameterKey().equals("TURBIDITY")).findFirst().orElseThrow();
        assertThat(turb.analyzed()).isEqualTo(3);
        assertThat(turb.indeterminate()).isEqualTo(3);
        assertThat(turb.conforming()).isEqualTo(0);
    }

    @Test
    void nonProcessReadingDoesNotFeedQuality() {
        StationUnit eta = TestSupport.etaRapida();
        ShiftLog log = new ShiftLog(UUID.randomUUID(), eta, LocalDate.of(2026, 3, 10),
                "DIA", "operador-1", TestSupport.T0);
        DiaryReading level = new DiaryReading(DiaryReading.Kind.LEVEL, null,
                new BigDecimal("3.20"), "m", TestSupport.T0);
        log.addReading(level);
        assertThatThrownBy(() -> ProcessControlFeed.toEntry(log, level, UUID.randomUUID(),
                NormativeCatalog.empty("CAT-TESTE"), "validador-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ProcessControlFeed.toEntries(log, UUID.randomUUID(),
                NormativeCatalog.empty("CAT-TESTE"), "validador-1")).isEmpty();
    }
}
