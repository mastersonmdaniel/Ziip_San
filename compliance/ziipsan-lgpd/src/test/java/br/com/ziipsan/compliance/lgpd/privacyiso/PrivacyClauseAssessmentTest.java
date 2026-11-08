package br.com.ziipsan.compliance.lgpd.privacyiso;

import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da ED-74: mapa de cláusulas/controles como autoavaliação, em
 * palavras próprias e por referência, ligado como evidência aos
 * registros da P3.40 e da P3.10 — sem texto da norma.
 */
class PrivacyClauseAssessmentTest {

    private static final UUID ASSESSOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);

    private static Iso27701Activation on() {
        return Iso27701Activation.enabled("2025", Iso27701Activation.EditionUse.TARGET,
                "escopo", "sgpi");
    }

    private static PrivacyClauseAssessment.EvidenceRef ropa() {
        return new PrivacyClauseAssessment.EvidenceRef(
                PrivacyClauseAssessment.EvidenceKind.PROCESSING_RECORD, "FATURAMENTO");
    }

    @Test
    @DisplayName("conforme com evidência da P3.40 ligada é aceito, nas três audiências")
    void conformingWithEvidence() {
        for (var audience : PrivacyClauseAssessment.Audience.values()) {
            var a = PrivacyClauseAssessment.assess(on(), "7.2", null, audience,
                    PrivacyClauseAssessment.Conformity.CONFORMING, List.of(ropa()), "",
                    ASSESSOR, DAY);
            assertThat(a.audience()).isEqualTo(audience);
        }
    }

    @Test
    @DisplayName("conforme sem evidência é recusado: conformidade sem prova não é avaliação")
    void conformingWithoutEvidenceRefused() {
        assertThatThrownBy(() -> PrivacyClauseAssessment.assess(on(), "7.2", null,
                PrivacyClauseAssessment.Audience.COMMON,
                PrivacyClauseAssessment.Conformity.CONFORMING, List.of(), "", ASSESSOR, DAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("não aplicável sem justificativa é recusado; com justificativa é aceito")
    void notApplicableNeedsJustification() {
        assertThatThrownBy(() -> PrivacyClauseAssessment.assess(on(), "8.1", null,
                PrivacyClauseAssessment.Audience.PROCESSOR,
                PrivacyClauseAssessment.Conformity.NOT_APPLICABLE, List.of(), " ", ASSESSOR, DAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PrivacyClauseAssessment.assess(on(), "8.1", null,
                PrivacyClauseAssessment.Audience.PROCESSOR,
                PrivacyClauseAssessment.Conformity.NOT_APPLICABLE, List.of(),
                "tenant não trata como operador neste escopo", ASSESSOR, DAY).conformity())
                .isEqualTo(PrivacyClauseAssessment.Conformity.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("referência é só número: texto solto como cláusula é recusado")
    void referenceIsNumberOnly() {
        assertThatThrownBy(() -> PrivacyClauseAssessment.assess(on(), "cláusula sete ponto dois",
                null, PrivacyClauseAssessment.Audience.COMMON,
                PrivacyClauseAssessment.Conformity.PARTIAL, List.of(), "", ASSESSOR, DAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("evidência de incidente/RIPD da P3.10 liga pelo tipo, sem copiar conteúdo")
    void incidentEvidenceLinks() {
        var a = PrivacyClauseAssessment.assess(on(), "6.3", "6.3.1",
                PrivacyClauseAssessment.Audience.COMMON,
                PrivacyClauseAssessment.Conformity.PARTIAL,
                List.of(new PrivacyClauseAssessment.EvidenceRef(
                        PrivacyClauseAssessment.EvidenceKind.DPIA, "RIPD-2026-01")),
                "", ASSESSOR, DAY);
        assertThat(a.evidence()).hasSize(1);
        assertThat(a.controlRef()).isEqualTo("6.3.1");
    }

    @Test
    @DisplayName("JSON tem lista fechada de chaves, sem campo capaz de guardar texto da norma")
    void jsonHasClosedKeysAndNotice() {
        var a = PrivacyClauseAssessment.assess(on(), "7.2", null,
                PrivacyClauseAssessment.Audience.CONTROLLER,
                PrivacyClauseAssessment.Conformity.CONFORMING, List.of(ropa()), "",
                ASSESSOR, DAY);
        Map<String, Object> map = Json.readMap(a.toJson());
        assertThat(map.keySet()).isEqualTo(Set.of("clauseNumber", "controlRef", "audience",
                "conformity", "evidence", "justification", "assessedBy", "assessedOn", "notice"));
        assertThat(map.get("notice")).isEqualTo(PrivacyClauseAssessment.SELF_ASSESSMENT_NOTICE);
        assertThat(String.valueOf(map.get("notice"))).contains("autoavaliação");
    }

    @Test
    @DisplayName("referências respeitam os tetos do SQL pendente (40/200)")
    void referenceLengthsRespectSql() {
        assertThatThrownBy(() -> PrivacyClauseAssessment.assess(on(), "1.".repeat(21) + "1",
                null, PrivacyClauseAssessment.Audience.COMMON,
                PrivacyClauseAssessment.Conformity.PARTIAL, List.of(), "", ASSESSOR, DAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PrivacyClauseAssessment.EvidenceRef(
                PrivacyClauseAssessment.EvidenceKind.OTHER, "X".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("com a norma desligada, nenhuma avaliação nasce")
    void disabledRefuses() {
        assertThatThrownBy(() -> PrivacyClauseAssessment.assess(Iso27701Activation.disabled(),
                "7.2", null, PrivacyClauseAssessment.Audience.COMMON,
                PrivacyClauseAssessment.Conformity.PARTIAL, List.of(), "", ASSESSOR, DAY))
                .isInstanceOf(IllegalStateException.class);
    }
}
