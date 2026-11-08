package br.com.ziipsan.intelligence.losses.nr15;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Nr15ApplicabilityTest {

    private static Nr15Applicability applicabilityOf(RegulatoryProfilePort.ProfileView viewOrNull) {
        RegulatoryProfilePort port = tenantId -> viewOrNull == null
                ? java.util.Optional.empty() : java.util.Optional.of(viewOrNull);
        return new Nr15Applicability(port);
    }

    private static RegulatoryProfilePort.ProfileView view(String status, List<String> acts) {
        return new RegulatoryProfilePort.ProfileView("AGENCIA-TESTE",
                Map.of("NR15", new RegulatoryProfilePort.Adoption(status, acts)));
    }

    @Test
    @DisplayName("agencia ADOTA a NR 15 com ato publicado: tenant aplicavel")
    void adoptedWithActApplies() {
        var assessment = applicabilityOf(view("ADOPTED", List.of("ATO-TESTE-01")))
                .assess(Nr15Fixtures.TENANT);
        assertThat(assessment.applicable()).isTrue();
        assertThat(assessment.reason()).isEqualTo(Nr15Applicability.Reason.APPLICABLE);
        assertThat(assessment.agencyCodeOrNull()).isEqualTo("AGENCIA-TESTE");
        assertThat(assessment.adoptionActs()).containsExactly("ATO-TESTE-01");
    }

    @Test
    @DisplayName("adocao PARTIAL mas com ato publicado tambem aplica (ha ato da ERI)")
    void partialWithActApplies() {
        var assessment = applicabilityOf(view("PARTIAL", List.of("ATO-TESTE-02")))
                .assess(Nr15Fixtures.TENANT);
        assertThat(assessment.applicable()).isTrue();
    }

    @Test
    @DisplayName("NO_ACT_LOCATED (situacao atual da NR15 nos perfis ARIS/AGIR da P2.31): nao aplicavel")
    void noActLocatedDoesNotApply() {
        var assessment = applicabilityOf(view("NO_ACT_LOCATED", List.of()))
                .assess(Nr15Fixtures.TENANT);
        assertThat(assessment.applicable()).isFalse();
        assertThat(assessment.reason()).isEqualTo(Nr15Applicability.Reason.NR15_WITHOUT_ADOPTION_ACT);
    }

    @Test
    @DisplayName("perfil sem entrada NR15 e tenant sem perfil: nao aplicavel, motivos distintos")
    void missingEntryAndMissingProfile() {
        var withoutNr15 = applicabilityOf(new RegulatoryProfilePort.ProfileView("AGENCIA-TESTE",
                Map.of("NR9", new RegulatoryProfilePort.Adoption("ADOPTED", List.of("ATO-TESTE-03")))))
                .assess(Nr15Fixtures.TENANT);
        assertThat(withoutNr15.reason()).isEqualTo(Nr15Applicability.Reason.NR15_NOT_IN_PROFILE);
        var withoutProfile = applicabilityOf(null).assess(UUID.randomUUID());
        assertThat(withoutProfile.reason()).isEqualTo(Nr15Applicability.Reason.NO_REGULATORY_PROFILE);
    }

    @Test
    @DisplayName("requireApplicable recusa nomeada para tenant nao aplicavel")
    void requireRefuses() {
        assertThatThrownBy(() -> applicabilityOf(view("NO_ACT_LOCATED", List.of()))
                .requireApplicable(Nr15Fixtures.TENANT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NR15_NOT_APPLICABLE")
                .hasMessageContaining("NR15_WITHOUT_ADOPTION_ACT");
    }

    @Test
    @DisplayName("servico de declaracao nem monta saida para tenant nao aplicavel")
    void declarationServiceBlocksNonApplicable() {
        var service = Nr15Fixtures.declarationService(
                Nr15Fixtures.profilePort(Map.of(Nr15Fixtures.TENANT,
                        view("NO_ACT_LOCATED", List.of()))),
                Nr15Fixtures.yearSnapshots(), Nr15Fixtures.producedPort(Nr15Fixtures.PRODUCED));
        assertThatThrownBy(() -> service.declare(Nr15Fixtures.TENANT, Nr15Fixtures.YEAR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NR15_NOT_APPLICABLE");
    }
}
