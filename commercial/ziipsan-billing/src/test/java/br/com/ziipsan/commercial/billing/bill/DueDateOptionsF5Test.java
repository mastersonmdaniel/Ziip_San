package br.com.ziipsan.commercial.billing.bill;

import br.com.ziipsan.commercial.billing.parameters.BillingParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RAS-24 (P2.5/F5): {@code bill.due_date_options} com no mínimo 6 datas para
 * concessionária e {@code bill.min_days_delivery_to_due} entre entrega e
 * vencimento. Datas fictícias, conferidas à mão.
 */
class DueDateOptionsF5Test {

    private static final List<Integer> SIX = List.of(5, 10, 15, 20, 25, 28);

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Test
    @DisplayName("configuração com 5 datas para concessionária é RECUSADA (Lei 8.987/1995, art. 7º-A)")
    void fiveOptionsForConcessionaireRefused() {
        List<Integer> five = List.of(5, 10, 15, 20, 25);
        assertThatThrownBy(() -> DueDateCalculator.validateDueDateOptions(five, true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ao menos 6");
        // a mesma recusa vale lendo a chave pelo motor de parâmetros
        BillingParameters parameters = BillingParameters.fromValues(
                Map.of(BillingParameters.DUE_DATE_OPTIONS, "[5, 10, 15, 20, 25]"));
        assertThatThrownBy(() -> parameters.dueDateOptions(true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("6 datas para concessionária são aceitas; 5 para não concessionária não cai na trava legal")
    void sixOptionsAccepted() {
        assertThat(DueDateCalculator.validateDueDateOptions(SIX, true, null)).containsExactlyElementsOf(SIX);
        assertThat(DueDateCalculator.validateDueDateOptions(List.of(5, 10, 15, 20, 25), false, null)).hasSize(5);
    }

    @Test
    @DisplayName("antecedência: entrega 08/10 + mínimo 7 dias = vencimento mais cedo em 15/10; dia 10 escolhido vai para 10/11")
    void deliveryToDueAntecedencePushesToNextMonth() {
        // candidato 10/10 < 08/10 + 7 = 15/10 → ocorrência seguinte do dia 10: 10/11/2026
        assertThat(DueDateCalculator.dueDateFromOptions(
                date(2026, 10, 15), 10, SIX, true, date(2026, 10, 8), 7))
                .isEqualTo(date(2026, 11, 10));
    }

    @Test
    @DisplayName("antecedência satisfeita: dia 20 escolhido com entrega 08/10 e mínimo 7 → 20/10")
    void deliveryToDueAntecedenceSatisfied() {
        assertThat(DueDateCalculator.dueDateFromOptions(
                date(2026, 10, 15), 20, SIX, true, date(2026, 10, 8), 7))
                .isEqualTo(date(2026, 10, 20));
    }

    @Test
    @DisplayName("sem dia escolhido vale o dia da rota, também empurrado pela antecedência: rota 12/10, entrega 08/10, mínimo 7 → 12/11")
    void routeDayAlsoRespectsAntecedence() {
        assertThat(DueDateCalculator.dueDateFromOptions(
                date(2026, 10, 12), null, SIX, true, date(2026, 10, 8), 7))
                .isEqualTo(date(2026, 11, 12));
    }

    @Test
    @DisplayName("antecedência maior que o mês (minDays 40): avança mês a mês até satisfazer — entrega 08/10 + 40 = 17/11; dia 10 → 10/12, dia da rota 12 → 12/12")
    void antecedenceLargerThanAMonthIterates() {
        // candidato 10/10 < 17/11 → 10/11 continua < 17/11 → 10/12/2026
        assertThat(DueDateCalculator.dueDateFromOptions(
                date(2026, 10, 15), 10, SIX, true, date(2026, 10, 8), 40))
                .isEqualTo(date(2026, 12, 10));
        // sem dia escolhido: 12/10 → 12/11 < 17/11 → 12/12/2026
        assertThat(DueDateCalculator.dueDateFromOptions(
                date(2026, 10, 12), null, SIX, true, date(2026, 10, 8), 40))
                .isEqualTo(date(2026, 12, 12));
    }

    @Test
    @DisplayName("dia escolhido fora das opções ofertadas é recusado")
    void chosenDayOutsideOptionsRefused() {
        assertThatThrownBy(() -> DueDateCalculator.dueDateFromOptions(
                date(2026, 10, 15), 7, SIX, true, date(2026, 10, 8), 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.due_date_options");
    }

    @Test
    @DisplayName("bill.min_days_delivery_to_due é por categoria, com default para as demais")
    void minDaysPerCategory() {
        BillingParameters parameters = BillingParameters.fromValues(Map.of(
                BillingParameters.MIN_DAYS_DELIVERY_TO_DUE, "{\"default\": 5, \"RES\": 7}"));
        assertThat(parameters.minDaysDeliveryToDue("RES")).isEqualTo(7);
        assertThat(parameters.minDaysDeliveryToDue("COM")).isEqualTo(5);
    }
}
