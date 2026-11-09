package br.com.ziipsan.core.certification.external;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite (7) -- TESTE DE ESCOPO POR PERFIL: o perfil regulador NAO le
 * pacote de evidencia nem dado pessoal; o auditor de certificacao le
 * o pacote das normas ligadas e nao excede o escopo (GES-22, JOR-23).
 */
class ExternalScopeByProfileTest {

    @Test
    void scopeMatrixIsExactlyAsSpecified() {
        assertThat(ExternalScope.readableBy(ExternalProfile.CERTIFICATION_AUDITOR))
                .containsExactly(ExternalResource.EVIDENCE_PACK);
        assertThat(ExternalScope.readableBy(ExternalProfile.REGULATOR_SERVICE_OWNER))
                .containsExactlyInAnyOrder(ExternalResource.REGULATORY_DATA,
                        ExternalResource.REGULATORY_REPORT,
                        ExternalResource.PERSONAL_DATA_MINIMUM);
        assertThat(ExternalScope.canRead(ExternalProfile.CERTIFICATION_AUDITOR,
                ExternalResource.PERSONAL_DATA)).isFalse();
        assertThat(ExternalScope.canRead(ExternalProfile.REGULATOR_SERVICE_OWNER,
                ExternalResource.PERSONAL_DATA)).isFalse();
        assertThat(ExternalScope.canRead(null, ExternalResource.REGULATORY_DATA)).isFalse();
    }

    @Test
    void regulatorDoesNotReadEvidencePack() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant regulator = TestSupport.regulatorGrant(true);
        EvidencePack pack = TestSupport.packOf(Set.of(CertificationStandard.ISO_9001),
                TestSupport.item(CertificationStandard.ISO_9001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-qms", TestSupport.SHA_A));
        assertThatThrownBy(() -> service.readEvidencePack(regulator, pack,
                Set.of(CertificationStandard.ISO_9001), TestSupport.NOW))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("nao le EVIDENCE_PACK");
        assertThat(service.readLog()).hasSize(1);
        assertThat(service.readLog().get(0).outcome())
                .isEqualTo(ExternalReadLogEntry.Outcome.DENIED);
        assertThatThrownBy(() -> service.readRegulatory(regulator,
                ExternalResource.EVIDENCE_PACK, "evidence-pack:syn", TestSupport.NOW))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void regulatorDoesNotReadPersonalData() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant regulator = TestSupport.regulatorGrant(true);
        assertThatThrownBy(() -> service.read(regulator, ExternalResource.PERSONAL_DATA,
                "SYN-titular-registro", TestSupport.NOW))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("nao le PERSONAL_DATA");
        assertThat(service.readLog().get(0).outcome())
                .isEqualTo(ExternalReadLogEntry.Outcome.DENIED);
    }

    @Test
    void regulatorPersonalDataViaReadRegulatoryDeniedAndLogged() {
        // Correcao D2 (revisao da ED-75): readRegulatory(PERSONAL_DATA)
        // recusava SEM registrar; agora a negacao e registrada (DENIED
        // com motivo) antes da recusa, como nas demais portas.
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant regulator = TestSupport.regulatorGrant(true);
        assertThatThrownBy(() -> service.readRegulatory(regulator,
                ExternalResource.PERSONAL_DATA, "SYN-titular-registro", TestSupport.NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERSONAL_DATA_MINIMUM");
        assertThat(service.readLog()).hasSize(1);
        ExternalReadLogEntry entry = service.readLog().get(0);
        assertThat(entry.outcome()).isEqualTo(ExternalReadLogEntry.Outcome.DENIED);
        assertThat(entry.resource()).isEqualTo(ExternalResource.PERSONAL_DATA);
        assertThat(entry.profile()).isEqualTo(ExternalProfile.REGULATOR_SERVICE_OWNER);
        assertThat(entry.tenantId()).isEqualTo(TestSupport.TENANT);
        assertThat(entry.readAt()).isEqualTo(TestSupport.NOW);
        assertThat(entry.denialReason()).isNotBlank()
                .contains("dado pessoal nao e legivel por nenhum perfil");
    }

    @Test
    void regulatorReadsRegulatoryScopeAndMinimumPersonal() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant regulator = TestSupport.regulatorGrant(true);
        assertThat(service.readRegulatory(regulator, ExternalResource.REGULATORY_DATA,
                "SYN-dado-regulatorio", TestSupport.NOW)).startsWith("READ:REGULATORY_DATA:");
        assertThat(service.readRegulatory(regulator, ExternalResource.REGULATORY_REPORT,
                "SYN-relatorio-regulatorio", TestSupport.NOW))
                .startsWith("READ:REGULATORY_REPORT:");
        service.read(regulator, ExternalResource.PERSONAL_DATA_MINIMUM,
                "SYN-minimo", TestSupport.NOW);
        assertThat(service.readLog()).hasSize(3);
        assertThat(service.readLog()).allMatch(
                e -> e.outcome() == ExternalReadLogEntry.Outcome.GRANTED);
    }

    @Test
    void auditorReadsPackOfEnabledStandards() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant auditor = TestSupport.auditorGrant(
                Set.of(CertificationStandard.ISO_9001, CertificationStandard.ISO_14001), true);
        EvidencePack pack = TestSupport.packOf(
                Set.of(CertificationStandard.ISO_9001, CertificationStandard.ISO_14001),
                TestSupport.item(CertificationStandard.ISO_9001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-qms", TestSupport.SHA_A),
                TestSupport.item(CertificationStandard.ISO_14001,
                        EvidenceItem.DataKind.MASKED, "SYN-ems", TestSupport.SHA_B));
        String manifest = service.readEvidencePack(auditor, pack,
                Set.of(CertificationStandard.ISO_9001, CertificationStandard.ISO_14001),
                TestSupport.NOW);
        assertThat(manifest).contains("ISO_9001").contains("ISO_14001");
    }

    @Test
    void auditorDoesNotExceedScope() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant auditor = TestSupport.auditorGrant(
                Set.of(CertificationStandard.ISO_9001), true);
        assertThatThrownBy(() -> service.read(auditor, ExternalResource.REGULATORY_DATA,
                "SYN-reg", TestSupport.NOW)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.read(auditor, ExternalResource.REGULATORY_REPORT,
                "SYN-reg", TestSupport.NOW)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.read(auditor,
                ExternalResource.PERSONAL_DATA_MINIMUM, "SYN-min", TestSupport.NOW))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.read(auditor, ExternalResource.PERSONAL_DATA,
                "SYN-pessoal", TestSupport.NOW)).isInstanceOf(SecurityException.class);
        assertThat(service.readLog()).hasSize(4);
        assertThat(service.readLog()).allMatch(
                e -> e.outcome() == ExternalReadLogEntry.Outcome.DENIED);
    }
}
