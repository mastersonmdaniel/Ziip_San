package br.com.ziipsan.commercial.billing.cobill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ED-25: aviso >= 60 dias (NR 13) pelo slot da P2.32 - teste por regra. */
class CobillNoticeRuleTest {

    private static CobillParameters params(String noticeDays, String enabled) {
        return CobillParameters.fromValues(Map.of(
                CobillParameters.NOTICE_MIN_DAYS, noticeDays,
                CobillParameters.ENABLED, enabled));
    }

    private static CobillContract contract(LocalDate notice, LocalDate start) {
        return new CobillContract(UUID.randomUUID(), CobillContract.ServiceKind.SOLID_WASTE,
                UUID.randomUUID(), "Coleta Urbana Ltda", "Coleta de residuos solidos",
                LocalDate.of(2026, 1, 1), null, notice, start);
    }

    @Test
    @DisplayName("60 dias exatos cumprem; 59 dias nao cumprem (conta a mao: 01/01 -> 02/03 = 60)")
    void leadTimeBoundary() {
        assertTrue(CobillNoticeRule.leadTimeMet(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2), 60));
        assertFalse(CobillNoticeRule.leadTimeMet(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), 60));
    }

    @Test
    @DisplayName("parametro ampliado para 90 dias: 60 dias de aviso nao bastam")
    void widenedLeadTime() {
        CobillContract c = contract(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2));
        assertFalse(CobillNoticeRule.leadTimeMet(c, params("90", "true")));
        assertTrue(CobillNoticeRule.leadTimeMet(
                contract(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1)), params("90", "true")));
    }

    @Test
    @DisplayName("slot da P2.32: chave exata do catalogo e ativacao so com antecedencia + ligado")
    void legalSlot() {
        assertEquals("slot.cobilling_60d", CobillNoticeRule.LEGAL_SLOT_KEY);
        CobillContract ok = contract(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2));
        assertTrue(CobillNoticeRule.legalSlotActivates(ok, params("60", "true")));
        assertFalse(CobillNoticeRule.legalSlotActivates(ok, params("60", "false")));
        CobillContract cedo = contract(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 15));
        assertFalse(CobillNoticeRule.legalSlotActivates(cedo, params("60", "true")));
    }

    @Test
    @DisplayName("portao do inicio: primeira cobranca sem antecedencia e recusada")
    void requireLeadTime() {
        CobillContract cedo = contract(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 15));
        assertThrows(IllegalStateException.class,
                () -> CobillNoticeRule.requireLeadTime(cedo, params("60", "true")));
    }
}
