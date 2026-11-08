package br.com.ziipsan.core.sewerraw;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IntakeRegistrationServiceTest {

    private IntakeRegistrationService service(br.com.ziipsan.core.watersystems.WaterSystem sys,
                                              br.com.ziipsan.core.certification.governance.LegalRequirement req) {
        return new IntakeRegistrationService(
                (tenantId, code) -> sys != null && sys.code().equals(code) ? Optional.of(sys) : Optional.empty(),
                (tenantId, id) -> req != null && req.id().equals(id) ? Optional.of(req) : Optional.empty());
    }

    @Test void registersAgainstSupplySystemAndLegalRequirement() {
        var sys = TestSupport.supplySystem(); var req = TestSupport.legalReq();
        WaterGrant g = TestSupport.grant(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1));
        EnvironmentalLicense l = TestSupport.license(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1));
        RawWaterIntake in = TestSupport.intake(g.id(), l.id());
        assertEquals(in, service(sys, req).register(in, g, l, req.id()));
    }

    @Test void refusesSewageSystemAndMissingLegalRequirement() {
        var ses = TestSupport.sewageSystem(); var req = TestSupport.legalReq();
        WaterGrant g = TestSupport.grant(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1));
        EnvironmentalLicense l = TestSupport.license(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1));
        RawWaterIntake inSes = new RawWaterIntake(java.util.UUID.randomUUID(), TestSupport.TENANT, "SES-1", "CAPT-1",
                "Cap", "Rio", br.com.ziipsan.core.watersystems.WaterSystem.SourceKind.SURFACE, g.id(), l.id(), true);
        assertThrows(IllegalArgumentException.class, () -> service(ses, req).register(inSes, g, l, req.id()));
        RawWaterIntake in = TestSupport.intake(g.id(), l.id());
        assertThrows(IllegalArgumentException.class, () ->
                service(TestSupport.supplySystem(), null).register(in, g, l, req.id()));
    }
}
