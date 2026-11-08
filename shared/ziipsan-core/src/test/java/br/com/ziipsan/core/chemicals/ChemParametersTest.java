package br.com.ziipsan.core.chemicals;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChemParametersTest {

    @Test void namedKeysMatchAcceptance() {
        assertEquals("chem.min_stock_days", ChemParameters.MIN_STOCK_DAYS);
        assertEquals("chem.controlled_products_list", ChemParameters.CONTROLLED_PRODUCTS_LIST);
        assertEquals("ops.reservoir_cleaning_interval_months", ChemParameters.RESERVOIR_CLEANING_INTERVAL_MONTHS);
    }

    @Test void missingMinStockDaysRefusesByName() {
        var e = assertThrows(MissingParameterException.class, () -> ChemParameters.requireMinStockDays(Optional.empty()));
        assertEquals(ChemParameters.MIN_STOCK_DAYS, e.definitionKey());
    }

    @Test void missingReservoirIntervalRefusesByName() {
        var e = assertThrows(MissingParameterException.class, () -> ChemParameters.requireReservoirIntervalMonths(Optional.empty()));
        assertEquals(ChemParameters.RESERVOIR_CLEANING_INTERVAL_MONTHS, e.definitionKey());
    }

    @Test void nonPositiveValuesRefused() {
        assertThrows(IllegalArgumentException.class, () -> ChemParameters.requireMinStockDays(Optional.of(0)));
        assertThrows(IllegalArgumentException.class, () -> ChemParameters.requireReservoirIntervalMonths(Optional.of(-2)));
    }

    @Test void pfEnableRequiresExplicitKey() {
        // ausencia nao liga nada por presuncao
        assertThrows(MissingParameterException.class, () -> ChemParameters.requirePfControlEnabled(Optional.empty()));
        assertFalse(ChemParameters.requirePfControlEnabled(Optional.of(false)));
        assertTrue(ChemParameters.requirePfControlEnabled(Optional.of(true)));
    }

    @Test void controlledListRefBlankRefuses() {
        assertThrows(MissingParameterException.class, () -> ChemParameters.requireControlledListRef(Optional.of("  ")));
        assertEquals("2026-01", ChemParameters.requireControlledListRef(Optional.of(" 2026-01 ")));
    }
}
