package br.com.ziipsan.core.access;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Portão de requisição do acesso assistido e do acesso de emergência
 * (P3.17; ADR-0020): TODA requisição e TODA tentativa passam por aqui, e
 * toda passagem é auditada ANTES de a decisão valer — permitida ou negada,
 * a trilha existe. Negação sem auditoria é impossível neste desenho: o
 * guard grava a entrada e só depois lança {@link AccessDeniedException}.
 *
 * <p>Trilhas por modo (aceite da P3.17):
 * <ul>
 *   <li>assistido: {@code audit_log} do tenant, com o id do consentimento
 *       (nulo só quando a ausência dele é o motivo da negação);</li>
 *   <li>emergência: {@code audit_log} do tenant E {@code audit_event} da
 *       plataforma, com o id do incidente; a abertura ainda registra a
 *       sessão no registro do incidente e agenda o aviso ao tenant pela
 *       outbox (prazo de 24 h calculado na {@link AccessSession}).</li>
 * </ul>
 *
 * <p>O relógio é injetado: abertura, revogação (≤ 1 min) e aviso (≤ 24 h)
 * são exercitados nos testes com {@code Clock.fixed}, sem dormir.
 */
public final class AccessGuard {

    private final AccessAuditPort audit;
    private final IncidentSessionRegistry incidentRegistry;
    private final EmergencyNoticePort noticePort;
    private final Clock clock;

    public AccessGuard(AccessAuditPort audit, IncidentSessionRegistry incidentRegistry,
                       EmergencyNoticePort noticePort, Clock clock) {
        if (audit == null || incidentRegistry == null || noticePort == null || clock == null) {
            throw new IllegalArgumentException(
                    "auditoria, registro de incidente, aviso e relógio são obrigatórios");
        }
        this.audit = audit;
        this.incidentRegistry = incidentRegistry;
        this.noticePort = noticePort;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Assistido
    // ------------------------------------------------------------------

    /**
     * Abre a sessão assistida: audita a tentativa no {@code audit_log} do
     * tenant com o id do consentimento e só devolve a sessão se a política
     * permitir; negada, lança {@link AccessDeniedException} (403) já
     * auditada.
     */
    public AccessSession openAssisted(AssistedConsent consent, UUID tenantId, PlatformActor actor,
                                      AccessOperation operation, UUID ticketId, UUID requestId) {
        Objects.requireNonNull(requestId, "requestId é obrigatório");
        java.time.Instant now = clock.instant();
        AccessPolicy.Decision decision = AccessPolicy.evaluateAssistedOpening(
                consent, tenantId, actor, operation, ticketId, now);
        audit.appendTenantAudit(entry(tenantId, AccessMode.ASSISTED, null, requestId, actor,
                operation, decision, consent == null ? null : consent.id(), null, now));
        if (!decision.allowed()) {
            throw new AccessDeniedException(decision.code(), decision.message());
        }
        return AccessSession.openAssisted(UUID.randomUUID(), tenantId, actor, consent, now);
    }

    /** Autoriza (ou nega, auditando) uma requisição dentro da sessão assistida. */
    public void authorizeAssistedRequest(AssistedConsent consent, AccessSession session,
                                         PlatformActor actor, AccessOperation operation,
                                         UUID requestId) {
        Objects.requireNonNull(requestId, "requestId é obrigatório");
        if (session == null && consent == null) {
            throw new IllegalArgumentException(
                    "requisição assistida exige a sessão ou o consentimento para auditar no tenant");
        }
        java.time.Instant now = clock.instant();
        AccessPolicy.Decision decision = AccessPolicy.evaluateAssistedRequest(
                consent, session, actor, operation, now);
        audit.appendTenantAudit(entry(session == null ? consent.tenantId() : session.tenantId(),
                AccessMode.ASSISTED, session == null ? null : session.id(), requestId, actor,
                operation, decision, consent == null ? null : consent.id(), null, now));
        if (!decision.allowed()) {
            throw new AccessDeniedException(decision.code(), decision.message());
        }
    }

    // ------------------------------------------------------------------
    // Emergência
    // ------------------------------------------------------------------

    /**
     * Abre a sessão de emergência: audita a tentativa no {@code audit_log}
     * do tenant E em {@code audit_event} com o id do incidente; permitida,
     * registra a sessão no registro do incidente e agenda o aviso ao
     * tenant pela outbox antes de devolver a sessão. Negada, lança
     * {@link AccessDeniedException} (403) já auditada nos dois lugares.
     */
    public AccessSession openEmergency(SecurityIncidentRef incident, UUID tenantId,
                                       PlatformActor actor, AccessOperation operation,
                                       UUID requestId) {
        Objects.requireNonNull(requestId, "requestId é obrigatório");
        java.time.Instant now = clock.instant();
        AccessPolicy.Decision decision = AccessPolicy.evaluateEmergencyOpening(
                incident, tenantId, actor, operation, now);
        AccessAuditPort.AccessAuditEntry auditEntry = entry(tenantId, AccessMode.EMERGENCY,
                null, requestId, actor, operation, decision, null,
                incident == null ? null : incident.incidentId(), now);
        audit.appendTenantAudit(auditEntry);
        audit.appendPlatformAudit(auditEntry);
        if (!decision.allowed()) {
            throw new AccessDeniedException(decision.code(), decision.message());
        }
        AccessSession session = AccessSession.openEmergency(
                UUID.randomUUID(), tenantId, actor, incident, now);
        incidentRegistry.registerSessionInIncident(session);
        noticePort.scheduleTenantNotice(session);
        return session;
    }

    /** Autoriza (ou nega, auditando nos dois lugares) uma requisição da sessão de emergência. */
    public void authorizeEmergencyRequest(SecurityIncidentRef incident, AccessSession session,
                                          PlatformActor actor, AccessOperation operation,
                                          UUID requestId) {
        Objects.requireNonNull(requestId, "requestId é obrigatório");
        if (session == null && incident == null) {
            throw new IllegalArgumentException(
                    "requisição de emergência exige a sessão ou o incidente para auditar no tenant");
        }
        java.time.Instant now = clock.instant();
        AccessPolicy.Decision decision = AccessPolicy.evaluateEmergencyRequest(
                incident, session, actor, operation, now);
        AccessAuditPort.AccessAuditEntry auditEntry = entry(
                session == null ? incident.tenantId() : session.tenantId(),
                AccessMode.EMERGENCY, session == null ? null : session.id(), requestId, actor,
                operation, decision, null, incident == null ? null : incident.incidentId(), now);
        audit.appendTenantAudit(auditEntry);
        audit.appendPlatformAudit(auditEntry);
        if (!decision.allowed()) {
            throw new AccessDeniedException(decision.code(), decision.message());
        }
    }

    // ------------------------------------------------------------------

    private static AccessAuditPort.AccessAuditEntry entry(
            UUID tenantId, AccessMode mode, UUID sessionId, UUID requestId, PlatformActor actor,
            AccessOperation operation, AccessPolicy.Decision decision, UUID consentId,
            UUID incidentId, java.time.Instant now) {
        return new AccessAuditPort.AccessAuditEntry(tenantId, mode, sessionId, requestId,
                actor.userId(), actor.role(), operation, decision.allowed(), decision.code(),
                consentId, incidentId, now);
    }
}
