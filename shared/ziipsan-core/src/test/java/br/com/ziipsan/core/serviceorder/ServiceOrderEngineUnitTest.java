package br.com.ziipsan.core.serviceorder;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests of the work-order engine (P1.31): state machine,
 * tenant-parameter validation, effect-payload schema validation and the
 * factory safety controls. Database flows live in ServiceOrderEngineIT.
 */
class ServiceOrderEngineUnitTest {

    @Test
    @DisplayName("maquina de estados: transicoes validas passam, invalidas nao, encerrados sao terminais")
    void stateMachine() {
        assertThat(ServiceOrderStatus.DRAFT.canMoveTo(ServiceOrderStatus.OPEN)).isTrue();
        assertThat(ServiceOrderStatus.DRAFT.canMoveTo(ServiceOrderStatus.PENDING_APPROVAL)).isTrue();
        assertThat(ServiceOrderStatus.DRAFT.canMoveTo(ServiceOrderStatus.CLOSED_EXECUTED)).isFalse();
        assertThat(ServiceOrderStatus.OPEN.canMoveTo(ServiceOrderStatus.EXECUTED)).isTrue();
        assertThat(ServiceOrderStatus.EXECUTED.canMoveTo(ServiceOrderStatus.CLOSED_EXECUTED)).isTrue();
        assertThat(ServiceOrderStatus.EXECUTED.canMoveTo(ServiceOrderStatus.OPEN)).isFalse();
        assertThat(ServiceOrderStatus.CLOSED_EXECUTED.canMoveTo(ServiceOrderStatus.OPEN)).isFalse();
        assertThat(ServiceOrderStatus.CLOSED_EXECUTED.isTerminal()).isTrue();
        assertThat(ServiceOrderStatus.CLOSED_NOT_EXECUTED.isTerminal()).isTrue();
        assertThat(ServiceOrderStatus.OPEN.isTerminal()).isFalse();
        // Ficar no mesmo estado e no-op, nao transicao invalida.
        assertThat(ServiceOrderStatus.OPEN.canMoveTo(ServiceOrderStatus.OPEN)).isTrue();
    }

    @Test
    @DisplayName("prazo do tenant so reduz: acima do prazo da agencia e recusado, abaixo passa")
    void deadlineOverrideOnlyReduces() {
        assertThatThrownBy(() -> ServiceOrderParameters.validateDeadlineOverride("IT-TYPE", 72, 100))
                .isInstanceOf(ApiException.Validation.class);
        ServiceOrderParameters.validateDeadlineOverride("IT-TYPE", 72, 72);
        ServiceOrderParameters.validateDeadlineOverride("IT-TYPE", 72, 24);
        assertThat(ServiceOrderParameters.effectiveDeadlineHours(72, 24)).isEqualTo(24);
        assertThat(ServiceOrderParameters.effectiveDeadlineHours(72, null)).isEqualTo(72);
        assertThatThrownBy(() -> ServiceOrderParameters.validateDeadlineOverride("IT-TYPE", 72, 0))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("equipe minima em espaco confinado: abaixo de 2 e recusado (NR-33, 33.3.4.4)")
    void minCrewConfinedSpaceFloor() {
        assertThatThrownBy(() -> ServiceOrderParameters.validateMinCrewConfinedSpace(1))
                .isInstanceOf(ApiException.Validation.class);
        ServiceOrderParameters.validateMinCrewConfinedSpace(2);
        ServiceOrderParameters.validateMinCrewConfinedSpace(3);
        assertThat(ServiceOrderParameters.MIN_CREW_CONFINED_SPACE_DEFAULT).isEqualTo(2);
    }

    @Test
    @DisplayName("validade da capacitacao: padrao da norma 12 meses (NR-33), so reduz")
    void trainingValidityOnlyReduces() {
        assertThat(ServiceOrderParameters.TRAINING_VALIDITY_MONTHS_DEFAULT).isEqualTo(12);
        assertThatThrownBy(() -> ServiceOrderParameters.validateTrainingValidityMonths(24))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> ServiceOrderParameters.validateTrainingValidityMonths(0))
                .isInstanceOf(ApiException.Validation.class);
        ServiceOrderParameters.validateTrainingValidityMonths(12);
        ServiceOrderParameters.validateTrainingValidityMonths(6);
    }

    @Test
    @DisplayName("controles do tenant so ampliam (classe c): remover controle de fabrica e recusado")
    void requiredControlsOnlyAmplify() {
        var factory = Set.of(ServiceOrderEnums.ControlCode.RISK_ANALYSIS,
                ServiceOrderEnums.ControlCode.ENTRY_PERMIT);
        assertThatThrownBy(() -> ServiceOrderParameters.validateRequiredControls("IT-TYPE", factory,
                Set.of(ServiceOrderEnums.ControlCode.RISK_ANALYSIS)))
                .isInstanceOf(ApiException.Validation.class);
        ServiceOrderParameters.validateRequiredControls("IT-TYPE", factory,
                Set.of(ServiceOrderEnums.ControlCode.RISK_ANALYSIS, ServiceOrderEnums.ControlCode.ENTRY_PERMIT,
                        ServiceOrderEnums.ControlCode.WORK_PERMIT));
        ServiceOrderParameters.validateRequiredControls("IT-TYPE", factory, null);
    }

    @Test
    @DisplayName("parse de parametros: mapa de prazos e mapa de controles, codigo desconhecido recusado")
    void parseParameterMaps() {
        assertThat(ServiceOrderParameters.parseDeadlineOverrides("{\"A\": 24, \"B\": 72}"))
                .containsEntry("A", 24).containsEntry("B", 72);
        assertThat(ServiceOrderParameters.parseDeadlineOverrides(null)).isEmpty();
        assertThatThrownBy(() -> ServiceOrderParameters.parseDeadlineOverrides("{\"A\": \"x\"}"))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(ServiceOrderParameters.parseRequiredControls("{\"A\": [\"RISK_ANALYSIS\"]}"))
                .containsEntry("A", Set.of(ServiceOrderEnums.ControlCode.RISK_ANALYSIS));
        assertThatThrownBy(() -> ServiceOrderParameters.parseRequiredControls("{\"A\": [\"NOPE\"]}"))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("carga do efeito: campo obrigatorio ausente e tipo errado recusados, campo extra passa")
    void effectPayloadValidation() {
        String schema = "{\"required\": [\"newValue\"], \"properties\": {\"newValue\": \"string\", \"count\": \"integer\"}}";
        EffectPayloadValidator.validate(schema, "{\"newValue\": \"x\"}", "effectPayload");
        EffectPayloadValidator.validate(schema, "{\"newValue\": \"x\", \"extra\": true}", "effectPayload");
        EffectPayloadValidator.validate(null, null, "effectPayload");
        assertThatThrownBy(() -> EffectPayloadValidator.validate(schema, "{}", "effectPayload"))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> EffectPayloadValidator.validate(schema, "{\"newValue\": 5}", "effectPayload"))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> EffectPayloadValidator.validate(schema, "{\"newValue\": \"x\", \"count\": \"n\"}", "effectPayload"))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("controles e normas de fabrica do tipo confinado: risco, permissao, equipe, treino, NR-1 e NR-33")
    void confinedFactoryControlsAndNorms() {
        var type = new ServiceOrderService.ServiceTypeView(null, "IT-CONFINED", "Confinado", "OP", "EFF", null,
                ServiceOrderEnums.TypeIndicator.FIELD, false, false, null, List.of(TargetType.CONSUMER_UNIT),
                null, null, 2, true, false, false, false, true, true, false, List.of("NR-1", "NR-33"), true);
        assertThat(ServiceOrderService.factoryControls(type)).containsExactlyInAnyOrder(
                ServiceOrderEnums.ControlCode.RISK_ANALYSIS, ServiceOrderEnums.ControlCode.ENTRY_PERMIT,
                ServiceOrderEnums.ControlCode.MIN_CREW, ServiceOrderEnums.ControlCode.TRAINING_VALID);
        assertThat(ServiceOrderService.requiredNorms(type)).containsExactlyInAnyOrder("NR-1", "NR-33");
        var plain = new ServiceOrderService.ServiceTypeView(null, "IT-PLAIN", "Simples", "OP", "EFF", null,
                ServiceOrderEnums.TypeIndicator.INTERNAL, false, false, null, List.of(TargetType.CONSUMER_UNIT),
                null, null, null, false, false, false, false, false, false, false, List.of(), true);
        assertThat(ServiceOrderService.factoryControls(plain)).isEmpty();
        assertThat(ServiceOrderService.requiredNorms(plain)).isEmpty();
    }

    @Test
    @DisplayName("registro de verificadores: LOCATION_ONLY sempre declaravel, resto so com verificador")
    void verifierRegistry() {
        var registry = new TargetVerifierRegistry();
        assertThat(registry.hasVerifier(TargetType.LOCATION_ONLY)).isTrue();
        assertThat(registry.hasVerifier(TargetType.EQUIPMENT)).isFalse();
        assertThat(registry.declarableClasses()).containsExactly(TargetType.LOCATION_ONLY);
        registry.register(TargetType.CONSUMER_UNIT, (c, t, id) -> true);
        assertThat(registry.hasVerifier(TargetType.CONSUMER_UNIT)).isTrue();
        assertThatThrownBy(() -> TargetVerifierRegistry.sqlVerifier("tenant", "bad-table; DROP"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registry.register(null, (c, t, id) -> true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("registro de efeitos: codigo em branco recusado, handler registrado e encontrado")
    void effectHandlerRegistry() {
        var registry = new EffectHandlerRegistry();
        assertThat(registry.hasHandler("X")).isFalse();
        registry.register("X", ctx -> {
        });
        assertThat(registry.hasHandler("X")).isTrue();
        assertThat(registry.handler("X")).isNotNull();
        assertThatThrownBy(() -> registry.register(" ", ctx -> {
        })).isInstanceOf(IllegalArgumentException.class);
    }
}
