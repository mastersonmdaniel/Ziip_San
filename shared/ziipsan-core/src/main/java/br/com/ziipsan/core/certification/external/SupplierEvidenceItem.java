package br.com.ziipsan.core.certification.external;

import java.util.Set;

/**
 * Um artefato da secao "evidencia do Ziip como fornecedor" do pacote
 * de evidencia (ED-75: "evidencia do Ziip como fornecedor").
 *
 * <p>Os tipos sao exatamente as fontes que o aceite da ED-73 ja
 * nomeia para o pacote de evidencia do fornecedor
 * ({@code SupplierEvidencePack} da ED-73): a linha de base de
 * seguranca da P3.8 (PLT-08), a exportacao verificavel da trilha de
 * auditoria da P1.7 (PLT-19), os documentos da P3.9 e os incidentes da
 * P3.17. A ED-75 NAO duplica aquela montagem: ela a inclui como SECAO
 * do pacote multi-normas, por referencia + SHA-256. Os 14 valores
 * abertos da P3.9 nao sao copiados nem inventados: o documento entra
 * so como referencia a minuta.</p>
 */
public record SupplierEvidenceItem(ArtifactKind kind, String ref, String sha256Hex) {

    /** Origem do artefato do fornecedor: as fontes nomeadas no aceite da ED-73. */
    public enum ArtifactKind {
        /** Linha de base de seguranca da P3.8 (ASVS 5.0, API Security Top 10, LLM Top 10; PLT-08). */
        SECURITY_BASELINE_P38,
        /** Exportacao verificavel da trilha de auditoria da P1.7 (PLT-19). */
        AUDIT_TRAIL_EXPORT_P17,
        /** Documento da P3.9 (minuta), por referencia — sem copiar valores de clausula. */
        CONTRACT_DOCUMENT_P39,
        /** Incidente de seguranca da P3.17, por referencia. */
        SECURITY_INCIDENT_P317
    }

    /** Tipos exigidos para a secao se declarar completa (os quatro do aceite da ED-73). */
    public static final Set<ArtifactKind> REQUIRED_KINDS = Set.of(ArtifactKind.values());

    public SupplierEvidenceItem {
        java.util.Objects.requireNonNull(kind, "kind do artefato do fornecedor e obrigatorio");
        if (ref == null || ref.isBlank() || ref.trim().length() > EvidenceItem.REF_MAX) {
            throw new IllegalArgumentException("ref do artefato do fornecedor e obrigatoria e "
                    + "tem no maximo " + EvidenceItem.REF_MAX + " caracteres");
        }
        ref = ref.trim();
        if (sha256Hex == null || !sha256Hex.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256Hex deve ser o SHA-256 do artefato em "
                    + "hexadecimal minusculo (64 caracteres)");
        }
    }
}
