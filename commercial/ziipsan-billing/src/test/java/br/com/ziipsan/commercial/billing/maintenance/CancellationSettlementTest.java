package br.com.ziipsan.commercial.billing.maintenance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P2.20: acerto do cancelamento com vetores a mao (CDC art. 42; motivo abrange ou nao). */
class CancellationSettlementTest {

    private static final UUID R1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID R2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    @DisplayName("creditos realizados 30,00 + 20,00 voltam ao saldo: 50,00; motivo abrange SERVICO: so MULTA (15,00) volta")
    void settleWithCoveredType() {
        var settlement = CancellationSettlement.settle(
                List.of(new BigDecimal("30.00"), new BigDecimal("20.00")),
                List.of(new CancellationSettlement.ChargedDebit(R1, "SERVICO", new BigDecimal("10.00")),
                        new CancellationSettlement.ChargedDebit(R2, "MULTA", new BigDecimal("15.00"))),
                Set.of("SERVICO"));
        assertThat(settlement.creditsReturnedToBalance()).isEqualByComparingTo("50.00");
        assertThat(settlement.debitsToRebill()).extracting(CancellationSettlement.ChargedDebit::debitTypeCode)
                .containsExactly("MULTA");
        assertThat(settlement.rebillTotal()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("motivo nao abrange nenhum ([]): todos os debitos voltam (25,00); sem creditos: 0,00 de volta")
    void settleCoveringNothing() {
        var settlement = CancellationSettlement.settle(
                List.of(),
                List.of(new CancellationSettlement.ChargedDebit(R1, "SERVICO", new BigDecimal("10.00")),
                        new CancellationSettlement.ChargedDebit(R2, "MULTA", new BigDecimal("15.00"))),
                Set.of());
        assertThat(settlement.creditsReturnedToBalance()).isEqualByComparingTo("0.00");
        assertThat(settlement.rebillTotal()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("plano de migracao: pagamento, item agrupador e debito automatico apontam para a conta nova")
    void migrationPlan() {
        UUID newBill = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        var plan = new CancellationSettlement.MigrationPlan(newBill, List.of(R1), R2,
                UUID.fromString("00000000-0000-0000-0000-0000000000bb"));
        assertThat(plan.hasAnythingToMigrate()).isTrue();
        assertThat(plan.newBillId()).isEqualTo(newBill);
        assertThat(plan.toNextEmission()).isFalse();
        assertThat(new CancellationSettlement.MigrationPlan(newBill, List.of(), null, null).hasAnythingToMigrate())
                .isFalse();
    }

    @Test
    @DisplayName("plano sem conta nova: destino explicito e a proxima emissao (nunca a propria conta cancelada)")
    void migrationPlanToNextEmission() {
        var plan = new CancellationSettlement.MigrationPlan(null, List.of(R1), null, null);
        assertThat(plan.newBillId()).isNull();
        assertThat(plan.toNextEmission()).isTrue();
        assertThat(plan.hasAnythingToMigrate()).isTrue();
    }
}
