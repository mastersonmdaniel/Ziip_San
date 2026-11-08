package br.com.ziipsan.core.certification.env14001;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmsClauseAssessmentTest {

    @Test
    void selfAssessmentWithEvidenceAndFixedNotice() {
        EmsClauseAssessment a = EmsClauseAssessment.assess(TestSupport.enabledTarget(), "6.1",
                "SYN-resumo em palavras proprias", EmsClauseAssessment.Conformity.CONFORMING,
                List.of(new EmsClauseAssessment.EvidenceRef(
                        EmsClauseAssessment.EvidenceKind.ASPECT, "SYN-aspecto-1")),
                "", TestSupport.ACTOR, LocalDate.of(2026, 1, 10));
        assertThat(a.literalTitle()).isEqualTo("[A DEFINIR]");
        assertThat(a.titlePending()).isTrue();
        assertThat(a.toJson()).contains(EmsClauseAssessment.SELF_ASSESSMENT_NOTICE)
                .contains("\"6.1\"");
    }

    @Test
    void conformingWithoutEvidenceAndNotApplicableWithoutJustificationRefused() {
        assertThatThrownBy(() -> EmsClauseAssessment.assess(TestSupport.enabledTarget(), "6.1",
                "SYN-r", EmsClauseAssessment.Conformity.CONFORMING, List.of(), "",
                TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmsClauseAssessment.assess(TestSupport.enabledTarget(), "6.1",
                "SYN-r", EmsClauseAssessment.Conformity.NOT_APPLICABLE, List.of(), "",
                TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editionComparisonForThe2015To2026Transition() {
        EmsClauseAssessment.EditionComparison cmp = EmsClauseAssessment.compareEditions(
                List.of("4.1", "6.1", "9.3"), List.of("4.1", "6.1", "6.2", "10.2"));
        // Ordem lexicografica do TreeSet da regua (como na ED-71): "10.2" < "6.2".
        assertThat(cmp.included()).containsExactly("10.2", "6.2");
        assertThat(cmp.removed()).containsExactly("9.3");
        assertThat(cmp.retained()).containsExactly("4.1", "6.1");
    }

    @Test
    void disabledNormRefusesAssessmentAndBadReferenceRefused() {
        assertThatThrownBy(() -> EmsClauseAssessment.assess(TestSupport.disabled(), "6.1",
                "SYN-r", EmsClauseAssessment.Conformity.PARTIAL, List.of(), "",
                TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EmsClauseAssessment.assess(TestSupport.enabledTarget(),
                "clausula seis ponto um", "SYN-r", EmsClauseAssessment.Conformity.PARTIAL,
                List.of(), "", TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
