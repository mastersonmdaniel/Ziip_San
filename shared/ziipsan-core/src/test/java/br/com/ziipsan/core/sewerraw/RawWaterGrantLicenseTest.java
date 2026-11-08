package br.com.ziipsan.core.sewerraw;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RawWaterGrantLicenseTest {

    @Test void grantRequiresValidityAndDeclaredBasis() {
        assertThrows(IllegalArgumentException.class, () -> new WaterGrant(UUID.randomUUID(), TestSupport.TENANT,
                "N1", "ORG", "Lei 9.433/1997", WaterGrant.WaterDomain.STATE,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new WaterGrant(UUID.randomUUID(), TestSupport.TENANT,
                " ", "ORG", "Lei 9.433/1997", WaterGrant.WaterDomain.STATE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), null, null, List.of()));
    }

    @Test void grantFlowNeedsValueAndUnitTogether() {
        assertThrows(IllegalArgumentException.class, () -> new WaterGrant(UUID.randomUUID(), TestSupport.TENANT,
                "N1", "ORG", "Lei 9.433/1997", WaterGrant.WaterDomain.STATE,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), new BigDecimal("10"), null, List.of()));
    }

    @Test void unionGrantDeclaresAnaReference() {
        WaterGrant g = TestSupport.grant(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1));
        assertTrue(g.federalReferenceDeclared());
        assertTrue(g.validOn(LocalDate.of(2028, 5, 5)));
        assertFalse(g.validOn(LocalDate.of(2031, 1, 1)));
    }

    @Test void licenseRequiresValidityWindow() {
        assertThrows(IllegalArgumentException.class, () -> new EnvironmentalLicense(UUID.randomUUID(), TestSupport.TENANT,
                "LO-1", "LO", "ORG", LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1), List.of()));
        EnvironmentalLicense l = TestSupport.license(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1));
        assertTrue(l.validOn(LocalDate.of(2027, 6, 1)));
    }

    @Test void intakeRequiresGrantAndLicenseAndSystem() {
        assertThrows(NullPointerException.class, () -> new RawWaterIntake(UUID.randomUUID(), TestSupport.TENANT,
                "SAA-1", "C1", "Cap", "Rio", br.com.ziipsan.core.watersystems.WaterSystem.SourceKind.SURFACE,
                null, UUID.randomUUID(), true));
        assertThrows(IllegalArgumentException.class, () -> new RawWaterIntake(UUID.randomUUID(), TestSupport.TENANT,
                "SAA-1", "C1", "Cap", " ", br.com.ziipsan.core.watersystems.WaterSystem.SourceKind.SURFACE,
                UUID.randomUUID(), UUID.randomUUID(), true));
    }
}
