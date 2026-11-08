package br.com.ziipsan.core.stationops;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Massa SINTETICA da ED-87: nenhum valor aqui e dado real nem norma. */
final class TestSupport {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final Instant T0 = Instant.parse("2026-03-10T06:00:00Z");

    private TestSupport() {}

    static StationUnit etaRapida() {
        return new StationUnit(UUID.randomUUID(), TENANT, "ETA-01", StationUnit.UnitKind.WTP,
                "SISTEMA-A", StationUnit.Filtration.RAPID, List.of("F1", "F2"), true);
    }

    static StationUnit reservatorio() {
        return new StationUnit(UUID.randomUUID(), TENANT, "RES-01", StationUnit.UnitKind.RESERVOIR,
                "SISTEMA-A", StationUnit.Filtration.NOT_APPLICABLE, List.of(), true);
    }

    static List<StationOpsParameters.ShiftDefinition> pattern() {
        return List.of(
                new StationOpsParameters.ShiftDefinition("DIA", LocalTime.of(6, 0), LocalTime.of(18, 0)),
                new StationOpsParameters.ShiftDefinition("NOITE", LocalTime.of(18, 0), LocalTime.of(6, 0)));
    }

    static RoundRoute route(UUID unitId) {
        return new RoundRoute(UUID.randomUUID(), TENANT, unitId, "RONDA-ETA",
                List.of(new RoundRoute.ChecklistItem(1, "NIVEL", "Conferir nivel", true),
                        new RoundRoute.ChecklistItem(2, "CLORO", "Conferir dosagem", true),
                        new RoundRoute.ChecklistItem(3, "LIMPEZA", "Limpeza da area", false)),
                true);
    }

    static RoundExecution completedExecution(RoundRoute route, String clientKey, Instant completedAt) {
        RoundExecution e = new RoundExecution(UUID.randomUUID(), TENANT, route,
                completedAt.plusSeconds(1800), clientKey);
        e.start("operador-1", completedAt.minusSeconds(600));
        e.answer(new RoundExecution.ItemAnswer("NIVEL", RoundExecution.ItemResult.OK, null));
        e.answer(new RoundExecution.ItemAnswer("CLORO", RoundExecution.ItemResult.OK, null));
        e.complete(completedAt);
        return e;
    }
}
