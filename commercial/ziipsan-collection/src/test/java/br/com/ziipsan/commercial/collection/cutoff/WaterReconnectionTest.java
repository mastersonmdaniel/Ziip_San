package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.core.cutoff.CutoffEnums;
import br.com.ziipsan.core.cutoff.ReconnectionRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Religacao de AGUA do SG-502 (TR-540, registrar a execucao da
 * religacao) exercida NESTE pacote sobre a regra real da P3.4
 * ({@link ReconnectionRule}) — correcao D3 da revisao cruzada: antes
 * so o lado esgoto tinha teste funcional aqui e o lado agua era mera
 * referencia a P3.4. A regra continua sendo a da P3.4, sem duplicacao;
 * este teste e a prova funcional do consumo dela pela ED-29.
 */
class WaterReconnectionTest {

    private static final OffsetDateTime PAYMENT_AT =
            OffsetDateTime.of(2026, 11, 16, 10, 0, 0, 0, ZoneOffset.ofHours(-3));

    @Test
    @DisplayName("SG-502 agua: divida paga com prova do aviso: religacao gerada, taxa cobrada e SLA da P3.4 (48h do fixture)")
    void waterReconnectionCreatesOrderWithFeeAndSla() {
        var facts = new ReconnectionRule.PaymentFacts(true, false, false, false, false,
                CutoffEnums.DeliveryProofStatus.DELIVERED);
        var decision = ReconnectionRule.evaluate(facts, CutoffExtTestSupport.cutoffParams(false), PAYMENT_AT);
        assertThat(decision.createOrder()).isTrue();
        assertThat(decision.blockedReason()).isNull();
        assertThat(decision.appliedCondition())
                .isEqualTo(CutoffEnums.ReleaseCondition.MOTIVATING_DEBT_PAID_OR_AGREED);
        assertThat(decision.feeDecision()).isEqualTo(CutoffEnums.FeeDecision.CHARGED);
        assertThat(decision.slaDueAt()).isEqualTo(PAYMENT_AT.plusHours(48));
    }

    @Test
    @DisplayName("SG-502 agua: sem prova de entrega do aviso, religacao SEM taxa (Lei 13.460/2017, regra da P3.4)")
    void waterReconnectionWithoutProofIsFree() {
        var facts = new ReconnectionRule.PaymentFacts(true, false, false, false, false,
                CutoffEnums.DeliveryProofStatus.NOT_DELIVERED);
        var decision = ReconnectionRule.evaluate(facts, CutoffExtTestSupport.cutoffParams(false), PAYMENT_AT);
        assertThat(decision.createOrder()).isTrue();
        assertThat(decision.feeDecision()).isEqualTo(CutoffEnums.FeeDecision.WAIVED_NO_PROOF);
    }

    @Test
    @DisplayName("SG-502 agua: condicao de liberacao nao satisfeita: nao gera, com motivo da P3.4 registrado")
    void waterReconnectionBlockedWhenConditionNotMet() {
        var facts = new ReconnectionRule.PaymentFacts(false, false, false, false, false,
                CutoffEnums.DeliveryProofStatus.DELIVERED);
        var decision = ReconnectionRule.evaluate(facts, CutoffExtTestSupport.cutoffParams(false), PAYMENT_AT);
        assertThat(decision.createOrder()).isFalse();
        assertThat(decision.blockedReason()).isEqualTo("RELEASE_CONDITION_NOT_MET");
        assertThat(decision.slaDueAt()).isNull();
    }
}
