package br.com.ziipsan.commercial.billing.maintenance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P2.20: travas da alteracao de vencimento, uma por opcao de parametro. Vetores conferidos a mao. */
class DueDateChangeRuleTest {

    private static MaintenanceParameters params(int maxPerYear, int minDays) {
        return MaintenanceParameters.fromValues(Map.of(
                MaintenanceParameters.DUE_CHANGE_MAX_PER_YEAR, String.valueOf(maxPerYear),
                MaintenanceParameters.DUE_CHANGE_MIN_DAYS_BEFORE_DUE, String.valueOf(minDays)));
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 1);
    private static final LocalDate CURRENT_DUE = LocalDate.of(2026, 3, 10);
    private static final LocalDate NEW_DUE = LocalDate.of(2026, 3, 20);

    @Test
    @DisplayName("dentro das duas travas (1 de 2 no ano; 9 dias >= 5): passa")
    void withinLimits() {
        assertThatCode(() -> DueDateChangeRule.check(params(2, 5), 1, CURRENT_DUE, NEW_DUE, TODAY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bill.due_change_max_per_year=2 com 2 ja feitas: recusa nomeando a chave")
    void maxPerYearReached() {
        assertThatThrownBy(() -> DueDateChangeRule.check(params(2, 5), 2, CURRENT_DUE, NEW_DUE, TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.due_change_max_per_year");
    }

    @Test
    @DisplayName("bill.due_change_min_days_before_due=5 com vencimento em 3 dias (04/03): recusa nomeando a chave")
    void tooCloseToDue() {
        assertThatThrownBy(() -> DueDateChangeRule.check(params(2, 5), 0,
                LocalDate.of(2026, 3, 4), NEW_DUE, TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.due_change_min_days_before_due");
    }

    @Test
    @DisplayName("min_days=0 desliga a antecedencia: vencimento amanha pode ser alterado")
    void minDaysZero() {
        assertThatCode(() -> DueDateChangeRule.check(params(2, 0), 0,
                LocalDate.of(2026, 3, 2), NEW_DUE, TODAY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("novo vencimento igual ao vigente ou no passado: recusa")
    void invalidNewDate() {
        assertThatThrownBy(() -> DueDateChangeRule.check(params(2, 5), 0, CURRENT_DUE, CURRENT_DUE, TODAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DueDateChangeRule.check(params(2, 5), 0, CURRENT_DUE,
                LocalDate.of(2026, 2, 20), TODAY)).isInstanceOf(IllegalArgumentException.class);
    }
}
