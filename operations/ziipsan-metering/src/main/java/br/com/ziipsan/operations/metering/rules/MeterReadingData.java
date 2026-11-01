package br.com.ziipsan.operations.metering.rules;

import java.time.LocalDate;

/**
 * Dados de leitura de uma unidade com hidrômetro, no mês.
 *
 * @param digits                   dígitos do mostrador (1 a 9)
 * @param previousReadingBilled    leitura anterior <b>faturada</b> (obrigatória; leitura de instalação
 *                                 se o hidrômetro é novo)
 * @param previousReadingInformed  leitura anterior informada; nula se não houve
 * @param previousReadingDate      data da leitura anterior
 * @param currentReadingInformed   leitura atual informada; <b>nula = sem leitura</b>
 * @param currentReadingDate       data da leitura atual (obrigatória se há leitura atual)
 * @param cycleDate                data prevista da leitura no cronograma do mês (para leitura ausente)
 * @param cycleDays                dias do ciclo: entre as leituras de dois meses consecutivos do cronograma (&gt;= 1)
 * @param routeAdjustsByDays       a rota faz o ajuste mensal por dias
 * @param previousStatus           situação da leitura anterior (REAL só se realizada ou confirmada)
 * @param currentStatus            situação da leitura atual; nula se não há leitura
 * @param informedAnomaly          anormalidade de leitura informada pelo leiturista; nula se nenhuma
 * @param informedConsumption      consumo informado pelo prestador no lugar do medido; nulo se nenhum
 * @param installationDate         data de instalação do hidrômetro atual (para detectar substituição); nula se desconhecida
 * @param installationReading      leitura de instalação do hidrômetro atual
 */
public record MeterReadingData(
        int digits,
        Integer previousReadingBilled,
        Integer previousReadingInformed,
        LocalDate previousReadingDate,
        Integer currentReadingInformed,
        LocalDate currentReadingDate,
        LocalDate cycleDate,
        int cycleDays,
        boolean routeAdjustsByDays,
        ReadingStatus previousStatus,
        ReadingStatus currentStatus,
        ReadingAnomalyRule informedAnomaly,
        Integer informedConsumption,
        LocalDate installationDate,
        Integer installationReading) {

    public MeterReadingData {
        MeteringInputException.require(digits >= 1 && digits <= 9, MeteringErrorCode.INVALID_INPUT,
                "dígitos do mostrador deve estar entre 1 e 9");
        MeteringInputException.require(previousReadingBilled != null, MeteringErrorCode.MISSING_PREVIOUS_READING,
                "leitura anterior faturada é obrigatória");
        long max = ReadingValidator.dialMaximum(digits);
        MeteringInputException.require(previousReadingBilled >= 0 && previousReadingBilled <= max,
                MeteringErrorCode.INVALID_INPUT, "leitura anterior faturada fora do mostrador");
        MeteringInputException.require(previousReadingInformed == null
                        || (previousReadingInformed >= 0 && previousReadingInformed <= max),
                MeteringErrorCode.INVALID_INPUT, "leitura anterior informada fora do mostrador");
        MeteringInputException.notNull(previousReadingDate, "previousReadingDate");
        MeteringInputException.notNull(cycleDate, "cycleDate");
        MeteringInputException.require(cycleDays >= 1, MeteringErrorCode.INVALID_INPUT, "cycleDays deve ser >= 1");
        MeteringInputException.notNull(previousStatus, "previousStatus");
        MeteringInputException.require(currentReadingInformed == null || currentReadingInformed >= 0,
                MeteringErrorCode.INVALID_INPUT, "leitura atual negativa");
        if (currentReadingInformed != null) {
            MeteringInputException.require(currentReadingDate != null, MeteringErrorCode.INVALID_DATES,
                    "leitura atual sem data");
            MeteringInputException.require(!currentReadingDate.isBefore(previousReadingDate),
                    MeteringErrorCode.INVALID_DATES, "leitura atual anterior à leitura anterior");
            MeteringInputException.notNull(currentStatus, "currentStatus");
        }
        MeteringInputException.require(informedConsumption == null || informedConsumption >= 0,
                MeteringErrorCode.INVALID_INPUT, "consumo informado negativo");
        MeteringInputException.require(installationReading == null
                        || (installationReading >= 0 && installationReading <= max),
                MeteringErrorCode.INVALID_INPUT, "leitura de instalação fora do mostrador");
    }

    boolean hasReading() {
        return currentReadingInformed != null;
    }

    /** O hidrômetro foi instalado depois da leitura anterior e até a atual: houve substituição no período. */
    boolean replacedInPeriod() {
        return hasReading() && installationDate != null
                && installationDate.isAfter(previousReadingDate) && !installationDate.isAfter(currentReadingDate);
    }
}
