package br.com.ziipsan.commercial.collection.dunning.ruler;

import br.com.ziipsan.commercial.collection.dunning.DunningParameters;
import br.com.ziipsan.commercial.collection.dunning.DunningPorts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regua de cobranca (CAD-100) e relogio da promessa: vencimento da conta
 * em 01/10/2026; passos D+7 (mensagem), D+15 (ligacao), D+45 (mensagem) e
 * um passo proprio do segmento de tarifa social em D+3.
 */
class DunningRulerEngineTest {

    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID BILL = UUID.randomUUID();
    private static final LocalDate DUE = LocalDate.of(2026, 10, 1);

    private static final String RULER_JSON = """
            { "steps": [
              { "code": "D7_MSG", "dayOffset": 7, "channel": "WHATSAPP",
                "segment": { "debtType": "TARIFF", "minAgeDays": 1, "maxAgeDays": 30,
                             "categories": ["RESIDENTIAL"] },
                "templateCode": "DUNNING_D7" },
              { "code": "D15_CALL", "dayOffset": 15, "channel": "PHONE",
                "segment": { "debtType": "TARIFF" } },
              { "code": "D45_MSG", "dayOffset": 45, "channel": "WHATSAPP",
                "segment": { "debtType": "TARIFF", "minAgeDays": 31 },
                "templateCode": "DUNNING_D45" },
              { "code": "D3_SOCIAL_MSG", "dayOffset": 3, "channel": "WHATSAPP",
                "segment": { "debtType": "TARIFF", "protectedClasses": ["SOCIAL_TARIFF"] },
                "templateCode": "DUNNING_SOCIAL" }
            ] }
            """;

    private static DunningRuler ruler() {
        return DunningRuler.fromParameters(
                DunningParameters.fromValues(Map.of("dun.ruler", RULER_JSON)));
    }

    private static DunningEngine.CaseState state(List<String> executed,
                                                 DunningEngine.PaymentPromise promise) {
        return new DunningEngine.CaseState(UNIT, BILL, "TARIFF", "RESIDENTIAL", null,
                DUE, executed, promise);
    }

    @Test
    @DisplayName("segmentacao: passos do segmento residencial/tarifa; D+3 social nao entra para quem nao e da classe")
    void segmentation() {
        var steps = ruler().stepsFor("TARIFF", 10, "RESIDENTIAL", null);
        assertThat(steps).extracting(DunningRuler.Step::code).containsExactly("D7_MSG", "D15_CALL");
        var social = ruler().stepsFor("TARIFF", 10, "RESIDENTIAL", "SOCIAL_TARIFF");
        assertThat(social).extracting(DunningRuler.Step::code)
                .contains("D3_SOCIAL_MSG", "D7_MSG", "D15_CALL");
        assertThat(ruler().stepsFor("OTHER", 10, "RESIDENTIAL", null)).isEmpty();
    }

    @Test
    @DisplayName("proximo passo pela idade: D+7 mensagem, depois D+15 ligacao, antes disso nada")
    void nextStepByAge() {
        var atD4 = DunningEngine.nextStep(ruler(), state(List.of(), null), LocalDate.of(2026, 10, 5));
        assertThat(atD4.kind()).isEqualTo(DunningEngine.DecisionKind.NO_STEP_DUE);
        var atD7 = DunningEngine.nextStep(ruler(), state(List.of(), null), LocalDate.of(2026, 10, 8));
        assertThat(atD7.kind()).isEqualTo(DunningEngine.DecisionKind.EXECUTE_STEP);
        assertThat(atD7.step().code()).isEqualTo("D7_MSG");
        var atD15 = DunningEngine.nextStep(ruler(), state(List.of("D7_MSG"), null),
                LocalDate.of(2026, 10, 16));
        assertThat(atD15.step().code()).isEqualTo("D15_CALL");
    }

    @Test
    @DisplayName("relogio da promessa: vigente suspende os passos; quebrada REINICIA a regua do primeiro passo")
    void promiseClock() {
        var promise = new DunningEngine.PaymentPromise(
                LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 20), false);
        var during = DunningEngine.nextStep(ruler(),
                state(List.of("D7_MSG"), promise), LocalDate.of(2026, 10, 18));
        assertThat(during.kind()).isEqualTo(DunningEngine.DecisionKind.SUSPENDED_BY_PROMISE);

        var broken = DunningEngine.nextStep(ruler(),
                state(List.of("D7_MSG"), promise), LocalDate.of(2026, 10, 21));
        assertThat(broken.kind()).isEqualTo(DunningEngine.DecisionKind.EXECUTE_STEP);
        assertThat(broken.step().code()).isEqualTo("D7_MSG");
        assertThat(broken.rulerRestartedByBrokenPromise()).isTrue();

        var fulfilled = new DunningEngine.PaymentPromise(
                LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 20), true);
        var afterPaid = DunningEngine.nextStep(ruler(),
                state(List.of("D7_MSG"), fulfilled), LocalDate.of(2026, 10, 21));
        assertThat(afterPaid.rulerRestartedByBrokenPromise()).isFalse();
        assertThat(afterPaid.step().code()).isEqualTo("D15_CALL");
    }

    @Test
    @DisplayName("mensagem da regua carrega o link Pix; sem link, recusa ANTES de renderizar (D9: motor nem e chamado)")
    void messageCarriesPixLink() {
        var ruler = ruler();
        var step = ruler.stepsFor("TARIFF", 10, "RESIDENTIAL", null).get(0);
        DunningPorts.TemplatePort templates = (tenantId, code, vars) ->
                new DunningPorts.TemplatePort.RenderedDocument(code, "v1", "doc-1");
        DunningPorts.PixLinkPort pix = (tenantId, unitId, billId) -> "https://pix.exemplo/abc";
        var message = DunningEngine.emitMessage(step, state(List.of(), null), UUID.randomUUID(),
                templates, pix);
        assertThat(message.pixLink()).isEqualTo("https://pix.exemplo/abc");
        assertThat(message.templateVersion()).isEqualTo("v1");

        DunningPorts.PixLinkPort noPix = (tenantId, unitId, billId) -> null;
        assertThatThrownBy(() -> DunningEngine.emitMessage(step, state(List.of(), null),
                UUID.randomUUID(), templates, noPix))
                .isInstanceOf(IllegalArgumentException.class);

        // D9: com Pix ausente ou em branco, o motor de modelos NUNCA e
        // chamado — nenhum documento e gerado para mensagem nao emitida,
        // e "null" nunca vira variavel do modelo.
        var renders = new java.util.concurrent.atomic.AtomicInteger();
        DunningPorts.TemplatePort counting = (tenantId, code, vars) -> {
            renders.incrementAndGet();
            return new DunningPorts.TemplatePort.RenderedDocument(code, "v1", "doc-1");
        };
        assertThatThrownBy(() -> DunningEngine.emitMessage(step, state(List.of(), null),
                UUID.randomUUID(), counting, noPix)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DunningEngine.emitMessage(step, state(List.of(), null),
                UUID.randomUUID(), counting, (t, u, b) -> "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(renders).hasValue(0);
    }

    @Test
    @DisplayName("conversao medida por passo: 2 de 3 executados converteram")
    void conversionPerStep() {
        var conversion = DunningEngine.conversionOf("D7_MSG", List.of(true, false, true));
        assertThat(conversion.executed()).isEqualTo(3);
        assertThat(conversion.converted()).isEqualTo(2);
        assertThat(conversion.rate()).isCloseTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(DunningEngine.conversionOf("D15_CALL", List.of()).rate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("regua vazia ou sem o parametro: erro explicito")
    void rulerAbsentOrEmpty() {
        assertThatThrownBy(() -> DunningRuler.fromParameters(DunningParameters.fromValues(Map.of())))
                .isInstanceOf(br.com.ziipsan.core.parameters.MissingParameterException.class);
        assertThatThrownBy(() -> DunningRuler.fromParameters(DunningParameters.fromValues(
                Map.of("dun.ruler", "{ \"steps\": [] }"))))
                .isInstanceOf(IllegalStateException.class);
    }
}
