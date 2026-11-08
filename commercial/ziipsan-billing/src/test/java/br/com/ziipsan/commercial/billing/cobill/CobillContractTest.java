package br.com.ziipsan.commercial.billing.cobill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ED-25: contrato de cofaturamento por servico - validacoes de registro. */
class CobillContractTest {

    @Test
    @DisplayName("contrato valido cobre a vigencia; sem rotulo/prestador/aviso e recusado")
    void validations() {
        var c = new CobillContract(UUID.randomUUID(), CobillContract.ServiceKind.SOLID_WASTE,
                UUID.randomUUID(), "Coleta Urbana Ltda", "Coleta de residuos solidos",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2));
        assertTrue(c.covers(LocalDate.of(2026, 6, 1)));
        assertFalse(c.covers(LocalDate.of(2027, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new CobillContract(UUID.randomUUID(),
                CobillContract.ServiceKind.SOLID_WASTE, UUID.randomUUID(), "Coleta Urbana Ltda", " ",
                LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2)));
        assertThrows(NullPointerException.class, () -> new CobillContract(UUID.randomUUID(),
                CobillContract.ServiceKind.SOLID_WASTE, UUID.randomUUID(), "Coleta Urbana Ltda", "Coleta",
                LocalDate.of(2026, 1, 1), null, null, LocalDate.of(2026, 3, 2)));
        assertThrows(IllegalArgumentException.class, () -> new CobillContract(UUID.randomUUID(),
                CobillContract.ServiceKind.SOLID_WASTE, UUID.randomUUID(), "Coleta Urbana Ltda", "Coleta",
                LocalDate.of(2026, 6, 1), null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2)));
    }
}
