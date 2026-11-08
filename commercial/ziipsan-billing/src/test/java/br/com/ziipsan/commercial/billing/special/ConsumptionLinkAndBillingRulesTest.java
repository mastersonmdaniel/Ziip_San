package br.com.ziipsan.commercial.billing.special;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ED-22: FK do consumo fixo, isencao, compensacao de qualidade, antecipado e conta final. */
class ConsumptionLinkAndBillingRulesTest {

    private static SpecialSituationParameters params(String key, String json) {
        return SpecialSituationParameters.fromValues(Map.of(key, json));
    }

    private static SpecialSituationRecord exemption(YearMonth start, YearMonth end) {
        return new SpecialSituationRecord(UUID.randomUUID(), UUID.randomUUID(),
                SpecialSituationRecord.Domain.BILLING, SpecialSituationType.EXEMPTION,
                "isencao legal", "Lei citada no atendimento", start, end, null,
                null, null, UUID.randomUUID(), null, null);
    }

    @Test
    @DisplayName("SPECIAL_SITUATION_FIXED sem special_situation_id e recusado; com id passa; outro tipo com id e recusado")
    void consumptionLink() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> ConsumptionLinkRule.check("SPECIAL_SITUATION_FIXED", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("special_situation_id");
        ConsumptionLinkRule.check("SPECIAL_SITUATION_FIXED", id);
        ConsumptionLinkRule.check("REAL", null);
        assertThatThrownBy(() -> ConsumptionLinkRule.check("REAL", id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isencao vigente zera agua e esgoto (80,00/40,00 -> 0/0); fora da vigencia nao muda")
    void exemption() {
        SpecialSituationRecord record = exemption(YearMonth.of(2026, 1), YearMonth.of(2026, 12));
        SpecialBillingRules.ExemptionResult in = SpecialBillingRules.applyExemption(record, YearMonth.of(2026, 6),
                new BigDecimal("80.00"), new BigDecimal("40.00"));
        assertThat(in.exempt()).isTrue();
        assertThat(in.waterValue()).isEqualByComparingTo("0.00");
        assertThat(in.sewerValue()).isEqualByComparingTo("0.00");
        SpecialBillingRules.ExemptionResult out = SpecialBillingRules.applyExemption(record, YearMonth.of(2027, 1),
                new BigDecimal("80.00"), new BigDecimal("40.00"));
        assertThat(out.exempt()).isFalse();
        assertThat(out.waterValue()).isEqualByComparingTo("80.00");
    }

    private static SpecialSituationParameters creditPercentParams(String percentJson) {
        return percentJson == null
                ? params(SpecialSituationParameters.QUALITY_COMPENSATION, "\"CREDIT_PERCENT\"")
                : SpecialSituationParameters.fromValues(Map.of(
                        SpecialSituationParameters.QUALITY_COMPENSATION, "\"CREDIT_PERCENT\"",
                        SpecialSituationParameters.QUALITY_COMPENSATION_PERCENT, percentJson));
    }

    @Test
    @DisplayName("compensacao de qualidade por opcao: NONE=0; CREDIT_PERCENT 10% de 1000,00=100,00; FIXED_CREDIT=valor fixo; sem nao-conformidade=0")
    void qualityCompensation() {
        BigDecimal total = new BigDecimal("1000.00");
        assertThat(SpecialBillingRules.qualityCompensation(
                params(SpecialSituationParameters.QUALITY_COMPENSATION, "\"NONE\""),
                true, total, new BigDecimal("75.50"))).isEqualByComparingTo("0.00");
        // D7: o percentual vem da chave nomeada quality.noncompliance_compensation_percent (=10.00).
        assertThat(SpecialBillingRules.qualityCompensation(
                creditPercentParams("10.00"),
                true, total, null)).isEqualByComparingTo("100.00");
        assertThat(SpecialBillingRules.qualityCompensation(
                params(SpecialSituationParameters.QUALITY_COMPENSATION, "\"FIXED_CREDIT\""),
                true, total, new BigDecimal("75.50"))).isEqualByComparingTo("75.50");
        assertThat(SpecialBillingRules.qualityCompensation(
                creditPercentParams("10.00"),
                false, total, null)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("D7: CREDIT_PERCENT sem a chave nomeada do percentual e erro explicito, nao valor do chamador")
    void qualityPercentNeedsNamedParameter() {
        assertThatThrownBy(() -> SpecialBillingRules.qualityCompensation(
                creditPercentParams(null), true, new BigDecimal("1000.00"), null))
                .isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> creditPercentParams("120.00").qualityCompensationPercent())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(creditPercentParams("10.00").qualityCompensationPercent()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("bill.advance_billing: desligado so emite no fim do periodo; ligado emite antes")
    void advanceBilling() {
        LocalDate end = LocalDate.of(2026, 3, 31);
        LocalDate before = LocalDate.of(2026, 3, 20);
        assertThat(SpecialBillingRules.canIssue(
                params(SpecialSituationParameters.ADVANCE_BILLING, "false"), before, end)).isFalse();
        assertThat(SpecialBillingRules.canIssue(
                params(SpecialSituationParameters.ADVANCE_BILLING, "false"), end, end)).isTrue();
        assertThat(SpecialBillingRules.canIssue(
                params(SpecialSituationParameters.ADVANCE_BILLING, "true"), before, end)).isTrue();
    }

    @Test
    @DisplayName("conta final: sem encerramento registrado e recusada; com isencao vigente nasce zerada")
    void finalBill() {
        SpecialSituationRecord record = exemption(YearMonth.of(2026, 1), YearMonth.of(2026, 12));
        assertThatThrownBy(() -> SpecialBillingRules.finalBill(record, YearMonth.of(2026, 6),
                new BigDecimal("50.00"), new BigDecimal("25.00"), false))
                .isInstanceOf(IllegalArgumentException.class);
        SpecialBillingRules.ExemptionResult result = SpecialBillingRules.finalBill(record, YearMonth.of(2026, 6),
                new BigDecimal("50.00"), new BigDecimal("25.00"), true);
        assertThat(result.waterValue()).isEqualByComparingTo("0.00");
        assertThat(result.sewerValue()).isEqualByComparingTo("0.00");
    }
}
