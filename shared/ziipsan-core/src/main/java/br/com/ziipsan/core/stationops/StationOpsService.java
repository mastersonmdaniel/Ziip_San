package br.com.ziipsan.core.stationops;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Servico do diario de operacao (ED-87) sobre as portas — a fiacao
 * (JDBC/Spring) implementa as portas na integracao, como nas ondas
 * anteriores:
 * <ul>
 *   <li>abrir o diario exige o turno declarado no
 *       {@code ops.shift_pattern} do tenant;</li>
 *   <li>cada leitura lancada e gravada TAMBEM como serie na tabela
 *       da ED-17, com origem {@code MANUAL} e a chave de idempotencia
 *       da propria leitura (reenvio nao duplica NENHUMA das duas
 *       pontas — correcao ED-87/D1: o diario dedupa pela mesma
 *       {@code seriesSampleKey} da serie, e a falha da porta desfaz
 *       o lancamento do diario para o retry ser limpo);</li>
 *   <li>a ocorrencia do diario vira OS pelo nucleo da P1.31
 *       (COE-10), UMA vez por ocorrencia, com a unidade como alvo
 *       {@code OPERATIONAL_SITE}.</li>
 * </ul>
 */
public final class StationOpsService {

    private final StationOpsPorts.ManualSeriesPort series;
    private final StationOpsPorts.WorkOrderPort workOrders;

    public StationOpsService(StationOpsPorts.ManualSeriesPort series,
                             StationOpsPorts.WorkOrderPort workOrders) {
        if (series == null || workOrders == null)
            throw new IllegalArgumentException("portas de serie (ED-17) e de OS (P1.31) sao obrigatorias");
        this.series = series;
        this.workOrders = workOrders;
    }

    /** Abre o diario do turno: o turno precisa existir no padrao do tenant. */
    public ShiftLog openShift(StationUnit unit, List<StationOpsParameters.ShiftDefinition> pattern,
                              java.time.LocalDate shiftDate, String shiftCode,
                              String openedByRef, Instant openedAt) {
        StationOpsParameters.requireShift(pattern, shiftCode);
        return new ShiftLog(UUID.randomUUID(), unit, shiftDate, shiftCode, openedByRef, openedAt);
    }

    /**
     * Lanca a leitura no diario E grava a serie MANUAL na ED-17, de
     * forma idempotente nas DUAS pontas pela mesma chave
     * deterministica ({@code seriesSampleKey} — correcao ED-87/D1):
     * <ul>
     *   <li>se o diario ja tem lancamento com essa chave, ele NAO e
     *       repetido; a serie e reconsultada (devolve o resultado
     *       dela) para curar uma eventual divergencia deixada por
     *       falha anterior da porta;</li>
     *   <li>se a porta lancar excecao depois do lancamento, o
     *       lancamento do diario e desfeito (rollback) e a excecao
     *       propaga: diario e serie ficam os dois sem a leitura, e
     *       o retry relanca cada ponta exatamente uma vez — o
     *       {@code ProcessControlFeed} (ED-50) nunca ve duplicata.</li>
     * </ul>
     * Devolve o resultado da serie: true se gravou, false se a mesma
     * leitura ja estava na serie (reenvio idempotente).
     */
    public boolean addReading(ShiftLog log, DiaryReading reading) {
        if (log == null || reading == null)
            throw new IllegalArgumentException("diario e leitura sao obrigatorios");
        String stationCode = log.unit().code();
        String sampleKey = reading.seriesSampleKey(stationCode);
        StationOpsPorts.ManualSample sample = new StationOpsPorts.ManualSample(
                stationCode, reading.seriesTag(stationCode), reading.value(),
                reading.unitOfMeasure(), reading.measuredAt(),
                StationOpsPorts.SeriesOrigin.MANUAL, sampleKey);
        boolean alreadyInDiary = log.readings().stream()
                .anyMatch(r -> sampleKey.equals(r.seriesSampleKey(stationCode)));
        if (alreadyInDiary) {
            return series.record(log.tenantId(), sample);
        }
        log.addReading(reading);
        try {
            return series.record(log.tenantId(), sample);
        } catch (RuntimeException portFailure) {
            log.removeReadingBySeriesKey(stationCode, sampleKey);
            throw portFailure;
        }
    }

    /**
     * Registra a ocorrencia no diario e abre a OS dela no nucleo da
     * P1.31. A ordem e: porta primeiro, vinculo depois — se a porta
     * falhar, a ocorrencia fica registrada SEM OS e a tentativa pode
     * ser repetida por {@link #openOrderFor}; nunca nasce OS orfa de
     * ocorrencia nem ocorrencia com OS duplicada.
     */
    public ShiftLog.DiaryOccurrence recordOccurrenceAndOpenOrder(ShiftLog log, String description,
                                                                 Instant occurredAt, String reportedBy) {
        if (log == null) throw new IllegalArgumentException("diario e obrigatorio");
        ShiftLog.DiaryOccurrence occurrence = log.recordOccurrence(description, occurredAt, reportedBy);
        openOrderFor(log, occurrence.id());
        return log.occurrences().stream().filter(o -> o.id().equals(occurrence.id())).findFirst()
                .orElseThrow();
    }

    /** Abre (uma unica vez) a OS de uma ocorrencia ja registrada do diario. */
    public UUID openOrderFor(ShiftLog log, UUID occurrenceId) {
        if (log == null || occurrenceId == null)
            throw new IllegalArgumentException("diario e ocorrencia sao obrigatorios");
        ShiftLog.DiaryOccurrence occurrence = log.occurrences().stream()
                .filter(o -> o.id().equals(occurrenceId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "ocorrencia inexistente neste diario: " + occurrenceId));
        if (occurrence.workOrderId() != null) throw new IllegalStateException(
                "ocorrencia " + occurrenceId + " ja tem a OS " + occurrence.workOrderId());
        UUID orderId = workOrders.openOccurrenceOrder(log.tenantId(), log.unit().id(),
                occurrence.description(), occurrence.reportedBy());
        log.attachWorkOrder(occurrenceId, orderId);
        return orderId;
    }
}
