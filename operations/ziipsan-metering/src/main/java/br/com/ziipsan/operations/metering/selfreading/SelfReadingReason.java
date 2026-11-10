package br.com.ziipsan.operations.metering.selfreading;

/** Motivos declarados do desfecho de uma autoleitura (II-7). */
public enum SelfReadingReason {
    /** Sem adaptador ligado a porta OCR nesta instalacao. */
    NO_OCR_ADAPTER,
    /** O adaptador respondeu indisponivel (OcrUnavailableException). */
    OCR_UNAVAILABLE,
    /** Submissao sem foto: neste fluxo a foto e o objecto do OCR. */
    PHOTO_MISSING,
    /** O motor correu mas nao leu valor na foto. */
    OCR_NO_READING,
    /** Score abaixo do limiar de aceite configurado. */
    SCORE_BELOW_THRESHOLD,
    /** Distancia a ligacao alem do maximo configurado: sinalizada, nunca aceite em silencio. */
    GEO_BEYOND_MAX_DISTANCE,
    /** Precisao do GPS acima da maxima: a prova nao pontuou (componente geografico = 0). */
    GEO_ACCURACY_INSUFFICIENT,
    /** Sem prova geografica ou sem coordenadas da ligacao: componente geografico = 0. */
    GEO_PROOF_MISSING,
    /** Leitura declarada pelo consumidor diverge da leitura OCR: coerencia anulada. */
    DECLARED_READING_DIVERGES,
    /** Aceite: score no limiar ou acima e prova dentro do filtro. */
    ACCEPTED
}
