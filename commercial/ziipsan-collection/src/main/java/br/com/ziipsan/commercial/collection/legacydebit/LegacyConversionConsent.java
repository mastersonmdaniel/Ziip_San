package br.com.ziipsan.commercial.collection.legacydebit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Consentimento do optante para conversao ao Pix Automatico (campanha da
 * P3.24 sobre a P4.5), GUARDADO: sem prova nao ha consentimento.
 *
 * <p>{@code proofHash} e o SHA-256 (64 hex) do registro do consentimento e
 * {@code proofReference} a referencia localizavel dele - mesmo desenho da
 * prova da autorizacao Pix da P4.5. O CANAL concreto de coleta (app, link,
 * atendimento) NAO tem fonte no plano/repo: ele entra como texto declarado
 * por quem chama ({@code channel}) e nenhum canal e presumido aqui
 * [MARCADOR no relatorio].
 */
public record LegacyConversionConsent(UUID id, UUID tenantId, UUID authorizationId,
                                      String enrollmentId, String proofHash, String proofReference,
                                      String channel, Instant consentedAt) {
    public LegacyConversionConsent {
        Objects.requireNonNull(id, "id do consentimento e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(authorizationId, "autorizacao e obrigatoria");
        if (enrollmentId == null || enrollmentId.isBlank()) {
            throw new IllegalArgumentException("matricula do optante e obrigatoria no consentimento");
        }
        if (proofHash == null || !proofHash.matches("^[0-9a-fA-F]{64}$")) {
            throw new IllegalArgumentException(
                    "consentimento sem prova: proofHash deve ser o SHA-256 (64 hex) do registro");
        }
        proofHash = proofHash.toLowerCase();
        if (proofReference == null || proofReference.isBlank()) {
            throw new IllegalArgumentException(
                    "consentimento sem prova: proofReference do registro e obrigatoria");
        }
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("canal de coleta do consentimento e obrigatorio");
        }
        Objects.requireNonNull(consentedAt, "instante do consentimento e obrigatorio");
    }
}
