package br.com.ziipsan.commercial.registry.municipality;

import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.BlockRef;
import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.DerivationStatus;
import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.LocalityRef;
import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.SectorRef;
import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.UnitRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MM-01 — Derivacao UC->municipio sobre a cadeia em memoria.
 * Os IBGEs sao os codigos FICTICIOS da base ficticia 12k (lidos do seed:
 * localities 9990001/9990002), nunca codigos reais.
 */
class MunicipalityDerivationTest {

    private static final UUID TENANT = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final String IBGE_A = "9990001"; // ficticio (seed base-ficticia-12k)
    private static final String IBGE_B = "9990002"; // ficticio (seed base-ficticia-12k)

    private static UUID id(int n) {
        return UUID.fromString(String.format("00000000-0000-4000-8000-%012d", n));
    }

    // Cadeia: locA(1) <- secA(11) <- blkA(21); locB(2) <- secB(12) <- blkB(22)
    private MunicipalityDerivation chain() {
        return new MunicipalityDerivation(
                List.of(new LocalityRef(TENANT, id(1), IBGE_A),
                        new LocalityRef(TENANT, id(2), IBGE_B)),
                List.of(new SectorRef(TENANT, id(11), id(1)),
                        new SectorRef(TENANT, id(12), id(2))),
                List.of(new BlockRef(TENANT, id(21), id(11)),
                        new BlockRef(TENANT, id(22), id(12))));
    }

    @Test
    void ucEmCadaMunicipioDerivaOSeuIbge() {
        MunicipalityDerivation d = chain();
        assertEquals(IBGE_A, d.derive(new UnitRef(TENANT, id(101), id(21))).orElseThrow());
        assertEquals(IBGE_B, d.derive(new UnitRef(TENANT, id(102), id(22))).orElseThrow());
    }

    @Test
    void dedupeDeLocalidadesComOMesmoIbge() {
        // O DDL (V3_1) so impoe unicidade de (tenant_id, code) em
        // localities: duas localidades com o mesmo IBGE sao legais e
        // tem de contar UMA vez para o backfill de perfis.
        Set<String> ibges = MunicipalityDerivation.distinctMunicipalities(TENANT, List.of(
                new LocalityRef(TENANT, id(1), IBGE_A),
                new LocalityRef(TENANT, id(3), IBGE_A),
                new LocalityRef(TENANT, id(2), IBGE_B),
                new LocalityRef(TENANT, id(4), null)));
        assertEquals(Set.of(IBGE_A, IBGE_B), ibges);
    }

    @Test
    void ucSemQuadraNaoTemDerivacao() {
        // block_id NULL e permitido pela V3_1 (FK anulavel).
        MunicipalityDerivation d = chain();
        UnitRef u = new UnitRef(TENANT, id(103), null);
        assertTrue(d.derive(u).isEmpty());
        assertEquals(DerivationStatus.NO_BLOCK, d.deriveDetailed(u).status());
    }

    @Test
    void localidadeComIbgeNuloDistingueSeDeCadeiaQuebrada() {
        MunicipalityDerivation d = new MunicipalityDerivation(
                List.of(new LocalityRef(TENANT, id(1), null)),
                List.of(new SectorRef(TENANT, id(11), id(1))),
                List.of(new BlockRef(TENANT, id(21), id(11))));
        assertEquals(DerivationStatus.NULL_MUNICIPALITY,
                d.deriveDetailed(new UnitRef(TENANT, id(104), id(21))).status());
    }

    @Test
    void ligacaoDeOutroTenantNaoResolve() {
        // As FK da cadeia sao compostas por (tenant_id, id): uma UC que
        // aponte para a quadra de outro tenant nao deriva nada.
        MunicipalityDerivation d = chain();
        UUID other = UUID.fromString("22222222-3333-4444-8555-666666666666");
        assertEquals(DerivationStatus.BROKEN_CHAIN,
                d.deriveDetailed(new UnitRef(other, id(105), id(21))).status());
    }
}
