package br.com.ziipsan.core.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Religacao gerada pelo pagamento (P3.4): um teste por opcao de
 * rec.release_condition, a taxa (sem prova, sem taxa — caso da v1) e o
 * prazo de rec.sla_hours. Pagamento-base: segunda 16/11/2026 as 10h.
 */
class ReconnectionRuleTest {

    private static final OffsetDateTime PAYMENT_AT =
            OffsetDateTime.of(2026, 11, 16, 10, 0, 0, 0, ZoneOffset.ofHours(-3));

    private static CutoffParameters.Values params(Map<String, String> extra) {
        var raw = new java.util.HashMap<String, String>();
        raw.put("rec.sla_hours", "{\"normalHours\":48,\"urgentHours\":4,\"undueHours\":4}");
        raw.putAll(extra);
        return CutoffParameters.parse(raw);
    }

    private static ReconnectionRule.PaymentFacts facts(boolean paidOrAgreed, boolean downPayment,
            boolean full, boolean urgent, boolean undue, CutoffEnums.DeliveryProofStatus proof) {
        return new ReconnectionRule.PaymentFacts(paidOrAgreed, downPayment, full, urgent, undue, proof);
    }

    @Test
    @DisplayName("condicao padrao MOTIVATING_DEBT_PAID_OR_AGREED: quitado ou parcelado gera a OS, prazo de 48 h")
    void motivatingDebtPaidOrAgreed() {
        var decision = ReconnectionRule.evaluate(
                facts(true, false, false, false, false, CutoffEnums.DeliveryProofStatus.DELIVERED),
                params(Map.of()), PAYMENT_AT);
        assertThat(decision.createOrder()).isTrue();
        assertThat(decision.feeDecision()).isEqualTo(CutoffEnums.FeeDecision.CHARGED);
        assertThat(decision.slaDueAt()).isEqualTo(PAYMENT_AT.plusHours(48));
        var notYet = ReconnectionRule.evaluate(
                facts(false, false, false, false, false, CutoffEnums.DeliveryProofStatus.DELIVERED),
                params(Map.of()), PAYMENT_AT);
        assertThat(notYet.createOrder()).isFalse();
        assertThat(notYet.blockedReason()).isEqualTo("RELEASE_CONDITION_NOT_MET");
    }

    @Test
    @DisplayName("opcao DOWN_PAYMENT_PAID: a entrada paga libera, a quitacao do motivo nao e exigida")
    void downPaymentOption() {
        var p = params(Map.of("rec.release_condition", "\"DOWN_PAYMENT_PAID\""));
        assertThat(ReconnectionRule.evaluate(
                facts(false, true, false, false, false, CutoffEnums.DeliveryProofStatus.DELIVERED),
                p, PAYMENT_AT).createOrder()).isTrue();
        assertThat(ReconnectionRule.evaluate(
                facts(true, false, false, false, false, CutoffEnums.DeliveryProofStatus.DELIVERED),
                p, PAYMENT_AT).createOrder()).isFalse();
    }

    @Test
    @DisplayName("opcao FULL_SETTLEMENT: so a quitacao integral libera")
    void fullSettlementOption() {
        var p = params(Map.of("rec.release_condition", "\"FULL_SETTLEMENT\""));
        assertThat(ReconnectionRule.evaluate(
                facts(true, true, false, false, false, CutoffEnums.DeliveryProofStatus.DELIVERED),
                p, PAYMENT_AT).createOrder()).isFalse();
        assertThat(ReconnectionRule.evaluate(
                facts(true, true, true, false, false, CutoffEnums.DeliveryProofStatus.DELIVERED),
                p, PAYMENT_AT).createOrder()).isTrue();
    }

    @Test
    @DisplayName("rec.auto_order_on_payment desligado: pagamento nao gera OS")
    void autoOrderDisabled() {
        var decision = ReconnectionRule.evaluate(
                facts(true, false, false, false, false, CutoffEnums.DeliveryProofStatus.DELIVERED),
                params(Map.of("rec.auto_order_on_payment", "false")), PAYMENT_AT);
        assertThat(decision.createOrder()).isFalse();
        assertThat(decision.blockedReason()).isEqualTo("AUTO_ORDER_DISABLED");
    }

    @Test
    @DisplayName("caso da v1: aviso sem prova de entrega, religacao SEM taxa")
    void noProofNoFee() {
        for (var proof : new CutoffEnums.DeliveryProofStatus[]{
                CutoffEnums.DeliveryProofStatus.NONE, CutoffEnums.DeliveryProofStatus.NOT_DELIVERED}) {
            var decision = ReconnectionRule.evaluate(
                    facts(true, false, false, false, false, proof), params(Map.of()), PAYMENT_AT);
            assertThat(decision.feeDecision()).isEqualTo(CutoffEnums.FeeDecision.WAIVED_NO_PROOF);
        }
    }

    @Test
    @DisplayName("corte indevido: sem taxa e prazo de 4 h; urgencia pedida: prazo de 4 h com taxa")
    void undueAndUrgentSla() {
        var undue = ReconnectionRule.evaluate(
                facts(true, false, false, false, true, CutoffEnums.DeliveryProofStatus.DELIVERED),
                params(Map.of()), PAYMENT_AT);
        assertThat(undue.feeDecision()).isEqualTo(CutoffEnums.FeeDecision.WAIVED_UNDUE_CUT);
        assertThat(undue.slaDueAt()).isEqualTo(PAYMENT_AT.plusHours(4));
        var urgent = ReconnectionRule.evaluate(
                facts(true, false, false, true, false, CutoffEnums.DeliveryProofStatus.DELIVERED),
                params(Map.of()), PAYMENT_AT);
        assertThat(urgent.feeDecision()).isEqualTo(CutoffEnums.FeeDecision.CHARGED);
        assertThat(urgent.slaDueAt()).isEqualTo(PAYMENT_AT.plusHours(4));
    }

    @Test
    @DisplayName("sem rec.sla_hours aceito, o prazo nao e inventado: erro explicito")
    void slaMissingIsExplicitError() {
        var noSla = CutoffParameters.parse(Map.of());
        assertThatThrownBy(() -> ReconnectionRule.evaluate(
                facts(true, false, false, false, false, CutoffEnums.DeliveryProofStatus.DELIVERED),
                noSla, PAYMENT_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rec.sla_hours");
    }

    @Test
    @DisplayName("atraso: concluir depois do prazo e atraso; no prazo exato, nao")
    void lateness() {
        assertThat(ReconnectionRule.isLate(PAYMENT_AT.plusHours(48), PAYMENT_AT.plusHours(49))).isTrue();
        assertThat(ReconnectionRule.isLate(PAYMENT_AT.plusHours(48), PAYMENT_AT.plusHours(48))).isFalse();
        assertThat(ReconnectionRule.isLate(PAYMENT_AT.plusHours(48), null)).isFalse();
    }
}
