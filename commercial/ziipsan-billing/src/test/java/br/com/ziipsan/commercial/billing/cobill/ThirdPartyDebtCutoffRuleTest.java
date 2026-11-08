package br.com.ziipsan.commercial.billing.cobill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ED-25: inadimplencia do cofaturado sem efeito de corte (cut.third_party_debt_blocks). */
class ThirdPartyDebtCutoffRuleTest {

    private static CobillParameters params(String blocks) {
        return CobillParameters.fromValues(Map.of(CobillParameters.THIRD_PARTY_DEBT_BLOCKS, blocks));
    }

    @Test
    @DisplayName("desligado: divida SO de terceiro, de qualquer valor, NAO conta para corte")
    void thirdPartyOnlyNeverBlocksWhenOff() {
        assertFalse(ThirdPartyDebtCutoffRule.thirdPartyOnlyDebtBlocks(params("false"), new BigDecimal("999.00")));
        assertEquals(0, BigDecimal.ZERO.compareTo(ThirdPartyDebtCutoffRule.eligibleDebt(
                params("false"), BigDecimal.ZERO, new BigDecimal("999.00"))));
    }

    @Test
    @DisplayName("desligado: divida mista conta SO a parte propria")
    void mixedCountsOwnOnlyWhenOff() {
        assertEquals(0, new BigDecimal("40.00").compareTo(ThirdPartyDebtCutoffRule.eligibleDebt(
                params("false"), new BigDecimal("40.00"), new BigDecimal("35.00"))));
    }

    @Test
    @DisplayName("ligado (decisao expressa do tenant): proprio + terceiro contam somados")
    void blocksWhenOn() {
        assertTrue(ThirdPartyDebtCutoffRule.thirdPartyOnlyDebtBlocks(params("true"), new BigDecimal("35.00")));
        assertEquals(0, new BigDecimal("75.00").compareTo(ThirdPartyDebtCutoffRule.eligibleDebt(
                params("true"), new BigDecimal("40.00"), new BigDecimal("35.00"))));
    }

    @Test
    @DisplayName("parametro ausente: erro explicito - nenhuma decisao de corte sobre valor presumido")
    void missingParameterIsExplicitError() {
        assertThrows(CobillParameters.MissingCobillParameterException.class, () ->
                ThirdPartyDebtCutoffRule.eligibleDebt(CobillParameters.fromValues(Map.of()),
                        BigDecimal.ZERO, new BigDecimal("35.00")));
    }
}
