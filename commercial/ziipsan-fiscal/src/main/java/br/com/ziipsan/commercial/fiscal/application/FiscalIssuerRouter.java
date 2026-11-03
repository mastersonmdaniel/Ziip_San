package br.com.ziipsan.commercial.fiscal.application;

import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceGate.Environment;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentIssuer;
import br.com.ziipsan.commercial.fiscal.port.SefinRestrictedClient;
import br.com.ziipsan.commercial.fiscal.port.SimulatedFiscalIssuer;

/**
 * Roteamento por (ambiente) do porte (FISCAL-PORTE-JAVA §3.1, §3.6): o simulador é o
 * padrão de todo ambiente que não seja produção, como no C#; HOMOLOGATION e PRODUCTION
 * caem no stub da produção restrita até o cliente mTLS real chegar (P2). A trava de
 * produção do {@link FiscalIssuanceGate} é aplicada aqui, antes de qualquer roteamento
 * para produção — nunca degradada em silêncio para homologação.
 */
public final class FiscalIssuerRouter {

    private final FiscalIssuanceGate gate;
    private final SimulatedFiscalIssuer simulator;
    private final SefinRestrictedClient restricted;

    public FiscalIssuerRouter(FiscalIssuanceGate gate, SimulatedFiscalIssuer simulator,
            SefinRestrictedClient restricted) {
        this.gate = gate;
        this.simulator = simulator;
        this.restricted = restricted;
    }

    /** Resolve o ambiente (aplica a trava de produção) e devolve o emissor correspondente. */
    public FiscalDocumentIssuer issuerFor(Environment requested, boolean tenantApproved) {
        Environment resolved = gate.resolve(requested, tenantApproved);
        return switch (resolved) {
            case SIMULATED -> simulator;
            case HOMOLOGATION, PRODUCTION -> restricted;
        };
    }

    /** Verdadeiro quando o ambiente cai no stub (o serviço recusa antes de escrever). */
    public boolean isRestricted(Environment environment) {
        return environment == Environment.HOMOLOGATION || environment == Environment.PRODUCTION;
    }

    public SimulatedFiscalIssuer simulator() {
        return simulator;
    }

    public SefinRestrictedClient restricted() {
        return restricted;
    }
}
