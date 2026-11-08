package br.com.ziipsan.intelligence.gis.asset;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import br.com.ziipsan.intelligence.gis.asset.AssetInventoryModel.ControlMode;

import static org.junit.jupiter.api.Assertions.*;

class McpsbCodesTest {

    private McpsbCodes codes(String cib, ControlMode mode) {
        return new McpsbCodes(cib, "4205407", "123456", null, "12", "1234567",
                List.of("00", "01"), mode, "m", "REDE_DISTRIBUICAO", 1250.0);
    }

    @Test
    void validCodesAreAccepted() {
        var c = codes("1234567890123456789012345", ControlMode.INDIVIDUAL);
        assertEquals(25, c.cib().length());
        assertEquals(1250.0, c.extensionMeters());
    }

    @Test
    void cibOnlyForIndividualControl() {
        assertThrows(IllegalArgumentException.class,
                () -> codes("1234567890123456789012345", ControlMode.MASS));
        assertThrows(IllegalArgumentException.class,
                () -> codes("123", ControlMode.INDIVIDUAL));
    }

    @Test
    void fixedLengthFieldsAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> new McpsbCodes(
                null, "123", null, null, null, null, List.of(), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new McpsbCodes(
                null, null, null, null, null, "123", List.of(), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new McpsbCodes(
                null, null, null, null, "1234", null, List.of(), null, null, null, null));
        // up com 3 digitos aceito (tamanho a conferir no manual — pendencia declarada)
        assertEquals("123", new McpsbCodes(null, null, null, null, "123", null,
                List.of(), null, null, null, null).up());
    }

    @Test
    void extensionRequiresSubdivision() {
        assertThrows(IllegalArgumentException.class, () -> new McpsbCodes(
                null, null, null, null, null, null, List.of(), null, null, null, 10.0));
    }

    @Test
    void ntsSdsWithoutOfficialListIsSemFonte() {
        var c = codes(null, ControlMode.MASS);
        var withCode = new McpsbCodes(null, null, null, "ABC", null, null,
                List.of(), null, null, null, null);
        assertFalse(withCode.ntsSdsInOfficialList(List.of()));
        assertTrue(withCode.ntsSdsCheckNote(List.of()).startsWith("[SEM FONTE]"));
        assertTrue(withCode.ntsSdsInOfficialList(List.of("ABC", "DEF")));
        assertEquals("OK", withCode.ntsSdsCheckNote(List.of("ABC")));
    }

    @Test
    void usefulLifeComesOnlyFromAgencyProfile() {
        var profile = new McpsbCodes.UsefulLifeProfile("AGENCIA-SINTETICA",
                Map.of("12/1234567", 25));
        var c = codes(null, ControlMode.MASS);
        assertEquals(25, c.usefulLifeFrom(profile).orElseThrow());
        var other = new McpsbCodes(null, null, null, null, "99", "7654321",
                List.of(), null, null, null, null);
        assertTrue(other.usefulLifeFrom(profile).isEmpty());
        assertTrue(c.usefulLifeFrom(new McpsbCodes.UsefulLifeProfile("VAZIO", Map.of())).isEmpty());
    }
}
