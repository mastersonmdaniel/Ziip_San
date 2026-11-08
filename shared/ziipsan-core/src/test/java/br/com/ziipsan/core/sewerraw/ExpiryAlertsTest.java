package br.com.ziipsan.core.sewerraw;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpiryAlertsTest {

    @Test void alertStatusesFollowDeclaredWindow() {
        WaterGrant g = TestSupport.grant(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(ExpiryAlerts.ValidityStatus.VALID,
                ExpiryAlerts.alertForGrant(g, List.of(), LocalDate.of(2026, 3, 1), 30).status());
        assertEquals(ExpiryAlerts.ValidityStatus.EXPIRING_SOON,
                ExpiryAlerts.alertForGrant(g, List.of(), LocalDate.of(2026, 12, 15), 30).status());
        assertEquals(ExpiryAlerts.ValidityStatus.EXPIRED,
                ExpiryAlerts.alertForGrant(g, List.of(), LocalDate.of(2027, 1, 2), 30).status());
        assertEquals(ExpiryAlerts.ValidityStatus.NOT_YET_VALID,
                ExpiryAlerts.alertForGrant(g, List.of(), LocalDate.of(2025, 12, 1), 30).status());
    }

    @Test void unfulfilledConditionsArePendingNeverPresumed() {
        WaterGrant g = TestSupport.grant(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1));
        var alert = ExpiryAlerts.alertForGrant(g,
                List.of(new ExpiryAlerts.ConditionFact("COND-A", true)), LocalDate.of(2026, 6, 1), 30);
        assertEquals(1, alert.pendingConditions());
        assertThrows(IllegalArgumentException.class, () -> ExpiryAlerts.alertForGrant(g,
                List.of(new ExpiryAlerts.ConditionFact("COND-INEXISTENTE", true)), LocalDate.of(2026, 6, 1), 30));
    }

    @Test void operabilityBlocksExpiredLicenseAndPendingConditions() {
        WaterGrant g = TestSupport.grant(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1));
        EnvironmentalLicense l = TestSupport.license(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        RawWaterIntake in = TestSupport.intake(g.id(), l.id());
        var op = ExpiryAlerts.operability(in, g, l, List.of(), List.of(), LocalDate.of(2026, 8, 1), 30);
        assertFalse(op.operable());
        assertTrue(op.blockingReasons().stream().anyMatch(r -> r.contains("licenca vencida")));
        assertTrue(op.blockingReasons().stream().anyMatch(r -> r.contains("condicionante")));
    }

    @Test void operabilityOkWhenAllValidAndFulfilled() {
        WaterGrant g = TestSupport.grant(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1));
        EnvironmentalLicense l = TestSupport.license(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1));
        RawWaterIntake in = TestSupport.intake(g.id(), l.id());
        var op = ExpiryAlerts.operability(in, g, l,
                List.of(new ExpiryAlerts.ConditionFact("COND-A", true), new ExpiryAlerts.ConditionFact("COND-B", true)),
                List.of(new ExpiryAlerts.ConditionFact("COND-L1", true)),
                LocalDate.of(2026, 6, 1), 30);
        assertTrue(op.operable());
        assertTrue(op.blockingReasons().isEmpty());
    }

    @Test void missingAlertWindowParameterIsExplicitError() {
        WaterGrant g = TestSupport.grant(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1));
        EnvironmentalLicense l = TestSupport.license(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1));
        RawWaterIntake in = TestSupport.intake(g.id(), l.id());
        assertThrows(MissingParameterException.class, () ->
                ExpiryAlerts.operability(in, g, l, List.of(), List.of(), LocalDate.of(2026, 6, 1), null));
        assertThrows(IllegalArgumentException.class, () -> SewerRawParameters.expiryAlertWindowDays(0));
        assertEquals(45, SewerRawParameters.expiryAlertWindowDays(45));
    }
}
