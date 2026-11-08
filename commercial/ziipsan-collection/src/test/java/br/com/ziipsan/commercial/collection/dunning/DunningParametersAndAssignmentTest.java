package br.com.ziipsan.commercial.collection.dunning;

import br.com.ziipsan.commercial.collection.dunning.repactuation.Repactuation;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parametros da cobranca: o minimo existencial e PARAMETRO (sem valor,
 * erro — e o codigo nao tem o valor), os interruptores nascem desligados
 * onde a spec fixa, e a cessao federal exige parametro E parecer.
 */
class DunningParametersAndAssignmentTest {

    @Test
    @DisplayName("minimo existencial: sem parametro, erro explicito; com parametro, vale o parametro (600 e depois 700)")
    void existentialMinimumIsAParameter() {
        var absent = DunningParameters.fromValues(Map.of());
        assertThatThrownBy(absent::existentialMinimum)
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("dun.existential_minimum");
        var decree = DunningParameters.fromValues(Map.of("dun.existential_minimum", "600.00"));
        assertThat(decree.existentialMinimum()).isEqualByComparingTo("600.00");
        var cmnAct = DunningParameters.fromValues(Map.of("dun.existential_minimum", "700.00"));
        assertThat(cmnAct.existentialMinimum()).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("escore desligado por padrao; inst.income_based sem valor e erro explicito")
    void switchDefaults() {
        var absent = DunningParameters.fromValues(Map.of());
        assertThat(absent.collectionScoringEnabled()).isFalse();
        assertThat(absent.federalAssignmentEnabled()).isFalse();
        assertThatThrownBy(absent::incomeBasedEnabled).isInstanceOf(MissingParameterException.class);
        var on = DunningParameters.fromValues(Map.of(
                "ai.collection_scoring_enabled", "true", "inst.income_based", "false"));
        assertThat(on.collectionScoringEnabled()).isTrue();
        assertThat(on.incomeBasedEnabled()).isFalse();
    }

    @Test
    @DisplayName("efeitos da repactuacao: vocabulario fechado, nome estranho recusado, ausencia e erro")
    void repactuationEffectsParsing() {
        var params = DunningParameters.fromValues(Map.of(
                "dun.repactuation_effects", "[\"SUSPEND_CUTOFF\", \"SUSPEND_NEGATIVATION\"]"));
        assertThat(Repactuation.effectsOf(params))
                .isEqualTo(Set.of(Repactuation.Effect.SUSPEND_CUTOFF, Repactuation.Effect.SUSPEND_NEGATIVATION));
        var bad = DunningParameters.fromValues(Map.of("dun.repactuation_effects", "[\"FLY_AWAY\"]"));
        assertThatThrownBy(() -> Repactuation.effectsOf(bad)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Repactuation.effectsOf(DunningParameters.fromValues(Map.of())))
                .isInstanceOf(MissingParameterException.class);
    }

    @Test
    @DisplayName("cessao federal (MP 1.393/2026): desligada recusa; ligada sem parecer recusa; ligada com parecer habilita o interruptor")
    void federalAssignmentGate() {
        UUID tenant = UUID.randomUUID();
        var off = DunningParameters.fromValues(Map.of());
        var noOpinion = new DunningTestSupport.FakeLegalOpinions(null);
        assertThatThrownBy(() -> DebtAssignmentGate.requireEnabled(tenant, off, noOpinion))
                .isInstanceOf(ApiException.Validation.class);

        var onNoOpinion = DunningParameters.fromValues(Map.of("dun.federal_assignment_enabled", "true"));
        assertThatThrownBy(() -> DebtAssignmentGate.requireEnabled(tenant, onNoOpinion, noOpinion))
                .isInstanceOf(ApiException.Validation.class);

        var opinion = new DunningPorts.LegalOpinionPort.Opinion(
                "Assessoria juridica do ente", Instant.parse("2026-10-01T12:00:00Z"), "Parecer 07/2026");
        var withOpinion = new DunningTestSupport.FakeLegalOpinions(opinion);
        assertThat(DebtAssignmentGate.requireEnabled(tenant, onNoOpinion, withOpinion))
                .isEqualTo(opinion);
    }

    @Test
    @DisplayName("repactuacao: processo, orgao, audiencia e prazo obrigatorios; plano homologado detectado")
    void repactuationCaseShape() {
        var c = new Repactuation.Case(UUID.randomUUID(), UUID.randomUUID(), "0001/2026",
                "Procon", java.time.LocalDate.of(2026, 12, 1), java.time.LocalDate.of(2027, 6, 1),
                null, null);
        assertThat(c.hasHomologatedPlan()).isFalse();
        assertThat(c.activeAt(java.time.LocalDate.of(2027, 6, 1))).isTrue();
        assertThat(c.activeAt(java.time.LocalDate.of(2027, 6, 2))).isFalse();
        assertThatThrownBy(() -> new Repactuation.Case(UUID.randomUUID(), UUID.randomUUID(), " ",
                "Procon", java.time.LocalDate.of(2026, 12, 1), java.time.LocalDate.of(2027, 6, 1),
                null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
