package br.com.ziipsan.reporting.indicator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fechamento de um periodo do indicador (aceite da P3.41): congela o valor
 * com a versao da formula e o retrato dos valores das variaveis
 * ({@code variableValuesJson}, JSON gravado so pela classe {@code Json} do
 * core, com os decimais em texto pela {@code toPlainString} — nenhum
 * arredondamento na serializacao). Enquanto {@code status == CLOSED},
 * recalculo nenhum altera este registro: o servico devolve o valor
 * congelado. A reabertura muda o status para {@code REOPENED}, exige motivo
 * e fica na trilha de auditoria; o fechamento seguinte cria um registro
 * novo, sem reescrever este.
 */
public record IndicatorClosing(UUID id, UUID tenantId, String indicatorCode,
                               LocalDate periodStart, LocalDate periodEnd, String areaCode,
                               BigDecimal value, int formulaVersion, String variableValuesJson,
                               Semaphore semaphore, Status status,
                               UUID closedBy, Instant closedAt,
                               UUID reopenedBy, Instant reopenedAt, String reopenReason) {

    public enum Status {
        CLOSED,
        REOPENED
    }

    // Validacoes espelhando os CHECKs e os NOT NULL da V15_2
    // (indicator_closings), sem regra nova (correcao D3 da revisao
    // independente): o dominio recusa em memoria o que o banco recusaria.
    public IndicatorClosing {
        if (id == null) {
            throw new IndicatorValidationException("CLOSING_ID_REQUIRED",
                    "id do fechamento e obrigatorio");
        }
        if (tenantId == null) {
            throw new IndicatorValidationException("TENANT_REQUIRED", "tenant e obrigatorio");
        }
        if (indicatorCode == null || indicatorCode.isBlank()) {
            throw new IndicatorValidationException("CLOSING_INDICATOR_REQUIRED",
                    "fechamento sem indicador");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IndicatorValidationException("CLOSING_PERIOD_REQUIRED",
                    "periodo do fechamento (inicio e fim) e obrigatorio para " + indicatorCode);
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new IndicatorValidationException("CLOSING_PERIOD_INVALID",
                    "fim do periodo " + periodEnd + " anterior ao inicio " + periodStart
                            + " no fechamento de " + indicatorCode
                            + " (CHECK period_end >= period_start da V15_2)");
        }
        if (value == null) {
            throw new IndicatorValidationException("CLOSING_VALUE_REQUIRED",
                    "valor congelado e obrigatorio no fechamento de " + indicatorCode);
        }
        if (formulaVersion < 1) {
            throw new IndicatorValidationException("FORMULA_VERSION_INVALID",
                    "versao de formula deve ser >= 1, recebida: " + formulaVersion);
        }
        if (variableValuesJson == null || variableValuesJson.isBlank()) {
            throw new IndicatorValidationException("CLOSING_VARIABLE_VALUES_REQUIRED",
                    "retrato das variaveis e obrigatorio no fechamento de " + indicatorCode);
        }
        if (semaphore == null) {
            throw new IndicatorValidationException("CLOSING_SEMAPHORE_REQUIRED",
                    "semaforo e obrigatorio no fechamento de " + indicatorCode);
        }
        if (status == null) {
            throw new IndicatorValidationException("CLOSING_STATUS_REQUIRED",
                    "status e obrigatorio no fechamento de " + indicatorCode);
        }
        if (closedAt == null) {
            throw new IndicatorValidationException("CLOSING_CLOSED_AT_REQUIRED",
                    "instante do fechamento e obrigatorio para " + indicatorCode);
        }
        // CHECK da V15_2: reabertura so com motivo, responsavel e instante.
        if (status == Status.REOPENED && (reopenedBy == null || reopenedAt == null
                || reopenReason == null || reopenReason.isBlank())) {
            throw new IndicatorValidationException("CLOSING_REOPEN_DATA_REQUIRED",
                    "fechamento reaberto exige motivo, responsavel e instante de reabertura ("
                            + indicatorCode + ")");
        }
    }

    public IndicatorClosing reopened(UUID by, Instant at, String reason) {
        return new IndicatorClosing(id, tenantId, indicatorCode, periodStart, periodEnd, areaCode,
                value, formulaVersion, variableValuesJson, semaphore, Status.REOPENED,
                closedBy, closedAt, by, at, reason);
    }
}
