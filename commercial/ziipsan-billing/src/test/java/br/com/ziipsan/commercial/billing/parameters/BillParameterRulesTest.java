package br.com.ziipsan.commercial.billing.parameters;

import br.com.ziipsan.commercial.billing.bill.LateFeePolicy;
import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.5/F5: <b>um teste por chave</b> de PARAMETROS-DO-TENANT da fase, lida pelo
 * motor, mais as chaves de apoio da frente (fixed_part_model,
 * cut_connection_charge). Valores fictícios, conferidos à mão.
 */
class BillParameterRulesTest {

    private static BillingParameters params(String key, String json) {
        return BillingParameters.fromValues(Map.of(key, json));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("bill.hold_on_anomaly: ligada + anomalia → retém; desligada ou sem anomalia → não retém")
    void holdOnAnomaly() {
        assertThat(BillParameterRules.shouldHoldForAnomaly(
                params(BillingParameters.HOLD_ON_ANOMALY, "true"), true)).isTrue();
        assertThat(BillParameterRules.shouldHoldForAnomaly(
                params(BillingParameters.HOLD_ON_ANOMALY, "true"), false)).isFalse();
        assertThat(BillParameterRules.shouldHoldForAnomaly(
                params(BillingParameters.HOLD_ON_ANOMALY, "false"), true)).isFalse();
    }

    @Test
    @DisplayName("bill.auto_review_value_bands: total 49,99 fora da faixa de revisão [50, aberto); 50,00 dentro")
    void autoReviewValueBands() {
        BillingParameters p = params(BillingParameters.AUTO_REVIEW_VALUE_BANDS,
                "[{\"min\": \"0.00\", \"max\": \"50.00\", \"review\": false},"
                        + " {\"min\": \"50.00\", \"max\": null, \"review\": true}]");
        assertThat(BillParameterRules.requiresAutoReview(p, money("49.99"))).isFalse();
        assertThat(BillParameterRules.requiresAutoReview(p, money("50.00"))).isTrue();
        assertThat(BillParameterRules.requiresAutoReview(p, money("500.00"))).isTrue();
    }

    @Test
    @DisplayName("bill.min_issue_amount: total igual ao mínimo (10,00) emite; 9,99 fica abaixo")
    void minIssueAmount() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(BillingParameters.MIN_ISSUE_AMOUNT, "10.00");
        values.put(BillingParameters.BELOW_MIN_RULE, "\"ISSUE_ANYWAY\"");
        BillingParameters p = BillingParameters.fromValues(values);
        assertThat(p.minIssueAmount()).isEqualByComparingTo("10.00");
        assertThat(BillParameterRules.issueDecision(p, money("10.00")))
                .isEqualTo(BillParameterRules.IssueDecision.ISSUE);
    }

    @Test
    @DisplayName("bill.below_min_rule: abaixo do mínimo, cada opção decide um destino")
    void belowMinRule() {
        assertThat(decisionBelowMin("CARRY_OVER_TO_NEXT"))
                .isEqualTo(BillParameterRules.IssueDecision.CARRY_OVER_TO_NEXT);
        assertThat(decisionBelowMin("HOLD_FOR_REVIEW"))
                .isEqualTo(BillParameterRules.IssueDecision.HOLD_FOR_REVIEW);
        assertThat(decisionBelowMin("ISSUE_ANYWAY"))
                .isEqualTo(BillParameterRules.IssueDecision.ISSUE);
    }

    private static BillParameterRules.IssueDecision decisionBelowMin(String rule) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(BillingParameters.MIN_ISSUE_AMOUNT, "10.00");
        values.put(BillingParameters.BELOW_MIN_RULE, "\"" + rule + "\"");
        return BillParameterRules.issueDecision(BillingParameters.fromValues(values), money("9.99"));
    }

    @Test
    @DisplayName("bill.validity_months: vencimento 15/10/2026 com validade 3 → válida até 31/01/2027")
    void validityMonths() {
        BillingParameters p = params(BillingParameters.VALIDITY_MONTHS, "3");
        assertThat(BillParameterRules.validUntil(p, LocalDate.of(2026, 10, 15)))
                .isEqualTo(LocalDate.of(2027, 1, 31));
    }

    @Test
    @DisplayName("bill.mail_extra_days: entrega por correio soma os dias; entrega direta, nenhum")
    void mailExtraDays() {
        BillingParameters p = params(BillingParameters.MAIL_EXTRA_DAYS, "3");
        assertThat(BillParameterRules.mailExtraDays(p, true)).isEqualTo(3);
        assertThat(BillParameterRules.mailExtraDays(p, false)).isNull();
    }

    @Test
    @DisplayName("bill.delivery_address_fee: endereço alternativo paga a taxa de 2,50; endereço da unidade, zero")
    void deliveryAddressFee() {
        BillingParameters p = params(BillingParameters.DELIVERY_ADDRESS_FEE, "2.50");
        assertThat(BillParameterRules.deliveryFee(p, true)).isEqualByComparingTo("2.50");
        assertThat(BillParameterRules.deliveryFee(p, false)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("bill.inactive_economy_rule: as três opções são lidas do parâmetro")
    void inactiveEconomyRule() {
        assertThat(BillParameterRules.inactiveEconomyRule(
                params(BillingParameters.INACTIVE_ECONOMY_RULE, "\"CHARGE_FIXED_ONLY\"")))
                .isEqualTo(BillingParameters.InactiveEconomyRule.CHARGE_FIXED_ONLY);
        assertThat(BillParameterRules.inactiveEconomyRule(
                params(BillingParameters.INACTIVE_ECONOMY_RULE, "\"ESTIMATE_BY_AVERAGE\"")))
                .isEqualTo(BillingParameters.InactiveEconomyRule.ESTIMATE_BY_AVERAGE);
        assertThat(BillParameterRules.inactiveEconomyRule(
                params(BillingParameters.INACTIVE_ECONOMY_RULE, "\"NO_BILL\"")))
                .isEqualTo(BillingParameters.InactiveEconomyRule.NO_BILL);
    }

    @Test
    @DisplayName("bill.carry_over_unpaid: ligada, os 42,10 não pagos vão à próxima; desligada, nada vai")
    void carryOverUnpaid() {
        assertThat(BillParameterRules.carriedOverAmount(
                params(BillingParameters.CARRY_OVER_UNPAID, "true"), money("42.10")))
                .isEqualByComparingTo("42.10");
        assertThat(BillParameterRules.carriedOverAmount(
                params(BillingParameters.CARRY_OVER_UNPAID, "false"), money("42.10")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("sewer.no_water_volume_rule: fonte alternativa 18 m³, mínimo 10 m³ ou sem esgoto, conforme a opção")
    void noWaterVolumeRule() {
        assertThat(BillParameterRules.sewerBaseVolumeWithoutWater(
                params(BillingParameters.NO_WATER_VOLUME_RULE, "\"ALTERNATIVE_SOURCE_VOLUME\""), 18, 10))
                .isEqualTo(18);
        assertThat(BillParameterRules.sewerBaseVolumeWithoutWater(
                params(BillingParameters.NO_WATER_VOLUME_RULE, "\"MINIMUM_VOLUME\""), 18, 10))
                .isEqualTo(10);
        assertThat(BillParameterRules.sewerBaseVolumeWithoutWater(
                params(BillingParameters.NO_WATER_VOLUME_RULE, "\"NO_SEWER_CHARGE\""), 18, 10))
                .isEqualTo(0);
    }

    @Test
    @DisplayName("credit.type_limits: teto 500,00/12 parcelas — no teto passa; 500,01 ou 13 parcelas recusam; tipo sem teto passa")
    void creditTypeLimits() {
        BillingParameters p = params(BillingParameters.CREDIT_TYPE_LIMITS,
                "{\"SERVICE\": {\"maxAmount\": \"500.00\", \"maxInstallments\": 12}}");
        BillParameterRules.checkCreditWithinLimits(p, "SERVICE", money("500.00"), 12);
        assertThatThrownBy(() -> BillParameterRules.checkCreditWithinLimits(p, "SERVICE", money("500.01"), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credit.type_limits");
        assertThatThrownBy(() -> BillParameterRules.checkCreditWithinLimits(p, "SERVICE", money("100.00"), 13))
                .isInstanceOf(IllegalArgumentException.class);
        BillParameterRules.checkCreditWithinLimits(p, "OTHER", money("9999.00"), 99);
    }

    @Test
    @DisplayName("fee.fine_mode: FLAT e PER_DAY lidos no vocabulário do LateFeePolicy")
    void fineMode() {
        assertThat(params(BillingParameters.FINE_MODE, "\"FLAT\"").fineMode())
                .isEqualTo(LateFeePolicy.FineMode.FLAT);
        assertThat(params(BillingParameters.FINE_MODE, "\"PER_DAY\"").fineMode())
                .isEqualTo(LateFeePolicy.FineMode.PER_DAY);
    }

    @Test
    @DisplayName("fee.charge_moment: ON_NEXT_BILL cobra na conta seguinte; AT_PAYMENT, no pagamento")
    void chargeMoment() {
        assertThat(BillParameterRules.lateFeeChargedOnNextBill(
                params(BillingParameters.CHARGE_MOMENT, "\"ON_NEXT_BILL\""))).isTrue();
        assertThat(BillParameterRules.lateFeeChargedOnNextBill(
                params(BillingParameters.CHARGE_MOMENT, "\"AT_PAYMENT\""))).isFalse();
    }

    @Test
    @DisplayName("fee.exemptions: categoria listada é isenta de multa/juros; outra, não")
    void feeExemptions() {
        BillingParameters p = params(BillingParameters.FEE_EXEMPTIONS, "[\"RES_SOCIAL\", \"ASSISTANCE\"]");
        assertThat(BillParameterRules.isFeeExempt(p, "RES_SOCIAL")).isTrue();
        assertThat(BillParameterRules.isFeeExempt(p, "RES")).isFalse();
    }

    @Test
    @DisplayName("tariff.fixed_part_model e bill.cut_connection_charge: leitura das chaves de apoio da TFDI")
    void fixedPartKeys() {
        assertThat(params(BillingParameters.FIXED_PART_MODEL, "\"MINIMUM_CHARGE_PER_ECONOMY\"").fixedPartModel().name())
                .isEqualTo("MINIMUM_CHARGE_PER_ECONOMY");
        assertThat(params(BillingParameters.FIXED_PART_MODEL, "\"NONE\"").fixedPartModel().name())
                .isEqualTo("NONE");
        assertThat(params(BillingParameters.CUT_CONNECTION_CHARGE, "true").cutConnectionCharge()).isTrue();
        assertThat(params(BillingParameters.CUT_CONNECTION_CHARGE, "false").cutConnectionCharge()).isFalse();
    }

    @Test
    @DisplayName("chave ausente é erro explícito (MissingParameterException), nunca valor assumido")
    void missingKeyIsExplicitError() {
        BillingParameters empty = BillingParameters.fromValues(Map.of());
        assertThatThrownBy(empty::holdOnAnomaly).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(empty::minIssueAmount).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> empty.minDaysDeliveryToDue("RES"))
                .isInstanceOf(MissingParameterException.class);
    }
}
