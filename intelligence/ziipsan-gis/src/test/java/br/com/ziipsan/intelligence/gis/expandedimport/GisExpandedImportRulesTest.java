package br.com.ziipsan.intelligence.gis.expandedimport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GIS-18 (ED-55): formatos ampliados, GNSS e janela de 60 dias. */
class GisExpandedImportRulesTest {

    private static final String HASH_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    @DisplayName("DGN v7 entra direto com hash; com conversao registrada e recusado")
    void dgnV7Direct() {
        GisExpandedImportRules.ensureExpandedBatch("DGN_V7", null,
                HASH_A, null, null, 31982, "DETECTED");
        assertThrows(IllegalArgumentException.class, () ->
                GisExpandedImportRules.ensureExpandedBatch("DGN_V7", "DXF",
                        HASH_A, HASH_B, "ASSISTED_EXTERNAL", 31982, "DETECTED"));
    }

    @Test
    @DisplayName("DGN v8 so pela conversao assistida para DWG ou DXF (regra GIS-07)")
    void dgnV8AssistedOnly() {
        GisExpandedImportRules.ensureExpandedBatch("DGN_V8", "DXF",
                HASH_A, HASH_B, "ASSISTED_EXTERNAL", 31982, "DETECTED");
        assertThrows(IllegalArgumentException.class, () ->
                GisExpandedImportRules.ensureExpandedBatch("DGN_V8", null,
                        HASH_A, null, null, 31982, "DETECTED"));
    }

    @Test
    @DisplayName("INP (EPANET/SWMM) exige SRS declarado (SG-1339); leitor e da GIS-12")
    void inpRequiresDeclaredSrs() {
        GisExpandedImportRules.ensureExpandedBatch("INP_EPANET", null,
                HASH_A, null, null, 31982, "OPERATOR_DECLARED");
        GisExpandedImportRules.ensureExpandedBatch("INP_SWMM", null,
                HASH_A, null, null, 4674, "DETECTED");
        assertThrows(IllegalArgumentException.class, () ->
                GisExpandedImportRules.ensureExpandedBatch("INP_EPANET", null,
                        HASH_A, null, null, null, null));
    }

    @Test
    @DisplayName("DWG nao e da GIS-18: continua na GIS-07/P3.26")
    void dwgStaysInGis07() {
        assertThrows(IllegalArgumentException.class, () ->
                GisExpandedImportRules.ensureExpandedBatch("DWG", "DXF",
                        HASH_A, HASH_B, "ODA_FILE_CONVERTER", 31982, "DETECTED"));
    }

    @Test
    @DisplayName("ponto GNSS sem metodo ou sem precisao positiva e recusado")
    void gnssPointRequiresMetadata() {
        GisExpandedImportRules.ensureGnssPoint("RTK_FIXED", 0.02);
        assertThrows(IllegalArgumentException.class, () ->
                GisExpandedImportRules.ensureGnssPoint(null, 0.02));
        assertThrows(IllegalArgumentException.class, () ->
                GisExpandedImportRules.ensureGnssPoint("RTK_FIXED", null));
        assertThrows(IllegalArgumentException.class, () ->
                GisExpandedImportRules.ensureGnssPoint("RTK_FIXED", 0.0));
        assertThrows(IllegalArgumentException.class, () ->
                GisExpandedImportRules.ensureGnssPoint("GPS_COMUM", 1.0));
    }

    @Test
    @DisplayName("atribuicao de formatos: so INP e novo da GIS-18; DGN/GNSS ja eram da GIS-07 (correcao D8)")
    void formatAttribution() {
        assertTrue(GisExpandedImportRules.isExpandedFormat("INP_EPANET"));
        assertTrue(GisExpandedImportRules.isExpandedFormat("INP_SWMM"));
        assertFalse(GisExpandedImportRules.isExpandedFormat("DGN_V7"));
        assertFalse(GisExpandedImportRules.isExpandedFormat("CSV_GNSS"));
        assertTrue(GisExpandedImportRules.isCoveredFormat("DGN_V7"));
        assertTrue(GisExpandedImportRules.isCoveredFormat("DGN_V8"));
        assertTrue(GisExpandedImportRules.isCoveredFormat("CSV_GNSS"));
        assertFalse(GisExpandedImportRules.isCoveredFormat("DWG"));
    }

    @Test
    @DisplayName("roteamento do leitor: INP vai a GIS-12; DGN/GNSS ao worker GDAL (correcao D3)")
    void readerRouting() {
        assertEquals("GIS-12_INP_READER",
                GisExpandedImportRules.readerRoute("INP_EPANET"));
        assertEquals("GIS-12_INP_READER",
                GisExpandedImportRules.readerRoute("INP_SWMM"));
        assertEquals("GDAL_WORKER",
                GisExpandedImportRules.readerRoute("DGN_V7"));
        assertEquals("GDAL_WORKER",
                GisExpandedImportRules.readerRoute("CSV_GNSS"));
        assertThrows(IllegalArgumentException.class, () ->
                GisExpandedImportRules.readerRoute("DWG"));
    }

    @Test
    @DisplayName("amostra de rede: ate 60 dias; 61 estoura; fim antes do inicio e invalido")
    void sampleWindowIsSixtyDays() {
        LocalDate start = LocalDate.of(2027, 8, 2);
        assertTrue(GisExpandedImportRules.withinSampleWindow(
                start, start.plusDays(60)));
        assertFalse(GisExpandedImportRules.withinSampleWindow(
                start, start.plusDays(61)));
        assertFalse(GisExpandedImportRules.withinSampleWindow(
                start, start.minusDays(1)));
        assertFalse(GisExpandedImportRules.withinSampleWindow(start, null));
    }
}
