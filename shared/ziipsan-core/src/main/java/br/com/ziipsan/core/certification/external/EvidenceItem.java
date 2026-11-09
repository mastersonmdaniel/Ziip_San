package br.com.ziipsan.core.certification.external;

import java.util.Objects;

/**
 * Um item do pacote de evidencia (ED-75): referencia a um artefato de
 * UMA norma ligada, com o resumo criptografico do artefato e a CLASSE
 * DO DADO que o item carrega.
 *
 * <p>Trava de classe a (aceite da ED-75): o pacote so contem dado
 * AGREGADO, MASCARADO ou PSEUDONIMIZADO. Dado PESSOAL e recusado no
 * construtor canonico — nao existe caminho, por fabrica ou por
 * construtor, que monte um item pessoal. A trava espelha o parametro
 * {@code cert.evidence_pack_personal_data} travado em {@code false}
 * (classe a, LOCKED, core V3; ADR-0041, item 10). Nada aqui copia
 * conteudo de artefato: item e norma + classe + referencia + SHA-256
 * declarado, como no {@code SupplierEvidencePack} da ED-73.</p>
 */
public record EvidenceItem(CertificationStandard standard, DataKind dataKind, String ref,
                           String sha256Hex) {

    /** Classe do dado de um item de evidencia. */
    public enum DataKind {
        /** Dado agregado (contagens, totais, percentuais): admitido. */
        AGGREGATE,
        /** Dado mascarado (parte oculta de forma irreversivel no item): admitido. */
        MASKED,
        /** Dado pseudonimizado (identificador substituido por codigo): admitido. */
        PSEUDONYMIZED,
        /** Dado pessoal: RECUSADO sempre (classe a, travado). */
        PERSONAL
    }

    /** Teto da referencia do artefato (o mesmo teto de evidencia dos pacotes irmaos). */
    public static final int REF_MAX = 200;

    public EvidenceItem {
        Objects.requireNonNull(standard, "standard (norma do item) e obrigatorio");
        Objects.requireNonNull(dataKind, "dataKind (classe do dado do item) e obrigatorio");
        if (dataKind == DataKind.PERSONAL) {
            throw new IllegalArgumentException("dado pessoal e recusado no pacote de evidencia "
                    + "(classe a, travado: " + ExternalAccessPolicy.EVIDENCE_PACK_PERSONAL_DATA_PARAM
                    + "=false, ADR-0041 item 10): o pacote so leva dado agregado, mascarado ou "
                    + "pseudonimizado");
        }
        if (ref == null || ref.isBlank() || ref.trim().length() > REF_MAX) {
            throw new IllegalArgumentException("ref do artefato e obrigatoria (identificador do "
                    + "artefato existente, sem copiar conteudo) e tem no maximo " + REF_MAX
                    + " caracteres");
        }
        ref = ref.trim();
        if (sha256Hex == null || !sha256Hex.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256Hex deve ser o SHA-256 do artefato em "
                    + "hexadecimal minusculo (64 caracteres): evidencia sem resumo verificavel "
                    + "nao e evidencia");
        }
    }
}
