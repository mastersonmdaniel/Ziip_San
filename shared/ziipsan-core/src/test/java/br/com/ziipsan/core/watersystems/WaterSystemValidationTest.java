package br.com.ziipsan.core.watersystems;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WaterSystemValidationTest {

    @Test void supplyRequiresSource() {
        assertThrows(IllegalArgumentException.class, () -> new WaterSystem(UUID.randomUUID(),
                SystemsTestSupport.TENANT, "SAA-X", "X", WaterSystem.Kind.SUPPLY, 100,
                LocalDate.of(2026, 1, 1), null,
                new WaterSystem.TreatmentInfo(List.of("ETA-01"), List.of(), null), true));
    }

    @Test void sewageRejectsSource() {
        assertThrows(IllegalArgumentException.class, () -> new WaterSystem(UUID.randomUUID(),
                SystemsTestSupport.TENANT, "SES-X", "X", WaterSystem.Kind.SEWAGE, 100,
                LocalDate.of(2026, 1, 1),
                new WaterSystem.SourceInfo(WaterSystem.SourceKind.SURFACE, "Manancial", null),
                new WaterSystem.TreatmentInfo(List.of("ETE-01"), List.of(), null), true));
    }

    @Test void populationRequiresReferenceDate() {
        assertThrows(NullPointerException.class, () -> new WaterSystem(UUID.randomUUID(),
                SystemsTestSupport.TENANT, "SAA-X", "X", WaterSystem.Kind.SUPPLY, 100, null,
                new WaterSystem.SourceInfo(WaterSystem.SourceKind.GROUNDWATER, "Poco Ficticio", null),
                new WaterSystem.TreatmentInfo(List.of(), List.of("TYPE_A"), null), true));
    }

    @Test void negativePopulationRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WaterSystem(UUID.randomUUID(),
                SystemsTestSupport.TENANT, "SAA-X", "X", WaterSystem.Kind.SUPPLY, -1,
                LocalDate.of(2026, 1, 1),
                new WaterSystem.SourceInfo(WaterSystem.SourceKind.MIXED, "Misto Ficticio", null),
                new WaterSystem.TreatmentInfo(List.of("ETA-01"), List.of(), null), true));
    }

    @Test void emptyTreatmentRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new WaterSystem.TreatmentInfo(List.of(), List.of(), "  "));
        assertThrows(IllegalArgumentException.class,
                () -> new WaterSystem.TreatmentInfo(null, null, null));
    }

    @Test void treatmentByCatalogTypeOnlyIsValid() {
        WaterSystem.TreatmentInfo t = new WaterSystem.TreatmentInfo(List.of(), List.of("TYPE_ETA"), null);
        assertEquals(List.of("TYPE_ETA"), t.catalogTypeKeys());
    }

    @Test void sourceRequiresName() {
        assertThrows(IllegalArgumentException.class,
                () -> new WaterSystem.SourceInfo(WaterSystem.SourceKind.SURFACE, " ", null));
    }

    @Test void validSystemsBuild() {
        assertEquals(WaterSystem.Kind.SUPPLY, SystemsTestSupport.supplySystem("SAA-01").kind());
        assertNull(SystemsTestSupport.sewageSystem("SES-01").source());
        assertEquals(12_000, SystemsTestSupport.supplySystem("SAA-01").servedPopulation());
    }
}
