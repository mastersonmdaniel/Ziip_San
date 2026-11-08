package br.com.ziipsan.platform.implantation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CloneGateLineageSimulationTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-01-10T12:00:00Z");

    private static CloneGate.CloneRequest request(boolean production, boolean isClone) {
        return new CloneGate.CloneRequest(TENANT, production, isClone,
                CloneAnonymization.Purpose.HOMOLOGATION, T0.plusSeconds(3600));
    }

    private static CloneGate.ReidentificationAssessment assessment(boolean viable, Instant at) {
        return new CloneGate.ReidentificationAssessment(TENANT, at, UUID.randomUUID(), viable, "RIPD-2026-001");
    }

    @Test
    @DisplayName("sem avaliação no RIPD, nenhum clone é liberado")
    void noAssessmentBlocks() {
        assertThat(CloneGate.allowed(request(true, false), null)).isFalse();
        assertThat(CloneGate.blockers(request(true, false), null))
                .anyMatch(b -> b.contains("RIPD"));
    }

    @Test
    @DisplayName("avaliação viável e anterior ao pedido libera o clone de produção")
    void viablePriorAssessmentAllows() {
        assertThat(CloneGate.allowed(request(true, false), assessment(true, T0))).isTrue();
    }

    @Test
    @DisplayName("avaliação posterior ao pedido, inviável, de outro tenant ou clone de clone bloqueiam")
    void blockingVariants() {
        assertThat(CloneGate.allowed(request(true, false), assessment(true, T0.plusSeconds(7200)))).isFalse();
        assertThat(CloneGate.allowed(request(true, false), assessment(false, T0))).isFalse();
        assertThat(CloneGate.allowed(request(true, true), assessment(true, T0))).isFalse();
        assertThat(CloneGate.allowed(request(false, false), assessment(true, T0))).isFalse();
        CloneGate.ReidentificationAssessment otherTenant = new CloneGate.ReidentificationAssessment(
                UUID.randomUUID(), T0, UUID.randomUUID(), true, "RIPD-X");
        assertThat(CloneGate.allowed(request(true, false), otherTenant)).isFalse();
    }

    @Test
    @DisplayName("avaliação do RIPD no mesmo instante do pedido NÃO é anterior: bloqueia")
    void sameInstantAssessmentBlocks() {
        // pedido em T0+3600; avaliação exatamente nesse instante não precede o pedido
        assertThat(CloneGate.allowed(request(true, false), assessment(true, T0.plusSeconds(3600)))).isFalse();
        assertThat(CloneGate.blockers(request(true, false), assessment(true, T0.plusSeconds(3600))))
                .anyMatch(b -> b.contains("não é anterior"));
        // um segundo antes do pedido, a avaliação volta a liberar
        assertThat(CloneGate.allowed(request(true, false), assessment(true, T0.plusSeconds(3599)))).isTrue();
    }

    @Test
    @DisplayName("linhagem completa: aplicado sem linhagem, órfã e duplicada são nomeados")
    void lineageCheck() {
        UUID batch = UUID.randomUUID();
        MigrationLineage.Entry e1 = new MigrationLineage.Entry(batch, "GSAN", "orig-1",
                "consumer_units", "unit-1", "gsan-v1", "1");
        MigrationLineage.Check ok = MigrationLineage.verify(List.of(e1), Set.of("consumer_units|unit-1"));
        assertThat(ok.complete()).isTrue();

        MigrationLineage.Check missing = MigrationLineage.verify(List.of(e1),
                Set.of("consumer_units|unit-1", "consumer_units|unit-2"));
        assertThat(missing.complete()).isFalse();
        assertThat(missing.appliedWithoutLineage()).containsExactly("consumer_units|unit-2");

        MigrationLineage.Check orphan = MigrationLineage.verify(List.of(e1), Set.of());
        assertThat(orphan.lineageWithoutApplied()).containsExactly("consumer_units|unit-1");

        MigrationLineage.Check dup = MigrationLineage.verify(List.of(e1, e1),
                Set.of("consumer_units|unit-1"));
        assertThat(dup.duplicateLineage()).contains("GSAN|orig-1");
        assertThat(dup.complete()).isFalse();

        MigrationLineage.Entry otherSource = new MigrationLineage.Entry(batch, "GSAN", "orig-2",
                "consumer_units", "unit-1", "gsan-v1", "1");
        MigrationLineage.Check dupTarget = MigrationLineage.verify(List.of(e1, otherSource),
                Set.of("consumer_units|unit-1"));
        assertThat(dupTarget.duplicateLineage()).containsExactly("DESTINO_REPETIDO:consumer_units|unit-1");
        assertThat(dupTarget.complete()).isFalse();
    }

    @Test
    @DisplayName("simulação: jornada completa em 45 dias com tenant sintético conclui dentro do plano de 60")
    void simulationWithinPlan() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        List<ImplantationSimulation.StepRun> runs = new ArrayList<>();
        int day = 5;
        for (String key : ImplantationPlan.MANDATORY_STEP_KEYS) {
            runs.add(new ImplantationSimulation.StepRun(key, start.plusDays(day)));
            day += 3;
        }
        ImplantationSimulation.Result r = ImplantationSimulation.evaluate(true, start, runs);
        assertThat(r.concludedWithinPlan()).isTrue();
        assertThat(r.elapsedDays()).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("simulação: passo desconhecido é nomeado e não move o relógio do ensaio")
    void simulationUnknownStepNamedAndIgnoredByClock() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        List<ImplantationSimulation.StepRun> runs = new ArrayList<>();
        for (String key : ImplantationPlan.MANDATORY_STEP_KEYS) {
            runs.add(new ImplantationSimulation.StepRun(key, start.plusDays(9)));
        }
        runs.add(new ImplantationSimulation.StepRun("PASSO_DESCONHECIDO", start.plusDays(79)));
        ImplantationSimulation.Result r = ImplantationSimulation.evaluate(true, start, runs);
        assertThat(r.concludedWithinPlan()).isFalse();
        assertThat(r.failures()).anyMatch(f -> f.contains("PASSO_DESCONHECIDO"));
        // o relógio corre só sobre os passos do plano: 10 dias, não 80
        assertThat(r.elapsedDays()).isEqualTo(10);
        assertThat(r.failures()).noneMatch(f -> f.contains("levou"));
    }

    @Test
    @DisplayName("simulação: passo obrigatório executado duas vezes é nomeado como duplicado")
    void simulationDuplicateStepNamed() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        List<ImplantationSimulation.StepRun> runs = new ArrayList<>();
        for (String key : ImplantationPlan.MANDATORY_STEP_KEYS) {
            runs.add(new ImplantationSimulation.StepRun(key, start.plusDays(9)));
        }
        runs.add(new ImplantationSimulation.StepRun(ImplantationPlan.STEP_TRAINING, start.plusDays(19)));
        ImplantationSimulation.Result r = ImplantationSimulation.evaluate(true, start, runs);
        assertThat(r.concludedWithinPlan()).isFalse();
        assertThat(r.failures()).anyMatch(f -> f.contains("mais de uma vez")
                && f.contains(ImplantationPlan.STEP_TRAINING));
        // vale a primeira conclusão: o relógio não anda para o dia da duplicata
        assertThat(r.elapsedDays()).isEqualTo(10);
    }

    @Test
    @DisplayName("simulação: tenant real, passo faltando e estouro de 60 dias reprovam com motivos")
    void simulationFailures() {
        LocalDate start = LocalDate.of(2026, 2, 1);
        List<ImplantationSimulation.StepRun> runs = new ArrayList<>();
        for (String key : ImplantationPlan.MANDATORY_STEP_KEYS) {
            if (!key.equals(ImplantationPlan.STEP_TRAINING)) {
                runs.add(new ImplantationSimulation.StepRun(key, start.plusDays(70)));
            }
        }
        ImplantationSimulation.Result r = ImplantationSimulation.evaluate(false, start, runs);
        assertThat(r.concludedWithinPlan()).isFalse();
        assertThat(r.failures()).anyMatch(f -> f.contains("sintético"));
        assertThat(r.failures()).anyMatch(f -> f.contains(ImplantationPlan.STEP_TRAINING));
        assertThat(r.failures()).anyMatch(f -> f.contains("60"));
    }
}
