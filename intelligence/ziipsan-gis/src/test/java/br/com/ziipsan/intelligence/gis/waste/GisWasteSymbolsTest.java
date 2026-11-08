package br.com.ziipsan.intelligence.gis.waste;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Símbolos da GIS-14: inventário e cobertura. A biblioteca vazia é o
 * estado REAL hoje (arte da P1.30 pendente): o teste prova que a
 * cobertura acusa as 65 chaves em falta — não finge cobertura.
 */
class GisWasteSymbolsTest {

    @Test
    void fiveStatesTimes13Types() {
        assertEquals(5, GisWasteSymbols.SYMBOL_STATES.size());
        assertEquals(65, GisWasteSymbols.expectedKeys().size());
        assertEquals(13, GisWasteSymbols.manifest().size());
        assertEquals("waste-container@IN_SERVICE",
                GisWasteSymbols.symbolKeyFor(GisWasteCatalog.requireType("CONTAINER"), "IN_SERVICE"));
    }

    @Test
    void emptyLibraryMissesEverything() {
        Set<String> allActive = Set.of("COLLECTION_POINT", "CONTAINER", "DROP_OFF_POINT",
                "ECOPOINT", "LITTER_BIN", "ILLEGAL_DUMP", "COLLECTION_SECTOR",
                "TRANSFER_STATION", "SORTING_FACILITY", "COMPOSTING_SITE",
                "SANITARY_LANDFILL", "INERT_LANDFILL", "CONSTRUCTION_WASTE_FACILITY");
        assertEquals(65, GisWasteSymbols.missingSymbols(allActive, Set.of()).size());
    }

    @Test
    void fullLibraryCoversAndInactiveTypesAreIgnored() {
        Set<String> library = Set.copyOf(GisWasteSymbols.expectedKeys());
        assertTrue(GisWasteSymbols.missingSymbols(Set.of("CONTAINER"), library).isEmpty());
        // Um tipo ativo sem biblioteca: faltam so as 5 chaves dele.
        assertEquals(5, GisWasteSymbols.missingSymbols(Set.of("CONTAINER"), Set.of()).size());
        // Tipo inativo nao entra na cobranca de cobertura.
        assertTrue(GisWasteSymbols.missingSymbols(Set.of(), Set.of()).isEmpty());
    }
}
