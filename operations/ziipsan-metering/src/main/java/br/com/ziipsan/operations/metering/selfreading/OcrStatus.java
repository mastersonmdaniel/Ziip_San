package br.com.ziipsan.operations.metering.selfreading;

/** Desfecho tipificado de uma extraccao OCR (II-7). */
public enum OcrStatus {
    /** O motor leu um valor do mostrador na foto. */
    EXTRACTED,
    /** O motor correu mas nao leu valor nenhum (foto ilegivel/sem mostrador). */
    NO_READING
}
