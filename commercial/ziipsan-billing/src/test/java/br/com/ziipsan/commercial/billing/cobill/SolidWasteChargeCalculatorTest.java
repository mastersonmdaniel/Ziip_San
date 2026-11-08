package br.com.ziipsan.commercial.billing.cobill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ED-25: componente de residuos solidos no calculo da conta - teste por regra. */
class SolidWasteChargeCalculatorTest {

    private static final UUID UNIT = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final YearMonth COMP = YearMonth.of(2026, 4);

    private static CobillParameters params(String mode, String enabled) {
        return CobillParameters.fromValues(Map.of(
                CobillParameters.ENABLED, enabled,
                CobillParameters.BILLING_MODE, mode,
                CobillParameters.NOTICE_MIN_DAYS, "60"));
    }

    private static CobillContract contract() {
        return new CobillContract(UUID.randomUUID(), CobillContract.ServiceKind.SOLID_WASTE,
                UUID.randomUUID(), "Coleta Urbana Ltda", "Coleta de residuos solidos",
                LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2));
    }

    private static String docId() {
        return CobillBookletPlan.documentIdentifier(UNIT, COMP, 1);
    }

    @Test
    @DisplayName("COBILLED: componente discriminado soma no total da conta como parcela propria")
    void cobilledAddsDiscriminated() {
        var result = SolidWasteChargeCalculator.compose(params("COBILLED", "true"), contract(), COMP,
                new BigDecimal("100.00"), new BigDecimal("35.00"), false, false, false, docId());
        assertEquals(0, new BigDecimal("35.00").compareTo(result.cobillAmount()));
        assertEquals(0, new BigDecimal("135.00").compareTo(result.billDocumentTotal()));
        assertTrue(result.cobillLine().includedInBillTotal());
        assertEquals("Coleta de residuos solidos", result.cobillLine().label());
        assertEquals("Coleta Urbana Ltda", result.cobillLine().providerName());
        assertEquals(docId(), result.cobillLine().documentIdentifier());
    }

    @Test
    @DisplayName("fatura DESVINCULADA: valor existe mas fica FORA do total da conta de agua")
    void separateInvoiceStaysOutOfBillTotal() {
        var result = SolidWasteChargeCalculator.compose(params("SEPARATE_INVOICE", "true"), contract(), COMP,
                new BigDecimal("100.00"), new BigDecimal("35.00"), false, false, false, docId());
        assertEquals(0, new BigDecimal("35.00").compareTo(result.cobillAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(result.billDocumentTotal()));
        assertFalse(result.cobillLine().includedInBillTotal());
    }

    @Test
    @DisplayName("carne MENSAL: valor existe mas fica FORA do total da conta de agua (correcao D5)")
    void bookletMonthlyStaysOutOfBillTotal() {
        var result = SolidWasteChargeCalculator.compose(params("BOOKLET_MONTHLY", "true"), contract(), COMP,
                new BigDecimal("100.00"), new BigDecimal("35.00"), false, false, false, docId());
        assertEquals(0, new BigDecimal("35.00").compareTo(result.cobillAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(result.billDocumentTotal()));
        assertFalse(result.cobillLine().includedInBillTotal());
    }

    @Test
    @DisplayName("carne ANUAL: valor existe mas fica FORA do total da conta de agua (correcao D5)")
    void bookletAnnualStaysOutOfBillTotal() {
        var result = SolidWasteChargeCalculator.compose(params("BOOKLET_ANNUAL", "true"), contract(), COMP,
                new BigDecimal("100.00"), new BigDecimal("35.00"), false, false, false, docId());
        assertEquals(0, new BigDecimal("35.00").compareTo(result.cobillAmount()));
        assertEquals(0, new BigDecimal("100.00").compareTo(result.billDocumentTotal()));
        assertFalse(result.cobillLine().includedInBillTotal());
    }

    @Test
    @DisplayName("ISENCAO vigente: linha discriminada ZERADA e marcada, total sem o componente")
    void exemptionZeroesWithLine() {
        var result = SolidWasteChargeCalculator.compose(params("COBILLED", "true"), contract(), COMP,
                new BigDecimal("100.00"), new BigDecimal("35.00"), true, false, false, docId());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.cobillAmount()));
        assertTrue(result.cobillLine().exempt());
        assertEquals(0, new BigDecimal("100.00").compareTo(result.billDocumentTotal()));
    }

    @Test
    @DisplayName("desligado, fora de vigencia e retirado: sem linha e sem valor")
    void offOutOfTermWithdrawn() {
        assertNull(SolidWasteChargeCalculator.compose(params("COBILLED", "false"), contract(), COMP,
                new BigDecimal("100.00"), new BigDecimal("35.00"), false, false, false, docId()).cobillLine());
        assertNull(SolidWasteChargeCalculator.compose(params("COBILLED", "true"), contract(), YearMonth.of(2025, 12),
                new BigDecimal("100.00"), new BigDecimal("35.00"), false, false, false, docId()).cobillLine());
        assertNull(SolidWasteChargeCalculator.compose(params("COBILLED", "true"), contract(), COMP,
                new BigDecimal("100.00"), new BigDecimal("35.00"), false, true, false, docId()).cobillLine());
    }

    @Test
    @DisplayName("primeira cobranca sem antecedencia do aviso e RECUSADA; cobranca seguinte nao reavalia o portao")
    void firstChargeGate() {
        // Contrato com inicio valido mas aviso tardio (aviso+60 > inicio): exercita o portao.
        CobillContract avisoTardio = new CobillContract(UUID.randomUUID(), CobillContract.ServiceKind.SOLID_WASTE,
                UUID.randomUUID(), "Coleta Urbana Ltda", "Coleta de residuos solidos",
                LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 2));
        // Competencia de abril: primeira competencia cheia apos o inicio (02/03);
        // em marco a regra nem entra (competencia comeca antes do inicio do contrato).
        assertThrows(IllegalStateException.class, () -> SolidWasteChargeCalculator.compose(
                params("COBILLED", "true"), avisoTardio, YearMonth.of(2026, 4),
                new BigDecimal("100.00"), new BigDecimal("35.00"), false, false, true,
                CobillBookletPlan.documentIdentifier(UNIT, YearMonth.of(2026, 4), 1)));
        // Sem ser a primeira cobranca, o portao do aviso nao se reaplica:
        var seguinte = SolidWasteChargeCalculator.compose(
                params("COBILLED", "true"), avisoTardio, YearMonth.of(2026, 4),
                new BigDecimal("100.00"), new BigDecimal("35.00"), false, false, false,
                CobillBookletPlan.documentIdentifier(UNIT, YearMonth.of(2026, 4), 1));
        assertEquals(0, new BigDecimal("35.00").compareTo(seguinte.cobillAmount()));
    }
}
