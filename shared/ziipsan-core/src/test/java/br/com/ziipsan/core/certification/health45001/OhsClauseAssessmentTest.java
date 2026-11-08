package br.com.ziipsan.core.certification.health45001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OhsClauseAssessmentTest {

    private static final Iso45001Activation ON =
            Iso45001Activation.enabled("SYN-escopo", UUID.randomUUID(), UUID.randomUUID());
    private static final UUID BY = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 1, 31);

    @Test
    @DisplayName("título literal é sempre o marcador [A DEFINIR] — sem caminho de preenchimento")
    void literalTitleAlwaysPending() {
        OhsClauseAssessment a = OhsClauseAssessment.assess(ON, "SYN.1", "SYN resumo proprio",
                OhsClauseAssessment.Conformity.PARTIAL, List.of(), "", BY, DAY);
        assertThat(a.literalTitle()).isEqualTo("[A DEFINIR]");
        assertThat(a.titlePending()).isTrue();
        assertThat(a.toJson()).contains("[A DEFINIR]")
                .contains(OhsClauseAssessment.SELF_ASSESSMENT_NOTICE);
    }

    @Test
    @DisplayName("CONFORME sem evidência é recusado")
    void conformingNeedsEvidence() {
        assertThatThrownBy(() -> OhsClauseAssessment.assess(ON, "SYN.1", "SYN",
                OhsClauseAssessment.Conformity.CONFORMING, List.of(), "", BY, DAY))
                .isInstanceOf(IllegalArgumentException.class);
        OhsClauseAssessment ok = OhsClauseAssessment.assess(ON, "SYN.1", "SYN",
                OhsClauseAssessment.Conformity.CONFORMING,
                List.of(new OhsClauseAssessment.EvidenceRef(
                        OhsClauseAssessment.EvidenceKind.HAZARD, "SYN-HAZ-1")), "", BY, DAY);
        assertThat(ok.evidence()).hasSize(1);
    }

    @Test
    @DisplayName("NÃO_APLICÁVEL sem justificativa é recusado")
    void notApplicableNeedsJustification() {
        assertThatThrownBy(() -> OhsClauseAssessment.assess(ON, "SYN.2", "SYN",
                OhsClauseAssessment.Conformity.NOT_APPLICABLE, List.of(), " ", BY, DAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("referência com texto livre é recusada (só número de cláusula)")
    void freeTextReferenceRefused() {
        assertThatThrownBy(() -> OhsClauseAssessment.assess(ON, "clausula com texto!", "SYN",
                OhsClauseAssessment.Conformity.PARTIAL, List.of(), "", BY, DAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("comparação de edições: incluídas, removidas e mantidas por referência")
    void editionComparison() {
        OhsClauseAssessment.EditionComparison c = OhsClauseAssessment.compareEditions(
                List.of("SYN.1", "SYN.2", "SYN.3"), List.of("SYN.2", "SYN.3", "SYN.4"));
        assertThat(c.included()).containsExactly("SYN.4");
        assertThat(c.removed()).containsExactly("SYN.1");
        assertThat(c.retained()).containsExactly("SYN.2", "SYN.3");
    }

    @Test
    @DisplayName("norma desligada: nem pela fábrica nem pelo construtor canônico")
    void disabledRefused() {
        Iso45001Activation off = Iso45001Activation.disabled();
        assertThatThrownBy(() -> OhsClauseAssessment.assess(off, "SYN.1", "SYN",
                OhsClauseAssessment.Conformity.PARTIAL, List.of(), "", BY, DAY))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OhsClauseAssessment(off, "SYN.1", "SYN",
                OhsClauseAssessment.Conformity.PARTIAL, List.of(), "", BY, DAY))
                .isInstanceOf(IllegalStateException.class);
    }
}
