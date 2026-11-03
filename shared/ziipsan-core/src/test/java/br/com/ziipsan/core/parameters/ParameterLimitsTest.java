package br.com.ziipsan.core.parameters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Um teste por limite travado do §2.3 (P0.20), nomeado pela chave.
 * Valores de referência de SC (6 meses, 3 ciclos, 0,033%/dia) são sugestões
 * documentadas, não tetos — os testes distinguem os dois casos.
 */
class ParameterLimitsTest {

    @Test
    @DisplayName("bill.due_date_options: 5 dias para concessionária é recusado (Lei 8.987, art. 7º-A)")
    void bill_due_date_options_fiveForConcessionaireRefused() {
        assertThatThrownBy(() -> ParameterLimits.validateDueDateOptions(
                List.of(5, 10, 15, 20, 25), true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ao menos 6");
    }

    @Test
    @DisplayName("bill.due_date_options: 6 dias aceitos; 7 ampliam; reduzir de 7 para 6 é recusado (só amplia)")
    void bill_due_date_options_sixAcceptedOnlyExpands() {
        assertThatCode(() -> ParameterLimits.validateDueDateOptions(
                List.of(1, 5, 10, 15, 20, 25), true, null)).doesNotThrowAnyException();
        assertThatCode(() -> ParameterLimits.validateDueDateOptions(
                List.of(1, 5, 10, 15, 20, 25, 30), true, 6)).doesNotThrowAnyException();
        // 6 dias passa no mínimo, mas é redução frente aos 7 aceitos: só amplia.
        assertThatThrownBy(() -> ParameterLimits.validateDueDateOptions(
                List.of(1, 5, 10, 15, 20, 25), true, 7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("só amplia");
    }

    @Test
    @DisplayName("bill.due_date_options: dia fora de 1..31 e dia duplicado são recusados")
    void bill_due_date_options_invalidDaysRefused() {
        assertThatThrownBy(() -> ParameterLimits.validateDueDateOptions(
                List.of(0, 10), false, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParameterLimits.validateDueDateOptions(
                List.of(10, 10), false, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("meter.average_months: 6 (referência SC) aceito; 0 recusado (NR 11, art. 35)")
    void meter_average_months_scReferenceAcceptedZeroRefused() {
        assertThatCode(() -> ParameterLimits.validateAverageMonths(6)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ParameterLimits.validateAverageMonths(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("meter.max_estimated_cycles: 3 (referência SC) aceito; 0 recusado (NR 11, art. 35)")
    void meter_max_estimated_cycles_scReferenceAcceptedZeroRefused() {
        assertThatCode(() -> ParameterLimits.validateMaxEstimatedCycles(3)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ParameterLimits.validateMaxEstimatedCycles(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ret.billing_history_months: 119 recusado; 120 aceito; reduzir de 150 para 120 é recusado (piso, NR 11 art. 22)")
    void ret_billing_history_months_floor120OnlyExpands() {
        assertThatThrownBy(() -> ParameterLimits.validateBillingHistoryMonths(119, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("120");
        assertThatCode(() -> ParameterLimits.validateBillingHistoryMonths(120, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> ParameterLimits.validateBillingHistoryMonths(150, 120))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ParameterLimits.validateBillingHistoryMonths(120, 150))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("só amplia");
    }

    @Test
    @DisplayName("bill.invoice_template: modelo sem bloco do fornecedor é recusado (CDC, art. 42-A)")
    void bill_invoice_template_supplierBlockLocked() {
        assertThatThrownBy(() -> ParameterLimits.validateInvoiceHasSupplierBlock(false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("42-A");
        assertThatCode(() -> ParameterLimits.validateInvoiceHasSupplierBlock(true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("fee.interest_mode: modos simples aceitos; CALENDAR_MONTH recusado (não implementado)")
    void fee_interest_mode_simpleOnly() {
        assertThatCode(() -> ParameterLimits.validateInterestMode("DAILY_RATE_WITH_CAP"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ParameterLimits.validateInterestMode("MONTHLY_PRO_RATA"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ParameterLimits.validateInterestMode("CALENDAR_MONTH"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ParameterLimits.validateInterestMode("COMPOUND"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fee.fine_pct: negativo recusado; 2,00 aceito (o teto e o aceite vivem em FineRule)")
    void fee_fine_pct_nonNegative() {
        assertThatThrownBy(() -> ParameterLimits.validateFinePercent(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> ParameterLimits.validateFinePercent(new BigDecimal("2.00")))
                .doesNotThrowAnyException();
    }
}
