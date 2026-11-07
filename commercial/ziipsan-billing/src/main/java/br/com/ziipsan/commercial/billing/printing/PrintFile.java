package br.com.ziipsan.commercial.billing.printing;

import br.com.ziipsan.commercial.billing.content.LegalMessageEngine;

import java.util.List;

/**
 * Resultado da geracao de um lote para a grafica (P2.8).
 *
 * @param content  o arquivo da grafica, completo. E o UNICO artefato desta
 *                 tarefa que contem o codigo de vinculacao ao portal
 *                 (ADR-0022): quem o recebe o guarda pelo pipeline de
 *                 documentos (P1.20) e o transmite a grafica — nao o copia
 *                 para log, fila ou nome de arquivo
 * @param manifest manifesto intermediario seguro ({@link PrintFileManifest})
 * @param proofs   prova de emissao da P2.32 de cada conta do lote, na mesma
 *                 ordem — o chamador a persiste (repositorio da P2.32) junto
 *                 da emissao; a prova nao vai no arquivo nem no manifesto
 */
public record PrintFile(
        String content,
        PrintFileManifest manifest,
        List<LegalMessageEngine.EmissionProof> proofs) {

    public PrintFile {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("arquivo da grafica vazio nao e resultado valido");
        }
        if (manifest == null) {
            throw new IllegalArgumentException("manifest e obrigatorio");
        }
        proofs = proofs == null ? List.of() : List.copyOf(proofs);
        if (proofs.size() != manifest.recordCount()) {
            throw new IllegalArgumentException("provas de emissao nao fecham com o lote");
        }
    }

    /** Seguro: delega ao manifesto, que nao carrega dado do arquivo. */
    @Override
    public String toString() {
        return "PrintFile[" + manifest.describeForLog() + "]";
    }
}
