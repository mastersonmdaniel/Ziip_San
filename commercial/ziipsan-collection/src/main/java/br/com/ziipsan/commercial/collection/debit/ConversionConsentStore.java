package br.com.ziipsan.commercial.collection.debit;

import br.com.ziipsan.commercial.collection.legacydebit.LegacyConversionConsent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistencia do consentimento de conversao ao Pix Automatico
 * (B2-2 da rodada 2 da auditoria continua).
 *
 * <p>UM SO store canonico: {@code legacy_debit_conversion_consents}
 * (V102 da P3.24, com FK para a autorizacao legada, prova por hash
 * SHA-256 + referencia e canal de coleta). A tabela
 * {@code debit_conversion_consent} da ED-27 era um SEGUNDO store sem
 * sincronizacao com este - nenhum Java da ED-27 a referenciava e o
 * {@code convertOptant} nao persistia nada - e foi REMOVIDA do SQL
 * pendente da ED-27 por este pacote; a implementacao canonica e
 * {@link CanonicalConversionConsentAdapter}, sobre o
 * {@code LegacyDebitService.Store} da P3.24.
 */
public interface ConversionConsentStore {

    /**
     * Guarda o consentimento do optante no store canonico. Idempotente
     * por prova: o mesmo consentimento (mesma matricula + mesmo hash)
     * guardado duas vezes devolve o registro ja existente, sem duplicar.
     */
    LegacyConversionConsent saveConsent(UUID tenantId, UUID unitId, String enrollmentId,
                                        String proofHash, String proofReference,
                                        String channel, Instant consentedAt);

    /** Consentimento guardado do optante, se houver. */
    Optional<LegacyConversionConsent> findConsent(UUID tenantId, String enrollmentId);
}
