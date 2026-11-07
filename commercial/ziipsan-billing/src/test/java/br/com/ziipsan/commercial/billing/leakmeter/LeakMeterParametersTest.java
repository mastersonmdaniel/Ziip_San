package br.com.ziipsan.commercial.billing.leakmeter;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.28: uma prova por chave leak.* / meter.test_*, inclusive os padroes declarados e o erro explicito. */
class LeakMeterParametersTest {

    @Test
    @DisplayName("leak.discount_rule ausente: padrao MOST_FAVORABLE declarado em PARAMETROS-DO-TENANT")
    void discountRuleDefault() {
        LeakMeterParameters p = LeakMeterParameters.fromValues(Map.of());
        assertThat(p.discountRule()).isEqualTo(LeakMeterParameters.DiscountRule.MOST_FAVORABLE);
    }

    @Test
    @DisplayName("leak.discount_rule presente le o enum; fora do catalogo e erro explicito")
    void discountRuleValues() {
        assertThat(LeakMeterParameters.fromValues(Map.of(
                LeakMeterParameters.LEAK_DISCOUNT_RULE, "\"AVERAGE_6M\"")).discountRule())
                .isEqualTo(LeakMeterParameters.DiscountRule.AVERAGE_6M);
        LeakMeterParameters bad = LeakMeterParameters.fromValues(Map.of(
                LeakMeterParameters.LEAK_DISCOUNT_RULE, "\"FREE\""));
        assertThatThrownBy(bad::discountRule).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leak.discount_rule");
    }

    @Test
    @DisplayName("leak.max_discount_pct_water ausente: MissingParameterException (o 70% vem do perfil P2.31, nao daqui)")
    void waterPctMissing() {
        LeakMeterParameters p = LeakMeterParameters.fromValues(Map.of());
        assertThatThrownBy(p::maxDiscountPctWater).isInstanceOf(MissingParameterException.class);
    }

    @Test
    @DisplayName("percentuais: leem numero JSON e recusam fora de 0..100")
    void percentParsing() {
        LeakMeterParameters p = LeakMeterParameters.fromValues(Map.of(
                LeakMeterParameters.LEAK_MAX_DISCOUNT_PCT_WATER, "70",
                LeakMeterParameters.LEAK_MAX_DISCOUNT_PCT_SEWER, "50.5"));
        assertThat(p.maxDiscountPctWater()).isEqualByComparingTo(new BigDecimal("70"));
        assertThat(p.maxDiscountPctSewer()).isEqualByComparingTo(new BigDecimal("50.5"));
        LeakMeterParameters over = LeakMeterParameters.fromValues(Map.of(
                LeakMeterParameters.LEAK_MAX_DISCOUNT_PCT_WATER, "101"));
        assertThatThrownBy(over::maxDiscountPctWater).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leak.max_discount_pct_water");
    }

    @Test
    @DisplayName("prazo e ancora: obrigatorios; ancora fora do catalogo e erro")
    void deadlineParams() {
        LeakMeterParameters empty = LeakMeterParameters.fromValues(Map.of());
        assertThatThrownBy(empty::claimDeadlineDays).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(empty::claimDeadlineAnchor).isInstanceOf(MissingParameterException.class);
        LeakMeterParameters p = LeakMeterParameters.fromValues(Map.of(
                LeakMeterParameters.LEAK_CLAIM_DEADLINE_DAYS, "45",
                LeakMeterParameters.LEAK_CLAIM_DEADLINE_ANCHOR, "\"READING_DATE\""));
        assertThat(p.claimDeadlineDays()).isEqualTo(45);
        assertThat(p.claimDeadlineAnchor()).isEqualTo(LeakMeterParameters.DeadlineAnchor.READING_DATE);
    }

    @Test
    @DisplayName("leak.max_bills_per_12_months: ausente e erro explicito (M1); nulo JSON explicito = sem teto (ARIS); presente exige >= 1")
    void maxBills() {
        // Ausencia NAO e ausencia de teto: e o precedente real da P2.20 (a chave e obrigatoria).
        LeakMeterParameters missing = LeakMeterParameters.fromValues(Map.of());
        assertThatThrownBy(missing::maxBillsPer12Months).isInstanceOf(MissingParameterException.class);
        // Sem teto se declara deliberadamente, com nulo JSON explicito (caso ARIS).
        assertThat(LeakMeterParameters.fromValues(Map.of(
                LeakMeterParameters.LEAK_MAX_BILLS_PER_12_MONTHS, "null")).maxBillsPer12Months()).isNull();
        assertThat(LeakMeterParameters.fromValues(Map.of(
                LeakMeterParameters.LEAK_MAX_BILLS_PER_12_MONTHS, "2")).maxBillsPer12Months()).isEqualTo(2);
        LeakMeterParameters zero = LeakMeterParameters.fromValues(Map.of(
                LeakMeterParameters.LEAK_MAX_BILLS_PER_12_MONTHS, "0"));
        assertThatThrownBy(zero::maxBillsPer12Months).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("leak.bills_limit_mode ausente: ANY_IN_12_MONTHS; leak.sewer_by_average e obrigatorio")
    void limitModeAndSewerFlag() {
        assertThat(LeakMeterParameters.fromValues(Map.of()).billsLimitMode())
                .isEqualTo(LeakMeterParameters.BillsLimitMode.ANY_IN_12_MONTHS);
        assertThatThrownBy(() -> LeakMeterParameters.fromValues(Map.of()).sewerByAverage())
                .isInstanceOf(MissingParameterException.class);
        assertThat(LeakMeterParameters.fromValues(Map.of(
                LeakMeterParameters.LEAK_SEWER_BY_AVERAGE, "true")).sewerByAverage()).isTrue();
    }

    @Test
    @DisplayName("meter.test_*: aviso e meses de revisao obrigatorios; cobranca so se normal tem padrao true declarado")
    void meterTestParams() {
        LeakMeterParameters empty = LeakMeterParameters.fromValues(Map.of());
        assertThatThrownBy(empty::meterTestNoticeDays).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(empty::meterTestReviewMonths).isInstanceOf(MissingParameterException.class);
        assertThat(empty.meterTestChargeOnlyIfNormal()).isTrue();
        LeakMeterParameters p = LeakMeterParameters.fromValues(Map.of(
                LeakMeterParameters.METER_TEST_NOTICE_DAYS, "5",
                LeakMeterParameters.METER_TEST_REVIEW_MONTHS, "6",
                LeakMeterParameters.METER_TEST_CHARGE_ONLY_IF_NORMAL, "false"));
        assertThat(p.meterTestNoticeDays()).isEqualTo(5);
        assertThat(p.meterTestReviewMonths()).isEqualTo(6);
        assertThat(p.meterTestChargeOnlyIfNormal()).isFalse();
    }

    @Test
    @DisplayName("service_type.internal_approval: REQUIRED/NONE vencem a fabrica dos tipos novos; sem entrada vale a fabrica")
    void internalApproval() {
        LeakMeterParameters p = LeakMeterParameters.fromValues(Map.of(
                LeakMeterParameters.INTERNAL_APPROVAL,
                "{\"LEAK_REVIEW\": \"NONE\", \"METER_TEST\": \"REQUIRED\"}"));
        assertThat(p.approvalRequired(LeakMeterOrderType.LEAK_REVIEW, true)).isFalse();
        assertThat(p.approvalRequired(LeakMeterOrderType.METER_TEST, false)).isTrue();
        LeakMeterParameters none = LeakMeterParameters.fromValues(Map.of());
        assertThat(none.approvalRequired(LeakMeterOrderType.LEAK_REVIEW,
                LeakMeterOrderType.LEAK_REVIEW.factoryRequiresApproval())).isTrue();
        assertThat(none.approvalRequired(LeakMeterOrderType.METER_TEST,
                LeakMeterOrderType.METER_TEST.factoryRequiresApproval())).isFalse();
    }
}
