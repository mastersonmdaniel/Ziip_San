package br.com.ziipsan.commercial.fiscal.port;

import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceGate.Environment;

import java.util.Optional;

/**
 * Porta do emissor fiscal (FISCAL-PORTE-JAVA §3.1). O roteamento escolhe a implementação por
 * (tenant, emitente, tipo, ambiente); o simulador é o padrão de todo ambiente que não seja
 * produção, como no C#. A DPS é persistida antes do envio e nunca se retransmite sem
 * reconciliação — essas duas regras vivem na camada de aplicação da P1.8 (outbox), não aqui.
 */
public interface FiscalDocumentIssuer {

    /** Tipos de documento do porte: NFS-e Nacional e NFAg-e (modelo 75). */
    enum FiscalDocumentType {
        NFSE_NACIONAL,
        NFAG
    }

    /** Documento assinado pronto para autorizar (o XML assinado só existe a partir da P1.8). */
    record SignedDocument(String documentId, String xml, Environment environment) {
    }

    /** Resultado de uma autorização (simulada ou real). */
    record AuthorizationResult(String documentId, String protocol, boolean authorized, Environment environment) {
    }

    /** Resultado de um evento (cancelamento, substituição). */
    record EventResult(String eventId, boolean accepted) {
    }

    FiscalDocumentType type();

    AuthorizationResult authorize(SignedDocument document);

    /** Reconciliação pós-timeout: consulta antes de qualquer retransmissão. */
    Optional<AuthorizationResult> query(String documentId);

    EventResult registerEvent(String eventId);
}
