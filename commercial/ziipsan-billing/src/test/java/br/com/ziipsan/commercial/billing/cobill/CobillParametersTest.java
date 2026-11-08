package br.com.ziipsan.commercial.billing.cobill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ED-25: um teste por regra de cada parametro cobill.* / cut.third_party_debt_blocks. */
class CobillParametersTest {

    private static CobillParameters params(String key, String value) {
        return CobillParameters.fromValues(Map.of(key, value));
    }

    @Test
    @DisplayName("cobill.enabled: ligado e desligado; ausente e erro explicito")
    void enabled() {
        assertTrue(params(CobillParameters.ENABLED, "true").enabled());
        assertFalse(params(CobillParameters.ENABLED, "false").enabled());
        assertThrows(CobillParameters.MissingCobillParameterException.class,
                () -> CobillParameters.fromValues(Map.of()).enabled());
    }

    @Test
    @DisplayName("cobill.notice_min_days: 60 passa, 90 amplia, 59 e recusado (piso travado), ausente e erro")
    void noticeMinDays() {
        assertEquals(60, params(CobillParameters.NOTICE_MIN_DAYS, "60").noticeMinDays());
        assertEquals(90, params(CobillParameters.NOTICE_MIN_DAYS, "90").noticeMinDays());
        assertThrows(IllegalArgumentException.class,
                () -> params(CobillParameters.NOTICE_MIN_DAYS, "59").noticeMinDays());
        assertThrows(CobillParameters.MissingCobillParameterException.class,
                () -> CobillParameters.fromValues(Map.of()).noticeMinDays());
    }

    @Test
    @DisplayName("cobill.billing_mode: os quatro modos; fora do catalogo e recusado")
    void billingMode() {
        assertEquals(CobillParameters.BillingMode.COBILLED,
                params(CobillParameters.BILLING_MODE, "COBILLED").billingMode());
        assertEquals(CobillParameters.BillingMode.SEPARATE_INVOICE,
                params(CobillParameters.BILLING_MODE, "SEPARATE_INVOICE").billingMode());
        assertEquals(CobillParameters.BillingMode.BOOKLET_MONTHLY,
                params(CobillParameters.BILLING_MODE, "BOOKLET_MONTHLY").billingMode());
        assertEquals(CobillParameters.BillingMode.BOOKLET_ANNUAL,
                params(CobillParameters.BILLING_MODE, "BOOKLET_ANNUAL").billingMode());
        assertThrows(IllegalArgumentException.class,
                () -> params(CobillParameters.BILLING_MODE, "BOLETO").billingMode());
    }

    @Test
    @DisplayName("cobill.payment_allocation: OWN_FIRST e PRO_RATA")
    void paymentAllocation() {
        assertEquals(CobillParameters.PaymentAllocation.OWN_FIRST,
                params(CobillParameters.PAYMENT_ALLOCATION, "OWN_FIRST").paymentAllocation());
        assertEquals(CobillParameters.PaymentAllocation.PRO_RATA,
                params(CobillParameters.PAYMENT_ALLOCATION, "PRO_RATA").paymentAllocation());
    }

    @Test
    @DisplayName("cut.third_party_debt_blocks: le os dois valores; ausente e erro explicito (sem padrao presumido)")
    void thirdPartyDebtBlocks() {
        assertTrue(params(CobillParameters.THIRD_PARTY_DEBT_BLOCKS, "true").thirdPartyDebtBlocks());
        assertFalse(params(CobillParameters.THIRD_PARTY_DEBT_BLOCKS, "false").thirdPartyDebtBlocks());
        assertThrows(CobillParameters.MissingCobillParameterException.class,
                () -> CobillParameters.fromValues(Map.of()).thirdPartyDebtBlocks());
    }
}
