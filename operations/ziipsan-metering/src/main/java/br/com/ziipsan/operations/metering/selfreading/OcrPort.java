package br.com.ziipsan.operations.metering.selfreading;

/**
 * A PORTA OCR (II-7, E2): o unico caminho por onde uma leitura pode
 * nascer de uma foto. Nenhum motor OCR real e presumido nesta entrega --
 * o motor real (fornecedor/biblioteca) e a pendencia P-II7-OCR
 * [SEM FONTE]; o que se entrega e a porta, o contrato tipificado e o
 * adaptador de prova deterministico
 * ({@link DeterministicProofOcrAdapter}).
 *
 * <p>Contrato dos adaptadores:
 * <ul>
 *   <li>foto ilegivel/sem mostrador =&gt; {@link OcrStatus#NO_READING},
 *       sem leitura inventada;</li>
 *   <li>motor em baixo/nao configurado =&gt; {@link OcrUnavailableException};</li>
 *   <li>mesma foto (mesmo hash) =&gt; mesmo resultado: a extraccao e
 *       deterministica em relacao ao artefacto.</li>
 * </ul>
 */
public interface OcrPort {

    /**
     * @param photo o artefacto da foto (referencia + hash + dimensoes)
     * @return a extraccao tipificada
     * @throws OcrUnavailableException se o motor estiver indisponivel
     */
    OcrExtraction extract(PhotoArtifact photo);
}
