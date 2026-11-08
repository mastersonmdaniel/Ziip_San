package br.com.ziipsan.core.certification.quality9001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da ED-69: mapa de cláusulas em palavras próprias apresentado
 * como autoavaliação do cliente, sem afirmar certificação.
 * Dados 100% sintéticos (SYN-): nenhum título de cláusula real é usado.
 */
class QualityClauseAssessmentTest {

    private static Iso9001Activation on() {
        return Iso9001Activation.enabled("2026", Iso9001Activation.EditionUse.TARGET,
                "escopo", UUID.randomUUID(), UUID.randomUUID());
    }

    private static QualityClauseAssessment.EvidenceRef docEvidence() {
        return new QualityClauseAssessment.EvidenceRef(
                QualityClauseAssessment.EvidenceKind.DOCUMENT, "SYN-DOC-1");
    }

    @Test
    @DisplayName("avaliação conforme com evidência nasce e o JSON carrega o aviso de autoavaliação")
    void conformingWithEvidenceSerializesWithNotice() {
        QualityClauseAssessment a = QualityClauseAssessment.assess(on(), "SYN.1",
                "título sintético em palavras próprias",
                QualityClauseAssessment.Conformity.CONFORMING, List.of(docEvidence()), "",
                UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        assertThat(a.toJson()).contains("SYN.1")
                .contains("autoavaliação do cliente")
                .contains("não afirma certificação");
    }

    @Test
    @DisplayName("conforme sem evidência é recusado: conformidade sem prova não é avaliação")
    void conformingWithoutEvidenceRefused() {
        assertThatThrownBy(() -> QualityClauseAssessment.assess(on(), "SYN.1", "título",
                QualityClauseAssessment.Conformity.CONFORMING, List.of(), "",
                UUID.randomUUID(), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("não aplicável sem justificativa é recusado; com justificativa, nasce")
    void notApplicableNeedsJustification() {
        assertThatThrownBy(() -> QualityClauseAssessment.assess(on(), "SYN.2", "título",
                QualityClauseAssessment.Conformity.NOT_APPLICABLE, List.of(), " ",
                UUID.randomUUID(), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(QualityClauseAssessment.assess(on(), "SYN.2", "título",
                QualityClauseAssessment.Conformity.NOT_APPLICABLE, List.of(),
                "justificativa sintética", UUID.randomUUID(), LocalDate.of(2026, 3, 1))
                .conformity()).isEqualTo(QualityClauseAssessment.Conformity.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("referência de cláusula com texto (espaços/frase) é recusada: só o número entra")
    void clauseTextRefused() {
        assertThatThrownBy(() -> QualityClauseAssessment.assess(on(), "cláusula sobre contexto",
                "título", QualityClauseAssessment.Conformity.PARTIAL, List.of(), "",
                UUID.randomUUID(), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem título em palavras próprias, a avaliação é recusada")
    void ownWordsTitleIsRequired() {
        assertThatThrownBy(() -> QualityClauseAssessment.assess(on(), "SYN.1", " ",
                QualityClauseAssessment.Conformity.PARTIAL, List.of(), "",
                UUID.randomUUID(), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("com a norma desligada, autoavaliação não nasce")
    void disabledBlocksAssessment() {
        assertThatThrownBy(() -> QualityClauseAssessment.assess(Iso9001Activation.disabled(),
                "SYN.1", "título", QualityClauseAssessment.Conformity.PARTIAL, List.of(), "",
                UUID.randomUUID(), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("registro criado com a norma ligada sobrevive ao desligamento (objeto não é apagado)")
    void recordSurvivesDeactivation() {
        QualityClauseAssessment a = QualityClauseAssessment.assess(on(), "SYN.1", "título",
                QualityClauseAssessment.Conformity.PARTIAL, List.of(), "",
                UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        Iso9001Activation off = Iso9001Activation.disabled();
        assertThat(off.enabled()).isFalse();
        assertThat(a.clauseNumber()).isEqualTo("SYN.1");
        assertThat(a.toJson()).contains("PARTIAL");
    }
}
