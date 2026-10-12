package br.com.sigsan.operations.metering.rules;

/**
 * Fatos que o validador precisa e que só o banco sabe. Quem chama resolve (consulta) e passa; a
 * regra continua pura e testável sem I/O.
 *
 * @param readerKnown            o leiturista da linha existe
 * @param unitKnown              a unidade existe
 * @param meterInstalledForKind  há hidrômetro instalado para o tipo de medição informado
 * @param meterDigits            dígitos do mostrador do hidrômetro instalado; nulo se não há
 * @param anomaly                anormalidade resolvida no catálogo; nula se a linha não traz código
 *                               ou se o código não existe no catálogo
 */
public record ReadingContext(boolean readerKnown, boolean unitKnown, boolean meterInstalledForKind,
                             Integer meterDigits, ReadingAnomalyRule anomaly) {
}
