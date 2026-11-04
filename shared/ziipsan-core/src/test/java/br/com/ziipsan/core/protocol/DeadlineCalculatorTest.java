package br.com.ziipsan.core.protocol;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests of the single deadline engine (P1.36): one test per
 * counting rule (plan line 663: "um teste por regra de contagem"), plus
 * the holiday-scope filter, the optional-point default and the
 * protocol state machine. Database flows live in ProtocolIT.
 */
class DeadlineCalculatorTest {

    // A Monday, so the calendar/business contrast is unambiguous.
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);

    @Test
    @DisplayName("regra CALENDAR: dias corridos, fim de semana e feriado contam")
    void calendarDaysIncludeWeekendsAndHolidays() {
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 11, 4));
        // Monday + 5 calendar days = Saturday, even with a holiday inside.
        assertThat(DeadlineCalculator.computeDueDate(MONDAY, 5, DeadlineCountingRule.CALENDAR, holidays))
                .isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(DeadlineCalculator.computeDueDate(MONDAY, 1, DeadlineCountingRule.CALENDAR, Set.of()))
                .isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    @DisplayName("regra BUSINESS: fim de semana nao conta, o dia da abertura nao conta")
    void businessDaysSkipWeekends() {
        // Friday + 1 business day = Monday; Monday + 5 = next Monday.
        assertThat(DeadlineCalculator.computeDueDate(LocalDate.of(2026, 10, 30), 1,
                DeadlineCountingRule.BUSINESS, Set.of()))
                .isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(DeadlineCalculator.computeDueDate(MONDAY, 5, DeadlineCountingRule.BUSINESS, Set.of()))
                .isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    @DisplayName("regra BUSINESS com feriado: o feriado e pulado como o fim de semana")
    void businessDaysSkipHolidays() {
        // Monday + 5 business days, with Wednesday a holiday: Tue, Thu, Fri, Mon, Tue.
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 11, 4));
        assertThat(DeadlineCalculator.computeDueDate(MONDAY, 5, DeadlineCountingRule.BUSINESS, holidays))
                .isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    @DisplayName("escopo NATIONAL: feriado estadual e municipal nao contam no prazo")
    void nationalScopeIgnoresStateAndMunicipalHolidays() {
        var entries = List.of(
                new DeadlineCalculator.HolidayEntry(LocalDate.of(2026, 11, 3),
                        DeadlineCalculator.HolidaySphere.STATE, "SC", null, false),
                new DeadlineCalculator.HolidayEntry(LocalDate.of(2026, 11, 4),
                        DeadlineCalculator.HolidaySphere.MUNICIPAL, null, "4205407", false));
        Set<LocalDate> filtered = DeadlineCalculator.filterHolidayDates(entries,
                HolidayScope.NATIONAL, "SC", "4205407");
        assertThat(filtered).isEmpty();
    }

    @Test
    @DisplayName("escopo NATIONAL_STATE: conta o estadual da UF, nao o de outra UF")
    void stateScopeCountsOnlyMatchingState() {
        var entries = List.of(
                new DeadlineCalculator.HolidayEntry(LocalDate.of(2026, 11, 3),
                        DeadlineCalculator.HolidaySphere.STATE, "SC", null, false),
                new DeadlineCalculator.HolidayEntry(LocalDate.of(2026, 11, 4),
                        DeadlineCalculator.HolidaySphere.STATE, "PR", null, false),
                new DeadlineCalculator.HolidayEntry(LocalDate.of(2026, 11, 5),
                        DeadlineCalculator.HolidaySphere.NATIONAL, null, null, false));
        Set<LocalDate> filtered = DeadlineCalculator.filterHolidayDates(entries,
                HolidayScope.NATIONAL_STATE, "SC", null);
        assertThat(filtered).containsExactlyInAnyOrder(
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 5));
    }

    @Test
    @DisplayName("escopo NATIONAL_STATE_MUNICIPAL: conta nacional, estadual e municipal do municipio")
    void municipalScopeCountsAllSpheresForTheMunicipality() {
        var entries = List.of(
                new DeadlineCalculator.HolidayEntry(LocalDate.of(2026, 11, 3),
                        DeadlineCalculator.HolidaySphere.NATIONAL, null, null, false),
                new DeadlineCalculator.HolidayEntry(LocalDate.of(2026, 11, 4),
                        DeadlineCalculator.HolidaySphere.STATE, "SC", null, false),
                new DeadlineCalculator.HolidayEntry(LocalDate.of(2026, 11, 5),
                        DeadlineCalculator.HolidaySphere.MUNICIPAL, null, "4205407", false),
                new DeadlineCalculator.HolidayEntry(LocalDate.of(2026, 11, 6),
                        DeadlineCalculator.HolidaySphere.MUNICIPAL, null, "4106902", false));
        Set<LocalDate> filtered = DeadlineCalculator.filterHolidayDates(entries,
                HolidayScope.NATIONAL_STATE_MUNICIPAL, "SC", "4205407");
        assertThat(filtered).containsExactlyInAnyOrder(
                LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5));
    }

    @Test
    @DisplayName("ponto facultativo nao conta por padrao, conta com o opt-in do tenant")
    void optionalPointExcludedByDefault() {
        var entries = List.of(
                new DeadlineCalculator.HolidayEntry(LocalDate.of(2026, 11, 3),
                        DeadlineCalculator.HolidaySphere.NATIONAL, null, null, true));
        assertThat(DeadlineCalculator.filterHolidayDates(entries, HolidayScope.NATIONAL, null, null))
                .isEmpty();
        assertThat(DeadlineCalculator.filterHolidayDates(entries, HolidayScope.NATIONAL, null, null, true))
                .containsExactly(LocalDate.of(2026, 11, 3));
    }

    @Test
    @DisplayName("prorrogacao conta a partir do prazo corrente, com a mesma regra")
    void extensionCountsFromCurrentDueDate() {
        Set<LocalDate> holidays = Set.of(LocalDate.of(2026, 11, 11));
        // Current due Wednesday 2026-11-11 is a holiday: +2 business days = Fri 13th.
        assertThat(DeadlineCalculator.extendDueDate(LocalDate.of(2026, 11, 11), 2,
                DeadlineCountingRule.BUSINESS, holidays))
                .isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    @DisplayName("prazo invalido e recusado, sem prazo silencioso")
    void invalidDeadlineInputsAreRefused() {
        assertThatThrownBy(() -> DeadlineCalculator.computeDueDate(MONDAY, 0,
                DeadlineCountingRule.CALENDAR, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeadlineCalculator.computeDueDate(null, 5,
                DeadlineCountingRule.CALENDAR, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeadlineCalculator.computeDueDate(MONDAY, 5, null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("maquina de estados do protocolo: encerramento exige resposta, cancelamento nao e de respondido")
    void protocolStateMachine() {
        assertThat(ProtocolStatus.OPEN.canMoveTo(ProtocolStatus.IN_PROGRESS)).isTrue();
        assertThat(ProtocolStatus.OPEN.canMoveTo(ProtocolStatus.CLOSED)).isFalse();
        assertThat(ProtocolStatus.IN_PROGRESS.canMoveTo(ProtocolStatus.ANSWER_PENDING)).isTrue();
        assertThat(ProtocolStatus.ANSWER_PENDING.canMoveTo(ProtocolStatus.ANSWERED)).isTrue();
        assertThat(ProtocolStatus.ANSWERED.canMoveTo(ProtocolStatus.CLOSED)).isTrue();
        assertThat(ProtocolStatus.ANSWERED.canMoveTo(ProtocolStatus.CANCELLED)).isFalse();
        assertThat(ProtocolStatus.CLOSED.canMoveTo(ProtocolStatus.OPEN)).isFalse();
        assertThat(ProtocolStatus.CLOSED.isTerminal()).isTrue();
        assertThat(ProtocolStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(ProtocolStatus.OPEN.isTerminal()).isFalse();
        assertThat(ProtocolStatus.OPEN.canMoveTo(ProtocolStatus.OPEN)).isTrue();
    }

    @Test
    @DisplayName("parametros sac.*: padroes de fabrica e valor desconhecido recusado, sem fallback silencioso")
    void sacParameterParsing() {
        assertThat(ProtocolParameters.parseCountingRule(null)).isEqualTo(DeadlineCountingRule.CALENDAR);
        assertThat(ProtocolParameters.parseCountingRule("\"BUSINESS\"")).isEqualTo(DeadlineCountingRule.BUSINESS);
        assertThat(ProtocolParameters.parseHolidayScope(null)).isEqualTo(HolidayScope.NATIONAL);
        assertThat(ProtocolParameters.parseHolidayScope("\"NATIONAL_STATE_MUNICIPAL\""))
                .isEqualTo(HolidayScope.NATIONAL_STATE_MUNICIPAL);
        assertThat(ProtocolParameters.parseExtensionPolicy(null)).isEqualTo(ExtensionPolicy.NONE);
        assertThat(ProtocolParameters.parseExtensionPolicy("\"ONCE_JUSTIFIED\""))
                .isEqualTo(ExtensionPolicy.ONCE_JUSTIFIED);
        assertThatThrownBy(() -> ProtocolParameters.parseCountingRule("\"WORKDAYS\""))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> ProtocolParameters.parseHolidayScope("\"ALL\""))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> ProtocolParameters.parseExtensionPolicy("\"TWICE\""))
                .isInstanceOf(ApiException.Validation.class);
    }
}
