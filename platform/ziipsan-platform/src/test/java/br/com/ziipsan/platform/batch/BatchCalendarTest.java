package br.com.ziipsan.platform.batch;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ziipsan.core.parameters.TenantTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2.1 / P0.19 (PLT-14): agendamento e data de referência no fuso e
 * no calendário operacional do tenant, com relógio simulado.
 */
class BatchCalendarTest {

    @Test
    @DisplayName("o mesmo instante tem data local diferente em Manaus, Rio Branco e Noronha")
    void sameInstantDifferentLocalDates() {
        Instant instant = Instant.parse("2027-02-01T02:00:00Z");
        Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
        assertThat(new BatchCalendar(TenantTime.of("America/Manaus", clock), Set.of())
                .referenceDateAt(instant)).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(new BatchCalendar(TenantTime.of("America/Rio_Branco", clock), Set.of())
                .referenceDateAt(instant)).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(new BatchCalendar(TenantTime.of("America/Noronha", clock), Set.of())
                .referenceDateAt(instant)).isEqualTo(LocalDate.of(2027, 2, 1));
    }

    @Test
    @DisplayName("agendamento pula fim de semana e feriado do calendário do tenant")
    void scheduleSkipsWeekendAndHoliday() {
        // 2027-02-05 é sexta; 06-07 fim de semana; 08 segunda é feriado.
        BatchCalendar calendar = new BatchCalendar(
                TenantTime.of("America/Sao_Paulo", Clock.fixed(
                        Instant.parse("2027-02-01T12:00:00Z"), ZoneOffset.UTC)),
                Set.of(LocalDate.of(2027, 2, 8)));
        assertThat(calendar.isBusinessDay(LocalDate.of(2027, 2, 5))).isTrue();
        assertThat(calendar.isBusinessDay(LocalDate.of(2027, 2, 6))).isFalse();
        assertThat(calendar.isBusinessDay(LocalDate.of(2027, 2, 7))).isFalse();
        assertThat(calendar.isBusinessDay(LocalDate.of(2027, 2, 8))).isFalse();
        assertThat(calendar.nextBusinessDayOnOrAfter(LocalDate.of(2027, 2, 5)))
                .isEqualTo(LocalDate.of(2027, 2, 5));
        assertThat(calendar.nextBusinessDayOnOrAfter(LocalDate.of(2027, 2, 6)))
                .isEqualTo(LocalDate.of(2027, 2, 9));
        // 02:00 em America/Sao_Paulo no dia 09/02/2027 = 05:00Z (UTC-3, sem DST).
        assertThat(calendar.scheduleAt(LocalTime.of(2, 0), LocalDate.of(2027, 2, 6)))
                .isEqualTo(Instant.parse("2027-02-09T05:00:00Z"));
    }

    @Test
    @DisplayName("data de referência 'hoje' usa o relógio injetado, não o relógio da máquina")
    void todayUsesInjectedClock() {
        Clock clock = Clock.fixed(Instant.parse("2027-02-01T02:00:00Z"), ZoneOffset.UTC);
        BatchCalendar calendar = new BatchCalendar(TenantTime.of("America/Sao_Paulo", clock), Set.of());
        // 02:00Z = 23:00 do dia anterior em Sao_Paulo (UTC-3).
        assertThat(calendar.todayReferenceDate()).isEqualTo(LocalDate.of(2027, 1, 31));
    }
}
