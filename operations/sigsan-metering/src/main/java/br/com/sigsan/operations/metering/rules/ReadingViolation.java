package br.com.sigsan.operations.metering.rules;

/** Motivo pelo qual uma linha do retorno de leitura é recusada (vai para o relatório de erros). */
public enum ReadingViolation {
    READER_UNKNOWN,
    UNIT_UNKNOWN,
    KIND_UNKNOWN,
    READING_DATE_MISSING,
    READING_DATE_IN_FUTURE,
    /** A data da leitura cai fora de [referência - 1 mês, referência + 1 mês]. */
    READING_DATE_OUTSIDE_WINDOW,
    READING_NEGATIVE,
    /** Leitura maior que zero para ligação sem hidrômetro (do tipo informado). */
    READING_WITHOUT_METER,
    /** Anormalidade "imóvel sem hidrômetro" lançada para ligação que tem hidrômetro. */
    NO_METER_ANOMALY_WITH_METER,
    /** Código de anormalidade que não existe no catálogo. */
    ANOMALY_UNKNOWN,
    /** Tipo de medição (água ou poço) informado sem hidrômetro instalado e sem a anormalidade de "sem hidrômetro". */
    METER_NOT_INSTALLED_FOR_KIND
}
