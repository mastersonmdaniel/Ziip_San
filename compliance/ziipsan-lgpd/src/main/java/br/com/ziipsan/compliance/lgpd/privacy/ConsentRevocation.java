package br.com.ziipsan.compliance.lgpd.privacy;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Revogação de consentimento pelo portal (aceite da P3.40; LGPD, art. 8º, §5º, e art. 18, IX),
 * com a finalidade pelo catálogo da P1.3 ({@code consent_purposes}, DAD-30 — a coluna de
 * finalidade do {@code lgpd_consent} passa a referenciar o código do catálogo na {@code V23_1}).
 *
 * <p>Recusas explícitas, todas falha fechada:
 * <ul>
 *   <li>consentimento de OUTRO titular (regra da P1.15: {@code consentId} conferido contra
 *       o titular — consentimento alheio é recusado, nunca revogado por terceiros);</li>
 *   <li>finalidade fora do catálogo do tenant (código desconhecido não revoga nada);</li>
 *   <li>tratamento que não se funda em consentimento: revogar é ato sobre a base
 *       CONSENT; dado tratado por obrigação legal/contrato não se "revoga" — o caminho
 *       é o pedido de eliminação/oposição, e fingir revogação esconderia isso do titular;</li>
 *   <li>consentimento já revogado ou já encerrado (dupla revogação não reescreve a prova;
 *       a prova do consentimento sobrevive à exclusão lógica — P1.15/{@code V2_11}).</li>
 * </ul>
 */
public final class ConsentRevocation {

    private ConsentRevocation() {
    }

    /** Estados do consentimento, espelho do CHECK de {@code lgpd_consent.status} ({@code V2_5}). */
    public enum ConsentStatus {
        GRANTED, REVOKED, EXPIRED, PENDING, WITHDRAWN
    }

    /** Consentimento como lido de {@code lgpd_consent}. */
    public record Consent(UUID id, UUID subjectId, String purposeCode, LegalBasis legalBasis,
                          ConsentStatus status) {

        public Consent {
            Objects.requireNonNull(id, "id do consentimento é obrigatório");
            Objects.requireNonNull(subjectId, "subjectId do consentimento é obrigatório");
            if (purposeCode == null || purposeCode.isBlank()) {
                throw new IllegalArgumentException("purposeCode é obrigatório (catálogo da P1.3, DAD-30)");
            }
            Objects.requireNonNull(legalBasis, "legalBasis é obrigatória");
            Objects.requireNonNull(status, "status é obrigatório");
        }
    }

    /** Resultado da revogação: o registro que a escrita grava em {@code lgpd_consent}. */
    public record Revocation(UUID consentId, UUID subjectId, String purposeCode,
                             ConsentStatus newStatus, Instant revokedAt, String method) {
    }

    /**
     * Revoga o consentimento do próprio titular, pela finalidade do catálogo.
     *
     * @param consent          consentimento lido de {@code lgpd_consent}
     * @param requesterId      titular autenticado que pede a revogação (portal)
     * @param catalogPurposes  códigos ativos do catálogo {@code consent_purposes} do tenant
     * @param revokedAt        instante da revogação
     * @param method           meio da revogação (ex.: canal do portal), gravado em
     *                         {@code withdrawal_method}
     */
    public static Revocation revoke(Consent consent, UUID requesterId, Set<String> catalogPurposes,
                                    Instant revokedAt, String method) {
        Objects.requireNonNull(consent, "consent é obrigatório");
        Objects.requireNonNull(requesterId, "requesterId é obrigatório");
        Objects.requireNonNull(catalogPurposes, "catalogPurposes é obrigatório (catálogo da P1.3)");
        Objects.requireNonNull(revokedAt, "revokedAt é obrigatório");
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method é obrigatório (withdrawal_method)");
        }
        if (!consent.subjectId().equals(requesterId)) {
            throw new IllegalStateException("revogação recusada: o consentimento é de outro "
                    + "titular (P1.15 — consentId conferido contra o titular)");
        }
        if (!catalogPurposes.contains(consent.purposeCode())) {
            throw new IllegalStateException("revogação recusada: finalidade '"
                    + consent.purposeCode() + "' fora do catálogo de finalidades do tenant "
                    + "(consent_purposes, DAD-30)");
        }
        if (consent.legalBasis() != LegalBasis.CONSENT) {
            throw new IllegalStateException("revogação recusada: este tratamento não se funda "
                    + "em consentimento (base " + consent.legalBasis() + ") — revogação só se "
                    + "aplica à base CONSENT (LGPD, art. 8º, §5º); os demais direitos seguem "
                    + "pelo pedido do titular");
        }
        if (consent.status() != ConsentStatus.GRANTED) {
            throw new IllegalStateException("revogação recusada: consentimento em estado "
                    + consent.status() + " — só consentimento GRANTED é revogável, e a prova "
                    + "do ato anterior não é reescrita");
        }
        return new Revocation(consent.id(), consent.subjectId(), consent.purposeCode(),
                ConsentStatus.REVOKED, revokedAt, method);
    }
}
