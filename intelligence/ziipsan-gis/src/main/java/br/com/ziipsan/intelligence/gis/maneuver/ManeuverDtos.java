package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.IsolationEngine.IsolationResult;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverSectors.ManeuverSector;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** DTOs da superfície da GIS-11 (pedidos e visões, sem entidade de banco). */
public final class ManeuverDtos {

    private ManeuverDtos() {
    }

    /** Pedido de traçado/isolamento: exatamente um de {@code arcId}/{@code nodeId}. */
    public record TargetInput(UUID arcId, UUID nodeId) {
    }

    public record TraceView(List<UUID> upstreamNodeIds, List<UUID> downstreamNodeIds) {
        public TraceView {
            upstreamNodeIds = List.copyOf(upstreamNodeIds);
            downstreamNodeIds = List.copyOf(downstreamNodeIds);
        }
    }

    public record IsolationView(boolean isolable, String failure,
                                List<String> valvesToClose, List<String> inoperableTraversed,
                                List<UUID> affectedNodeIds, List<UUID> affectedArcIds,
                                List<UUID> affectedUnitIds, int totalEconomies,
                                List<UUID> essentialUnitIds) {
        public static IsolationView of(IsolationResult r) {
            return new IsolationView(r.isolable(), r.failure() == null ? null : r.failure().name(),
                    r.valvesToClose().stream().map(v -> v.code()).toList(),
                    r.inoperableTraversed().stream().map(v -> v.code()).toList(),
                    r.affectedNodeIds(), r.affectedArcIds(), r.affectedUnitIds(), r.totalEconomies(),
                    r.essentialConnections().stream()
                            .flatMap(c -> c.consumerUnitIds().stream()).distinct().toList());
        }
    }

    public record SectorView(String sectorCode, List<UUID> nodeIds, List<UUID> arcIds,
                             List<UUID> boundaryValveNodeIds, int totalEconomies, boolean hasSource) {
        public static SectorView of(ManeuverSector s) {
            return new SectorView(s.sectorCode(), s.nodeIds(), s.arcIds(),
                    s.boundaryValveNodeIds(), s.totalEconomies(), s.hasSource());
        }
    }

    public record PlanInput(TargetInput target, LocalDateTime scheduledStart,
                            LocalDateTime expectedReturn, String reason) {
    }

    public record AreaView(List<UUID> nodeIds, List<String> polygonPoints, boolean polygonAvailable) {
    }
}
