package br.com.ziipsan.core.quality;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QualityParametersTest {

    @Test void complaintDeadlineAcceptsNormValueAndBelow() {
        assertEquals(30, QualityParameters.complaintDeadlineDays(30));
        assertEquals(15, QualityParameters.complaintDeadlineDays(15));
    }

    @Test void complaintDeadlineAboveNormIsRefused() {
        // 30 dias do Decreto 5.440/2005 conforme o aceite (OPE-26): so reduz.
        assertThrows(IllegalArgumentException.class, () -> QualityParameters.complaintDeadlineDays(31));
        assertThrows(IllegalArgumentException.class, () -> QualityParameters.complaintDeadlineDays(0));
    }

    @Test void complaintDeadlineAbsentIsExplicitError() {
        MissingParameterException e = assertThrows(MissingParameterException.class,
                () -> QualityParameters.complaintDeadlineDays(null));
        assertEquals(QualityParameters.COMPLAINT_DEADLINE_DAYS, e.definitionKey());
    }

    @Test void sisaguaLayoutHasNoFactoryValue() {
        assertThrows(MissingParameterException.class, () -> QualityParameters.sisaguaLayout(null));
        assertThrows(MissingParameterException.class, () -> QualityParameters.sisaguaLayout(" "));
        assertEquals("LAYOUT-X", QualityParameters.sisaguaLayout(" LAYOUT-X "));
    }

    @Test void riskTemplateKeyHasNoFactoryValue() {
        MissingParameterException e = assertThrows(MissingParameterException.class,
                () -> QualityParameters.riskTemplateKey(null));
        assertEquals(QualityParameters.RISK_TEMPLATE_KEY, e.definitionKey());
    }
}
