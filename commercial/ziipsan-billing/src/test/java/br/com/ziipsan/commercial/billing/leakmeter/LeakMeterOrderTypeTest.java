package br.com.ziipsan.commercial.billing.leakmeter;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** P3.28: as declaracoes dos tipos de OS no padrao do nucleo da P1.31. */
class LeakMeterOrderTypeTest {

    @Test
    @DisplayName("codigos e efeitos: o codigo de efeito e o proprio codigo do tipo")
    void codes() {
        assertThat(LeakMeterOrderType.LEAK_REVIEW.code()).isEqualTo("LEAK_REVIEW");
        assertThat(LeakMeterOrderType.LEAK_REVIEW.effectCode()).isEqualTo("LEAK_REVIEW");
        assertThat(LeakMeterOrderType.METER_TEST.code()).isEqualTo("METER_TEST");
        assertThat(LeakMeterOrderType.METER_TEST.effectCode()).isEqualTo("METER_TEST");
    }

    @Test
    @DisplayName("fabrica de aprovacao: vazamento muda faturamento (aprova); afericao nao (sem aprovacao)")
    void factoryApproval() {
        assertThat(LeakMeterOrderType.LEAK_REVIEW.factoryRequiresApproval()).isTrue();
        assertThat(LeakMeterOrderType.METER_TEST.factoryRequiresApproval()).isFalse();
    }

    @Test
    @DisplayName("declaracao LEAK_REVIEW: campo, alvo CONSUMER_UNIT, esquema exige a ligacao com a BILL_REVIEW")
    void leakReviewDeclaration() {
        ServiceOrderService.ServiceTypeInput input = LeakMeterOrderType.LEAK_REVIEW.declaration(true);
        assertThat(input.code()).isEqualTo("LEAK_REVIEW");
        assertThat(input.indicator()).isEqualTo(ServiceOrderEnums.TypeIndicator.FIELD);
        assertThat(input.requiresApproval()).isTrue();
        assertThat(input.operational()).isFalse();
        assertThat(input.targetClasses()).containsExactly(TargetType.CONSUMER_UNIT);
        assertThat(input.effectSchemaJson()).contains("\"billId\"", "\"billReviewId\"", "\"billReviewOrderId\"");
        ServiceOrderService.ServiceTypeInput free = LeakMeterOrderType.LEAK_REVIEW.declaration(false);
        assertThat(free.requiresApproval()).isFalse();
    }

    @Test
    @DisplayName("declaracao METER_TEST: campo, alvo CONSUMER_UNIT, esquema exige meterId e unitId (hidrometro na carga)")
    void meterTestDeclaration() {
        ServiceOrderService.ServiceTypeInput input = LeakMeterOrderType.METER_TEST.declaration(false);
        assertThat(input.code()).isEqualTo("METER_TEST");
        assertThat(input.indicator()).isEqualTo(ServiceOrderEnums.TypeIndicator.FIELD);
        assertThat(input.requiresApproval()).isFalse();
        assertThat(input.targetClasses()).containsExactly(TargetType.CONSUMER_UNIT);
        assertThat(input.effectSchemaJson()).contains("\"meterId\"", "\"unitId\"");
    }
}
