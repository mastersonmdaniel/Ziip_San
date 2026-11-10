package br.com.ziipsan.commercial.registry.municipality;

import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.BlockRef;
import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.LocalityRef;
import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.SectorRef;
import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.UnitRef;
import br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScope.Kind;
import br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScope.MixedReason;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MM-03 — Escopo rota->municipio sobre a derivacao do MM-01.
 * IBGEs FICTICIOS da base ficticia 12k (9990001/9990002, lidos do seed).
 *
 * Cadeia da fixture:
 *   locA(1, IBGE_A) <- secA(11) <- blkA(21)
 *   locB(2, IBGE_B) <- secB(12) <- blkB(22)
 *   locA2(3, IBGE_A) <- secA2(13) <- blkA2(23)   (2.a localidade, mesmo IBGE)
 *   locNula(4, null) <- secNula(14) <- blkNula(24)
 */
class RouteMunicipalityScopeTest {

    static final UUID TENANT = UUID.fromString("11111111-2222-4333-8444-555555555555");
    static final String IBGE_A = "9990001";
    static final String IBGE_B = "9990002";

    static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012d", n));
    }

    static MunicipalityDerivation chain() {
        return new MunicipalityDerivation(
                List.of(new LocalityRef(TENANT, id(1), IBGE_A),
                        new LocalityRef(TENANT, id(2), IBGE_B),
                        new LocalityRef(TENANT, id(3), IBGE_A),
                        new LocalityRef(TENANT, id(4), null)),
                List.of(new SectorRef(TENANT, id(11), id(1)),
                        new SectorRef(TENANT, id(12), id(2)),
                        new SectorRef(TENANT, id(13), id(3)),
                        new SectorRef(TENANT, id(14), id(4))),
                List.of(new BlockRef(TENANT, id(21), id(11)),
                        new BlockRef(TENANT, id(22), id(12)),
                        new BlockRef(TENANT, id(23), id(13)),
                        new BlockRef(TENANT, id(24), id(14))));
    }

    static RouteMunicipalityResolver resolver() {
        return new RouteMunicipalityResolver(chain());
    }

    static UnitRef unit(int n, int block) {
        return new UnitRef(TENANT, id(n), id(block));
    }

    @Test
    void rotaPura() {
        RouteMunicipalityScope s = resolver().resolve(TENANT, id(501),
                List.of(unit(101, 21), unit(102, 21), unit(103, 21)));
        assertEquals(Kind.PURE, s.kind());
        assertEquals(IBGE_A, s.municipalityIbge().orElseThrow());
        assertEquals(Map.of(IBGE_A, 3), s.unitsByMunicipality());
        assertEquals(0, s.underivedUnits());
        assertEquals(3, s.totalUnits());
        assertTrue(s.isPure());
    }

    @Test
    void rotaMistaComContagensCertas() {
        RouteMunicipalityScope s = resolver().resolve(TENANT, id(502),
                List.of(unit(101, 21), unit(102, 21), unit(103, 22)));
        assertEquals(Kind.MIXED, s.kind());
        assertEquals(Map.of(IBGE_A, 2, IBGE_B, 1), s.unitsByMunicipality());
        assertEquals(3, s.totalUnits());
        assertTrue(s.municipalityIbge().isEmpty());
        assertEquals(MixedReason.MULTIPLE_MUNICIPALITIES, s.mixedReason());
    }

    @Test
    void rotaVazia() {
        RouteMunicipalityScope s = resolver().resolve(TENANT, id(503), List.of());
        assertEquals(Kind.EMPTY, s.kind());
        assertTrue(s.unitsByMunicipality().isEmpty());
        assertEquals(0, s.totalUnits());
        assertTrue(s.municipalityIbge().isEmpty());
    }

    @Test
    void ucsDeDoisMunicipiosViaLocalidadesDistintas() {
        // A UC de blkA chega ao municipio A pela localidade 1; a de blkB
        // chega ao municipio B pela localidade 2 — localidades DISTINTAS,
        // municipios distintos: a rota e mista pelos dois caminhos reais.
        MunicipalityDerivation d = chain();
        assertEquals(IBGE_A, d.derive(unit(101, 21)).orElseThrow());
        assertEquals(IBGE_B, d.derive(unit(102, 22)).orElseThrow());
        RouteMunicipalityScope s = resolver().resolve(TENANT, id(504),
                List.of(unit(101, 21), unit(102, 22)));
        assertEquals(Kind.MIXED, s.kind());
        assertEquals(Map.of(IBGE_A, 1, IBGE_B, 1), s.unitsByMunicipality());
    }

    @Test
    void duasLocalidadesDistintasComOMesmoIbgeContinuamPuras() {
        // O municipio e o IBGE (D-MM-01), nao a localidade: UCs servidas
        // por duas localidades diferentes do MESMO municipio = rota pura.
        RouteMunicipalityScope s = resolver().resolve(TENANT, id(505),
                List.of(unit(101, 21), unit(102, 23)));
        assertEquals(Kind.PURE, s.kind());
        assertEquals(IBGE_A, s.municipalityIbge().orElseThrow());
        assertEquals(Map.of(IBGE_A, 2), s.unitsByMunicipality());
    }

    @Test
    void ucSemQuadraImpedeCertificarPureza() {
        // block_id NULL e legal na V3_1, mas essa UC nao tem municipio
        // derivavel: a rota NAO pode ser declarada pura.
        RouteMunicipalityScope s = resolver().resolve(TENANT, id(506),
                List.of(unit(101, 21), new UnitRef(TENANT, id(102), null)));
        assertEquals(Kind.MIXED, s.kind());
        assertEquals(Map.of(IBGE_A, 1), s.unitsByMunicipality());
        assertEquals(1, s.underivedUnits());
        assertEquals(2, s.totalUnits());
        assertEquals(MixedReason.UNDETERMINED_UNITS, s.mixedReason());
    }

    @Test
    void localidadeComIbgeNuloContaComoInderivavel() {
        RouteMunicipalityScope s = resolver().resolve(TENANT, id(507),
                List.of(unit(101, 24)));
        assertEquals(Kind.MIXED, s.kind());
        assertTrue(s.unitsByMunicipality().isEmpty());
        assertEquals(1, s.underivedUnits());
        assertEquals(MixedReason.UNDETERMINED_UNITS, s.mixedReason());
    }

    @Test
    void escopoRejeitaKindInconsistenteComAsContagens() {
        assertThrows(IllegalArgumentException.class, () ->
                new RouteMunicipalityScope(id(508), TENANT, Kind.PURE,
                        Map.of(IBGE_A, 1, IBGE_B, 1), 0, 2));
        assertThrows(IllegalArgumentException.class, () ->
                RouteMunicipalityScope.of(id(508), TENANT, Map.of(IBGE_A, 1), -1));
    }

    @Test
    void unidadeDeOutroTenantNaRotaFalhaExplicito() {
        UUID other = UUID.fromString("22222222-3333-4444-8555-666666666666");
        assertThrows(IllegalArgumentException.class, () ->
                resolver().resolve(TENANT, id(509),
                        List.of(new UnitRef(other, id(101), id(21)))));
    }
}
