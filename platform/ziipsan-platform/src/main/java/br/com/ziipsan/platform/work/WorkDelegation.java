package br.com.ziipsan.platform.work;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Delegação temporária registrada ({@code work_item_delegations} da V1_11): quem delega,
 * quem recebe, INÍCIO e FIM, e o CONJUNTO de permissões concedidas — um instantâneo do
 * teto: a delegação nunca carrega permissão que o delegante não tinha na concessão
 * (conferido na criação pela {@link DelegationRule}) e a permissão efetiva continua
 * limitada à do delegante VIGENTE no uso (interseção na {@link DelegationRule}).
 * O registro é a auditoria: linha nunca é apagada; revogação grava {@code revokedAt}
 * e {@code revokedBy}.
 */
public record WorkDelegation(UUID id, UUID tenantId, UUID delegatorId, UUID delegateId,
                             Instant startsAt, Instant endsAt, Set<String> grantedPermissions,
                             String reason, UUID createdBy, Instant createdAt,
                             Instant revokedAt, UUID revokedBy) {

    public WorkDelegation {
        if (id == null || tenantId == null || delegatorId == null || delegateId == null
                || startsAt == null || endsAt == null || createdBy == null || createdAt == null) {
            throw new IllegalArgumentException("delegação exige id, tenant, delegante, delegado, início, fim e registro");
        }
        if (delegatorId.equals(delegateId)) {
            throw new IllegalArgumentException("delegação para si mesmo não é delegação");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("delegação exige fim estritamente depois do início");
        }
        grantedPermissions = grantedPermissions == null ? Set.of() : Set.copyOf(grantedPermissions);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("delegação exige o motivo do registro");
        }
        if ((revokedAt == null) != (revokedBy == null)) {
            throw new IllegalArgumentException("revogação exige revokedAt e revokedBy juntos");
        }
    }

    /**
     * Vigente no instante: {@code startsAt <= instante < endsAt} e não revogada.
     * O fim é EXCLUSIVO — na data/hora de fim a delegação JÁ expirou (teste do aceite).
     */
    public boolean isActiveAt(Instant instant) {
        return revokedAt == null && !instant.isBefore(startsAt) && instant.isBefore(endsAt);
    }
}
