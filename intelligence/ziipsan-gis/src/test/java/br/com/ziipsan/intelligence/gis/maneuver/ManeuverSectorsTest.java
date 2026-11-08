package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverSectors.ManeuverSector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Setores de manobra pré-calculados: partição pelas válvulas OPERÁVEIS. */
class ManeuverSectorsTest {

    @Test
    void precomputedSectorsAreDeterministicAndKnown() {
        List<ManeuverSector> sectors = ManeuverSectors.precompute(base());
        assertEquals(3, sectors.size());
        assertEquals("MS-01", sectors.get(0).sectorCode());
        assertEquals(List.of(N1, S), sectors.get(0).nodeIds());
        assertEquals(List.of(VA), sectors.get(0).boundaryValveNodeIds());
        assertTrue(sectors.get(0).hasSource());

        assertEquals("MS-02", sectors.get(1).sectorCode());
        assertEquals(List.of(N2, N3, N6, VX), sectors.get(1).nodeIds());
        assertEquals(List.of(VA, VB), sectors.get(1).boundaryValveNodeIds());
        assertEquals(6, sectors.get(1).totalEconomies());

        assertEquals("MS-03", sectors.get(2).sectorCode());
        assertEquals(List.of(N4, N5, Z1), sectors.get(2).nodeIds());
        assertEquals(List.of(VB), sectors.get(2).boundaryValveNodeIds());
    }

    @Test
    void inoperableValveDoesNotSplitSector() {
        // VX é inoperável: N2 e N3 ficam no MESMO setor (ela não delimita).
        List<ManeuverSector> sectors = ManeuverSectors.precompute(base());
        ManeuverSector s = ManeuverSectors.sectorOf(sectors, N3);
        assertTrue(s.nodeIds().contains(N2));
        assertTrue(s.nodeIds().contains(VX));
    }

    @Test
    void operableValveNodeBelongsToNoSector() {
        List<ManeuverSector> sectors = ManeuverSectors.precompute(base());
        assertNull(ManeuverSectors.sectorOf(sectors, VA));
    }
}
