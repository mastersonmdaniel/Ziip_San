package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.core.backoffice.OrderMonitoring;
import br.com.ziipsan.core.incident.NetworkIncident;
import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-93: a projecao — quem ESCREVE a tabela de estado resumida a
 * partir das portas de fonte. Roda fora do caminho de leitura: o
 * console nunca espera a fonte, le o retrato ja projetado (e por
 * isso a latencia do console e a idade do retrato, medida pela
 * {@link SituationLatencyPolicy}).
 *
 * <p>Regras de projecao (todas com teste):
 * <ul>
 *   <li>ocorrencias ABERTAS = OPEN ou CONTROLLED na entidade real da
 *       ED-92; RESTORED (servico restabelecido, aguardando o
 *       encerramento formal) e CLOSED nao sao "abertas" no console —
 *       leitura declarada do aceite ("ocorrencias abertas");</li>
 *   <li>OS terminais (CLOSED_*) nao entram; vencida e calculada pela
 *       regra REAL da ED-36 ({@link OrderMonitoring#timeliness});
 *       OS sem dueAt e sem deadline do tipo entra com
 *       {@code timeliness=UNKNOWN} — prazo nao se inventa;</li>
 *   <li>alarme RESOLVED nao entra; OPEN/ACKNOWLEDGED entram;</li>
 *   <li>reservatorio entra com a faixa da porta: UNKNOWN sem limiar
 *       declarado do tenant, nunca faixa presumida;</li>
 *   <li>instantes {@link LocalDateTime} das fontes (ED-92/ED-36) sao
 *       lidos em UTC na projecao — convencao declarada desta camada;
 *       a fiacao real converte do fuso do tenant para Instant antes
 *       de gravar (pendencia P-ED93-01).</li>
 * </ul>
 */
public final class SituationProjector {

    private final SituationFeeds.IncidentFeed incidents;
    private final SituationFeeds.WorkOrderFeed workOrders;
    private final SituationFeeds.CrewFeed crews;
    private final SituationFeeds.TelemetryAlarmFeed alarms;
    private final SituationFeeds.ReservoirFeed reservoirs;
    private final SituationFeeds.UnitOutageFeed outages;
    private final Clock clock;

    public SituationProjector(SituationFeeds.IncidentFeed incidents,
                              SituationFeeds.WorkOrderFeed workOrders,
                              SituationFeeds.CrewFeed crews,
                              SituationFeeds.TelemetryAlarmFeed alarms,
                              SituationFeeds.ReservoirFeed reservoirs,
                              SituationFeeds.UnitOutageFeed outages,
                              Clock clock) {
        this.incidents = Objects.requireNonNull(incidents, "porta de ocorrencias obrigatoria");
        this.workOrders = Objects.requireNonNull(workOrders, "porta de OS obrigatoria");
        this.crews = Objects.requireNonNull(crews, "porta de equipes obrigatoria");
        this.alarms = Objects.requireNonNull(alarms, "porta de alarmes obrigatoria");
        this.reservoirs = Objects.requireNonNull(reservoirs, "porta de reservatorios obrigatoria");
        this.outages = Objects.requireNonNull(outages, "porta de unidades fora de operacao obrigatoria");
        this.clock = Objects.requireNonNull(clock, "relogio obrigatorio");
    }

    /** Projeta o retrato completo do tenant e o grava na tabela de estado. */
    public List<SituationStateRow> projectInto(UUID tenantId, SituationStateStore store) {
        Objects.requireNonNull(store, "tabela de estado obrigatoria");
        List<SituationStateRow> rows = project(tenantId);
        store.replaceTenantSnapshot(tenantId, rows);
        return rows;
    }

    /** Projeta sem gravar (a sonda de latencia usa este caminho + a gravacao). */
    public List<SituationStateRow> project(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenant obrigatorio");
        Instant projectedAt = clock.instant();
        List<SituationStateRow> rows = new ArrayList<>();
        projectIncidents(tenantId, projectedAt, rows);
        projectWorkOrders(tenantId, projectedAt, rows);
        projectCrews(tenantId, projectedAt, rows);
        projectAlarms(tenantId, projectedAt, rows);
        projectReservoirs(tenantId, projectedAt, rows);
        projectOutages(tenantId, projectedAt, rows);
        return List.copyOf(rows);
    }

    private void projectIncidents(UUID tenantId, Instant projectedAt, List<SituationStateRow> rows) {
        for (NetworkIncident incident : incidents.incidents(tenantId)) {
            if (!tenantId.equals(incident.tenantId())) {
                continue; // linha de outro tenant na fonte nao vaza para este retrato
            }
            if (incident.status() != NetworkIncident.Status.OPEN
                    && incident.status() != NetworkIncident.Status.CONTROLLED) {
                continue; // RESTORED/CLOSED nao sao ocorrencia aberta
            }
            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("type", incident.type().name());
            detail.put("origin", incident.origin().name());
            detail.put("planned", Boolean.toString(incident.planned()));
            detail.put("economies", Integer.toString(incident.startSnapshot().totalEconomies()));
            detail.put("workOrders", Integer.toString(incident.workOrderIds().size()));
            rows.add(new SituationStateRow(rowId(tenantId, SituationItemKind.INCIDENT, incident.code()),
                    tenantId, SituationItemKind.INCIDENT, incident.code(),
                    incident.type().name(), incident.status().name(), true,
                    null, null,
                    utc(incident.startedAt()), projectedAt, detail));
        }
    }

    private void projectWorkOrders(UUID tenantId, Instant projectedAt, List<SituationStateRow> rows) {
        LocalDateTime now = LocalDateTime.ofInstant(projectedAt, ZoneOffset.UTC);
        for (SituationFeeds.WorkOrderSnapshot order : workOrders.workOrders(tenantId)) {
            if (order.status().isTerminal()) {
                continue;
            }
            String timeliness;
            boolean overdue;
            if (order.dueKnown()) {
                OrderMonitoring.BoardItem board = new OrderMonitoring.BoardItem(order.orderId(),
                        order.status(), order.priority(), order.createdAt(),
                        order.deadlineHoursOrNull(), order.dueAtOrNull(), null);
                overdue = OrderMonitoring.timeliness(board, now) == OrderMonitoring.Timeliness.OVERDUE;
                timeliness = overdue ? "OVERDUE" : "ON_TRACK";
            } else {
                overdue = false;
                timeliness = "UNKNOWN";
            }
            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("priority", order.priority().name());
            detail.put("timeliness", timeliness);
            if (order.crewCodeOrNull() != null) {
                detail.put("crew", order.crewCodeOrNull());
            }
            rows.add(new SituationStateRow(rowId(tenantId, SituationItemKind.WORK_ORDER, order.orderId().toString()),
                    tenantId, SituationItemKind.WORK_ORDER, order.orderId().toString(),
                    "OS " + order.status().name(), order.status().name(),
                    overdue,
                    order.latitudeOrNull(), order.longitudeOrNull(),
                    utc(order.createdAt()), projectedAt, detail));
        }
    }

    private void projectCrews(UUID tenantId, Instant projectedAt, List<SituationStateRow> rows) {
        for (SituationFeeds.CrewPosition crew : crews.crewPositions(tenantId)) {
            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("positioned", Boolean.toString(crew.positioned()));
            if (crew.currentOrderIdOrNull() != null) {
                detail.put("currentOrder", crew.currentOrderIdOrNull().toString());
            }
            rows.add(new SituationStateRow(rowId(tenantId, SituationItemKind.CREW, crew.crewCode()),
                    tenantId, SituationItemKind.CREW, crew.crewCode(),
                    "Equipe " + crew.crewCode(), crew.positioned() ? "POSITIONED" : "UNPOSITIONED",
                    false, crew.latitudeOrNull(), crew.longitudeOrNull(),
                    crew.reportedAt(), projectedAt, detail));
        }
    }

    private void projectAlarms(UUID tenantId, Instant projectedAt, List<SituationStateRow> rows) {
        for (SituationFeeds.TelemetryAlarm alarm : alarms.alarms(tenantId)) {
            if ("RESOLVED".equals(alarm.status())) {
                continue;
            }
            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("kind", alarm.kind().name());
            detail.put("measuringPoint", alarm.measuringPointCode());
            rows.add(new SituationStateRow(rowId(tenantId, SituationItemKind.TELEMETRY_ALARM, alarm.alarmRef()),
                    tenantId, SituationItemKind.TELEMETRY_ALARM, alarm.alarmRef(),
                    "Alarme " + alarm.kind().name(), alarm.status(), true,
                    alarm.latitudeOrNull(), alarm.longitudeOrNull(),
                    alarm.raisedAt(), projectedAt, detail));
        }
    }

    private void projectReservoirs(UUID tenantId, Instant projectedAt, List<SituationStateRow> rows) {
        for (SituationFeeds.ReservoirLevel level : reservoirs.reservoirLevels(tenantId)) {
            String band = level.band();
            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("levelPercent", level.levelPercent().toPlainString());
            detail.put("band", band);
            rows.add(new SituationStateRow(rowId(tenantId, SituationItemKind.RESERVOIR, level.reservoirCode()),
                    tenantId, SituationItemKind.RESERVOIR, level.reservoirCode(),
                    "Reservatorio " + level.reservoirCode(), band,
                    "LOW".equals(band) || "HIGH".equals(band),
                    level.latitudeOrNull(), level.longitudeOrNull(),
                    level.measuredAt(), projectedAt, detail));
        }
    }

    private void projectOutages(UUID tenantId, Instant projectedAt, List<SituationStateRow> rows) {
        for (SituationFeeds.UnitOutage outage : outages.openOutages(tenantId)) {
            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("reason", outage.reason());
            detail.put("source", outage.source());
            rows.add(new SituationStateRow(rowId(tenantId, SituationItemKind.UNIT_OUTAGE, outage.stationCode()),
                    tenantId, SituationItemKind.UNIT_OUTAGE, outage.stationCode(),
                    "Unidade " + outage.stationCode(), "OUT_OF_OPERATION", true,
                    outage.latitudeOrNull(), outage.longitudeOrNull(),
                    outage.startedAt(), projectedAt, detail));
        }
    }

    /** Id deterministico da linha: mesma fonte projeta para a mesma linha (upsert natural). */
    static UUID rowId(UUID tenantId, SituationItemKind kind, String itemRef) {
        return UUID.nameUUIDFromBytes(
                (tenantId + "|" + kind.name() + "|" + itemRef).getBytes(StandardCharsets.UTF_8));
    }

    private static Instant utc(LocalDateTime local) {
        return local.toInstant(ZoneOffset.UTC);
    }
}
