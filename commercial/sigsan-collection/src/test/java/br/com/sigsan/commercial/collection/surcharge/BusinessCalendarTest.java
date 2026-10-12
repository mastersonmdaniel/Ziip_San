package br.com.sigsan.commercial.collection.surcharge;

import br.com.sigsan.commercial.collection.surcharge.BusinessCalendar.HolidayEntry;
import br.com.sigsan.commercial.collection.surcharge.BusinessCalendar.Scope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As datas abaixo são fixtures de teste, não feriados reais: o módulo não traz calendário.
 * 2026-10-05 é segunda-feira (01/01/2026 é quinta; de 01/01 a 05/10 são 277 dias, 277 mod 7 = 4).
 */
class BusinessCalendarTest {

    private static final LocalDate MON_05_OCT = LocalDate.of(2026, 10, 5);
    private static final LocalDate MON_12_OCT = LocalDate.of(2026, 10, 12);

    @Test
    @DisplayName("fim de semana não é dia útil; feriado injetado também não")
    void weekendsAndInjectedHolidays() {
        BusinessCalendar cal = BusinessCalendar.of(List.of(MON_12_OCT));
        assertThat(MON_05_OCT.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(cal.isBusinessDay(MON_05_OCT)).isTrue();
        assertThat(cal.isBusinessDay(LocalDate.of(2026, 10, 10))).isFalse(); // sábado
        assertThat(cal.isBusinessDay(LocalDate.of(2026, 10, 11))).isFalse(); // domingo
        assertThat(cal.isBusinessDay(MON_12_OCT)).isFalse();                 // feriado injetado
        assertThat(BusinessCalendar.of(List.of()).isBusinessDay(MON_12_OCT)).isTrue();
    }

    @Test
    @DisplayName("dias úteis entre duas datas contam (de, até]: seg 05 a seg 12 são 5, ou 4 com o feriado")
    void businessDaysBetween() {
        BusinessCalendar plain = BusinessCalendar.of(List.of());
        BusinessCalendar holiday = BusinessCalendar.of(List.of(MON_12_OCT));
        // 06, 07, 08, 09 e 12
        assertThat(plain.businessDaysBetween(MON_05_OCT, MON_12_OCT)).isEqualTo(5);
        assertThat(holiday.businessDaysBetween(MON_05_OCT, MON_12_OCT)).isEqualTo(4);
        assertThat(plain.businessDaysBetween(MON_05_OCT, MON_05_OCT)).isZero();
        assertThat(plain.businessDaysBetween(MON_12_OCT, MON_05_OCT)).isZero();
        assertThat(plain.businessDaysBetween(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 11))).isZero();
    }

    @Test
    @DisplayName("próximo dia útil: sábado vai para segunda; sexta fica; segunda feriada vai para terça")
    void nextBusinessDay() {
        BusinessCalendar cal = BusinessCalendar.of(List.of(MON_12_OCT));
        assertThat(cal.nextBusinessDayOnOrAfter(LocalDate.of(2026, 10, 9))).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(cal.nextBusinessDayOnOrAfter(LocalDate.of(2026, 10, 10))).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(BusinessCalendar.of(List.of()).nextBusinessDayOnOrAfter(LocalDate.of(2026, 10, 10)))
                .isEqualTo(MON_12_OCT);
    }

    @Test
    @DisplayName("calendário de um município reúne os feriados nacionais, os da UF e os do município, e ignora os de fora")
    void municipalityCalendar() {
        List<HolidayEntry> entries = List.of(
                new HolidayEntry(LocalDate.of(2026, 11, 2), Scope.NATIONAL, null, null),
                new HolidayEntry(LocalDate.of(2026, 3, 3), Scope.STATE, "SC", null),
                new HolidayEntry(LocalDate.of(2026, 6, 12), Scope.STATE, "SP", null),
                new HolidayEntry(LocalDate.of(2026, 6, 10), Scope.MUNICIPAL, "SC", "4205407"),
                new HolidayEntry(LocalDate.of(2026, 6, 11), Scope.MUNICIPAL, "PR", "4106902"));
        BusinessCalendar cal = BusinessCalendar.forMunicipality(entries, "sc", "4205407");
        assertThat(cal.isBusinessDay(LocalDate.of(2026, 11, 2))).isFalse();
        assertThat(cal.isBusinessDay(LocalDate.of(2026, 3, 3))).isFalse();
        assertThat(cal.isBusinessDay(LocalDate.of(2026, 6, 10))).isFalse();
        assertThat(cal.isBusinessDay(LocalDate.of(2026, 6, 11))).as("município de outro estado").isTrue();
        assertThat(cal.isBusinessDay(LocalDate.of(2026, 6, 12))).as("UF diferente").isTrue();
    }

    @Test
    @DisplayName("feriado estadual sem UF, municipal sem IBGE, sem data ou sem escopo são recusados")
    void entryValidation() {
        assertThatThrownBy(() -> new HolidayEntry(MON_12_OCT, Scope.STATE, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HolidayEntry(MON_12_OCT, Scope.MUNICIPAL, "SC", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HolidayEntry(null, Scope.NATIONAL, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HolidayEntry(MON_12_OCT, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
