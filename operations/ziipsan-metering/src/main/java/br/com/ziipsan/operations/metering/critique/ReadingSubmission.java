package br.com.ziipsan.operations.metering.critique;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A leitura submetida a critica num ciclo (II-2): a leitura actual (nula =
 * em falta), a anterior, o consumo medido derivado e o historico de
 * consumos calculados dos ciclos anteriores (a mesma materia que a P2.5
 * guarda em {@code ConsumptionRecord.calculatedConsumption}).
 *
 * @param unitId              unidade consumidora
 * @param reference           competencia do ciclo
 * @param currentReading      leitura actual do hidrometro; nulo = sem leitura
 * @param previousReading     leitura anterior; nulo = primeira leitura da unidade
 * @param measuredConsumption consumo medido do ciclo; nulo quando nao derivavel (sem leitura)
 * @param history             consumos historicos, do mais antigo ao mais recente (sem nulos, &gt;= 0)
 */
public record ReadingSubmission(UUID unitId, YearMonth reference, Integer currentReading,
                                Integer previousReading, Integer measuredConsumption,
                                List<Integer> history) {

    public ReadingSubmission {
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(history, "history");
        history = List.copyOf(history);
        for (Integer h : history) {
            if (h == null || h < 0) {
                throw new IllegalArgumentException("historico so aceita consumos >= 0");
            }
        }
        if (currentReading != null && currentReading < 0) {
            throw new IllegalArgumentException("currentReading negativo nao e leitura de hidrometro");
        }
        if (previousReading != null && previousReading < 0) {
            throw new IllegalArgumentException("previousReading negativo nao e leitura de hidrometro");
        }
    }
}
