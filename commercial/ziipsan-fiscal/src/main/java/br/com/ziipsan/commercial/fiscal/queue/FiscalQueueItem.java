package br.com.ziipsan.commercial.fiscal.queue;

import java.time.Duration;
import java.util.Optional;

/**
 * Item da fila como a tela o mostra: o documento, o estado da fila, a explicação da
 * rejeição pelo catálogo da P2.16 (vazia quando o código não está no catálogo —
 * nunca inventada) e, para contingência ainda não transmitida, o tempo decorrido
 * desde o início da contingência (aceite expresso).
 */
public record FiscalQueueItem(FiscalDocumentView document, FiscalQueueState queueState,
                              Optional<FiscalRejectionExplainer.Explanation> rejectionExplanation,
                              Optional<Duration> contingencyElapsed) {
}
