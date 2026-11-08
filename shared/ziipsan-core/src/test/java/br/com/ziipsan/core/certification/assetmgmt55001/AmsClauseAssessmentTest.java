package br.com.ziipsan.core.certification.assetmgmt55001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmsClauseAssessmentTest {

    @Test
    @DisplayName("CONFORME exige evidência; NÃO_APLICÁVEL exige justificativa")
    void honestyRules() {
        assertThatThrownBy(() -> AmsClauseAssessment.assess(Iso55001Fixtures.on(), "6.1",
                "título próprio", AmsClauseAssessment.Conformity.CONFORMING, List.of(), "",
                Iso55001Fixtures.USER, LocalDate.of(2026, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("evidência");
        assertThatThrownBy(() -> AmsClauseAssessment.assess(Iso55001Fixtures.on(), "6.1",
                "título próprio", AmsClauseAssessment.Conformity.NOT_APPLICABLE, List.of(), " ",
                Iso55001Fixtures.USER, LocalDate.of(2026, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("justificativa");
    }

    @Test
    @DisplayName("título é [A DEFINIR] ou palavras próprias: coerência título × pendência travada")
    void titlePendingCoherence() {
        assertThatThrownBy(() -> new AmsClauseAssessment(Iso55001Fixtures.on(), "6.1",
                "título próprio", true, AmsClauseAssessment.Conformity.PARTIAL, List.of(), "",
                Iso55001Fixtures.USER, LocalDate.of(2026, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SE E SOMENTE SE");
        var pending = AmsClauseAssessment.assess(Iso55001Fixtures.on(), "6.1", "[A DEFINIR]",
                AmsClauseAssessment.Conformity.PARTIAL, List.of(), "",
                Iso55001Fixtures.USER, LocalDate.of(2026, 2, 1));
        assertThat(pending.titlePending()).isTrue();
        assertThat(pending.toJson()).contains(AmsClauseAssessment.SELF_ASSESSMENT_NOTICE);
    }

    @Test
    @DisplayName("referência de cláusula é só o número: texto de norma não entra como referência")
    void referenceOnly() {
        assertThatThrownBy(() -> AmsClauseAssessment.assess(Iso55001Fixtures.on(),
                "6.1 A organização deve", "título próprio",
                AmsClauseAssessment.Conformity.PARTIAL, List.of(), "",
                Iso55001Fixtures.USER, LocalDate.of(2026, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
