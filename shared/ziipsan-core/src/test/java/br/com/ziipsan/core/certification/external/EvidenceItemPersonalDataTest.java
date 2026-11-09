package br.com.ziipsan.core.certification.external;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aceite (2): recusa/trava de dado pessoal no pacote (classe a, travado). */
class EvidenceItemPersonalDataTest {

    @Test
    void personalDataKindRefusedByConstructor() {
        assertThatThrownBy(() -> new EvidenceItem(CertificationStandard.ISO_9001,
                EvidenceItem.DataKind.PERSONAL, "SYN-cpf-do-titular", TestSupport.SHA_A))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dado pessoal")
                .hasMessageContaining("cert.evidence_pack_personal_data");
    }

    @Test
    void aggregateMaskedPseudonymizedAdmitted() {
        for (EvidenceItem.DataKind kind : new EvidenceItem.DataKind[]{
                EvidenceItem.DataKind.AGGREGATE, EvidenceItem.DataKind.MASKED,
                EvidenceItem.DataKind.PSEUDONYMIZED}) {
            EvidenceItem item = new EvidenceItem(CertificationStandard.ISO_27701, kind,
                    "SYN-item-" + kind, TestSupport.SHA_B);
            assertThat(item.dataKind()).isEqualTo(kind);
        }
    }

    @Test
    void lockedPolicyConstants() {
        assertThat(ExternalAccessPolicy.EVIDENCE_PACK_PERSONAL_DATA_PARAM)
                .isEqualTo("cert.evidence_pack_personal_data");
        assertThat(ExternalAccessPolicy.EVIDENCE_PACK_PERSONAL_DATA_LOCKED_VALUE).isFalse();
        assertThat(ExternalAccessPolicy.ACCESS_FACTORY_DEFAULT).isFalse();
    }

    @Test
    void itemWithoutVerifiableHashRefused() {
        assertThatThrownBy(() -> new EvidenceItem(CertificationStandard.ISO_9001,
                EvidenceItem.DataKind.AGGREGATE, "SYN-sem-hash", "nao-e-hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sha256Hex");
    }
}
