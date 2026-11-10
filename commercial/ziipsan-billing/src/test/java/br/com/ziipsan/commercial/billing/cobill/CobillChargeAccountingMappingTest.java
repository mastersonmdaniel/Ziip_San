package br.com.ziipsan.commercial.billing.cobill;

import br.com.ziipsan.commercial.accounting.event.AccountingEventKey;
import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billingrules.composition.BillChargeLine;
import br.com.ziipsan.commercial.billingrules.servicecatalog.RevenueNature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Camada 3: de-para contabil por dono x natureza (INV-C3-07). */
class CobillChargeAccountingMappingTest {

    private static BillChargeLine line(ChargeOwner owner) {
        return new BillChargeLine("COBILL-FIXTURE", UUID.randomUUID(), owner, RevenueNature.ANCILLARY,
                CalculationModel.EXTERNAL_VALUE, new BigDecimal("35.00"), null, null);
    }

    @Test
    @DisplayName("INV-C3-07: THIRD_PARTY x ISSUED -> COBILL_ISSUED")
    void issuedDeTerceiro() {
        assertEquals(AccountingEventKey.COBILL_ISSUED, CobillChargeAccountingMapping.toAccountingEventKey(
                CobillSettlement.CobillAccountingEvent.COBILL_ISSUED, ChargeOwner.THIRD_PARTY));
    }

    @Test
    @DisplayName("INV-C3-07: THIRD_PARTY x RECEIVED -> COBILL_RECEIVED")
    void receivedDeTerceiro() {
        assertEquals(AccountingEventKey.COBILL_RECEIVED, CobillChargeAccountingMapping.toAccountingEventKey(
                CobillSettlement.CobillAccountingEvent.COBILL_RECEIVED, ChargeOwner.THIRD_PARTY));
    }

    @Test
    @DisplayName("INV-C3-07: THIRD_PARTY x TRANSFERRED -> COBILL_TRANSFERRED")
    void transferredDeTerceiro() {
        assertEquals(AccountingEventKey.COBILL_TRANSFERRED, CobillChargeAccountingMapping.toAccountingEventKey(
                CobillSettlement.CobillAccountingEvent.COBILL_TRANSFERRED, ChargeOwner.THIRD_PARTY));
    }

    @Test
    @DisplayName("INV-C3-07: dono OWN NUNCA mapeia para COBILL_* (os tres eventos recusados)")
    void proprioNuncaMapeia() {
        for (CobillSettlement.CobillAccountingEvent event : CobillSettlement.CobillAccountingEvent.values()) {
            assertThrows(IllegalArgumentException.class,
                    () -> CobillChargeAccountingMapping.toAccountingEventKey(event, ChargeOwner.OWN));
        }
    }

    @Test
    @DisplayName("INV-C3-07: forLine usa o dono da linha — terceiro mapeia, propria recusa")
    void forLineUsaODonoDaLinha() {
        assertEquals(AccountingEventKey.COBILL_RECEIVED, CobillChargeAccountingMapping.forLine(
                line(ChargeOwner.THIRD_PARTY), CobillSettlement.CobillAccountingEvent.COBILL_RECEIVED));
        assertThrows(IllegalArgumentException.class, () -> CobillChargeAccountingMapping.forLine(
                line(ChargeOwner.OWN), CobillSettlement.CobillAccountingEvent.COBILL_RECEIVED));
    }

    @Test
    @DisplayName("sentido inverso: cada COBILL_* volta ao evento local; ida e volta estaveis")
    void inversoEstavel() {
        for (CobillSettlement.CobillAccountingEvent event : CobillSettlement.CobillAccountingEvent.values()) {
            AccountingEventKey key = CobillChargeAccountingMapping.toAccountingEventKey(
                    event, ChargeOwner.THIRD_PARTY);
            assertEquals(event, CobillChargeAccountingMapping.toLocalEvent(key));
        }
    }

    @Test
    @DisplayName("sentido inverso: chave que nao e COBILL_* falha fechado (todas as 9 vigentes)")
    void inversoRecusaNaoCobill() {
        for (AccountingEventKey key : AccountingEventKey.values()) {
            if (!CobillChargeAccountingMapping.isCobillKey(key)) {
                assertThrows(IllegalArgumentException.class,
                        () -> CobillChargeAccountingMapping.toLocalEvent(key));
            }
        }
    }

    @Test
    @DisplayName("isCobillKey: so as 3 constantes novas; o enum da ED-32 tem 9 vigentes + 3 = 12")
    void isCobillKeySoAsNovas() {
        assertTrue(CobillChargeAccountingMapping.isCobillKey(AccountingEventKey.COBILL_ISSUED));
        assertTrue(CobillChargeAccountingMapping.isCobillKey(AccountingEventKey.COBILL_RECEIVED));
        assertTrue(CobillChargeAccountingMapping.isCobillKey(AccountingEventKey.COBILL_TRANSFERRED));
        assertFalse(CobillChargeAccountingMapping.isCobillKey(AccountingEventKey.BILL_ISSUED));
        assertFalse(CobillChargeAccountingMapping.isCobillKey(AccountingEventKey.PAYMENT_RECEIVED));
        assertEquals(12, AccountingEventKey.values().length);
    }

    @Test
    @DisplayName("nulos falham fechado")
    void nulosFalhamFechado() {
        assertThrows(NullPointerException.class, () -> CobillChargeAccountingMapping.toAccountingEventKey(
                null, ChargeOwner.THIRD_PARTY));
        assertThrows(NullPointerException.class, () -> CobillChargeAccountingMapping.toAccountingEventKey(
                CobillSettlement.CobillAccountingEvent.COBILL_ISSUED, null));
        assertThrows(NullPointerException.class,
                () -> CobillChargeAccountingMapping.toLocalEvent(null));
    }
}
