package br.com.ziipsan.core.sac.kiosk;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KioskParametersTest {

    @Test void factoryValuesAreTheScLawValues() {
        assertEquals(15, KioskParameters.FACTORY_WAIT_LIMIT_BUSINESS_DAY_MINUTES);
        assertEquals(30, KioskParameters.FACTORY_WAIT_LIMIT_HOLIDAY_ADJACENT_MINUTES);
    }

    @Test void limitsComeFromTheParameterNotFromTheCode() {
        assertEquals(15, KioskParameters.waitLimitBusinessDayMinutes(15));
        assertEquals(30, KioskParameters.waitLimitHolidayAdjacentMinutes(30));
        // tenant com norma propria troca o VALOR do parametro
        assertEquals(20, KioskParameters.waitLimitBusinessDayMinutes(20));
        assertEquals(45, KioskParameters.waitLimitHolidayAdjacentMinutes(45));
    }

    @Test void missingParameterIsExplicitError() {
        assertThrows(MissingParameterException.class, () -> KioskParameters.waitLimitBusinessDayMinutes(null));
        assertThrows(MissingParameterException.class, () -> KioskParameters.waitLimitHolidayAdjacentMinutes(null));
        assertThrows(MissingParameterException.class, () -> KioskParameters.kioskEnabled(null));
    }

    @Test void nonPositiveLimitRefused() {
        assertThrows(IllegalArgumentException.class, () -> KioskParameters.waitLimitBusinessDayMinutes(0));
        assertThrows(IllegalArgumentException.class, () -> KioskParameters.waitLimitHolidayAdjacentMinutes(-5));
    }

    @Test void kioskEnabledPassesThrough() {
        assertTrue(KioskParameters.kioskEnabled(true));
        assertFalse(KioskParameters.kioskEnabled(false));
    }
}
