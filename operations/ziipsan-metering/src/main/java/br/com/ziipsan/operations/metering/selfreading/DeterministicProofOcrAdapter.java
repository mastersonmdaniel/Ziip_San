package br.com.ziipsan.operations.metering.selfreading;

import java.util.Map;
import java.util.Objects;

/**
 * Adaptador de prova DETERMINISTICO da porta OCR (II-7, E2) --
 * CONSTRUCAO DE PROVA, nao e um motor OCR: nao processa imagem nenhuma.
 * Conhece uma tabela explicita hash-da-foto -&gt; extraccao, montada por
 * quem chama (fixtures de teste/integracao); foto fora da tabela devolve
 * {@link OcrStatus#NO_READING}. Existe para o fluxo completo
 * (submissao -&gt; score -&gt; roteamento -&gt; prova geografica) ser
 * provavel de ponta a ponta sem fingir um motor real -- o motor real e
 * a pendencia P-II7-OCR [SEM FONTE] e entra por esta mesma porta.
 */
public final class DeterministicProofOcrAdapter implements OcrPort {

    /** Identificador declarado deste adaptador nas extraccoes. */
    public static final String ENGINE_ID = "PROOF-DETERMINISTIC";

    private final Map<String, OcrExtraction> byPhotoHash;

    /**
     * @param byPhotoHash tabela hash SHA-256 da foto (hex) -&gt; extraccao
     *                    esperada; as extraccoes sao re-carimbadas com o
     *                    {@link #ENGINE_ID} deste adaptador
     */
    public DeterministicProofOcrAdapter(Map<String, OcrExtraction> byPhotoHash) {
        Objects.requireNonNull(byPhotoHash, "byPhotoHash");
        this.byPhotoHash = Map.copyOf(byPhotoHash);
    }

    @Override
    public OcrExtraction extract(PhotoArtifact photo) {
        Objects.requireNonNull(photo, "photo");
        OcrExtraction known = byPhotoHash.get(photo.sha256Hex());
        if (known == null) {
            return OcrExtraction.noReading(ENGINE_ID);
        }
        if (known.status() == OcrStatus.NO_READING) {
            return OcrExtraction.noReading(ENGINE_ID);
        }
        return OcrExtraction.extracted(known.reading(), known.confidence(), ENGINE_ID);
    }
}
