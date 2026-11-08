package br.com.ziipsan.core.certification.infosec27001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da ED-73: pacote de evidência do fornecedor com a baseline da
 * P3.8 (PLT-08), a exportação verificável da trilha da P1.7 (PLT-19), os
 * documentos da P3.9 e os incidentes da P3.17 — por referência + hash,
 * sem inventar conteúdo de artefato (hashes dos testes são sintéticos).
 */
class SupplierEvidencePackTest {

    private static final Iso27001Activation ON = Iso27001Activation.enabled("escopo SYN",
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            UUID.fromString("00000000-0000-0000-0000-000000000102"));
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);
    private static final String HASH_D = "d".repeat(64);

    private static List<SupplierEvidencePack.PackItem> completeItems() {
        return List.of(
                new SupplierEvidencePack.PackItem(
                        SupplierEvidencePack.ArtifactKind.SECURITY_BASELINE_P38, "SYN-BASELINE-P38", HASH_A),
                new SupplierEvidencePack.PackItem(
                        SupplierEvidencePack.ArtifactKind.AUDIT_TRAIL_EXPORT_P17, "SYN-TRILHA-P17", HASH_B),
                new SupplierEvidencePack.PackItem(
                        SupplierEvidencePack.ArtifactKind.CONTRACT_DOCUMENT_P39, "SYN-MINUTA-P39", HASH_C),
                new SupplierEvidencePack.PackItem(
                        SupplierEvidencePack.ArtifactKind.SECURITY_INCIDENT_P317, "SYN-INC-P317", HASH_D));
    }

    @Test
    @DisplayName("pacote com os quatro tipos do aceite está completo e exporta verificável")
    void completePackExportsVerifiable() {
        SupplierEvidencePack pack = SupplierEvidencePack.assemble(ON, completeItems());
        assertThat(pack.isComplete()).isTrue();
        assertThat(pack.missingKinds()).isEmpty();
        assertThat(SupplierEvidencePack.verifyManifestHash(pack.canonicalManifest(),
                pack.manifestHash())).isTrue();
        assertThat(pack.exportJson()).contains(pack.manifestHash()).contains("SYN-BASELINE-P38");
        assertThat(pack.refsOfKind(SupplierEvidencePack.ArtifactKind.CONTRACT_DOCUMENT_P39))
                .containsExactly("SYN-MINUTA-P39");
    }

    @Test
    @DisplayName("pacote sem um tipo se declara incompleto, nomeando o que falta")
    void incompletePackDeclaresMissing() {
        SupplierEvidencePack pack = SupplierEvidencePack.assemble(ON, completeItems().subList(0, 3));
        assertThat(pack.isComplete()).isFalse();
        assertThat(pack.missingKinds())
                .containsExactly(SupplierEvidencePack.ArtifactKind.SECURITY_INCIDENT_P317);
    }

    @Test
    @DisplayName("hash adulterado não verifica; item sem hash válido é recusado")
    void tamperAndBadHashRefused() {
        SupplierEvidencePack pack = SupplierEvidencePack.assemble(ON, completeItems());
        assertThat(SupplierEvidencePack.verifyManifestHash(pack.canonicalManifest(),
                "0".repeat(64))).isFalse();
        assertThatThrownBy(() -> new SupplierEvidencePack.PackItem(
                SupplierEvidencePack.ArtifactKind.SECURITY_BASELINE_P38, "SYN-X", "nao-e-hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("correção D1: pacote vazio é recusado também no construtor canônico, sem passar pela fábrica")
    void canonicalEmptyPackRefused() {
        assertThatThrownBy(() -> new SupplierEvidencePack(ON, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SupplierEvidencePack(ON, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("correção D1: construtor canônico do pacote exige ativação ligada")
    void canonicalPackRequiresEnabledActivation() {
        assertThatThrownBy(() -> new SupplierEvidencePack(Iso27001Activation.disabled(),
                completeItems())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("pacote vazio, artefato duplicado e norma desligada são recusados")
    void assemblyRefusals() {
        assertThatThrownBy(() -> SupplierEvidencePack.assemble(ON, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        List<SupplierEvidencePack.PackItem> dup = List.of(completeItems().get(0), completeItems().get(0));
        assertThatThrownBy(() -> SupplierEvidencePack.assemble(ON, dup))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SupplierEvidencePack.assemble(Iso27001Activation.disabled(),
                completeItems())).isInstanceOf(IllegalStateException.class);
    }
}
