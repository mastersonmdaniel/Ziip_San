package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.InfluenceBasinService.AreaResult;
import br.com.ziipsan.intelligence.gis.maneuver.InfluenceBasinService.Basin;
import br.com.ziipsan.intelligence.gis.maneuver.InfluenceBasinService.Point;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;
import br.com.ziipsan.intelligence.gis.maneuver.PlannedOutageAreaService.AffectedArea;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Interrupção programada da ED-35 com área CALCULADA pelo isolamento
 * (teste com resultado conhecido) + áreas de influência, bacias e
 * polígonos de roteamento (SG-887, SG-889).
 */
class PlannedOutageAndAreasTest {

    @Test
    void plannedOutageAreaComesFromIsolationWithEssentials() {
        AffectedArea area = PlannedOutageAreaService.compute(base(), Target.arc(A5));
        assertTrue(area.calculable());
        assertEquals(List.of(U2, U3, U4, U1), area.affectedUnitIds());
        assertEquals(List.of(new PlannedOutageAreaService.EssentialAffected(U4, "HOSPITAL")),
                area.essentialUnits());
        assertEquals(List.of(U2, U3), area.sensitiveUnitIds());
        assertEquals(6, area.totalEconomies());
        // O aviso da interrupção usa o MESMO pedido da ED-41 do plano de manobra.
        var notice = PlannedOutageAreaService.toNoticeRequest(TENANT, id(600), area,
                LocalDateTime.of(2026, 11, 10, 8, 0), LocalDateTime.of(2026, 11, 10, 18, 0), "manutencao");
        assertEquals(area.affectedUnitIds(), notice.consumerUnitIdsInNoticeOrder());
        assertEquals(List.of(U4), notice.essentialUnitIds());
    }

    @Test
    void unisolableOutageAreaIsNotCalculable() {
        AffectedArea area = PlannedOutageAreaService.compute(base(), Target.arc(A1));
        assertFalse(area.calculable());
        assertEquals("SOURCE_REACHED_WITHOUT_VALVE", area.failure());
        assertThrows(IllegalArgumentException.class, () -> PlannedOutageAreaService.toNoticeRequest(
                TENANT, id(600), area, LocalDateTime.of(2026, 11, 10, 8, 0),
                LocalDateTime.of(2026, 11, 10, 18, 0), "x"));
    }

    @Test
    void basinIsUpstreamAndInfluenceIsDownstream() {
        AreaResult basin = InfluenceBasinService.basinOf(base(), N3);
        assertEquals(java.util.Set.of(N3, VX, N2, VA, N1, S), java.util.Set.copyOf(basin.nodeIds()));
        AreaResult influence = InfluenceBasinService.influenceOf(base(), N3);
        assertEquals(java.util.Set.of(N3, VB, N4, N5, N6), java.util.Set.copyOf(influence.nodeIds()));
    }

    @Test
    void routingPolygonIsTheConvexHullOfTheArea() {
        AreaResult influence = InfluenceBasinService.influenceOf(base(), N3);
        assertTrue(influence.polygonAvailable());
        assertEquals(List.of(new Point(5.0, 0.0), new Point(8.0, 0.0), new Point(5.0, 2.0)),
                influence.routingPolygon());
        // Bacia linear (tudo em y=0): a envolvente é o segmento S..N3.
        AreaResult basin = InfluenceBasinService.basinOf(base(), N3);
        assertEquals(List.of(new Point(0.0, 0.0), new Point(5.0, 0.0)), basin.routingPolygon());
    }

    @Test
    void basinsBySourceLeavesOrphanVisibleAsSemFonte() {
        List<Basin> basins = InfluenceBasinService.basinsBySource(base());
        assertEquals(2, basins.size());
        assertEquals("BACIA-S", basins.get(0).basinCode());
        assertEquals(10, basins.get(0).nodeIds().size());
        assertEquals("SEM_FONTE", basins.get(1).basinCode());
        assertEquals(List.of(Z1), basins.get(1).nodeIds());
    }

    @Test
    void functionalConnectionRulesFlagBadLinksOnly() {
        assertTrue(FunctionalConnectionRules.validate(base()).isEmpty());
        var issues = FunctionalConnectionRules.validate(withBadConnections());
        assertEquals(List.of("FUNCTIONAL_CONNECTION_ON_RETIRED_ARC",
                        "FUNCTIONAL_CONNECTION_EMPTY",
                        "FUNCTIONAL_CONNECTION_DUPLICATED"),
                issues.stream().map(i -> i.ruleCode()).toList());
        assertEquals(List.of("C5", "C5", "C6"),
                issues.stream().map(i -> i.connectionCode()).toList());
    }

    @Test
    void coverageRegistersSgTrAndSamaeItems() {
        assertEquals(List.of("SG-852", "SG-887", "SG-889", "TR-1064", "TR-1065", "SAMAE-2.27"),
                ManeuverCoverage.ITEMS.stream().map(i -> i.code()).toList());
    }
}
