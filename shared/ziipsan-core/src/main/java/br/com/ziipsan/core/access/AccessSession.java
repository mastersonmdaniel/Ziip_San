package br.com.ziipsan.core.access;

import br.com.ziipsan.core.json.Json;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Sessão de acesso de plataforma a um tenant (P3.17; ADR-0020).
 *
 * <p>Vínculo fechado por modo, imposto no construtor e no banco (core
 * {@code V10_3}): {@code ASSISTED} exige {@code consentId} e proíbe
 * {@code incidentId}; {@code EMERGENCY} exige {@code incidentId}, proíbe
 * {@code consentId} e só nasce para {@code PLATFORM_OWNER} com o instante da
 * reautenticação MFA gravado ({@code mfaStepUpAt}).
 *
 * <p>Prazos do aceite, calculados aqui com o relógio de quem chama (nada de
 * relógio da máquina dentro do record):
 * <ul>
 *   <li><b>revogação ≤ 1 min</b>: {@link #onConsentRevoked(Instant)} fixa
 *       {@code mustEndBy = revokedAt + }{@link #REVOCATION_CLOSE_LIMIT};
 *       {@link #isTerminatedAt(Instant)} diz se o encerramento já é devido.
 *       O portão ({@link AccessPolicy}) nega qualquer requisição desde o
 *       instante da revogação — o minuto é o teto do encerramento
 *       persistido, não uma janela de uso;</li>
 *   <li><b>aviso ao tenant ≤ 24 h</b> (só emergência):
 *       {@code noticeDueAt = startedAt + }{@link #EMERGENCY_NOTICE_LIMIT},
 *       fixado na abertura; {@link #isNoticeOverdue(Instant)} acusa o
 *       estouro enquanto {@code noticeSentAt} for nulo, e
 *       {@link #isNoticeOnTime()} prova o cumprimento depois do envio.</li>
 * </ul>
 *
 * <p>JSON somente pela classe {@code Json} do core (regra do projeto).
 */
public record AccessSession(
        UUID id,
        UUID tenantId,
        AccessMode mode,
        UUID actorId,
        String actorRole,
        UUID consentId,
        UUID incidentId,
        UUID ticketId,
        Instant startedAt,
        Instant mfaStepUpAt,
        Instant endedAt,
        String endReason,
        Instant mustEndBy,
        Instant noticeDueAt,
        Instant noticeSentAt) {

    /** Teto do encerramento da sessão após a revogação do consentimento (aceite P3.17). */
    public static final Duration REVOCATION_CLOSE_LIMIT = Duration.ofMinutes(1);

    /** Teto do aviso ao tenant sobre a sessão de emergência (aceite P3.17). */
    public static final Duration EMERGENCY_NOTICE_LIMIT = Duration.ofHours(24);

    public AccessSession {
        Objects.requireNonNull(id, "id da sessão é obrigatório");
        Objects.requireNonNull(tenantId, "tenantId da sessão é obrigatório");
        Objects.requireNonNull(mode, "mode da sessão é obrigatório");
        Objects.requireNonNull(actorId, "actorId da sessão é obrigatório");
        Objects.requireNonNull(actorRole, "actorRole da sessão é obrigatório");
        Objects.requireNonNull(startedAt, "startedAt da sessão é obrigatório");
        if (mode == AccessMode.ASSISTED) {
            Objects.requireNonNull(consentId, "sessão ASSISTED exige consentId");
            if (incidentId != null) {
                throw new IllegalArgumentException("sessão ASSISTED não se vincula a incidente");
            }
            if (noticeDueAt != null || noticeSentAt != null) {
                throw new IllegalArgumentException("aviso de 24 h é só da sessão EMERGENCY");
            }
        } else {
            Objects.requireNonNull(incidentId, "sessão EMERGENCY exige incidentId");
            if (consentId != null) {
                throw new IllegalArgumentException("sessão EMERGENCY não usa consentimento");
            }
            if (!PlatformActor.PLATFORM_OWNER.equals(actorRole)) {
                throw new IllegalArgumentException(
                        "sessão EMERGENCY só nasce para PLATFORM_OWNER; recebido: " + actorRole);
            }
            Objects.requireNonNull(mfaStepUpAt, "sessão EMERGENCY exige mfaStepUpAt (MFA no momento)");
            Objects.requireNonNull(noticeDueAt, "sessão EMERGENCY exige noticeDueAt (startedAt + 24 h)");
            if (!noticeDueAt.equals(startedAt.plus(EMERGENCY_NOTICE_LIMIT))) {
                throw new IllegalArgumentException(
                        "noticeDueAt precisa ser startedAt + 24 h (" + EMERGENCY_NOTICE_LIMIT + ")");
            }
        }
        if ((endedAt == null) != (endReason == null)) {
            throw new IllegalArgumentException("endedAt e endReason andam juntos");
        }
    }

    /** Abertura assistida: vinculada ao consentimento, sem prazo de aviso. */
    public static AccessSession openAssisted(UUID id, UUID tenantId, PlatformActor actor,
                                             AssistedConsent consent, Instant startedAt) {
        Objects.requireNonNull(actor, "actor é obrigatório");
        Objects.requireNonNull(consent, "consent é obrigatório");
        return new AccessSession(id, tenantId, AccessMode.ASSISTED, actor.userId(), actor.role(),
                consent.id(), null, consent.ticketId(), startedAt, actor.mfaStepUpAt(),
                null, null, null, null, null);
    }

    /** Abertura de emergência: vinculada ao incidente, com o aviso devido em 24 h. */
    public static AccessSession openEmergency(UUID id, UUID tenantId, PlatformActor actor,
                                              SecurityIncidentRef incident, Instant startedAt) {
        Objects.requireNonNull(actor, "actor é obrigatório");
        Objects.requireNonNull(incident, "incident é obrigatório");
        Objects.requireNonNull(startedAt, "startedAt é obrigatório");
        return new AccessSession(id, tenantId, AccessMode.EMERGENCY, actor.userId(), actor.role(),
                null, incident.incidentId(), null, startedAt, actor.mfaStepUpAt(),
                null, null, null, startedAt.plus(EMERGENCY_NOTICE_LIMIT), null);
    }

    /** Cópia encerrada em {@code when} pelo motivo {@code reason}. */
    public AccessSession end(Instant when, String reason) {
        Objects.requireNonNull(when, "instante do encerramento é obrigatório");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("endReason é obrigatório ao encerrar a sessão");
        }
        if (endedAt != null) {
            throw new IllegalStateException("sessão já encerrada em " + endedAt);
        }
        return new AccessSession(id, tenantId, mode, actorId, actorRole, consentId, incidentId,
                ticketId, startedAt, mfaStepUpAt, when, reason, mustEndBy, noticeDueAt, noticeSentAt);
    }

    /**
     * Cópia marcada pela revogação do consentimento em {@code revokedAt}:
     * o encerramento passa a ser devido até {@code revokedAt + 1 min}.
     * Só vale para sessão ASSISTED.
     */
    public AccessSession onConsentRevoked(Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt é obrigatório");
        if (mode != AccessMode.ASSISTED) {
            throw new IllegalStateException("só sessão ASSISTED tem consentimento a revogar");
        }
        return new AccessSession(id, tenantId, mode, actorId, actorRole, consentId, incidentId,
                ticketId, startedAt, mfaStepUpAt, endedAt, endReason,
                revokedAt.plus(REVOCATION_CLOSE_LIMIT), noticeDueAt, noticeSentAt);
    }

    /** Encerramento devido em {@code now}: sessão com {@code mustEndBy} já alcançado. */
    public boolean isTerminatedAt(Instant now) {
        Objects.requireNonNull(now, "now é obrigatório");
        return endedAt != null || (mustEndBy != null && !now.isBefore(mustEndBy));
    }

    /** Sessão aberta (não encerrada nem com encerramento devido) em {@code now}. */
    public boolean isOpenAt(Instant now) {
        return !isTerminatedAt(now);
    }

    /** Cópia com o aviso ao tenant registrado como enviado em {@code when} (só EMERGENCY). */
    public AccessSession withNoticeSentAt(Instant when) {
        Objects.requireNonNull(when, "instante do envio do aviso é obrigatório");
        if (mode != AccessMode.EMERGENCY) {
            throw new IllegalStateException("aviso ao tenant é só da sessão EMERGENCY");
        }
        return new AccessSession(id, tenantId, mode, actorId, actorRole, consentId, incidentId,
                ticketId, startedAt, mfaStepUpAt, endedAt, endReason, mustEndBy, noticeDueAt, when);
    }

    /** Aviso vencido em {@code now}: prazo de 24 h passado e envio ainda não registrado. */
    public boolean isNoticeOverdue(Instant now) {
        Objects.requireNonNull(now, "now é obrigatório");
        return mode == AccessMode.EMERGENCY && noticeSentAt == null && now.isAfter(noticeDueAt);
    }

    /** Aviso cumprido no prazo: enviado e não depois de {@code noticeDueAt}. */
    public boolean isNoticeOnTime() {
        return mode == AccessMode.EMERGENCY && noticeSentAt != null && !noticeSentAt.isAfter(noticeDueAt);
    }

    /** JSON da sessão, só pela classe {@code Json} do core. */
    public String toJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sessionId", id.toString());
        map.put("tenantId", tenantId.toString());
        map.put("mode", mode.name());
        map.put("actorId", actorId.toString());
        map.put("actorRole", actorRole);
        map.put("consentId", consentId == null ? null : consentId.toString());
        map.put("incidentId", incidentId == null ? null : incidentId.toString());
        map.put("ticketId", ticketId == null ? null : ticketId.toString());
        map.put("startedAt", startedAt.toString());
        map.put("noticeDueAt", noticeDueAt == null ? null : noticeDueAt.toString());
        map.put("noticeSentAt", noticeSentAt == null ? null : noticeSentAt.toString());
        return Json.write(map);
    }
}
