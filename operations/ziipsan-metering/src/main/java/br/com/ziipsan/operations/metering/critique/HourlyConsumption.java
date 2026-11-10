package br.com.ziipsan.operations.metering.critique;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Um ponto da serie horaria de consumo de uma UC (II-2, E5) -- a materia da
 * deteccao proactiva de vazamento. Em producao a serie vem da medicao
 * horaria/telemetria quando existir; sem serie, nao ha deteccao (a
 * deteccao nunca fabrica pontos).
 *
 * @param date       dia do ponto
 * @param hourOfDay  hora 0..23
 * @param consumption consumo da hora (&gt;= 0), na unidade da serie (ex.: litros/hora ou m3)
 */
public record HourlyConsumption(LocalDate date, int hourOfDay, BigDecimal consumption) {

    public HourlyConsumption {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(consumption, "consumption");
        if (hourOfDay < 0 || hourOfDay > 23) {
            throw new IllegalArgumentException("hourOfDay fora de 0..23: " + hourOfDay);
        }
        if (consumption.signum() < 0) {
            throw new IllegalArgumentException("consumo horario negativo");
        }
    }
}
