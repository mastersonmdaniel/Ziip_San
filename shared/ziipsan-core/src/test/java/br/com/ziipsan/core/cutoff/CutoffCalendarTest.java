package br.com.ziipsan.core.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vedacoes temporais da P3.4 (RAS-12), calculadas a mao sobre o
 * calendario real de 2026: 20/11/2026 (sexta) e o feriado nacional da
 * Consciencia Negra; 19/11/2026 (quinta) e a vespera; o ponto
 * facultativo de 25/11/2026 (quarta) e um registro sintetico de
 * cal.holidays (dado do tenant, nao feriado nacional).
 */
class CutoffCalendarTest {

    private static final ZoneId TZ = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOLIDAY = LocalDate.of(2026, 11, 20);
    private static final LocalDate OPTIONAL_POINT = LocalDate.of(2026, 11, 25);

    private static CutoffCalendar.CalendarData sc() {
        return new CutoffCalendar.CalendarData(Set.of(HOLIDAY), Set.of(OPTIONAL_POINT), "SC");
    }

    private static ZonedDateTime at(int day, int hour) {
        return ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, TZ);
    }

    @Test
    @DisplayName("federal: sexta, sabado, domingo, feriado e vespera sao dias vedados para iniciar")
    void federalBans() {
        var holidays = Set.of(HOLIDAY);
        assertThat(CutoffCalendar.federalStartBanned(LocalDate.of(2026, 11, 13), holidays)).isTrue(); // sexta
        assertThat(CutoffCalendar.federalStartBanned(LocalDate.of(2026, 11, 14), holidays)).isTrue(); // sabado
        assertThat(CutoffCalendar.federalStartBanned(LocalDate.of(2026, 11, 15), holidays)).isTrue(); // domingo
        assertThat(CutoffCalendar.federalStartBanned(HOLIDAY, holidays)).isTrue(); // feriado
        assertThat(CutoffCalendar.federalStartBanned(LocalDate.of(2026, 11, 19), holidays)).isTrue(); // vespera
        assertThat(CutoffCalendar.federalStartBanned(LocalDate.of(2026, 11, 16), holidays)).isFalse(); // segunda
        assertThat(CutoffCalendar.federalStartBanned(LocalDate.of(2026, 11, 18), holidays)).isFalse(); // quarta
        // Ponto facultativo NAO e feriado para a camada federal.
        assertThat(CutoffCalendar.federalStartBanned(OPTIONAL_POINT, holidays)).isFalse();
    }

    @Test
    @DisplayName("SC: corte as 7h de segunda-feira e RECUSADO; as 8h, liberado (janela das 8h)")
    void scMondayEightOClock() {
        assertThat(CutoffCalendar.isForbidden(at(16, 7), sc())).isTrue();
        assertThat(CutoffCalendar.isForbidden(at(16, 8), sc())).isFalse();
        assertThat(CutoffCalendar.isForbidden(at(16, 10), sc())).isFalse();
        // Fora de SC a camada federal nao veda a segunda as 7h.
        var rs = new CutoffCalendar.CalendarData(Set.of(HOLIDAY), Set.of(OPTIONAL_POINT), "RS");
        assertThat(CutoffCalendar.isForbidden(at(16, 7), rs)).isFalse();
    }

    @Test
    @DisplayName("SC: janela do feriado vai das 8h do dia util anterior as 8h do primeiro dia util seguinte")
    void scHolidayWindow() {
        // Feriado sexta 20/11: janela de quinta 19/11 8h a segunda 23/11 8h.
        assertThat(CutoffCalendar.isForbidden(at(19, 7), sc())).isTrue(); // tambem vespera federal
        assertThat(CutoffCalendar.scForbiddenAt(at(19, 9).toLocalDateTime(), sc())).isTrue();
        assertThat(CutoffCalendar.isForbidden(at(23, 7), sc())).isTrue();
        assertThat(CutoffCalendar.isForbidden(at(23, 8), sc())).isFalse();
        // Quarta 18/11 de manha esta fora da janela estadual (e fora da federal).
        assertThat(CutoffCalendar.isForbidden(at(18, 9), sc())).isFalse();
    }

    @Test
    @DisplayName("SC: corte em ponto facultativo e RECUSADO (janela das 8h ao redor do ponto)")
    void scOptionalPointRefused() {
        // Ponto facultativo quarta 25/11: janela de terca 24/11 8h a quinta 26/11 8h.
        assertThat(CutoffCalendar.isForbidden(at(25, 10), sc())).isTrue();
        assertThat(CutoffCalendar.isForbidden(at(24, 9), sc())).isTrue();
        assertThat(CutoffCalendar.isForbidden(at(26, 7), sc())).isTrue();
        assertThat(CutoffCalendar.isForbidden(at(26, 8), sc())).isFalse();
        // O mesmo instante fora de SC nao e vedado (nem feriado, nem vespera).
        var rs = new CutoffCalendar.CalendarData(Set.of(HOLIDAY), Set.of(OPTIONAL_POINT), "RS");
        assertThat(CutoffCalendar.isForbidden(at(25, 10), rs)).isFalse();
    }

    @Test
    @DisplayName("primeira data permitida: sabado 14/11/2026 avanca para segunda 16/11/2026 (caso da v1)")
    void firstAllowedDateV1Case() {
        assertThat(CutoffCalendar.firstAllowedDateOnOrAfter(LocalDate.of(2026, 11, 14), sc()))
                .isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(CutoffCalendar.firstAllowedDateOnOrAfter(LocalDate.of(2026, 11, 19), sc()))
                .isEqualTo(LocalDate.of(2026, 11, 23));
    }

    @Test
    @DisplayName("primeiro instante permitido salta o fim de semana e a janela das 8h")
    void firstAllowedInstant() {
        assertThat(CutoffCalendar.firstAllowedInstantOnOrAfter(at(14, 10), sc()))
                .isEqualTo(at(16, 8));
        assertThat(CutoffCalendar.firstAllowedInstantOnOrAfter(at(16, 7), sc()))
                .isEqualTo(at(16, 8));
        assertThat(CutoffCalendar.firstAllowedInstantOnOrAfter(at(16, 10), sc()))
                .isEqualTo(at(16, 10));
    }
}
