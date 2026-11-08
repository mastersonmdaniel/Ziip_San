package br.com.ziipsan.commercial.billing.special;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ED-22: um teste por opcao de parametro da tarefa. Ausencia = erro explicito, nunca padrao presumido. */
class SpecialSituationParametersTest {

    private static SpecialSituationParameters params(String key, String json) {
        return SpecialSituationParameters.fromValues(Map.of(key, json));
    }

    @Test
    @DisplayName("special_situation.max_months: le o teto; 0 e recusado; ausente e erro explicito")
    void maxMonths() {
        assertThat(params(SpecialSituationParameters.MAX_MONTHS, "12").maxMonths()).isEqualTo(12);
        assertThatThrownBy(() -> params(SpecialSituationParameters.MAX_MONTHS, "0").maxMonths())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpecialSituationParameters.fromValues(Map.of()).maxMonths())
                .isInstanceOf(MissingParameterException.class);
    }

    @Test
    @DisplayName("quality.noncompliance_compensation: os tres modos; valor fora do catalogo e recusado")
    void qualityMode() {
        assertThat(params(SpecialSituationParameters.QUALITY_COMPENSATION, "\"NONE\"").qualityCompensationMode())
                .isEqualTo(SpecialSituationParameters.QualityCompensationMode.NONE);
        assertThat(params(SpecialSituationParameters.QUALITY_COMPENSATION, "\"CREDIT_PERCENT\"").qualityCompensationMode())
                .isEqualTo(SpecialSituationParameters.QualityCompensationMode.CREDIT_PERCENT);
        assertThat(params(SpecialSituationParameters.QUALITY_COMPENSATION, "\"FIXED_CREDIT\"").qualityCompensationMode())
                .isEqualTo(SpecialSituationParameters.QualityCompensationMode.FIXED_CREDIT);
        assertThatThrownBy(() -> params(SpecialSituationParameters.QUALITY_COMPENSATION, "\"DESCONTO\"").qualityCompensationMode())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("bill.advance_billing: ligado e desligado")
    void advanceBilling() {
        assertThat(params(SpecialSituationParameters.ADVANCE_BILLING, "true").advanceBilling()).isTrue();
        assertThat(params(SpecialSituationParameters.ADVANCE_BILLING, "false").advanceBilling()).isFalse();
    }

    @Test
    @DisplayName("fiscal.central_billing_invoice_mode: SINGLE, PER_UNIT e NONE")
    void centralInvoiceMode() {
        assertThat(params(SpecialSituationParameters.CENTRAL_INVOICE_MODE, "\"SINGLE\"").centralBillingInvoiceMode())
                .isEqualTo(SpecialSituationParameters.CentralBillingInvoiceMode.SINGLE);
        assertThat(params(SpecialSituationParameters.CENTRAL_INVOICE_MODE, "\"PER_UNIT\"").centralBillingInvoiceMode())
                .isEqualTo(SpecialSituationParameters.CentralBillingInvoiceMode.PER_UNIT);
        assertThat(params(SpecialSituationParameters.CENTRAL_INVOICE_MODE, "\"NONE\"").centralBillingInvoiceMode())
                .isEqualTo(SpecialSituationParameters.CentralBillingInvoiceMode.NONE);
    }

    @Test
    @DisplayName("large_user.*: minimo, penalidade, aprovacao e relatorio, um teste por chave")
    void largeUserKeys() {
        assertThat(params(SpecialSituationParameters.LARGE_MIN_REQUIRED, "true").largeUserMinVolumeRequired()).isTrue();
        assertThat(params(SpecialSituationParameters.LARGE_MIN_REQUIRED, "false").largeUserMinVolumeRequired()).isFalse();
        assertThat(params(SpecialSituationParameters.LARGE_EXCESS_PENALTY_PERCENT, "20.00").largeUserExcessPenaltyPercent())
                .isEqualByComparingTo(new BigDecimal("20.00"));
        assertThatThrownBy(() -> params(SpecialSituationParameters.LARGE_EXCESS_PENALTY_PERCENT, "120.00").largeUserExcessPenaltyPercent())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(params(SpecialSituationParameters.LARGE_AGENCY_APPROVAL_REQUIRED, "true").largeUserAgencyApprovalRequired()).isTrue();
        assertThat(params(SpecialSituationParameters.LARGE_REPORT_ENABLED, "false").largeUserReportEnabled()).isFalse();
    }
}
