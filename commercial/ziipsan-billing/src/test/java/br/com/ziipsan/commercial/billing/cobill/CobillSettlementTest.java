package br.com.ziipsan.commercial.billing.cobill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** ED-25: repasse e segregacao contabil pela regra por evento (ED-32) - teste por regra. */
class CobillSettlementTest {

    private static CobillParameters params(String allocation) {
        return CobillParameters.fromValues(Map.of(CobillParameters.PAYMENT_ALLOCATION, allocation));
    }

    @Test
    @DisplayName("OWN_FIRST: pagamento parcial quita o proprio antes; repasse so do que sobrou ao terceiro")
    void ownFirst() {
        var a = CobillSettlement.allocate(params("OWN_FIRST"),
                new BigDecimal("100.00"), new BigDecimal("35.00"), new BigDecimal("120.00"));
        assertEquals(0, new BigDecimal("100.00").compareTo(a.ownSettled()));
        assertEquals(0, new BigDecimal("20.00").compareTo(a.cobillSettled()));
        assertEquals(0, new BigDecimal("20.00").compareTo(a.transferDue()));
        assertEquals(0, new BigDecimal("15.00").compareTo(a.cobillOutstanding()));
    }

    @Test
    @DisplayName("PRO_RATA: rateio proporcional (135 devido, 67.50 pago -> 50.00 proprio e 17.50 terceiro)")
    void proRata() {
        var a = CobillSettlement.allocate(params("PRO_RATA"),
                new BigDecimal("100.00"), new BigDecimal("35.00"), new BigDecimal("67.50"));
        assertEquals(0, new BigDecimal("50.00").compareTo(a.ownSettled()));
        assertEquals(0, new BigDecimal("17.50").compareTo(a.cobillSettled()));
    }

    @Test
    @DisplayName("pagamento maior que o devido e recusado (excedente e credito da P2.5, nao repasse)")
    void overpaymentRefused() {
        assertThrows(IllegalArgumentException.class, () -> CobillSettlement.allocate(params("OWN_FIRST"),
                new BigDecimal("100.00"), new BigDecimal("35.00"), new BigDecimal("200.00")));
    }

    @Test
    @DisplayName("fato de repasse: evento COBILL_TRANSFERRED, mes contabil dia 1, valor = recebido do terceiro")
    void transferFact() {
        var a = CobillSettlement.allocate(params("OWN_FIRST"),
                new BigDecimal("100.00"), new BigDecimal("35.00"), new BigDecimal("135.00"));
        UUID contractId = UUID.randomUUID();
        UUID municipalityId = UUID.randomUUID();
        var fact = CobillSettlement.transferFact(a, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15),
                CobillSettlement.Sphere.PUBLICO, CobillSettlement.Scope.REGULATORY,
                "COBILL-123e4567-e89b-12d3-a456-426614174000-202604-001", contractId, municipalityId);
        assertEquals(CobillSettlement.CobillAccountingEvent.COBILL_TRANSFERRED, fact.event());
        assertEquals(0, new BigDecimal("35.00").compareTo(fact.amount()));
        assertThrows(IllegalArgumentException.class, () -> new CobillSettlement.CobillFact(
                CobillSettlement.CobillAccountingEvent.COBILL_ISSUED, new BigDecimal("35.00"),
                LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 15),
                CobillSettlement.Sphere.PUBLICO, CobillSettlement.Scope.REGULATORY,
                contractId, municipalityId, "COBILL-x"));
        var zero = CobillSettlement.allocate(params("OWN_FIRST"),
                new BigDecimal("100.00"), new BigDecimal("35.00"), new BigDecimal("50.00"));
        assertThrows(IllegalArgumentException.class, () -> CobillSettlement.transferFact(
                zero, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15),
                CobillSettlement.Sphere.PUBLICO, CobillSettlement.Scope.REGULATORY,
                "COBILL-x", contractId, municipalityId));
    }

    @Test
    @DisplayName("fato no desenho INTEGRAL do Fact da ED-32 (correcao D3): data do fato, esfera, escopo e municipio presentes e obrigatorios")
    void factFollowsEd32Shape() {
        UUID contractId = UUID.randomUUID();
        UUID municipalityId = UUID.randomUUID();
        var fact = new CobillSettlement.CobillFact(
                CobillSettlement.CobillAccountingEvent.COBILL_ISSUED, new BigDecimal("35.00"),
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3),
                CobillSettlement.Sphere.PARTICULAR, CobillSettlement.Scope.MANAGERIAL,
                contractId, municipalityId, "COBILL-x");
        assertEquals(LocalDate.of(2026, 4, 3), fact.factDate());
        assertEquals(CobillSettlement.Sphere.PARTICULAR, fact.sphere());
        assertEquals(CobillSettlement.Scope.MANAGERIAL, fact.scope());
        assertEquals(municipalityId, fact.municipalityId());
        assertEquals(contractId, fact.contractId());
        // Os campos que a ED-32 introduziu sao obrigatorios, como no fato real:
        assertThrows(NullPointerException.class, () -> new CobillSettlement.CobillFact(
                CobillSettlement.CobillAccountingEvent.COBILL_ISSUED, new BigDecimal("35.00"),
                LocalDate.of(2026, 4, 1), null,
                CobillSettlement.Sphere.PARTICULAR, CobillSettlement.Scope.MANAGERIAL,
                contractId, municipalityId, "COBILL-x"));
        assertThrows(NullPointerException.class, () -> new CobillSettlement.CobillFact(
                CobillSettlement.CobillAccountingEvent.COBILL_ISSUED, new BigDecimal("35.00"),
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3),
                null, CobillSettlement.Scope.MANAGERIAL,
                contractId, municipalityId, "COBILL-x"));
        assertThrows(NullPointerException.class, () -> new CobillSettlement.CobillFact(
                CobillSettlement.CobillAccountingEvent.COBILL_ISSUED, new BigDecimal("35.00"),
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3),
                CobillSettlement.Sphere.PARTICULAR, null,
                contractId, municipalityId, "COBILL-x"));
    }
}
