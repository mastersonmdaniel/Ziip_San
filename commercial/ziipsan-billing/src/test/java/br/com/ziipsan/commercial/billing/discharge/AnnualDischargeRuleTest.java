package br.com.ziipsan.commercial.billing.discharge;

import br.com.ziipsan.commercial.billing.bill.BillStatus;
import br.com.ziipsan.commercial.billing.discharge.AnnualDischargeRule.DischargeDecision;
import br.com.ziipsan.commercial.billing.discharge.AnnualDischargeRule.Trigger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regra da declaracao anual de quitacao (P2.24, Lei 12.007/2009): os tres
 * casos do aceite — conta sem debito, conta com debito e quitacao tardia —
 * com as datas recomputadas a mao em cada caso.
 */
class AnnualDischargeRuleTest {

    private static AnnualBillFact bill(int year, int month, BillStatus status, LocalDate settledOn) {
        return new AnnualBillFact(UUID.randomUUID(), LocalDate.of(year, month, 1), status, settledOn);
    }

    /** As 12 contas de {@code year}, todas PAID, cada uma quitada no dia 10 do mes seguinte. */
    private static List<AnnualBillFact> settledYear(int year) {
        List<AnnualBillFact> facts = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDate ref = LocalDate.of(year, month, 1);
            facts.add(bill(year, month, BillStatus.PAID, ref.plusMonths(1).withDayOfMonth(10)));
        }
        return facts;
    }

    @Test
    @DisplayName("sem debito: ano todo pago (ultima quitacao 10/01/2026) vence na fatura de maio de 2026")
    void noDebtYearIsDueInMayBill() {
        // Dezembro/2025 quitado em 10/01/2026 (vencimento normal); a maior
        // quitacao do ano e 10/01/2026, ate 30/04/2026 -> ciclo de maio.
        Optional<DischargeDecision> due = AnnualDischargeRule.evaluate(
                2025, settledYear(2025), LocalDate.of(2026, 5, 1), false);
        assertThat(due).isPresent();
        assertThat(due.get().trigger()).isEqualTo(Trigger.MAY_CYCLE);
        assertThat(due.get().settledOn()).isEqualTo(LocalDate.of(2026, 1, 10));
        assertThat(due.get().dueReferenceMonth()).isEqualTo(LocalDate.of(2026, 5, 1));
        // E nao vence em nenhum outro mes.
        assertThat(AnnualDischargeRule.evaluate(2025, settledYear(2025), LocalDate.of(2026, 4, 1), false))
                .isEmpty();
        assertThat(AnnualDischargeRule.evaluate(2025, settledYear(2025), LocalDate.of(2026, 6, 1), false))
                .isEmpty();
    }

    @Test
    @DisplayName("com debito: uma conta NORMAL no ano bloqueia a declaracao em maio e em qualquer mes")
    void openDebtBlocksDeclaration() {
        List<AnnualBillFact> facts = settledYear(2025);
        facts.set(5, bill(2025, 6, BillStatus.NORMAL, null));
        assertThat(AnnualDischargeRule.evaluate(2025, facts, LocalDate.of(2026, 5, 1), false)).isEmpty();
        assertThat(AnnualDischargeRule.evaluate(2025, facts, LocalDate.of(2026, 7, 1), false)).isEmpty();
        assertThat(AnnualDischargeRule.evaluateAll(facts, LocalDate.of(2026, 5, 1), Set.of())).isEmpty();
    }

    @Test
    @DisplayName("quitacao tardia: ultima conta paga em 15/06/2026 vence na fatura de julho de 2026, nao na de maio")
    void lateSettlementIsDueInFollowingMonth() {
        List<AnnualBillFact> facts = settledYear(2025);
        // Junho/2025 so foi pago em 15/06/2026: a quitacao do ano (15/06/2026)
        // passou do ciclo de maio -> mes seguinte a quitacao = julho/2026.
        facts.set(5, bill(2025, 6, BillStatus.PAID, LocalDate.of(2026, 6, 15)));
        assertThat(AnnualDischargeRule.evaluate(2025, facts, LocalDate.of(2026, 5, 1), false)).isEmpty();
        assertThat(AnnualDischargeRule.evaluate(2025, facts, LocalDate.of(2026, 6, 1), false)).isEmpty();
        Optional<DischargeDecision> due = AnnualDischargeRule.evaluate(
                2025, facts, LocalDate.of(2026, 7, 1), false);
        assertThat(due).isPresent();
        assertThat(due.get().trigger()).isEqualTo(Trigger.LATE_SETTLEMENT);
        assertThat(due.get().settledOn()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    @DisplayName("fronteira do ciclo: quitacao em 30/04/2026 e maio; em 01/05/2026 ja e tardia e vence em junho")
    void mayCycleBoundary() {
        List<AnnualBillFact> onTime = settledYear(2025);
        onTime.set(5, bill(2025, 6, BillStatus.PAID, LocalDate.of(2026, 4, 30)));
        Optional<DischargeDecision> onTimeDue = AnnualDischargeRule.evaluate(
                2025, onTime, LocalDate.of(2026, 5, 1), false);
        assertThat(onTimeDue).isPresent();
        assertThat(onTimeDue.get().trigger()).isEqualTo(Trigger.MAY_CYCLE);

        List<AnnualBillFact> late = settledYear(2025);
        late.set(5, bill(2025, 6, BillStatus.PAID, LocalDate.of(2026, 5, 1)));
        assertThat(AnnualDischargeRule.evaluate(2025, late, LocalDate.of(2026, 5, 1), false)).isEmpty();
        Optional<DischargeDecision> lateDue = AnnualDischargeRule.evaluate(
                2025, late, LocalDate.of(2026, 6, 1), false);
        assertThat(lateDue).isPresent();
        assertThat(lateDue.get().trigger()).isEqualTo(Trigger.LATE_SETTLEMENT);
    }

    @Test
    @DisplayName("ano ja declarado nao vence de novo, nem em maio nem por quitacao tardia")
    void alreadyIssuedYearIsNeverDueAgain() {
        assertThat(AnnualDischargeRule.evaluate(2025, settledYear(2025), LocalDate.of(2026, 5, 1), true))
                .isEmpty();
        List<AnnualBillFact> late = settledYear(2025);
        late.set(5, bill(2025, 6, BillStatus.PAID, LocalDate.of(2026, 6, 15)));
        assertThat(AnnualDischargeRule.evaluateAll(late, LocalDate.of(2026, 7, 1), Set.of(2025))).isEmpty();
    }

    @Test
    @DisplayName("conta cancelada ou com erro nao ocupa o ano: o ano das vivas quitadas continua quitado")
    void nonLiveBillsDoNotBlockTheYear() {
        List<AnnualBillFact> facts = settledYear(2025);
        facts.set(5, bill(2025, 6, BillStatus.CANCELLED_BY_RECTIFICATION, null));
        facts.add(bill(2025, 6, BillStatus.PAID, LocalDate.of(2025, 7, 10)));
        facts.add(bill(2025, 3, BillStatus.PROCESSING_ERROR, null));
        assertThat(AnnualDischargeRule.evaluate(2025, facts, LocalDate.of(2026, 5, 1), false)).isPresent();
    }

    @Test
    @DisplayName("ano sem nenhuma conta viva nao e ano quitado: sem fatura do ano, nao ha declaracao")
    void yearWithoutLiveBillsIsNotSettled() {
        List<AnnualBillFact> onlyCancelled = List.of(bill(2025, 6, BillStatus.CANCELLED, null));
        assertThat(AnnualDischargeRule.evaluate(2025, onlyCancelled, LocalDate.of(2026, 5, 1), false))
                .isEmpty();
        assertThat(AnnualDischargeRule.evaluate(2025, List.of(), LocalDate.of(2026, 5, 1), false))
                .isEmpty();
    }

    @Test
    @DisplayName("dois anos vencem no mesmo maio: a tardia de 2024 e o ciclo de 2025, em ordem de ano")
    void twoYearsCanBeDueInTheSameMonth() {
        List<AnnualBillFact> facts = new ArrayList<>(settledYear(2025));
        // 2024 quitado so em 20/04/2026: para 2024 o limite era 30/04/2025,
        // logo e tardia, com vencimento no mes seguinte: maio/2026.
        List<AnnualBillFact> year2024 = settledYear(2024);
        year2024.set(11, bill(2024, 12, BillStatus.PAID, LocalDate.of(2026, 4, 20)));
        facts.addAll(year2024);
        List<DischargeDecision> due = AnnualDischargeRule.evaluateAll(
                facts, LocalDate.of(2026, 5, 1), Set.of());
        assertThat(due).extracting(DischargeDecision::referenceYear).containsExactly(2024, 2025);
        assertThat(due).extracting(DischargeDecision::trigger)
                .containsExactly(Trigger.LATE_SETTLEMENT, Trigger.MAY_CYCLE);
    }

    @Test
    @DisplayName("fato contraditorio e recusado: PAID sem data de quitacao e nao paga com data nao alimentam a regra")
    void contradictoryFactsAreRejected() {
        assertThatThrownBy(() -> bill(2025, 6, BillStatus.PAID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem data de quitacao");
        assertThatThrownBy(() -> bill(2025, 6, BillStatus.NORMAL, LocalDate.of(2025, 7, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contraditorio");
    }
}
