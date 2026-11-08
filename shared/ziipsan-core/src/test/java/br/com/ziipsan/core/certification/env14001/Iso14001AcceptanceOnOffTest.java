package br.com.ziipsan.core.certification.env14001;

import br.com.ziipsan.core.effluent.SludgeAspectSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste de aceite da ED-70 com a norma LIGADA e DESLIGADA: ligada, o
 * fluxo completo nasce (aspecto -> significancia -> licenca ->
 * cenario -> autoavaliacao) e o lodo e lido da ED-105; desligada,
 * nada da norma nasce, o registro criado ligado segue integro e
 * legivel, e a leitura do lodo continua disponivel (a obrigacao e
 * REGULATORY, da ED-105).
 */
class Iso14001AcceptanceOnOffTest {

    @Test
    void fullFlowWithNormEnabled() {
        Iso14001Activation on = TestSupport.enabledTarget();
        EnvironmentalAspect aspect = EnvironmentalAspect.fromTemplate(on, TestSupport.id(),
                TestSupport.TENANT, "WWTP-SLUDGE", EnvironmentalAspect.Situation.NORMAL);
        SignificanceCriteria criteria = new SignificanceCriteria(on, TestSupport.id(),
                TestSupport.TENANT, "SYN-criterio",
                List.of(new SignificanceCriteria.Factor("SYN-severidade", new BigDecimal("10"))),
                new BigDecimal("6"));
        SignificanceEvaluation ev = SignificanceEvaluation.evaluate(aspect, criteria,
                Map.of("SYN-severidade", new BigDecimal("8")),
                TestSupport.ACTOR, LocalDate.of(2026, 1, 10));
        assertThat(ev.significant()).isTrue();

        EnvironmentalLicense lic = new EnvironmentalLicense(on, TestSupport.id(),
                TestSupport.TENANT, "SYN-LIC-ACEITE", "SYN-licenca", "SYN-orgao",
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 2, 1), TestSupport.ACTOR,
                List.of(new EnvironmentalLicense.EvidenceRef("DOCUMENT", "SYN-doc")), List.of());
        assertThat(lic.expiryStatus(LocalDate.of(2026, 1, 20), 30))
                .isEqualTo(EnvironmentalLicense.ExpiryStatus.EXPIRING);

        EnvironmentalEmergencyScenario scenario = EnvironmentalEmergencyScenario.declare(on,
                TestSupport.id(), TestSupport.TENANT, "SYN-CEN-ACEITE", "SYN-rompimento",
                List.of("SYN-isolar o trecho"), TestSupport.ACTOR, List.of("WWTP-SLUDGE"));
        assertThat(scenario.responsibleId()).isEqualTo(TestSupport.ACTOR);

        EmsClauseAssessment assessment = EmsClauseAssessment.assess(on, "8.1",
                "SYN-preparacao e resposta", EmsClauseAssessment.Conformity.CONFORMING,
                List.of(new EmsClauseAssessment.EvidenceRef(
                        EmsClauseAssessment.EvidenceKind.EMERGENCY_SCENARIO, scenario.id().toString())),
                "", TestSupport.ACTOR, LocalDate.of(2026, 1, 10));
        assertThat(assessment.conformity()).isEqualTo(EmsClauseAssessment.Conformity.CONFORMING);

        SludgeAspectView sludge = SludgeAspectView.read(SludgeAspectSummary.summarize(
                TestSupport.TENANT, List.of()));
        assertThat(sludge.awaitingDestinationCount()).isZero();
    }

    @Test
    void nothingIsBornWithNormDisabledAndRecordsSurvive() {
        Iso14001Activation on = TestSupport.enabledTarget();
        EnvironmentalAspect bornEnabled = EnvironmentalAspect.fromTemplate(on, TestSupport.id(),
                TestSupport.TENANT, "NETWORK-WATER-LOSS", EnvironmentalAspect.Situation.ABNORMAL);

        Iso14001Activation off = TestSupport.disabled();
        assertThatThrownBy(() -> EnvironmentalAspect.fromTemplate(off, TestSupport.id(),
                TestSupport.TENANT, "NETWORK-WATER-LOSS", EnvironmentalAspect.Situation.NORMAL))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new EnvironmentalLicense(off, TestSupport.id(),
                TestSupport.TENANT, "SYN-L", "SYN-d", "SYN-o", LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 1), TestSupport.ACTOR, List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnvironmentalEmergencyScenario.declare(off, TestSupport.id(),
                TestSupport.TENANT, "SYN-C", "SYN-d", List.of("SYN-acao"), TestSupport.ACTOR,
                List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EmsClauseAssessment.assess(off, "6.1", "SYN-r",
                EmsClauseAssessment.Conformity.PARTIAL, List.of(), "",
                TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalStateException.class);

        // Desligar nao apaga: o registro nascido ligado segue integro e legivel.
        assertThat(bornEnabled.aspectDescription()).isNotBlank();
        assertThat(bornEnabled.situation()).isEqualTo(EnvironmentalAspect.Situation.ABNORMAL);

        // E a leitura do lodo (ED-105) segue disponivel desligada.
        SludgeAspectView sludge = SludgeAspectView.read(SludgeAspectSummary.summarize(
                TestSupport.TENANT, List.of()));
        assertThat(sludge.tenantId()).isEqualTo(TestSupport.TENANT);
    }

    @Test
    void transitionEdition2015RunsTheSameFlow() {
        Iso14001Activation transition = TestSupport.enabledTransition();
        EnvironmentalAspect aspect = EnvironmentalAspect.fromTemplate(transition, TestSupport.id(),
                TestSupport.TENANT, "WTP-CHEMICAL-HANDLING",
                EnvironmentalAspect.Situation.EMERGENCY);
        assertThat(aspect.activation().edition()).isEqualTo("2015");
        assertThat(aspect.activation().transition()).isTrue();
    }
}
