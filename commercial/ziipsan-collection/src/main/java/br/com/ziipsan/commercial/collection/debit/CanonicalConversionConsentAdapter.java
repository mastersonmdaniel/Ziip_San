package br.com.ziipsan.commercial.collection.debit;

import br.com.ziipsan.commercial.collection.legacydebit.LegacyConversionConsent;
import br.com.ziipsan.commercial.collection.legacydebit.LegacyDebitAuthorization;
import br.com.ziipsan.commercial.collection.legacydebit.LegacyDebitService;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador canonico do consentimento de conversao (B2-2 da rodada 2):
 * o {@link ConversionConsentStore} da ED-27 sobre o
 * {@code LegacyDebitService.Store} da P3.24, cuja tabela e
 * {@code legacy_debit_conversion_consents} (V102).
 *
 * <p>Mapeamento: a autorizacao e resolvida POR MATRICULA no store da
 * P3.24 (a ED-27 trabalha por unidade; a matricula e a chave de
 * conferencia da importacao legada). Sem autorizacao para a matricula,
 * nada e guardado - o consentimento orfao nao existe no canonico.
 * A prova e validada pelo proprio registro
 * {@link LegacyConversionConsent} (SHA-256 de 64 hex + referencia +
 * canal obrigatorios).
 *
 * <p>Este adaptador persiste o REGISTRO do consentimento; a transicao
 * de status da autorizacao para CONVERTIDA_PIX pertence a
 * {@code LegacyDebitService.registerConversionConsent} (P3.24), que
 * grava no MESMO store com a MESMA idempotencia por prova - um
 * consentimento guardado aqui e depois convertido por la nao duplica.
 */
public final class CanonicalConversionConsentAdapter implements ConversionConsentStore {

    private final LegacyDebitService.Store store;

    public CanonicalConversionConsentAdapter(LegacyDebitService.Store store) {
        this.store = Objects.requireNonNull(store, "Store da P3.24 e obrigatorio");
    }

    @Override
    public LegacyConversionConsent saveConsent(UUID tenantId, UUID unitId, String enrollmentId,
                                               String proofHash, String proofReference,
                                               String channel, Instant consentedAt) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (enrollmentId == null || enrollmentId.isBlank()) {
            throw new IllegalArgumentException(
                    "matricula do optante e obrigatoria para guardar o consentimento");
        }
        LegacyDebitAuthorization auth = store.findAuthorizationByEnrollment(tenantId, enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "matricula nao importada no legado para o tenant: " + enrollmentId));
        String normalized = proofHash == null ? null : proofHash.toLowerCase();
        for (LegacyConversionConsent prior : store.findConsents(tenantId, auth.id())) {
            if (prior.proofHash().equals(normalized)) {
                return prior;
            }
        }
        LegacyConversionConsent consent = new LegacyConversionConsent(UUID.randomUUID(), tenantId,
                auth.id(), enrollmentId, proofHash, proofReference, channel, consentedAt);
        store.saveConsent(consent);
        return consent;
    }

    @Override
    public Optional<LegacyConversionConsent> findConsent(UUID tenantId, String enrollmentId) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (enrollmentId == null || enrollmentId.isBlank()) {
            return Optional.empty();
        }
        return store.findAuthorizationByEnrollment(tenantId, enrollmentId)
                .flatMap(auth -> store.findConsents(tenantId, auth.id()).stream().findFirst());
    }
}
