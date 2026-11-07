package br.com.ziipsan.core.restore;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Aprovacao do tenant para a restauracao seletiva (P3.33), nos moldes do
 * consentimento do ADR-0020 — o mesmo desenho do {@code AssistedConsent}
 * da P3.17: consentimento DO tenant, com escopo declarado, janela de
 * vigencia e revogacao, acrescido do INSTANTE-ALVO da restauracao.
 *
 * <p>Diferenca deliberada para a restauracao: o consentimento amarra o
 * instante-alvo ({@code targetInstant}). Restaurar para um instante
 * diverso do consentido e restauracao diversa — a politica
 * ({@link TenantRestorePolicy}) nega com
 * {@code consent-target-mismatch}. Um consentimento tambem nao autoriza
 * mesclagem quando o escopo diz substituicao, e vice-versa: o
 * {@code mode} consentido ({@link RestoreMode}) faz parte do escopo.
 *
 * <p>Revogacao devolve COPIA revogada (record imutavel), como na P3.17.
 * JSON somente pela classe {@code Json} do core (regra do projeto).
 */
public record RestoreConsent(
        UUID id,
        UUID tenantId,
        UUID grantedBy,
        RestoreMode mode,
        Instant targetInstant,
        Instant grantedAt,
        Instant expiresAt,
        Instant revokedAt,
        UUID revokedBy) {

    /** Modo de aplicacao consentido pelo tenant. */
    public enum RestoreMode {
        /** O schema restaurado substitui o atual (o atual e preservado antes, ver runbook). */
        REPLACE,
        /** O schema restaurado e mesclado ao atual, tabela a tabela pelo diff aprovado. */
        MERGE
    }

    public RestoreConsent {
        Objects.requireNonNull(id, "id do consentimento e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId do consentimento e obrigatorio");
        Objects.requireNonNull(grantedBy, "grantedBy (usuario do tenant que consente) e obrigatorio");
        Objects.requireNonNull(mode, "mode consentido e obrigatorio: substituicao ou mesclagem fazem parte do escopo");
        Objects.requireNonNull(targetInstant, "targetInstant e obrigatorio: consentimento sem instante-alvo nao existe");
        Objects.requireNonNull(grantedAt, "grantedAt e obrigatorio");
        Objects.requireNonNull(expiresAt, "expiresAt e obrigatorio: consentimento sem fim nao e concedido");
        if (!expiresAt.isAfter(grantedAt)) {
            throw new IllegalArgumentException("expiresAt precisa ser posterior a grantedAt");
        }
        if ((revokedAt == null) != (revokedBy == null)) {
            throw new IllegalArgumentException("revokedAt e revokedBy andam juntos");
        }
        if (revokedAt != null && revokedAt.isBefore(grantedAt)) {
            throw new IllegalArgumentException("revokedAt nao pode preceder grantedAt");
        }
    }

    /** Fabrica de concessao: consentimento novo, ainda nao revogado. */
    public static RestoreConsent grant(UUID id, UUID tenantId, UUID grantedBy, RestoreMode mode,
                                       Instant targetInstant, Instant grantedAt, Instant expiresAt) {
        return new RestoreConsent(id, tenantId, grantedBy, mode, targetInstant,
                grantedAt, expiresAt, null, null);
    }

    /** Copia revogada a partir de {@code when}; revogar duas vezes e recusado. */
    public RestoreConsent revoke(Instant when, UUID by) {
        Objects.requireNonNull(when, "instante da revogacao e obrigatorio");
        Objects.requireNonNull(by, "autor da revogacao e obrigatorio");
        if (revokedAt != null) {
            throw new IllegalStateException("consentimento ja revogado em " + revokedAt);
        }
        return new RestoreConsent(id, tenantId, grantedBy, mode, targetInstant,
                grantedAt, expiresAt, when, by);
    }

    /** Vigente em {@code now}: concedido, nao expirado e nao revogado. */
    public boolean isActiveAt(Instant now) {
        Objects.requireNonNull(now, "now e obrigatorio");
        return !now.isBefore(grantedAt) && now.isBefore(expiresAt) && !isRevokedAt(now);
    }

    /** Revogado com efeito em {@code now}. */
    public boolean isRevokedAt(Instant now) {
        Objects.requireNonNull(now, "now e obrigatorio");
        return revokedAt != null && !now.isBefore(revokedAt);
    }

    /** JSON do consentimento, so pela classe {@code Json} do core. */
    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("consentId", id.toString());
        map.put("tenantId", tenantId.toString());
        map.put("mode", mode.name());
        map.put("targetInstant", targetInstant.toString());
        map.put("grantedAt", grantedAt.toString());
        map.put("expiresAt", expiresAt.toString());
        map.put("revokedAt", revokedAt == null ? null : revokedAt.toString());
        return Json.write(map);
    }
}
