package br.com.ziipsan.core.stationops;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Turbidez (OPE-02): intervalo maximo de 2 h por filtro com filtracao
 * rapida e percentil mensal. Valores de leitura sao massa sintetica;
 * o nivel do percentil e fixture declarada (95), nao norma.
 */
class TurbidityComplianceTest {

    private DiaryReading turb(String filter, String value, Instant at) {
        return new DiaryReading(DiaryReading.Kind.TURBIDITY, filter, new BigDecimal(value), "uT", at);
    }

    @Test
    void twoHourCadenceIsCompliantAndThreeHourGapIsNamed() {
        Instant base = Instant.parse("2026-03-10T00:00:00Z");
        List<DiaryReading> ok = List.of(
                turb("F1", "0.30", base),
                turb("F1", "0.35", base.plusSeconds(2 * 3600)),
                turb("F1", "0.32", base.plusSeconds(4 * 3600)));
        TurbidityCompliance.IntervalAssessment good = TurbidityCompliance.assessIntervals("F1", ok);
        assertThat(good.compliant()).isTrue();
        assertThat(good.readings()).isEqualTo(3);

        List<DiaryReading> bad = List.of(
                turb("F1", "0.30", base),
                turb("F1", "0.90", base.plusSeconds(3 * 3600)));
        TurbidityCompliance.IntervalAssessment violated = TurbidityCompliance.assessIntervals("F1", bad);
        assertThat(violated.compliant()).isFalse();
        assertThat(violated.gaps()).hasSize(1);
        assertThat(violated.gaps().get(0).gapMinutes()).isEqualTo(180);
        // Leitura de OUTRO filtro nao entra na conta deste filtro.
        assertThat(TurbidityCompliance.assessIntervals("F2", bad).readings()).isEqualTo(0);
    }

    @Test
    void singleReadingInADayWindowIsNotCompliant() {
        // Correcao ED-87/D2: 1 leitura isolada no dia NAO e conforme —
        // as bordas da janela (inicio -> leitura e leitura -> fim)
        // sao lacunas nomeadas maiores que o teto de 2 h.
        Instant dayStart = Instant.parse("2026-03-10T00:00:00Z");
        Instant dayEnd = Instant.parse("2026-03-11T00:00:00Z");
        List<DiaryReading> single = List.of(turb("F1", "0.40", Instant.parse("2026-03-10T10:00:00Z")));
        TurbidityCompliance.IntervalAssessment a =
                TurbidityCompliance.assessWindow("F1", single, dayStart, dayEnd);
        assertThat(a.compliant()).isFalse();
        assertThat(a.readings()).isEqualTo(1);
        assertThat(a.gaps()).hasSize(2);
        assertThat(a.gaps().get(0).from()).isEqualTo(dayStart);
        assertThat(a.gaps().get(0).gapMinutes()).isEqualTo(600);
        assertThat(a.gaps().get(1).to()).isEqualTo(dayEnd);
        assertThat(a.gaps().get(1).gapMinutes()).isEqualTo(840);
        // E nem pelo recorte de intervalos a leitura isolada se declara
        // conforme: cadencia exige ao menos um intervalo verificado.
        assertThat(TurbidityCompliance.assessIntervals("F1", single).compliant()).isFalse();
        // Janela sem leitura alguma: nao conforme, lacuna da janela inteira.
        TurbidityCompliance.IntervalAssessment empty =
                TurbidityCompliance.assessWindow("F1", List.of(), dayStart, dayEnd);
        assertThat(empty.compliant()).isFalse();
        assertThat(empty.gaps()).hasSize(1);
        assertThat(empty.gaps().get(0).gapMinutes()).isEqualTo(1440);
    }

    @Test
    void windowEdgeGapOverTwoHoursIsNotCompliant() {
        // Correcao ED-87/D2: intervalos internos perfeitos nao salvam
        // uma borda descoberta por mais de 2 h.
        Instant start = Instant.parse("2026-03-10T00:00:00Z");
        Instant end = Instant.parse("2026-03-10T08:00:00Z");
        // Borda inicial: primeira leitura so as 03:00 (interno 03:00->05:00 = 120, ok).
        TurbidityCompliance.IntervalAssessment leading = TurbidityCompliance.assessWindow("F1",
                List.of(turb("F1", "0.30", Instant.parse("2026-03-10T03:00:00Z")),
                        turb("F1", "0.32", Instant.parse("2026-03-10T05:00:00Z")),
                        turb("F1", "0.31", Instant.parse("2026-03-10T07:00:00Z"))),
                start, end);
        assertThat(leading.compliant()).isFalse();
        assertThat(leading.gaps()).hasSize(1);
        assertThat(leading.gaps().get(0).from()).isEqualTo(start);
        assertThat(leading.gaps().get(0).gapMinutes()).isEqualTo(180);
        // Borda final: leituras cessam as 02:30 e a janela vai ate as 08:00.
        TurbidityCompliance.IntervalAssessment trailing = TurbidityCompliance.assessWindow("F1",
                List.of(turb("F1", "0.30", Instant.parse("2026-03-10T00:30:00Z")),
                        turb("F1", "0.32", Instant.parse("2026-03-10T02:30:00Z"))),
                start, end);
        assertThat(trailing.compliant()).isFalse();
        assertThat(trailing.gaps()).hasSize(1);
        assertThat(trailing.gaps().get(0).to()).isEqualTo(end);
        assertThat(trailing.gaps().get(0).gapMinutes()).isEqualTo(330);
        // Controle positivo: janela coberta de 2 h em 2 h, bordas incluidas, e conforme.
        TurbidityCompliance.IntervalAssessment covered = TurbidityCompliance.assessWindow("F1",
                List.of(turb("F1", "0.30", start),
                        turb("F1", "0.32", Instant.parse("2026-03-10T02:00:00Z")),
                        turb("F1", "0.31", Instant.parse("2026-03-10T04:00:00Z")),
                        turb("F1", "0.33", Instant.parse("2026-03-10T06:00:00Z"))),
                start, Instant.parse("2026-03-10T06:00:00Z"));
        assertThat(covered.compliant()).isTrue();
        assertThat(covered.gaps()).isEmpty();
    }

    @Test
    void monthlyPercentileUsesNearestRankOverTheMonthOnly() {
        Instant base = Instant.parse("2026-03-05T00:00:00Z");
        // 10 leituras em marco: 0.1 .. 1.0; percentil 95 (fixture) -> posto
        // ceil(0.95*10)=10 -> maior valor, 1.0.
        List<DiaryReading> readings = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            readings.add(turb("F1", "0." + i, base.plusSeconds(i * 3600L)));
        }
        readings.set(9, turb("F1", "1.0", base.plusSeconds(10 * 3600L)));
        // Leitura de ABRIL nao entra no percentil de marco.
        readings.add(turb("F1", "9.9", Instant.parse("2026-04-01T00:00:00Z")));
        BigDecimal p = TurbidityCompliance.monthlyPercentile(
                readings, "F1", YearMonth.of(2026, 3), ZoneOffset.UTC, 95);
        assertThat(p).isEqualByComparingTo("1.0");
        // Mes sem leitura: null declarado, nunca zero.
        assertThat(TurbidityCompliance.monthlyPercentile(
                readings, "F1", YearMonth.of(2026, 5), ZoneOffset.UTC, 95)).isNull();
    }
}
