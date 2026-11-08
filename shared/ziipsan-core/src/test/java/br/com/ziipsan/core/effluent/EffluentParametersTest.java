package br.com.ziipsan.core.effluent;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffluentParametersTest {

    @Test
    void destinationCatalogIsRequiredAndNonEmpty() {
        assertThrows(MissingParameterException.class, () -> EffluentParameters.sludgeDestinationCatalog(null));
        assertThrows(MissingParameterException.class, () -> EffluentParameters.sludgeDestinationCatalog(List.of()));
        assertEquals(List.of("DEST-A"), EffluentParameters.sludgeDestinationCatalog(List.of("DEST-A")));
    }

    @Test
    void exemptOriginsAbsenceIsErrorButEmptyListIsDeclaration() {
        assertThrows(MissingParameterException.class, () -> EffluentParameters.mtrExemptOrigins(null));
        assertEquals(List.of(), EffluentParameters.mtrExemptOrigins(List.of()));
        assertFalse(EffluentParameters.mtrExempt(EffluentParameters.mtrExemptOrigins(List.of()), "ORIGEM-A"));
        assertTrue(EffluentParameters.mtrExempt(
                EffluentParameters.mtrExemptOrigins(List.of("ORIGEM-A")), "ORIGEM-A"));
    }

    @Test
    void duplicateOrBlankCodesAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> EffluentParameters.sludgeDestinationCatalog(List.of("DEST-A", "DEST-A")));
        assertThrows(IllegalArgumentException.class,
                () -> EffluentParameters.mtrExemptOrigins(List.of(" ")));
    }

    @Test
    void destinationAllowedChecksTheValidatedCatalog() {
        var catalog = EffluentParameters.sludgeDestinationCatalog(List.of("DEST-A"));
        assertTrue(EffluentParameters.destinationAllowed(catalog, "DEST-A"));
        assertFalse(EffluentParameters.destinationAllowed(catalog, "DEST-B"));
    }
}
