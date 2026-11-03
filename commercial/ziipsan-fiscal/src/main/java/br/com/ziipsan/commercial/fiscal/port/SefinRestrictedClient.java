package br.com.ziipsan.commercial.fiscal.port;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stub local do cliente da produção restrita da NFS-e Nacional (P1.8).
 *
 * <p>É um stub de propósito, não um cliente incompleto que finge funcionar: toda tentativa
 * de autorizar, consultar ou registrar evento falha fechada com
 * {@link RestrictedProductionUnavailableException} e conta a tentativa. O cliente mTLS real
 * (SSLContext por emitente, truststore ICP-Brasil, retry — FISCAL-PORTE-JAVA §2) chega na
 * P2, junto com o certificado do cofre local; até lá, o roteador entrega este stub para
 * HOMOLOGATION e PRODUCTION, e o serviço recusa antes de qualquer escrita.
 */
public final class SefinRestrictedClient implements FiscalDocumentIssuer {

    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    public FiscalDocumentType type() {
        return FiscalDocumentType.NFSE_NACIONAL;
    }

    @Override
    public AuthorizationResult authorize(SignedDocument document) {
        attempts.incrementAndGet();
        throw new RestrictedProductionUnavailableException();
    }

    @Override
    public Optional<AuthorizationResult> query(String documentId) {
        attempts.incrementAndGet();
        throw new RestrictedProductionUnavailableException();
    }

    @Override
    public EventResult registerEvent(String eventId) {
        attempts.incrementAndGet();
        throw new RestrictedProductionUnavailableException();
    }

    /** Tentativas recusadas pelo stub (prova de que nada foi emitido por aqui). */
    public int attempts() {
        return attempts.get();
    }
}
