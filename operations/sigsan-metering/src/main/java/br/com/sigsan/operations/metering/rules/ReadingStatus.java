package br.com.sigsan.operations.metering.rules;

/** Situação de uma leitura. */
public enum ReadingStatus {
    REALIZED,
    NOT_REALIZED,
    CONFIRMED,
    /** Leitura alterada depois de lida. Não conta como leitura real para classificar o consumo. */
    CHANGED;

    /** Leitura "real": realizada ou confirmada. As demais produzem consumo estimado. */
    public boolean isReal() {
        return this == REALIZED || this == CONFIRMED;
    }
}
