package br.com.ziipsan.core.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes puros do cálculo de SLA (P2.15): horas úteis, feriado municipal,
 * 24x7, pausa e matriz impacto × criticidade. Sem banco e sem relógio.
 */
class SlaDeadlineCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private static ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE);
    }

    @Test
    @DisplayName("horas uteis: prazo dentro do dia nao cruza a janela")
    void businessHoursWithinDay() {
        // Segunda-feira 10h + 2h uteis = 12h do mesmo dia.
        assertThat(SlaDeadlineCalculator.addHours(at(2026, 11, 2, 10, 0), 2,
                SlaDeadlineCalculator.Calendar.BUSINESS, Set.of(), 8, 18))
                .isEqualTo(at(2026, 11, 2, 12, 0));
    }

    @Test
    @DisplayName("horas uteis: prazo cruza o fim do dia e continua no dia util seguinte")
    void businessHoursCrossDayEnd() {
        // Sexta 17h + 2h uteis (8–18): 1h na sexta + 1h na segunda 8–9h.
        assertThat(SlaDeadlineCalculator.addHours(at(2026, 11, 6, 17, 0), 2,
                SlaDeadlineCalculator.Calendar.BUSINESS, Set.of(), 8, 18))
                .isEqualTo(at(2026, 11, 9, 9, 0));
    }

    @Test
    @DisplayName("horas uteis: abertura fora da janela comeca na proxima abertura")
    void businessHoursOpenedOutsideWindow() {
        // Sexta 19h + 1h util = segunda 9h (fim de semana nao conta).
        assertThat(SlaDeadlineCalculator.addHours(at(2026, 11, 6, 19, 0), 1,
                SlaDeadlineCalculator.Calendar.BUSINESS, Set.of(), 8, 18))
                .isEqualTo(at(2026, 11, 9, 9, 0));
    }

    @Test
    @DisplayName("feriado municipal: o dia do feriado nao conta, mesmo caindo em dia de semana")
    void municipalHolidaySkipped() {
        // Terca e feriado municipal; segunda 10h + 10h uteis: seg 10–18 (8h)
        // + quarta 8–10h (2h). A terca some do calculo.
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 11, 3));
        assertThat(SlaDeadlineCalculator.addHours(at(2026, 11, 2, 10, 0), 10,
                SlaDeadlineCalculator.Calendar.BUSINESS, holidays, 8, 18))
                .isEqualTo(at(2026, 11, 4, 10, 0));
    }

    @Test
    @DisplayName("feriado municipal: Videira S1 (2h) aberta na vespera do feriado")
    void videiraS1BeforeMunicipalHoliday() {
        // Segunda 17h + 2h uteis, terca feriado municipal: 1h na segunda
        // + 1h na quarta 8–9h.
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 11, 3));
        assertThat(SlaDeadlineCalculator.addHours(at(2026, 11, 2, 17, 0), 2,
                SlaDeadlineCalculator.Calendar.BUSINESS, holidays, 8, 18))
                .isEqualTo(at(2026, 11, 4, 9, 0));
    }

    @Test
    @DisplayName("24x7: prazo corre no fim de semana e no feriado, em horas de relogio")
    void roundTheClockIgnoresWeekendsAndHolidays() {
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 11, 7));
        assertThat(SlaDeadlineCalculator.addHours(at(2026, 11, 6, 20, 0), 12,
                SlaDeadlineCalculator.Calendar.ROUND_THE_CLOCK, holidays, 8, 18))
                .isEqualTo(at(2026, 11, 7, 8, 0));
    }

    @Test
    @DisplayName("SAMAE S5: 160h uteis a partir de segunda 8h")
    void samaeS5Resolution160BusinessHours() {
        // 160h uteis = 16 dias uteis de 10h (8–18), contando a segunda 02/11:
        // 02, 03, 04, 05, 06, 09, 10, 11, 12, 13, 16, 17, 18, 19, 20 e 23/11
        // — o prazo cai na segunda 23/11 as 18h.
        assertThat(SlaDeadlineCalculator.addHours(at(2026, 11, 2, 8, 0), 160,
                SlaDeadlineCalculator.Calendar.BUSINESS, Set.of(), 8, 18))
                .isEqualTo(at(2026, 11, 23, 18, 0));
    }

    @Test
    @DisplayName("pausa em horas uteis: 2h de pausa custam 2h uteis ao prazo")
    void pauseBusinessMinutes() {
        assertThat(SlaDeadlineCalculator.businessMinutesBetween(
                at(2026, 11, 2, 10, 0), at(2026, 11, 2, 12, 0), Set.of(), 8, 18))
                .isEqualTo(120);
    }

    @Test
    @DisplayName("pausa no fim de semana: quase nao custa minuto util ao prazo")
    void pauseOverWeekendCostsAlmostNothing() {
        // Sexta 17h ate segunda 9h: 1h na sexta + 1h na segunda = 120 min.
        assertThat(SlaDeadlineCalculator.businessMinutesBetween(
                at(2026, 11, 6, 17, 0), at(2026, 11, 9, 9, 0), Set.of(), 8, 18))
                .isEqualTo(120);
    }

    @Test
    @DisplayName("pausa com feriado municipal no meio nao conta o feriado")
    void pauseWithMunicipalHolidayInside() {
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 11, 3));
        // Segunda 16h ate quarta 10h: 2h na segunda + 2h na quarta = 240 min.
        assertThat(SlaDeadlineCalculator.businessMinutesBetween(
                at(2026, 11, 2, 16, 0), at(2026, 11, 4, 10, 0), holidays, 8, 18))
                .isEqualTo(240);
    }

    @Test
    @DisplayName("janela invalida e recusada")
    void invalidWindowRejected() {
        assertThatThrownBy(() -> SlaDeadlineCalculator.requireWindow(18, 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SlaDeadlineCalculator.addHours(at(2026, 11, 2, 10, 0), 1,
                SlaDeadlineCalculator.Calendar.BUSINESS, Set.of(), 8, 8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("matriz padrao: as 9 celulas impacto x criticidade")
    void defaultMatrixAllCells() {
        assertThat(SupportSlaService.classify("HIGH", "HIGH")).isEqualTo("S1");
        assertThat(SupportSlaService.classify("HIGH", "MEDIUM")).isEqualTo("S2");
        assertThat(SupportSlaService.classify("HIGH", "LOW")).isEqualTo("S3");
        assertThat(SupportSlaService.classify("MEDIUM", "HIGH")).isEqualTo("S2");
        assertThat(SupportSlaService.classify("MEDIUM", "MEDIUM")).isEqualTo("S3");
        assertThat(SupportSlaService.classify("MEDIUM", "LOW")).isEqualTo("S4");
        assertThat(SupportSlaService.classify("LOW", "HIGH")).isEqualTo("S3");
        assertThat(SupportSlaService.classify("LOW", "MEDIUM")).isEqualTo("S4");
        assertThat(SupportSlaService.classify("LOW", "LOW")).isEqualTo("S5");
    }

    @Test
    @DisplayName("matriz: eixo desconhecido e recusado")
    void matrixUnknownAxisRejected() {
        assertThatThrownBy(() -> SupportSlaService.classify("CRITICAL", "HIGH"))
                .isInstanceOf(RuntimeException.class);
    }
}
