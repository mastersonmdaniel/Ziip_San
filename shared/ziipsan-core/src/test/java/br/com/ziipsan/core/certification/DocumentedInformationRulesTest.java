package br.com.ziipsan.core.certification;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.25 pure-rule tests (no database: the live cycle is proven by
 * DocumentedInformationIT in the app module).
 *
 * <p>Coverage: the closure rule of
 * {@link DocumentedInformationService#closeBlockers} — free records
 * close without requirements, an active clause link requires root
 * cause, one done action and effectiveness verification, and an
 * inspection occurrence additionally requires its action plan, its
 * attendance evidence and zero open findings — plus the closed
 * catalogs of kinds, origins, authorities and link kinds.
 */
class DocumentedInformationRulesTest {

    @Test
    @DisplayName("registro livre fecha sem exigências, mesmo sem causa, ação ou verificação")
    void freeRecordClosesWithoutRequirements() {
        assertThat(DocumentedInformationService.closeBlockers(
                false, false, false, 0, false, false, false, 0)).isEmpty();
    }

    @Test
    @DisplayName("vínculo ativo exige causa raiz, ação concluída e verificação de eficácia")
    void activeClauseLinkRequiresRootCauseActionAndEffectiveness() {
        assertThat(DocumentedInformationService.closeBlockers(
                true, false, false, 0, false, false, false, 0))
                .containsExactly("rootCause", "action", "effectiveness");
        assertThat(DocumentedInformationService.closeBlockers(
                true, false, true, 0, false, false, false, 0))
                .containsExactly("action", "effectiveness");
        assertThat(DocumentedInformationService.closeBlockers(
                true, false, true, 1, false, false, false, 0))
                .containsExactly("effectiveness");
        assertThat(DocumentedInformationService.closeBlockers(
                true, false, true, 2, true, false, false, 0)).isEmpty();
    }

    @Test
    @DisplayName("fiscalização exige plano de ação, evidência de atendimento e constatações atendidas")
    void inspectionRequiresPlanEvidenceAndClosedFindings() {
        assertThat(DocumentedInformationService.closeBlockers(
                false, true, false, 0, false, false, false, 2))
                .containsExactly("inspectionActionPlan", "inspectionAttendanceEvidence",
                        "inspectionOpenFindings");
        assertThat(DocumentedInformationService.closeBlockers(
                false, true, false, 0, false, true, true, 0)).isEmpty();
        assertThat(DocumentedInformationService.closeBlockers(
                false, true, false, 0, false, true, false, 1))
                .containsExactly("inspectionAttendanceEvidence", "inspectionOpenFindings");
    }

    @Test
    @DisplayName("vínculo ativo e fiscalização somam as exigências")
    void activeLinkAndInspectionCombineBlockers() {
        assertThat(DocumentedInformationService.closeBlockers(
                true, true, false, 0, false, false, false, 1))
                .containsExactly("rootCause", "action", "effectiveness",
                        "inspectionActionPlan", "inspectionAttendanceEvidence", "inspectionOpenFindings");
    }

    @Test
    @DisplayName("os catálogos fechados recusam valores fora do catálogo")
    void closedCatalogsRefuseUnknownValues() {
        assertThatThrownBy(() -> DocumentedInformationService.OccurrenceKind.from("MULTA"))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> DocumentedInformationService.InspectionAuthority.from("PREFEITURA"))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> DocumentedInformationService.LinkKind.from("EMAIL"))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> DocumentedInformationService.Confidentiality.from("SECRETO"))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("as três origens de fiscalização e os quatro vínculos existem")
    void inspectionOriginsAndLinkKindsAreExactlyTheCatalog() {
        assertThat(List.of(
                DocumentedInformationService.InspectionAuthority.REGULATORY_AGENCY,
                DocumentedInformationService.InspectionAuthority.AUDIT_COURT,
                DocumentedInformationService.InspectionAuthority.ENVIRONMENTAL_AGENCY))
                .containsExactlyInAnyOrder(
                        DocumentedInformationService.InspectionAuthority.from("REGULATORY_AGENCY"),
                        DocumentedInformationService.InspectionAuthority.from("AUDIT_COURT"),
                        DocumentedInformationService.InspectionAuthority.from("ENVIRONMENTAL_AGENCY"));
        assertThat(DocumentedInformationService.LinkKind.from("SECURITY_INCIDENT"))
                .isEqualTo(DocumentedInformationService.LinkKind.SECURITY_INCIDENT);
        assertThat(DocumentedInformationService.OccurrenceOrigin.from(null))
                .isEqualTo(DocumentedInformationService.OccurrenceOrigin.INTERNAL);
        assertThat(DocumentedInformationService.OccurrenceOrigin.from("INSPECTION"))
                .isEqualTo(DocumentedInformationService.OccurrenceOrigin.INSPECTION);
    }
}
