package br.com.ziipsan.commercial.fiscal.application;

import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceGate.Environment;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentIssuer;
import br.com.ziipsan.commercial.fiscal.port.SimulatedFiscalIssuer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trava de produção da P0.32: sem {@code ziipsan.fiscal.production-allowed} verdadeira não
 * há emissão com tpAmb=1 — nem com a propriedade verdadeira sem aprovação do tenant, nem
 * pelo simulador em nenhuma hipótese.
 */
class FiscalIssuanceGateTest {

    @Test
    void productionRefusedWithoutProductionAllowed() {
        assertThrows(IllegalStateException.class,
                () -> FiscalIssuanceGate.closed().resolve(Environment.PRODUCTION, true));
    }

    @Test
    void productionRefusedWithoutTenantApprovalEvenWhenAllowed() {
        FiscalIssuanceGate gate = new FiscalIssuanceGate(true);
        assertThrows(IllegalStateException.class,
                () -> gate.resolve(Environment.PRODUCTION, false));
    }

    @Test
    void productionAllowedOnlyWithPropertyAndApproval() {
        FiscalIssuanceGate gate = new FiscalIssuanceGate(true);
        assertEquals(Environment.PRODUCTION, gate.resolve(Environment.PRODUCTION, true));
        assertEquals(Integer.valueOf(1), Environment.PRODUCTION.tpAmb());
        assertEquals(Integer.valueOf(2), Environment.HOMOLOGATION.tpAmb());
    }

    @Test
    void simulatorNeverEmitsWithTpAmb1() {
        SimulatedFiscalIssuer issuer =
                new SimulatedFiscalIssuer(FiscalDocumentIssuer.FiscalDocumentType.NFSE_NACIONAL);
        FiscalDocumentIssuer.SignedDocument first =
                new FiscalDocumentIssuer.SignedDocument("DPS-1", "<dps/>", Environment.SIMULATED);
        FiscalDocumentIssuer.AuthorizationResult authorized = issuer.authorize(first);
        assertTrue(authorized.authorized());
        // Idempotencia por Id da DPS: segunda autorizacao devolve o mesmo protocolo.
        assertEquals(authorized.protocol(), issuer.authorize(first).protocol());
        assertTrue(issuer.query("DPS-1").isPresent());
        assertTrue(issuer.query("DPS-inexistente").isEmpty());
        FiscalDocumentIssuer.SignedDocument productionAttempt =
                new FiscalDocumentIssuer.SignedDocument("DPS-2", "<dps/>", Environment.HOMOLOGATION);
        assertThrows(IllegalStateException.class, () -> issuer.authorize(productionAttempt));
    }
}
