package br.com.ziipsan.commercial.collection.surcharge;

import java.time.YearMonth;

/** Falta o fator do índice de correção para um mês necessário ao cálculo. */
public class MissingIndexException extends IllegalStateException {

    private final YearMonth month;

    public MissingIndexException(YearMonth month) {
        super("Índice de correção monetária não cadastrado para " + month);
        this.month = month;
    }

    public YearMonth month() {
        return month;
    }
}
