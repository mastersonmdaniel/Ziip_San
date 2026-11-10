package br.com.ziipsan.operations.metering.selfreading;

import java.util.Objects;

/**
 * A foto do mostrador como REFERENCIA/ARTEFACTO (II-7, E2): o nucleo nao
 * guarda bytes de imagem -- guarda a referencia do artefacto no
 * armazenamento de objectos, o hash SHA-256 do conteudo (a identidade
 * que a porta OCR e o adaptador de prova usam) e as dimensoes
 * declaradas, que sao o sinal deterministico de qualidade da foto no
 * score. Upload, armazenamento e retencao sao da integracao
 * (P-II7-FIACAO).
 *
 * @param artifactRef referencia do artefacto (nao vazia)
 * @param sha256Hex   SHA-256 do conteudo, 64 hex minusculos/maiusculos
 * @param widthPx     largura declarada em pixels (&gt; 0)
 * @param heightPx    altura declarada em pixels (&gt; 0)
 */
public record PhotoArtifact(String artifactRef, String sha256Hex, int widthPx, int heightPx) {

    public PhotoArtifact {
        Objects.requireNonNull(artifactRef, "artifactRef");
        Objects.requireNonNull(sha256Hex, "sha256Hex");
        if (artifactRef.isBlank()) {
            throw new IllegalArgumentException("referencia do artefacto nao pode ser vazia");
        }
        if (!sha256Hex.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(
                    "sha256Hex precisa de 64 caracteres hexadecimais; recebido: " + sha256Hex);
        }
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException(
                    "dimensoes da foto precisam ser > 0; recebido " + widthPx + "x" + heightPx);
        }
    }
}
