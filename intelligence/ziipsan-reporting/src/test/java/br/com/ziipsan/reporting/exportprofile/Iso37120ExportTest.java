package br.com.ziipsan.reporting.exportprofile;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Iso37120ExportTest {

    private final ExportProfileService service = new ExportProfileService();
    private final Instant now = Instant.parse("2026-10-22T00:00:00Z");

    @Test void exportsWithDeclaredMappingAndSeries() {
        ExportDocument doc = service.exportIso37120(ExportProfileFixtures.TENANT,
                List.of(ExportProfileFixtures.series("COBERTURA.IAA", "2024", "2025")),
                List.of(new Iso37120Mapping("COBERTURA.IAA", "[referencia declarada pelo integrador]", "integracao")),
                now);
        assertEquals(ExportProfileKind.ISO_37120, doc.kind());
        assertEquals("ABNT NBR ISO 37120:2021", doc.normativeReference());
        assertEquals(1, doc.lines().size());
        assertEquals("[referencia declarada pelo integrador]", doc.lines().get(0).externalReference());
        assertEquals(2, doc.lines().get(0).points().size());
    }

    @Test void refusesIndicatorWithoutDeclaredMapping() {
        ExportValidationException ex = assertThrows(ExportValidationException.class, () ->
                service.exportIso37120(ExportProfileFixtures.TENANT,
                        List.of(ExportProfileFixtures.series("COBERTURA.IAA", "2025")), List.of(), now));
        assertEquals("EXPORT_ISO_MAPPING_REQUIRED", ex.code());
    }

    @Test void refusesBlankExternalReference() {
        ExportValidationException ex = assertThrows(ExportValidationException.class, () ->
                new Iso37120Mapping("COBERTURA.IAA", "  ", "integracao"));
        assertEquals("EXPORT_MAPPING_REFERENCE_REQUIRED", ex.code());
    }

    // Correcao D3 (sonda J2 do revisor): duplicado recusa com ExportValidationException
    // nomeada, nao com IllegalStateException crua de Collectors.toMap.
    @Test void refusesDuplicatedMappingWithNamedCode() {
        ExportValidationException ex = assertThrows(ExportValidationException.class, () ->
                service.exportIso37120(ExportProfileFixtures.TENANT,
                        List.of(ExportProfileFixtures.series("COBERTURA.IAA", "2025")),
                        List.of(new Iso37120Mapping("COBERTURA.IAA",
                                        "[referencia declarada pelo integrador]", "integracao"),
                                new Iso37120Mapping("COBERTURA.IAA",
                                        "[outra referencia declarada pelo integrador]", "integracao")),
                        now));
        assertEquals("EXPORT_ISO_MAPPING_DUPLICATED", ex.code());
    }

    @Test void refusesEmptySeriesList() {
        ExportValidationException ex = assertThrows(ExportValidationException.class, () ->
                service.exportIso37120(ExportProfileFixtures.TENANT, List.of(), List.of(), now));
        assertEquals("EXPORT_SERIES_REQUIRED", ex.code());
    }
}
