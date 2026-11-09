package br.com.ziipsan.core.certification.external;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fluxo de aceite de ponta a ponta da ED-75. */
class Ed75AcceptanceTest {

    @Test
    void fullFlowAuditorAndRegulatorSideBySide() {
        ExternalAccessService service = new ExternalAccessService();
        // Pacote multi-normas (subconjunto de 3 das 6 normas ligadas),
        // so com dado agregado/mascarado/pseudonimizado e com a secao
        // completa de evidencia do Ziip como fornecedor.
        EvidencePack pack = TestSupport.packOf(
                Set.of(CertificationStandard.ISO_9001, CertificationStandard.ISO_45001,
                        CertificationStandard.ISO_27701),
                TestSupport.item(CertificationStandard.ISO_9001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-qms", TestSupport.SHA_A),
                TestSupport.item(CertificationStandard.ISO_45001,
                        EvidenceItem.DataKind.MASKED, "SYN-sst", TestSupport.SHA_B),
                TestSupport.item(CertificationStandard.ISO_27701,
                        EvidenceItem.DataKind.PSEUDONYMIZED, "SYN-sgpi", TestSupport.SHA_C));
        assertThat(pack.isSupplierEvidenceComplete()).isTrue();
        assertThat(pack.canonicalManifest()).contains("supplierEvidence");

        ExternalAccessGrant auditor = TestSupport.auditorGrant(
                Set.of(CertificationStandard.ISO_9001, CertificationStandard.ISO_45001,
                        CertificationStandard.ISO_27701), true);
        String manifest = service.readEvidencePack(auditor, pack,
                Set.of(CertificationStandard.ISO_9001, CertificationStandard.ISO_45001,
                        CertificationStandard.ISO_27701), TestSupport.NOW);
        assertThat(EvidencePack.verifyManifestHash(manifest, pack.manifestHash())).isTrue();

        ExternalAccessGrant regulator = TestSupport.regulatorGrant(true);
        assertThat(service.readRegulatory(regulator, ExternalResource.REGULATORY_REPORT,
                "SYN-relatorio", TestSupport.NOW)).startsWith("READ:");
        assertThatThrownBy(() -> service.readEvidencePack(regulator, pack,
                CertificationStandard.all(), TestSupport.NOW))
                .isInstanceOf(SecurityException.class);

        // 1 leitura concedida do auditor + 1 do regulador + 1 negada.
        assertThat(service.readLog()).hasSize(3);
        assertThat(service.readLog().get(2).outcome())
                .isEqualTo(ExternalReadLogEntry.Outcome.DENIED);
    }
}
