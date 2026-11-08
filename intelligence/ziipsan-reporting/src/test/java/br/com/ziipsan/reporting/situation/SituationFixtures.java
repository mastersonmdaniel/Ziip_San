package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.core.incident.NetworkIncident;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Massa SINTETICA declarada da ED-93: instantes, codigos e coordenadas
 * de teste. Nenhum valor aqui e dado real de operacao, limiar ou meta
 * — sao os veiculos das regras do aceite. O teto de latencia de 3
 * minutos usado nos testes e FIXTURE desta massa (interpreta a frase
 * do aceite para o teste), NAO padrao do produto: o produto exige o
 * teto configurado ([SEM FONTE], SituationLatencyPolicy).
 */
final class SituationFixtures {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000093");
    static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000094");
    static final Instant NOW = Instant.parse("2026-11-08T15:00:00Z");
    static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final SituationLatencyPolicy POLICY_3MIN = new SituationLatencyPolicy(java.time.Duration.ofMinutes(3));

    static NetworkIncident incident(String code, NetworkIncident.Type type, LocalDateTime startedAt) {
        return new NetworkIncident(UUID.randomUUID(), TENANT, code, type, false,
                NetworkIncident.Origin.COMPLAINTS, startedAt,
                new NetworkIncident.AffectedSnapshot(List.of(), 12, null), null);
    }

    static NetworkIncident closedIncident(String code) {
        NetworkIncident incident = incident(code, NetworkIncident.Type.RUPTURE,
                NOW_LOCAL.minusHours(10));
        UUID repairOrder = UUID.randomUUID();
        incident.linkWorkOrder(repairOrder);
        incident.markRestored(NOW_LOCAL.minusHours(2));
        incident.close(new NetworkIncident.ClosureData("ALAGAMENTO", null, null, repairOrder),
                List.of("ALAGAMENTO"));
        return incident;
    }

    static SituationProjector projector(SituationFeeds.IncidentFeed incidents,
                                        SituationFeeds.WorkOrderFeed workOrders,
                                        SituationFeeds.CrewFeed crews,
                                        SituationFeeds.TelemetryAlarmFeed alarms,
                                        SituationFeeds.ReservoirFeed reservoirs,
                                        SituationFeeds.UnitOutageFeed outages) {
        return new SituationProjector(incidents, workOrders, crews, alarms, reservoirs, outages, CLOCK);
    }

    static SituationProjector emptyProjector() {
        return projector(t -> List.of(), t -> List.of(), t -> List.of(),
                t -> List.of(), t -> List.of(), t -> List.of());
    }

    private SituationFixtures() {
    }
}
