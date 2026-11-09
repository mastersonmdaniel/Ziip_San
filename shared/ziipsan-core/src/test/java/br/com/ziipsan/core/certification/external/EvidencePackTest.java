package br.com.ziipsan.core.certification.external;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aceite (1): pacote de evidencia so com normas ligadas, subconjuntos variados. */
class EvidencePackTest {

    @Test
    void singleStandardSubsetBuilds() {
        EvidencePack pack = TestSupport.packOf(Set.of(CertificationStandard.ISO_9001),
                TestSupport.item(CertificationStandard.ISO_9001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-qms-sumario", TestSupport.SHA_A));
        assertThat(pack.enabledStandards()).containsExactly(CertificationStandard.ISO_9001);
        assertThat(pack.coveredStandards()).containsExactly(CertificationStandard.ISO_9001);
        assertThat(pack.isSupplierEvidenceComplete()).isTrue();
    }

    @Test
    void twoStandardSubsetBuilds() {
        EvidencePack pack = TestSupport.packOf(
                Set.of(CertificationStandard.ISO_14001, CertificationStandard.ISO_27001),
                TestSupport.item(CertificationStandard.ISO_14001,
                        EvidenceItem.DataKind.MASKED, "SYN-ems-licencas", TestSupport.SHA_A),
                TestSupport.item(CertificationStandard.ISO_27001,
                        EvidenceItem.DataKind.PSEUDONYMIZED, "SYN-sgsi-soa", TestSupport.SHA_B));
        assertThat(pack.coveredStandards()).containsExactlyInAnyOrder(
                CertificationStandard.ISO_14001, CertificationStandard.ISO_27001);
        assertThat(pack.itemsOf(CertificationStandard.ISO_14001)).hasSize(1);
    }

    @Test
    void allSixStandardsBuild() {
        EvidencePack pack = TestSupport.packOf(CertificationStandard.all(),
                TestSupport.item(CertificationStandard.ISO_9001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-1", TestSupport.SHA_A),
                TestSupport.item(CertificationStandard.ISO_14001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-2", TestSupport.SHA_B),
                TestSupport.item(CertificationStandard.ISO_45001,
                        EvidenceItem.DataKind.MASKED, "SYN-3", TestSupport.SHA_C),
                TestSupport.item(CertificationStandard.ISO_55001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-4", TestSupport.SHA_D),
                TestSupport.item(CertificationStandard.ISO_27001,
                        EvidenceItem.DataKind.PSEUDONYMIZED, "SYN-5", TestSupport.SHA_E),
                TestSupport.item(CertificationStandard.ISO_27701,
                        EvidenceItem.DataKind.MASKED, "SYN-6", TestSupport.SHA_F));
        assertThat(pack.enabledStandards()).hasSize(6);
        assertThat(pack.items()).hasSize(6);
    }

    @Test
    void itemOfDisabledStandardRefused() {
        assertThatThrownBy(() -> TestSupport.packOf(Set.of(CertificationStandard.ISO_9001),
                TestSupport.item(CertificationStandard.ISO_45001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-sst", TestSupport.SHA_A)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DESLIGADA");
    }

    @Test
    void noEnabledStandardRefused() {
        assertThatThrownBy(() -> new EvidencePack(Set.of(),
                List.of(TestSupport.item(CertificationStandard.ISO_9001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-x", TestSupport.SHA_A)),
                TestSupport.fullSupplierEvidence()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("norma ligada");
    }

    @Test
    void emptyPackRefused() {
        assertThatThrownBy(() -> new EvidencePack(Set.of(CertificationStandard.ISO_9001),
                List.of(), TestSupport.fullSupplierEvidence()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazio");
    }

    @Test
    void duplicateItemRefused() {
        EvidenceItem item = TestSupport.item(CertificationStandard.ISO_9001,
                EvidenceItem.DataKind.AGGREGATE, "SYN-dup", TestSupport.SHA_A);
        assertThatThrownBy(() -> TestSupport.packOf(Set.of(CertificationStandard.ISO_9001),
                item, item))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicado");
    }

    @Test
    void incompleteSupplierSectionDeclaresItself() {
        EvidencePack pack = new EvidencePack(Set.of(CertificationStandard.ISO_9001),
                List.of(TestSupport.item(CertificationStandard.ISO_9001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-qms", TestSupport.SHA_A)),
                List.of());
        assertThat(pack.isSupplierEvidenceComplete()).isFalse();
        assertThat(pack.missingSupplierKinds()).hasSize(4);
        assertThat(pack.canonicalManifest()).contains("personalData");
    }

    @Test
    void manifestHashVerifiableAndStable() {
        EvidencePack pack = TestSupport.packOf(Set.of(CertificationStandard.ISO_55001),
                TestSupport.item(CertificationStandard.ISO_55001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-ativos", TestSupport.SHA_B));
        assertThat(EvidencePack.verifyManifestHash(pack.canonicalManifest(),
                pack.manifestHash())).isTrue();
        assertThat(EvidencePack.verifyManifestHash(pack.canonicalManifest(),
                TestSupport.SHA_A)).isFalse();
        assertThat(pack.exportJson()).contains(pack.manifestHash());
    }

    // Dobra R5-A (F1-B3): o manifesto declara a cobertura por norma.
    // Norma ligada sem item nao some nem barra o pacote: segue em
    // enabledStandards e fica fora de coveredStandards.

    @Test
    void manifestDeclaresCoveredStandardsWhenStandardHasNoItem() {
        EvidencePack pack = TestSupport.packOf(
                Set.of(CertificationStandard.ISO_9001, CertificationStandard.ISO_14001),
                TestSupport.item(CertificationStandard.ISO_9001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-qms", TestSupport.SHA_A));
        assertThat(pack.coveredStandards()).containsExactly(CertificationStandard.ISO_9001);
        String manifest = pack.canonicalManifest();
        assertThat(manifest).contains("\"enabledStandards\":[\"ISO_14001\",\"ISO_9001\"]");
        assertThat(manifest).contains("\"coveredStandards\":[\"ISO_9001\"]");
    }

    @Test
    void manifestDeclaresFullCoverageWhenAllStandardsHaveItems() {
        EvidencePack pack = TestSupport.packOf(
                Set.of(CertificationStandard.ISO_9001, CertificationStandard.ISO_14001),
                TestSupport.item(CertificationStandard.ISO_9001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-qms", TestSupport.SHA_A),
                TestSupport.item(CertificationStandard.ISO_14001,
                        EvidenceItem.DataKind.MASKED, "SYN-ems", TestSupport.SHA_B));
        assertThat(pack.canonicalManifest())
                .contains("\"coveredStandards\":[\"ISO_14001\",\"ISO_9001\"]");
    }
}
