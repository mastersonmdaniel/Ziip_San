package br.com.ziipsan.intelligence.gis.municipality;

import br.com.ziipsan.intelligence.gis.municipality.GisMunicipalityCut.CutRow;
import br.com.ziipsan.intelligence.gis.municipality.GisMunicipalityCut.DerivationStatus;
import br.com.ziipsan.intelligence.gis.municipality.GisMunicipalityCut.DerivationVia;
import br.com.ziipsan.intelligence.gis.municipality.GisMunicipalityCut.ElementRef;
import br.com.ziipsan.intelligence.gis.municipality.GisMunicipalityCut.ResolutionKey;
import br.com.ziipsan.intelligence.gis.municipality.GisMunicipalityCut.SectorMunicipality;
import br.com.ziipsan.intelligence.gis.municipality.GisMunicipalityCut.SourceTable;
import br.com.ziipsan.intelligence.gis.municipality.GisMunicipalityCut.UnitMunicipality;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MM-07 — Recorte tematico GIS por municipio sobre fixtures em memoria.
 * Os IBGEs sao os codigos FICTICIOS da base ficticia 12k (os mesmos dos
 * testes do MM-01: 9990001/9990002), nunca codigos reais.
 *
 * Fixture territorial (espelha as vias do DDL):
 *   ligacao SC_A(201) -> ancora UC_A(101), cuja derivacao MM-01 = IBGE_A;
 *   ligacao SC_B(202) -> ancora UC_B(102), cuja derivacao MM-01 = IBGE_B;
 *   setor SEC_A(301) -> IBGE_A; setor SEC_B(302) -> IBGE_B
 *   (resolucao setor->localidade do registry).
 */
class GisMunicipalityCutTest {

    private static final UUID TENANT = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID OTHER_TENANT = UUID.fromString("22222222-3333-4444-8555-666666666666");
    private static final String IBGE_A = "9990001"; // ficticio (seed base-ficticia-12k)
    private static final String IBGE_B = "9990002"; // ficticio (seed base-ficticia-12k)

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012d", n));
    }

    private GisMunicipalityCut cut() {
        return new GisMunicipalityCut(
                Map.of(new ResolutionKey(TENANT, id(201)), id(101),
                       new ResolutionKey(TENANT, id(202)), id(102),
                       new ResolutionKey(TENANT, id(203)), id(103),
                       new ResolutionKey(TENANT, id(204)), id(104)),
                Map.of(new ResolutionKey(TENANT, id(101)), UnitMunicipality.derived(IBGE_A),
                       new ResolutionKey(TENANT, id(102)), UnitMunicipality.derived(IBGE_B),
                       new ResolutionKey(TENANT, id(103)), UnitMunicipality.absent(),
                       new ResolutionKey(TENANT, id(104)), UnitMunicipality.nullMunicipality()),
                Map.of(new ResolutionKey(TENANT, id(301)), SectorMunicipality.derived(IBGE_A),
                       new ResolutionKey(TENANT, id(302)), SectorMunicipality.derived(IBGE_B),
                       new ResolutionKey(TENANT, id(303)), SectorMunicipality.nullMunicipality()));
    }

    @Test
    void ramalLigaAUcDeCadaMunicipioDerivaOSeuIbge() {
        GisMunicipalityCut c = cut();
        CutRow a = c.cut(ElementRef.connection(TENANT, id(401), "RAMAL-A", null, id(201)));
        CutRow b = c.cut(ElementRef.connection(TENANT, id(402), "RAMAL-B", null, id(202)));
        assertEquals(DerivationStatus.DERIVED, a.status());
        assertEquals(IBGE_A, a.municipalityIbge());
        assertEquals(DerivationVia.SERVICE_CONNECTION, a.via());
        assertEquals(id(101), a.consumerUnitId());
        assertEquals(DerivationStatus.DERIVED, b.status());
        assertEquals(IBGE_B, b.municipalityIbge());
    }

    @Test
    void zonaDeSetorComercialLigaALocalidadeDerivaOSeuIbge() {
        GisMunicipalityCut c = cut();
        CutRow z = c.cut(ElementRef.zone(TENANT, id(410), "ZONA-SETOR-B",
                null, null, "COMMERCIAL_SECTOR", id(302)));
        assertEquals(DerivationStatus.DERIVED, z.status());
        assertEquals(IBGE_B, z.municipalityIbge());
        assertEquals(DerivationVia.COMMERCIAL_SECTOR, z.via());
    }

    @Test
    void elementoSemLigacaoApareceComoExcepcaoExplicita() {
        GisMunicipalityCut c = cut();
        List<ElementRef> fixture = List.of(
                // Ramal sem ligacao (service_connection_id NULL, V13_2):
                ElementRef.connection(TENANT, id(420), "RAMAL-ORFAO", null, null),
                // Arco sem nenhuma ligacao territorial:
                ElementRef.simple(TENANT, SourceTable.GIS_ARC, id(421), "ARCO-1", null));
        List<CutRow> rows = c.cutAll(fixture);
        assertEquals(DerivationStatus.NO_SERVICE_CONNECTION, rows.get(0).status());
        assertNull(rows.get(0).municipalityIbge());
        assertEquals(DerivationStatus.NO_TERRITORIAL_LINK, rows.get(1).status());
        // Ambos estao na lista de excepcoes — nenhum desaparece:
        assertEquals(2, c.exceptions(TENANT, rows).size());
        assertTrue(c.countByMunicipality(TENANT, rows).isEmpty());
    }

    @Test
    void elementoSoComDmaNaoDerivaMunicipio() {
        GisMunicipalityCut c = cut();
        // O DMA nao determina municipio (blocks.dma_id e N:1, sem
        // confinamento a uma localidade): derivar por ele seria inventar.
        CutRow no = c.cut(ElementRef.simple(TENANT, SourceTable.GIS_NODE, id(430), "NO-1", id(501)));
        assertEquals(DerivationStatus.DMA_ONLY_UNRESOLVED, no.status());
        assertNull(no.municipalityIbge());
        CutRow zonaDma = c.cut(ElementRef.zone(TENANT, id(431), "ZONA-DMA",
                null, id(502), "DMA", null));
        assertEquals(DerivationStatus.DMA_ONLY_UNRESOLVED, zonaDma.status());
    }

    @Test
    void cadeiaDoRamalDistingueOsMotivosDeExcepcao() {
        GisMunicipalityCut c = cut();
        // Ligacao inexistente no tenant (id presente, sem alvo):
        CutRow broken = c.cut(ElementRef.connection(TENANT, id(440), "RAMAL-X", null, id(299)));
        assertEquals(DerivationStatus.BROKEN_CHAIN, broken.status());
        // Ancora sem derivacao no MM-01 (UC sem quadra):
        CutRow semDeriv = c.cut(ElementRef.connection(TENANT, id(441), "RAMAL-Y", null, id(203)));
        assertEquals(DerivationStatus.NO_UNIT_DERIVATION, semDeriv.status());
        // Ancora derivada mas com IBGE nulo na localidade:
        CutRow ibgeNulo = c.cut(ElementRef.connection(TENANT, id(442), "RAMAL-Z", null, id(204)));
        assertEquals(DerivationStatus.NULL_MUNICIPALITY, ibgeNulo.status());
    }

    @Test
    void zonaDistingueOsMotivosDeExcepcao() {
        GisMunicipalityCut c = cut();
        CutRow semSetor = c.cut(ElementRef.zone(TENANT, id(450), "ZONA-S",
                null, null, "COMMERCIAL_SECTOR", null));
        assertEquals(DerivationStatus.NO_SECTOR_LINK, semSetor.status());
        // Setor inexistente: a coluna nao tem FK (V13_2) — nao resolve:
        CutRow broken = c.cut(ElementRef.zone(TENANT, id(451), "ZONA-T",
                null, null, "COMMERCIAL_SECTOR", id(399)));
        assertEquals(DerivationStatus.BROKEN_CHAIN, broken.status());
        CutRow ibgeNulo = c.cut(ElementRef.zone(TENANT, id(452), "ZONA-U",
                null, null, "COMMERCIAL_SECTOR", id(303)));
        assertEquals(DerivationStatus.NULL_MUNICIPALITY, ibgeNulo.status());
        // Zona de outro tipo com commercial_sector_id preenchido por
        // engano NAO deriva por ele: a coluna so significa setor nas
        // zonas COMMERCIAL_SECTOR (taxonomia da V13_2):
        CutRow pressao = c.cut(ElementRef.zone(TENANT, id(453), "ZONA-P",
                null, null, "PRESSURE_ZONE", id(301)));
        assertEquals(DerivationStatus.NO_TERRITORIAL_LINK, pressao.status());
    }

    @Test
    void ligacaoDeOutroTenantNuncaResolve() {
        GisMunicipalityCut c = cut();
        // A ligacao 201 existe, mas sob TENANT: vista de OTHER_TENANT,
        // a FK composta (tenant_id, id) nao resolve — BROKEN_CHAIN.
        CutRow r = c.cut(ElementRef.connection(OTHER_TENANT, id(460), "RAMAL-EXT", null, id(201)));
        assertEquals(DerivationStatus.BROKEN_CHAIN, r.status());
        assertTrue(c.countByMunicipality(OTHER_TENANT, List.of(r)).isEmpty());
    }

    @Test
    void invarianteSomaPorMunicipioMaisExcepcoesIgualTotal() {
        GisMunicipalityCut c = cut();
        List<ElementRef> fixture = List.of(
                ElementRef.connection(TENANT, id(401), "RAMAL-A", null, id(201)),
                ElementRef.connection(TENANT, id(402), "RAMAL-B", null, id(202)),
                ElementRef.connection(TENANT, id(403), "RAMAL-A2", id(501), id(201)),
                ElementRef.zone(TENANT, id(410), "ZONA-SETOR-B", null, null, "COMMERCIAL_SECTOR", id(302)),
                ElementRef.zone(TENANT, id(411), "ZONA-SETOR-A", null, null, "COMMERCIAL_SECTOR", id(301)),
                ElementRef.connection(TENANT, id(420), "RAMAL-ORFAO", null, null),
                ElementRef.simple(TENANT, SourceTable.GIS_NODE, id(430), "NO-1", id(501)),
                ElementRef.simple(TENANT, SourceTable.GIS_ARC, id(421), "ARCO-1", null),
                ElementRef.simple(TENANT, SourceTable.GIS_STRUCTURE, id(432), "ETA-1", id(503)),
                ElementRef.simple(TENANT, SourceTable.GIS_POI, id(433), "POI-1", null));
        List<CutRow> rows = c.cutAll(fixture);
        assertEquals(fixture.size(), rows.size());
        Map<String, Long> counts = c.countByMunicipality(TENANT, rows);
        assertEquals(Map.of(IBGE_A, 3L, IBGE_B, 2L), counts);
        List<CutRow> exc = c.exceptions(TENANT, rows);
        assertEquals(5, exc.size());
        long soma = counts.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(fixture.size(), soma + exc.size());
        // O filtro tematico devolve exactamente as feicoes do municipio:
        assertEquals(3, c.listByMunicipality(TENANT, IBGE_A, rows).size());
        assertEquals(2, c.listByMunicipality(TENANT, IBGE_B, rows).size());
        assertTrue(c.listByMunicipality(TENANT, IBGE_A, rows).stream()
                .allMatch(r -> r.isDerived() && IBGE_A.equals(r.municipalityIbge())));
    }
}
