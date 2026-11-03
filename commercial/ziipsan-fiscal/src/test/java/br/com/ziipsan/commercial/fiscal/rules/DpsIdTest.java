package br.com.ziipsan.commercial.fiscal.rules;

import br.com.ziipsan.core.document.DocumentNumber;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Id da DPS (42 posições) e Id de evento, do documento de porte §1 e §5. */
class DpsIdTest {

    @Test
    void dpsIdHas42PositionsWithCnpjAlfanumerico() {
        String first12 = "12ABC34501DE";
        String cnpj = first12 + DocumentNumber.cnpjCheckDigits(first12);
        assertTrue(DocumentNumber.isValidCnpj(cnpj));
        String id = DpsId.ofCnpj("4205407", cnpj, "00001", "000000000000001");
        assertEquals(42, id.length());
        assertTrue(id.startsWith("4205407" + DpsId.TYPE_CNPJ + cnpj));
        assertTrue(id.endsWith("00001" + "000000000000001"));
    }

    @Test
    void dpsIdWithCpfPadsToFourteenPositions() {
        String id = DpsId.ofCpf("4205407", "11144477735", "00001", "000000000000002");
        assertEquals(42, id.length());
        assertTrue(id.startsWith("4205407" + DpsId.TYPE_CPF + "00011144477735"));
    }

    @Test
    void dpsIdRejectsWrongFieldLengths() {
        assertThrows(IllegalArgumentException.class,
                () -> DpsId.of("420540", DpsId.TYPE_CNPJ, "12ABC34501DE35", "00001", "000000000000001"));
        assertThrows(IllegalArgumentException.class,
                () -> DpsId.of("4205407", DpsId.TYPE_CNPJ, "12ABC34501DE35", "0001", "000000000000001"));
    }

    @Test
    void eventIdIsPrePlusAccessKeyPlusType() {
        String key = "1".repeat(50);
        String id = DpsId.eventId(key, "101101");
        assertEquals("PRE" + key + "101101", id);
        assertEquals(59, id.length());
    }
}
