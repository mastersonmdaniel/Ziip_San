package br.com.ziipsan.core.certification.infosec27001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da ED-73: mapa de cláusulas como autoavaliação do cliente —
 * referência, título em palavras próprias, situação e evidência; nunca
 * texto da norma nem afirmação de certificação.
 */
class IsmsClauseAssessmentTest {

    private static final Iso27001Activation ON = Iso27001Activation.enabled("escopo SYN",
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            UUID.fromString("00000000-0000-0000-0000-000000000102"));
    private static final UUID ASSESSOR = UUID.fromString("00000000-0000-0000-0000-000000000401");

    @Test
    @DisplayName("CONFORME com evidência nasce e serializa com o aviso de autoavaliação")
    void conformingWithEvidence() {
        IsmsClauseAssessment a = IsmsClauseAssessment.assess(ON, "SYN.4", "contexto em palavras próprias",
                IsmsClauseAssessment.Conformity.CONFORMING,
                List.of(new IsmsClauseAssessment.EvidenceRef(
                        IsmsClauseAssessment.EvidenceKind.DOCUMENT, "SYN-DOC-1")),
                "", ASSESSOR, LocalDate.of(2026, 2, 1));
        assertThat(a.toJson()).contains(IsmsClauseAssessment.SELF_ASSESSMENT_NOTICE)
                .contains("\"clauseNumber\":\"SYN.4\"");
    }

    @Test
    @DisplayName("CONFORME sem evidência e NÃO_APLICÁVEL sem justificativa são recusados")
    void honestyRules() {
        assertThatThrownBy(() -> IsmsClauseAssessment.assess(ON, "SYN.4", "título próprio",
                IsmsClauseAssessment.Conformity.CONFORMING, List.of(), "", ASSESSOR,
                LocalDate.of(2026, 2, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IsmsClauseAssessment.assess(ON, "SYN.4", "título próprio",
                IsmsClauseAssessment.Conformity.NOT_APPLICABLE, List.of(), " ", ASSESSOR,
                LocalDate.of(2026, 2, 1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("NÃO_APLICÁVEL justificado nasce; referência inválida é recusada")
    void notApplicableJustified() {
        IsmsClauseAssessment a = IsmsClauseAssessment.assess(ON, "SYN.4", "título próprio",
                IsmsClauseAssessment.Conformity.NOT_APPLICABLE, List.of(),
                "fora do escopo declarado (sintético)", ASSESSOR, LocalDate.of(2026, 2, 1));
        assertThat(a.conformity()).isEqualTo(IsmsClauseAssessment.Conformity.NOT_APPLICABLE);
        assertThatThrownBy(() -> IsmsClauseAssessment.assess(ON, "cláusula com espaço", "título",
                IsmsClauseAssessment.Conformity.PARTIAL, List.of(), "", ASSESSOR,
                LocalDate.of(2026, 2, 1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("correção D1: construtor canônico da autoavaliação exige ativação ligada")
    void canonicalAssessmentRequiresEnabledActivation() {
        assertThatThrownBy(() -> new IsmsClauseAssessment(Iso27001Activation.disabled(), "SYN.4",
                "título próprio", IsmsClauseAssessment.Conformity.PARTIAL, List.of(), "",
                ASSESSOR, LocalDate.of(2026, 2, 1))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("autoavaliação não nasce com a norma desligada")
    void disabledBlocksAssessment() {
        assertThatThrownBy(() -> IsmsClauseAssessment.assess(Iso27001Activation.disabled(),
                "SYN.4", "título próprio", IsmsClauseAssessment.Conformity.PARTIAL, List.of(), "",
                ASSESSOR, LocalDate.of(2026, 2, 1))).isInstanceOf(IllegalStateException.class);
    }
}
