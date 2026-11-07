package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.DetectedIssue;
import br.com.ziipsan.intelligence.gis.validation.GisValidationReconciliation.ExistingIssue;
import br.com.ziipsan.intelligence.gis.validation.GisValidationReconciliation.IssueKey;
import br.com.ziipsan.intelligence.gis.validation.GisValidationReconciliation.Plan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GisValidationReconciliation (correção I1+I2 da revisão da P2.21): as
 * transições de estado da reconciliação, sem banco — OPEN some → RESOLVED;
 * ACCEPTED some → RESOLVED; ACCEPTED persiste → fica; RESOLVED volta →
 * novo OPEN; chave duplicada na rodada → um só, com a primeira severidade.
 */
class GisValidationReconciliationTest {

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", n));
    }

    private static DetectedIssue detected(String severity) {
        return new DetectedIssue("NODE_WITHOUT_ARC", severity, "gis_node", id(10), null, null);
    }

    private static ExistingIssue existing(String status) {
        return new ExistingIssue(id(99), IssueKey.of(detected("ERROR")), status);
    }

    @Test
    @DisplayName("problema novo abre OPEN; OPEN que persiste não abre nem resolve")
    void newOpensAndPersistingOpenStays() {
        Plan plan = GisValidationReconciliation.reconcile(
                List.of(existing("OPEN")), List.of(detected("ERROR")));
        assertThat(plan.toOpen()).isEmpty();
        assertThat(plan.toResolve()).isEmpty();

        Plan fresh = GisValidationReconciliation.reconcile(List.of(), List.of(detected("ERROR")));
        assertThat(fresh.toOpen()).containsExactly(detected("ERROR"));
        assertThat(fresh.toResolve()).isEmpty();
    }

    @Test
    @DisplayName("OPEN cuja condição sumiu vira RESOLVED")
    void openThatDisappearsResolves() {
        Plan plan = GisValidationReconciliation.reconcile(List.of(existing("OPEN")), List.of());
        assertThat(plan.toOpen()).isEmpty();
        assertThat(plan.toResolve()).containsExactly(existing("OPEN"));
    }

    @Test
    @DisplayName("ACCEPTED cuja condição sumiu vira RESOLVED (mantendo o aceite, no serviço)")
    void acceptedThatDisappearsResolves() {
        Plan plan = GisValidationReconciliation.reconcile(List.of(existing("ACCEPTED")), List.of());
        assertThat(plan.toOpen()).isEmpty();
        assertThat(plan.toResolve()).containsExactly(existing("ACCEPTED"));
    }

    @Test
    @DisplayName("ACCEPTED cuja condição persiste continua ACCEPTED: não duplica nem resolve")
    void acceptedThatPersistsStays() {
        Plan plan = GisValidationReconciliation.reconcile(
                List.of(existing("ACCEPTED")), List.of(detected("ERROR")));
        assertThat(plan.toOpen()).isEmpty();
        assertThat(plan.toResolve()).isEmpty();
    }

    @Test
    @DisplayName("condição que volta após RESOLVED abre OPEN novo")
    void resolvedThatReturnsOpensNew() {
        // O serviço nem carrega RESOLVED; se um RESOLVED aparecer na lista,
        // ele também não bloqueia a abertura.
        Plan plan = GisValidationReconciliation.reconcile(
                List.of(existing("RESOLVED")), List.of(detected("ERROR")));
        assertThat(plan.toOpen()).containsExactly(detected("ERROR"));
        assertThat(plan.toResolve()).isEmpty();
    }

    @Test
    @DisplayName("chave duplicada na rodada abre um só, com a primeira severidade detectada")
    void duplicateKeyInRoundOpensOnlyFirst() {
        Plan plan = GisValidationReconciliation.reconcile(
                List.of(), List.of(detected("ERROR"), detected("WARNING")));
        assertThat(plan.toOpen()).singleElement()
                .satisfies(i -> assertThat(i.severity()).isEqualTo("ERROR"));
    }
}
