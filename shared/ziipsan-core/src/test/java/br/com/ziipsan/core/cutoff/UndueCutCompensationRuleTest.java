package br.com.ziipsan.core.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compensacao automatica da P3.4 (rec.undue_cut_compensation), com as
 * contas feitas a mao: taxa de urgencia de R$ 50,00 (2x = R$ 100,00);
 * fatura de R$ 400,00 (20% = R$ 80,00) e de R$ 600,00 (20% = R$ 120,00).
 */
class UndueCutCompensationRuleTest {

    private static final OffsetDateTime DETECTED_AT =
            OffsetDateTime.of(2026, 11, 16, 10, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final BigDecimal URGENT_FEE = new BigDecimal("50.00");

    @Test
    @DisplayName("AGIR (ultima fatura): vale o MAIOR entre 2x a urgencia e 20% da fatura, nos dois ramos")
    void agirFormulaBothBranches() {
        var feeWins = UndueCutCompensationRule.compute(
                CutoffEnums.UndueCompensationFormula.GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE,
                URGENT_FEE, new BigDecimal("400.00"), null, null, DETECTED_AT);
        assertThat(feeWins.creditAmount()).isEqualByComparingTo("100.00");
        assertThat(feeWins.status()).isEqualTo(UndueCutCompensationRule.CompensationStatus.CREDITED);
        var invoiceWins = UndueCutCompensationRule.compute(
                CutoffEnums.UndueCompensationFormula.GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE,
                URGENT_FEE, new BigDecimal("600.00"), null, null, DETECTED_AT);
        assertThat(invoiceWins.creditAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("ARESC (primeira fatura apos a religacao): sem a fatura, PENDING_BASIS sem valor; com ela, calcula")
    void arescPendingBasis() {
        var pending = UndueCutCompensationRule.compute(
                CutoffEnums.UndueCompensationFormula
                        .GREATER_OF_2X_URGENT_FEE_AND_20PCT_FIRST_INVOICE_AFTER_RECONNECTION,
                URGENT_FEE, new BigDecimal("400.00"), null, null, DETECTED_AT);
        assertThat(pending.status()).isEqualTo(UndueCutCompensationRule.CompensationStatus.PENDING_BASIS);
        assertThat(pending.creditAmount()).isNull();
        var computed = UndueCutCompensationRule.compute(
                CutoffEnums.UndueCompensationFormula
                        .GREATER_OF_2X_URGENT_FEE_AND_20PCT_FIRST_INVOICE_AFTER_RECONNECTION,
                URGENT_FEE, null, new BigDecimal("600.00"), null, DETECTED_AT);
        assertThat(computed.creditAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("ARIS: sem credito em dinheiro; religacao em 4 h a partir da constatacao e sem taxa")
    void arisFormula() {
        var result = UndueCutCompensationRule.compute(
                CutoffEnums.UndueCompensationFormula.RECONNECTION_4H_NO_CHARGE,
                null, null, null, null, DETECTED_AT);
        assertThat(result.creditAmount()).isEqualByComparingTo("0.00");
        assertThat(result.reconnectionDueAt()).isEqualTo(DETECTED_AT.plusHours(4));
        assertThat(result.reconnectionFeeWaived()).isTrue();
    }

    @Test
    @DisplayName("sem rec.urgent_fee a formula do maior nao calcula: erro explicito, nunca credito zero")
    void missingUrgentFeeIsExplicitError() {
        assertThatThrownBy(() -> UndueCutCompensationRule.compute(
                CutoffEnums.UndueCompensationFormula.GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE,
                null, new BigDecimal("400.00"), null, null, DETECTED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rec.urgent_fee");
    }

    @Test
    @DisplayName("credito manual: nasce pendente de aprovacao e so credita com aprovador diferente de quem propos")
    void manualCreditWithApproval() {
        var pending = UndueCutCompensationRule.compute(
                CutoffEnums.UndueCompensationFormula.MANUAL_CREDIT_WITH_APPROVAL,
                null, null, null, new BigDecimal("75.50"), DETECTED_AT);
        assertThat(pending.status()).isEqualTo(UndueCutCompensationRule.CompensationStatus.PENDING_APPROVAL);
        UUID proposer = UUID.randomUUID();
        assertThatThrownBy(() -> UndueCutCompensationRule.approveManual(pending, proposer, proposer))
                .isInstanceOf(IllegalArgumentException.class);
        var approved = UndueCutCompensationRule.approveManual(pending, proposer, UUID.randomUUID());
        assertThat(approved.status()).isEqualTo(UndueCutCompensationRule.CompensationStatus.CREDITED);
        assertThat(approved.creditAmount()).isEqualByComparingTo("75.50");
        assertThatThrownBy(() -> UndueCutCompensationRule.compute(
                CutoffEnums.UndueCompensationFormula.MANUAL_CREDIT_WITH_APPROVAL,
                null, null, null, null, DETECTED_AT))
                .isInstanceOf(IllegalStateException.class);
    }
}
