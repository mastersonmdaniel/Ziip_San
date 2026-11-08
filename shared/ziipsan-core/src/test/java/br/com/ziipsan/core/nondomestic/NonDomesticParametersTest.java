package br.com.ziipsan.core.nondomestic;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Parametros nomeados no aceite: sem valor de fabrica; ausencia e erro nomeado. */
class NonDomesticParametersTest {

    @Test
    void userStandardCatalogVersionIsRequired() {
        assertEquals("PRESTADOR-1", NonDomesticParameters.userStandardCatalogVersion(" PRESTADOR-1 "));
        assertThrows(MissingParameterException.class, () -> NonDomesticParameters.userStandardCatalogVersion(null));
        assertThrows(MissingParameterException.class, () -> NonDomesticParameters.userStandardCatalogVersion(" "));
    }

    @Test
    void samplingIntervalHasNoFactoryValue() {
        assertEquals(30, NonDomesticParameters.samplingIntervalDays(30));
        assertThrows(MissingParameterException.class, () -> NonDomesticParameters.samplingIntervalDays(null));
        assertThrows(IllegalArgumentException.class, () -> NonDomesticParameters.samplingIntervalDays(0));
        assertThrows(IllegalArgumentException.class, () -> NonDomesticParameters.samplingIntervalDays(-5));
    }

    @Test
    void chargeModelParsesTheThreeClassDModelsOnly() {
        assertEquals(NonDomesticChargeModel.VOLUME_PERCENT, NonDomesticParameters.chargeModel("VOLUME_PERCENT"));
        assertEquals(NonDomesticChargeModel.POLLUTANT_LOAD, NonDomesticParameters.chargeModel("POLLUTANT_LOAD"));
        assertEquals(NonDomesticChargeModel.FIXED_BY_CLASS, NonDomesticParameters.chargeModel("FIXED_BY_CLASS"));
        assertThrows(MissingParameterException.class, () -> NonDomesticParameters.chargeModel(null));
        assertThrows(IllegalArgumentException.class, () -> NonDomesticParameters.chargeModel("FLAT"));
    }
}
