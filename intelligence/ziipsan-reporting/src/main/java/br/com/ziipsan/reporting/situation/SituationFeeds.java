package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.core.incident.NetworkIncident;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import br.com.ziipsan.operations.metering.telemetry.MeterAlarmKind;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * ED-93: as PORTAS de alimentacao da projecao — o lado de ESCRITA da
 * tabela de estado. Cada porta devolve o retrato tipado da sua fonte
 * REAL; o adaptador concreto (JDBC/eventos) e fiacao de integracao
 * (pendencia P-ED93-01). O console ({@link SituationConsoleService})
 * NAO conhece estas portas: ele le so a tabela de estado.
 *
 * <p>Formas consumidas, sem redefinicao:
 * <ul>
 *   <li>ocorrencias: a entidade REAL {@link NetworkIncident} da ED-92
 *       (compilada da fonte no harness);</li>
 *   <li>OS: {@link ServiceOrderStatus}/{@link ServiceOrderEnums.Priority}
 *       REAIS do motor da P1.31, com o prazo nos termos do
 *       {@code OrderMonitoring} da ED-36 (dueAt gravado, ou abertura +
 *       deadline_hours do tipo; nenhum dos dois = prazo DESCONHECIDO,
 *       nunca inventado);</li>
 *   <li>alarmes: o tipo REAL {@link MeterAlarmKind} da ED-17 e a
 *       situacao nos nomes do {@code MeterAlarm.Status} dela
 *       (OPEN/ACKNOWLEDGED/RESOLVED — espelho declarado: a entidade
 *       da ED-17 e JPA e nao atravessa para o nucleo puro);</li>
 *   <li>unidades fora de operacao: os campos do {@code StationOutage}
 *       da ED-17 (stationCode, inicio, motivo, origem MANUAL/
 *       SUPERVISORY — espelho declarado, mesmo motivo);</li>
 *   <li>equipes e reservatorios: NENHUMA fonte consultada define
 *       registro de posicao GPS de equipe nem leitura de nivel de
 *       reservatorio (a ED-59 trata frota/escala; a ED-89, limpeza de
 *       reservatorio) — os retratos abaixo sao a FORMA da porta, sem
 *       limiar, frequencia ou valor presumidos; limiares de nivel,
 *       quando existirem, sao dados do tenant carregados no retrato.</li>
 * </ul>
 */
public final class SituationFeeds {

    private SituationFeeds() {
    }

    // ------------------------------------------------------------------
    // Ocorrencias (ED-92, entidade real)
    // ------------------------------------------------------------------

    @FunctionalInterface
    public interface IncidentFeed {
        /** Ocorrencias do tenant na fonte (a projecao filtra as abertas). */
        List<NetworkIncident> incidents(UUID tenantId);
    }

    // ------------------------------------------------------------------
    // Ordens de servico (P1.31 / ED-36)
    // ------------------------------------------------------------------

    /**
     * Retrato de OS para a projecao. Coordenadas, quando presentes,
     * sao as do endereco da OS na fonte; ausentes = OS sem marcador
     * (contada como nao posicionada), nunca coordenadas presumidas.
     */
    public record WorkOrderSnapshot(UUID orderId, ServiceOrderStatus status,
                                    ServiceOrderEnums.Priority priority,
                                    LocalDateTime createdAt, Integer deadlineHoursOrNull,
                                    LocalDateTime dueAtOrNull, String crewCodeOrNull,
                                    Double latitudeOrNull, Double longitudeOrNull) {
        public WorkOrderSnapshot {
            Objects.requireNonNull(orderId, "OS obrigatoria");
            Objects.requireNonNull(status, "situacao da OS obrigatoria");
            Objects.requireNonNull(priority, "prioridade da OS obrigatoria");
            Objects.requireNonNull(createdAt, "abertura da OS obrigatoria");
            if (deadlineHoursOrNull != null && deadlineHoursOrNull <= 0) {
                throw new IllegalArgumentException("deadline_hours deve ser positivo");
            }
            if ((latitudeOrNull == null) != (longitudeOrNull == null)) {
                throw new IllegalArgumentException("coordenadas da OS vem em par ou nao vem");
            }
        }

        /** Prazo conhecido quando ha dueAt gravado ou deadline do tipo (regra da ED-36). */
        public boolean dueKnown() {
            return dueAtOrNull != null || deadlineHoursOrNull != null;
        }
    }

    @FunctionalInterface
    public interface WorkOrderFeed {
        List<WorkOrderSnapshot> workOrders(UUID tenantId);
    }

    // ------------------------------------------------------------------
    // Equipes (posicao — forma da porta, sem fonte de GPS nas deps)
    // ------------------------------------------------------------------

    /**
     * Posicao reportada de uma equipe. Sem coordenadas, a equipe
     * existe no console como NAO POSICIONADA — a projecao nao fabrica
     * posicao. {@code reportedAt} e o instante da fonte: posicao velha
     * aparece velha no frescor, nao como atual.
     */
    public record CrewPosition(String crewCode, UUID currentOrderIdOrNull,
                               Double latitudeOrNull, Double longitudeOrNull,
                               Instant reportedAt) {
        public CrewPosition {
            if (crewCode == null || crewCode.isBlank()) {
                throw new IndicatorValidationException("CREW_CODE_REQUIRED", "codigo da equipe obrigatorio");
            }
            Objects.requireNonNull(reportedAt, "instante da posicao obrigatorio");
            if ((latitudeOrNull == null) != (longitudeOrNull == null)) {
                throw new IndicatorValidationException("SITUATION_COORDINATES_INCOMPLETE",
                        "coordenadas da equipe vem em par ou nao vem (" + crewCode + ")");
            }
        }

        public boolean positioned() {
            return latitudeOrNull != null;
        }
    }

    @FunctionalInterface
    public interface CrewFeed {
        List<CrewPosition> crewPositions(UUID tenantId);
    }

    // ------------------------------------------------------------------
    // Telemetria (ED-17)
    // ------------------------------------------------------------------

    /** Situacoes do alarme nos nomes do MeterAlarm.Status da ED-17. */
    public static final Set<String> ALARM_STATUSES = Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED");

    /**
     * Retrato de alarme de telemetria: ponto pelo codigo estavel da
     * ED-17, tipo REAL {@link MeterAlarmKind}, situacao nos nomes da
     * ED-17. Coordenadas sao as do ponto na fonte, quando houver.
     */
    public record TelemetryAlarm(String alarmRef, String measuringPointCode, MeterAlarmKind kind,
                                 String status, Instant raisedAt,
                                 Double latitudeOrNull, Double longitudeOrNull) {
        public TelemetryAlarm {
            if (alarmRef == null || alarmRef.isBlank()) {
                throw new IndicatorValidationException("ALARM_REF_REQUIRED", "referencia do alarme obrigatoria");
            }
            if (measuringPointCode == null || measuringPointCode.isBlank()) {
                throw new IndicatorValidationException("MEASURING_POINT_REQUIRED",
                        "ponto de medicao obrigatorio no alarme " + alarmRef);
            }
            Objects.requireNonNull(kind, "tipo do alarme obrigatorio (MeterAlarmKind da ED-17)");
            if (status == null || !ALARM_STATUSES.contains(status)) {
                throw new IndicatorValidationException("ALARM_STATUS_INVALID",
                        "situacao do alarme fora de OPEN/ACKNOWLEDGED/RESOLVED (ED-17): " + status);
            }
            Objects.requireNonNull(raisedAt, "instante do alarme obrigatorio");
            if ((latitudeOrNull == null) != (longitudeOrNull == null)) {
                throw new IndicatorValidationException("SITUATION_COORDINATES_INCOMPLETE",
                        "coordenadas do alarme vem em par ou nao vem (" + alarmRef + ")");
            }
        }
    }

    @FunctionalInterface
    public interface TelemetryAlarmFeed {
        List<TelemetryAlarm> alarms(UUID tenantId);
    }

    // ------------------------------------------------------------------
    // Reservatorios (nivel — forma da porta; limiares sao do tenant)
    // ------------------------------------------------------------------

    /**
     * Leitura de nivel de reservatorio: percentual MEDIDO (0–100) e os
     * limiares DECLARADOS do tenant, quando existirem. Sem limiar
     * declarado, a faixa do nivel e DESCONHECIDA (UNKNOWN) — esta
     * entrega nao presume faixa normal de reservatorio [SEM FONTE].
     */
    public record ReservoirLevel(String reservoirCode, BigDecimal levelPercent,
                                 BigDecimal lowThresholdOrNull, BigDecimal highThresholdOrNull,
                                 Instant measuredAt,
                                 Double latitudeOrNull, Double longitudeOrNull) {
        public ReservoirLevel {
            if (reservoirCode == null || reservoirCode.isBlank()) {
                throw new IndicatorValidationException("RESERVOIR_CODE_REQUIRED", "codigo do reservatorio obrigatorio");
            }
            Objects.requireNonNull(levelPercent, "nivel medido obrigatorio");
            if (levelPercent.signum() < 0 || levelPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IndicatorValidationException("RESERVOIR_LEVEL_INVALID",
                        "nivel percentual fora de [0,100]: " + levelPercent.toPlainString());
            }
            for (BigDecimal t : new BigDecimal[]{lowThresholdOrNull, highThresholdOrNull}) {
                if (t != null && (t.signum() < 0 || t.compareTo(BigDecimal.valueOf(100)) > 0)) {
                    throw new IndicatorValidationException("RESERVOIR_THRESHOLD_INVALID",
                            "limiar percentual fora de [0,100]: " + t.toPlainString());
                }
            }
            if (lowThresholdOrNull != null && highThresholdOrNull != null
                    && lowThresholdOrNull.compareTo(highThresholdOrNull) > 0) {
                throw new IndicatorValidationException("RESERVOIR_THRESHOLDS_INVERTED",
                        "limiar baixo acima do alto no reservatorio " + reservoirCode);
            }
            Objects.requireNonNull(measuredAt, "instante da medicao obrigatorio");
            if ((latitudeOrNull == null) != (longitudeOrNull == null)) {
                throw new IndicatorValidationException("SITUATION_COORDINATES_INCOMPLETE",
                        "coordenadas do reservatorio vem em par ou nao vem (" + reservoirCode + ")");
            }
        }

        /** Faixa do nivel SOMENTE contra limiar declarado; sem limiar, UNKNOWN. */
        public String band() {
            if (lowThresholdOrNull == null && highThresholdOrNull == null) {
                return "UNKNOWN";
            }
            if (lowThresholdOrNull != null && levelPercent.compareTo(lowThresholdOrNull) < 0) {
                return "LOW";
            }
            if (highThresholdOrNull != null && levelPercent.compareTo(highThresholdOrNull) > 0) {
                return "HIGH";
            }
            return "WITHIN_DECLARED";
        }
    }

    @FunctionalInterface
    public interface ReservoirFeed {
        List<ReservoirLevel> reservoirLevels(UUID tenantId);
    }

    // ------------------------------------------------------------------
    // Unidades fora de operacao (StationOutage da ED-17, espelho)
    // ------------------------------------------------------------------

    /** Origens nos nomes do StationOutage.Source da ED-17. */
    public static final Set<String> OUTAGE_SOURCES = Set.of("MANUAL", "SUPERVISORY");

    /**
     * Unidade/estacao fora de operacao AGORA (a porta devolve so as
     * abertas: na fonte, {@code StationOutage} com fim nulo). Motivo
     * obrigatorio, como na entidade da ED-17.
     */
    public record UnitOutage(String stationCode, Instant startedAt, String reason, String source,
                             Double latitudeOrNull, Double longitudeOrNull) {
        public UnitOutage {
            if (stationCode == null || stationCode.isBlank()) {
                throw new IndicatorValidationException("STATION_CODE_REQUIRED", "codigo da estacao obrigatorio");
            }
            Objects.requireNonNull(startedAt, "inicio da parada obrigatorio");
            if (reason == null || reason.isBlank()) {
                throw new IndicatorValidationException("OUTAGE_REASON_REQUIRED",
                        "motivo da parada obrigatorio (StationOutage da ED-17)");
            }
            if (source == null || !OUTAGE_SOURCES.contains(source)) {
                throw new IndicatorValidationException("OUTAGE_SOURCE_INVALID",
                        "origem da parada fora de MANUAL/SUPERVISORY (ED-17): " + source);
            }
            if ((latitudeOrNull == null) != (longitudeOrNull == null)) {
                throw new IndicatorValidationException("SITUATION_COORDINATES_INCOMPLETE",
                        "coordenadas da estacao vem em par ou nao vem (" + stationCode + ")");
            }
        }
    }

    @FunctionalInterface
    public interface UnitOutageFeed {
        List<UnitOutage> openOutages(UUID tenantId);
    }
}
