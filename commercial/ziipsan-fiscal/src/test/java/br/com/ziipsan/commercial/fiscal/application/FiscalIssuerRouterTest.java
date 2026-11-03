package br.com.ziipsan.commercial.fiscal.application;

import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceGate.Environment;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentIssuer.FiscalDocumentType;
import br.com.ziipsan.commercial.fiscal.port.RestrictedProductionUnavailableException;
import br.com.ziipsan.commercial.fiscal.port.SefinRestrictedClient;
import br.com.ziipsan.commercial.fiscal.port.SimulatedFiscalIssuer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roteamento por ambiente e trava de produção (FISCAL-PORTE-JAVA §3.6). */
class FiscalIssuerRouterTest {

    private static FiscalIssuerRouter router(FiscalIssuanceGate gate) {
        var simulator = new SimulatedFiscalIssuer(FiscalDocumentType.NFSE_NACIONAL);
        var restricted = new SefinRestrictedClient();
        return new FiscalIssuerRouter(gate, simulator, restricted);
    }

    @Test
    void simulatedGoesToSimulatorAndOthersToRestrictedStub() {
        FiscalIssuerRouter router = router(FiscalIssuanceGate.closed());
        assertSame(router.simulator(), router.issuerFor(Environment.SIMULATED, false));
        assertSame(router.restricted(), router.issuerFor(Environment.HOMOLOGATION, false));
        assertTrue(router.isRestricted(Environment.HOMOLOGATION));
    }

    @Test
    void productionWithoutGateOrApprovalIsRefusedNeverDowngraded() {
        FiscalIssuerRouter closed = router(FiscalIssuanceGate.closed());
        assertThrows(IllegalStateException.class, () -> closed.issuerFor(Environment.PRODUCTION, true));
        FiscalIssuerRouter open = router(new FiscalIssuanceGate(true));
        assertThrows(IllegalStateException.class, () -> open.issuerFor(Environment.PRODUCTION, false));
        assertSame(open.restricted(), open.issuerFor(Environment.PRODUCTION, true));
    }

    @Test
    void restrictedStubFailsClosedAndCountsAttempts() {
        var restricted = new SefinRestrictedClient();
        assertThrows(RestrictedProductionUnavailableException.class,
                () -> restricted.authorize(null));
        assertThrows(RestrictedProductionUnavailableException.class, () -> restricted.query("x"));
        assertThrows(RestrictedProductionUnavailableException.class, () -> restricted.registerEvent("x"));
        assertEquals(3, restricted.attempts());
    }
}
