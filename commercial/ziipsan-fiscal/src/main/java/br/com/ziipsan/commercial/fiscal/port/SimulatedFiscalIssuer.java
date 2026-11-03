package br.com.ziipsan.commercial.fiscal.port;

import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceGate.Environment;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulador do C# (FISCAL-PORTE-JAVA §1, §3.1): implementação padrão de todo ambiente que
 * não seja produção, com idempotência por Id da DPS — autorizar duas vezes o mesmo documento
 * devolve o mesmo protocolo, sem segunda autorização. Nunca emite com tpAmb=1: o ambiente do
 * simulador é sempre SIMULATED e qualquer documento que chegue com outro ambiente é recusado.
 */
public final class SimulatedFiscalIssuer implements FiscalDocumentIssuer {

    private final Map<String, AuthorizationResult> authorized = new ConcurrentHashMap<>();
    private final FiscalDocumentType documentType;

    public SimulatedFiscalIssuer(FiscalDocumentType documentType) {
        this.documentType = documentType;
    }

    @Override
    public FiscalDocumentType type() {
        return documentType;
    }

    @Override
    public AuthorizationResult authorize(SignedDocument document) {
        if (document == null || document.documentId() == null || document.documentId().isBlank()) {
            throw new IllegalArgumentException("O documento a autorizar e obrigatorio");
        }
        if (document.environment() != Environment.SIMULATED) {
            throw new IllegalStateException("O simulador so autoriza em ambiente SIMULATED, nunca com tpAmb=1");
        }
        return authorized.computeIfAbsent(document.documentId(),
                id -> new AuthorizationResult(id, "SIM-" + id,
                        true, Environment.SIMULATED));
    }

    @Override
    public Optional<AuthorizationResult> query(String documentId) {
        return Optional.ofNullable(authorized.get(documentId));
    }

    @Override
    public EventResult registerEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("O Id do evento e obrigatorio");
        }
        return new EventResult(eventId, true);
    }
}
