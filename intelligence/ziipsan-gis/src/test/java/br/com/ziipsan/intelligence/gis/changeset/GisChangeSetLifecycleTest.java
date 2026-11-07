package br.com.ziipsan.intelligence.gis.changeset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIS-04 (P2.22): regras puras do pacote e do ciclo de vida — o espelho
 * Java das guardas da V13_9. O comportamento no banco (inclusive por SQL
 * direto) é provado pela Gis04ChangeSetIT; aqui ficam as tabelas de
 * transição completas, baratas de conferir.
 */
class GisChangeSetLifecycleTest {

    @Test
    @DisplayName("pacote: caminho feliz DRAFT -> SUBMITTED -> IN_REVIEW -> APPLIED")
    void changeSetHappyPath() {
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("DRAFT", "SUBMITTED")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("SUBMITTED", "IN_REVIEW")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("IN_REVIEW", "APPROVED")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("IN_REVIEW", "APPLIED")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("APPROVED", "APPLIED")).isTrue();
    }

    @Test
    @DisplayName("pacote: devolução, rejeição e cancelamento nos estados certos")
    void changeSetReviewOutcomes() {
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("IN_REVIEW", "RETURNED")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("IN_REVIEW", "REJECTED")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("RETURNED", "SUBMITTED")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("DRAFT", "CANCELLED")).isTrue();
    }

    @Test
    @DisplayName("pacote: estados terminais não saem; saltos são recusados")
    void changeSetForbidden() {
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("APPLIED", "DRAFT")).isFalse();
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("REJECTED", "SUBMITTED")).isFalse();
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("CANCELLED", "DRAFT")).isFalse();
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("DRAFT", "APPLIED")).isFalse();
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("DRAFT", "APPROVED")).isFalse();
        assertThat(GisChangeSetLifecycle.canTransitionChangeSet("APPROVED", "RETURNED")).isFalse();
    }

    @Test
    @DisplayName("feição: transições do §3.7 e só elas")
    void featureLifecycle() {
        assertThat(GisChangeSetLifecycle.canTransitionFeature("PLANNED", "UNDER_CONSTRUCTION")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionFeature("PLANNED", "IN_SERVICE")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionFeature("PLANNED", "ABANDONED")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionFeature("UNDER_CONSTRUCTION", "IN_SERVICE")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionFeature("IN_SERVICE", "ABANDONED")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionFeature("IN_SERVICE", "IN_SERVICE")).isTrue();
        assertThat(GisChangeSetLifecycle.canTransitionFeature("IN_SERVICE", "PLANNED")).isFalse();
        assertThat(GisChangeSetLifecycle.canTransitionFeature("ABANDONED", "IN_SERVICE")).isFalse();
        assertThat(GisChangeSetLifecycle.canTransitionFeature("UNDER_CONSTRUCTION", "ABANDONED")).isFalse();
    }

    @Test
    @DisplayName("projetada e em obra estão fora da rede em operação")
    void operationalOnlyInServiceAndConstruction() {
        assertThat(GisChangeSetLifecycle.isOperational("IN_SERVICE")).isTrue();
        assertThat(GisChangeSetLifecycle.isOperational("UNDER_CONSTRUCTION")).isTrue();
        assertThat(GisChangeSetLifecycle.isOperational("PLANNED")).isFalse();
        assertThat(GisChangeSetLifecycle.isOperational("ABANDONED")).isFalse();
    }

    @Test
    @DisplayName("autor não revisa o próprio pacote; outra pessoa pode")
    void selfReviewForbidden() {
        UUID author = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> GisChangeSetLifecycle.ensureReviewerIsNotAuthor(author, author))
                .isInstanceOf(GisChangeSetLifecycle.SelfReviewException.class);
        GisChangeSetLifecycle.ensureReviewerIsNotAuthor(author, other);
    }
}
