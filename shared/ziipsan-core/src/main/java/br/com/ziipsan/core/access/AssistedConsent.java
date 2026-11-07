package br.com.ziipsan.core.access;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Consentimento do tenant para o acesso assistido (P3.17; ADR-0020).
 *
 * <p>O consentimento é do tenant, para UM beneficiário de plataforma
 * ({@code beneficiaryId}), com escopo declarado em texto
 * ({@code scope} — o que o atendimento pode fazer, ex.: o chamado de suporte
 * da P1.23 ao qual o acesso se refere, em {@code ticketId} quando houver) e
 * janela de vigência [{@code grantedAt}, {@code expiresAt}). Sem este
 * registro vigente, não há acesso assistido: ver {@link AccessPolicy}.
 *
 * <p>Revogação: {@link #revoke(Instant, UUID)} devolve uma CÓPIA revogada
 * (o record é imutável). A partir do instante da revogação o consentimento
 * já não está ativo ({@link #isActiveAt(Instant)} devolve {@code false}) —
 * o portão nega na hora, e o prazo de "até 1 min" do aceite é o limite para
 * o ENCERRAMENTO persistido da sessão, calculado em
 * {@link AccessSession#onConsentRevoked(Instant)}.
 *
 * <p>JSON somente pela classe {@code Json} do core (regra do projeto).
 */
public record AssistedConsent(
        UUID id,
        UUID tenantId,
        UUID ticketId,
        UUID grantedBy,
        UUID beneficiaryId,
        String scope,
        Instant grantedAt,
        Instant expiresAt,
        Instant revokedAt,
        UUID revokedBy) {

    public AssistedConsent {
        Objects.requireNonNull(id, "id do consentimento é obrigatório");
        Objects.requireNonNull(tenantId, "tenantId do consentimento é obrigatório");
        Objects.requireNonNull(grantedBy, "grantedBy (usuário do tenant que consente) é obrigatório");
        Objects.requireNonNull(beneficiaryId, "beneficiaryId (ator de plataforma) é obrigatório");
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException(
                    "scope do consentimento é obrigatório: consentimento sem escopo declarado não existe");
        }
        Objects.requireNonNull(grantedAt, "grantedAt é obrigatório");
        Objects.requireNonNull(expiresAt, "expiresAt é obrigatório: consentimento sem fim não é concedido");
        if (!expiresAt.isAfter(grantedAt)) {
            throw new IllegalArgumentException("expiresAt precisa ser posterior a grantedAt");
        }
        if ((revokedAt == null) != (revokedBy == null)) {
            throw new IllegalArgumentException("revokedAt e revokedBy andam juntos");
        }
        if (revokedAt != null && revokedAt.isBefore(grantedAt)) {
            throw new IllegalArgumentException("revokedAt não pode preceder grantedAt");
        }
    }

    /** Fábrica de concessão: consentimento novo, ainda não revogado. */
    public static AssistedConsent grant(UUID id, UUID tenantId, UUID ticketId, UUID grantedBy,
                                        UUID beneficiaryId, String scope,
                                        Instant grantedAt, Instant expiresAt) {
        return new AssistedConsent(id, tenantId, ticketId, grantedBy, beneficiaryId, scope,
                grantedAt, expiresAt, null, null);
    }

    /** Cópia revogada a partir de {@code when}; revogar duas vezes é recusado. */
    public AssistedConsent revoke(Instant when, UUID by) {
        Objects.requireNonNull(when, "instante da revogação é obrigatório");
        Objects.requireNonNull(by, "autor da revogação é obrigatório");
        if (revokedAt != null) {
            throw new IllegalStateException("consentimento já revogado em " + revokedAt);
        }
        return new AssistedConsent(id, tenantId, ticketId, grantedBy, beneficiaryId, scope,
                grantedAt, expiresAt, when, by);
    }

    /** Vigente em {@code now}: concedido, não expirado e não revogado. */
    public boolean isActiveAt(Instant now) {
        Objects.requireNonNull(now, "now é obrigatório");
        return !now.isBefore(grantedAt) && now.isBefore(expiresAt) && !isRevokedAt(now);
    }

    /** Revogado com efeito em {@code now} (a revogação vale do seu instante em diante). */
    public boolean isRevokedAt(Instant now) {
        Objects.requireNonNull(now, "now é obrigatório");
        return revokedAt != null && !now.isBefore(revokedAt);
    }

    /** JSON do consentimento, só pela classe {@code Json} do core. */
    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("consentId", id.toString());
        map.put("tenantId", tenantId.toString());
        map.put("ticketId", ticketId == null ? null : ticketId.toString());
        map.put("beneficiaryId", beneficiaryId.toString());
        map.put("scope", scope);
        map.put("grantedAt", grantedAt.toString());
        map.put("expiresAt", expiresAt.toString());
        map.put("revokedAt", revokedAt == null ? null : revokedAt.toString());
        return Json.write(map);
    }
}
