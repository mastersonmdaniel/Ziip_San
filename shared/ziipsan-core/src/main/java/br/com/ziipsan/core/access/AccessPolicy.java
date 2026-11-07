package br.com.ziipsan.core.access;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Regras puras de autorização do acesso assistido e do acesso de emergência
 * (P3.17; ADR-0020). Nenhum banco, nenhum relógio da máquina: o instante
 * {@code now} vem de quem chama (nos testes, de um {@code Clock} fixo).
 *
 * <p>Assistido — nega (403) quando:
 * <ul>
 *   <li>a operação é revelar documento ({@code reveal-document-blocked}) ou
 *       exportar ({@code export-blocked}) — bloqueadas MESMO com
 *       consentimento vigente;</li>
 *   <li>não há consentimento ({@code consent-required}), ele é de outro
 *       tenant ({@code consent-tenant-mismatch}), de outro beneficiário
 *       ({@code consent-actor-mismatch}) ou de outro chamado que não o da
 *       requisição ({@code consent-scope-mismatch});</li>
 *   <li>o consentimento não está vigente em {@code now}: revogado
 *       ({@code consent-revoked} — a revogação nega desde o seu instante,
 *       dentro do teto de 1 min do encerramento), expirado
 *       ({@code consent-expired}) ou ainda não iniciado
 *       ({@code consent-not-yet-valid});</li>
 *   <li>há sessão e ela não serve: modo diverso, outro consentimento,
 *       outro ator, outro tenant ou sessão já encerrada/com encerramento
 *       devido ({@code session-invalid}/{@code session-ended}).</li>
 * </ul>
 *
 * <p>Emergência — nega (403) quando:
 * <ul>
 *   <li>a operação é escrita ({@code emergency-read-only}: qualquer escrita
 *       em emergência recebe 403), revelar documento ou exportar (mesmos
 *       códigos do assistido);</li>
 *   <li>o incidente não foi localizado ({@code incident-not-found}), é de
 *       outro tenant ({@code incident-tenant-mismatch}) ou não está
 *       declarado ({@code incident-not-declared});</li>
 *   <li>o ator não é {@code PLATFORM_OWNER}
 *       ({@code platform-owner-required});</li>
 *   <li>na ABERTURA, falta a reautenticação com MFA no momento:
 *       {@code mfaStepUpAt} nulo ({@code mfa-required}) ou diferente do
 *       instante da abertura ({@code mfa-not-current}) — "no momento" é o
 *       instante da abertura medido pelo relógio injetado; um MFA de uma
 *       requisição anterior não abre emergência nova. Nas requisições
 *       seguintes vale o {@code mfaStepUpAt} gravado na sessão, conferido
 *       contra ela, não contra o relógio;</li>
 *   <li>há sessão e ela não serve (análogo ao assistido, com o vínculo do
 *       incidente no lugar do consentimento).</li>
 * </ul>
 *
 * <p>A {@link Decision} negada carrega o código estável que o
 * {@link AccessGuard} grava na auditoria antes de lançar
 * {@link AccessDeniedException}: regra e trilha nunca divergem no motivo.
 *
 * <p><b>Lista fechada, negação por padrão</b> (correção da revisão cruzada
 * independente): as únicas operações ordinárias classificadas são
 * {@code READ} e {@code WRITE}; {@code REVEAL_DOCUMENT} e {@code EXPORT}
 * caem no bloqueio absoluto. Um valor FUTURO de {@link AccessOperation}
 * sem classificação explícita aqui é negado
 * ({@code operation-not-classified}) — nunca permitido por omissão, o que
 * numa sessão de emergência o transformaria em escrita disfarçada.
 */
public final class AccessPolicy {

    /** Decisão da política: permitida, ou negada com o código estável do motivo. */
    public record Decision(boolean allowed, String code, String message) {

        static Decision allow() {
            return new Decision(true, "allowed", "Acesso permitido.");
        }

        static Decision deny(String code, String message) {
            return new Decision(false, code, message);
        }
    }

    private AccessPolicy() {
    }

    // ------------------------------------------------------------------
    // Assistido
    // ------------------------------------------------------------------

    /**
     * Abertura assistida (ainda sem sessão): consentimento presente e
     * vigente para este tenant, este beneficiário e este chamado.
     */
    public static Decision evaluateAssistedOpening(AssistedConsent consent, UUID tenantId,
                                                   PlatformActor actor, AccessOperation operation,
                                                   UUID ticketId, Instant now) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório");
        Objects.requireNonNull(actor, "actor é obrigatório");
        Objects.requireNonNull(operation, "operation é obrigatória");
        Objects.requireNonNull(now, "now é obrigatório");
        Decision blocked = blockedOperation(operation);
        if (blocked != null) {
            return blocked;
        }
        Decision unclassified = unclassifiedOperation(operation);
        if (unclassified != null) {
            return unclassified;
        }
        return evaluateConsent(consent, tenantId, actor, ticketId, now);
    }

    /**
     * Requisição dentro de uma sessão assistida aberta: o consentimento é
     * reconferido a cada requisição (é isso que faz a revogação negar na
     * hora) e a sessão precisa ser a do consentimento, do ator e do tenant.
     */
    public static Decision evaluateAssistedRequest(AssistedConsent consent, AccessSession session,
                                                   PlatformActor actor, AccessOperation operation,
                                                   Instant now) {
        Objects.requireNonNull(actor, "actor é obrigatório");
        Objects.requireNonNull(operation, "operation é obrigatória");
        Objects.requireNonNull(now, "now é obrigatório");
        Decision blocked = blockedOperation(operation);
        if (blocked != null) {
            return blocked;
        }
        Decision unclassified = unclassifiedOperation(operation);
        if (unclassified != null) {
            return unclassified;
        }
        if (session == null) {
            return Decision.deny("session-required",
                    "Requisição assistida exige sessão aberta.");
        }
        Decision consentDecision = evaluateConsent(consent, session.tenantId(), actor,
                session.ticketId(), now);
        if (!consentDecision.allowed()) {
            return consentDecision;
        }
        if (session.mode() != AccessMode.ASSISTED || !session.consentId().equals(consent.id())) {
            return Decision.deny("session-invalid",
                    "A sessão não é a sessão assistida deste consentimento.");
        }
        if (!session.actorId().equals(actor.userId())) {
            return Decision.deny("session-invalid",
                    "A sessão assistida pertence a outro ator de plataforma.");
        }
        if (!session.isOpenAt(now)) {
            return Decision.deny("session-ended",
                    "A sessão assistida está encerrada ou com o encerramento devido.");
        }
        return Decision.allow();
    }

    private static Decision evaluateConsent(AssistedConsent consent, UUID tenantId,
                                            PlatformActor actor, UUID ticketId, Instant now) {
        if (consent == null) {
            return Decision.deny("consent-required",
                    "Acesso assistido sem consentimento do tenant.");
        }
        if (!consent.tenantId().equals(tenantId)) {
            return Decision.deny("consent-tenant-mismatch",
                    "O consentimento é de outro tenant.");
        }
        if (!consent.beneficiaryId().equals(actor.userId())) {
            return Decision.deny("consent-actor-mismatch",
                    "O consentimento foi concedido a outro atendente.");
        }
        if (consent.ticketId() != null && !consent.ticketId().equals(ticketId)) {
            return Decision.deny("consent-scope-mismatch",
                    "O consentimento é de outro chamado de suporte.");
        }
        if (consent.isRevokedAt(now)) {
            return Decision.deny("consent-revoked",
                    "O consentimento foi revogado pelo tenant.");
        }
        if (now.isBefore(consent.grantedAt())) {
            return Decision.deny("consent-not-yet-valid",
                    "O consentimento ainda não entrou em vigência.");
        }
        if (!now.isBefore(consent.expiresAt())) {
            return Decision.deny("consent-expired",
                    "O consentimento expirou.");
        }
        return Decision.allow();
    }

    // ------------------------------------------------------------------
    // Emergência
    // ------------------------------------------------------------------

    /**
     * Abertura de emergência (ainda sem sessão): incidente declarado deste
     * tenant, ator {@code PLATFORM_OWNER} e MFA reautenticado no instante
     * da abertura. Escrita já nasce negada — nem a abertura escreve.
     */
    public static Decision evaluateEmergencyOpening(SecurityIncidentRef incident, UUID tenantId,
                                                    PlatformActor actor, AccessOperation operation,
                                                    Instant now) {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório");
        Objects.requireNonNull(actor, "actor é obrigatório");
        Objects.requireNonNull(operation, "operation é obrigatória");
        Objects.requireNonNull(now, "now é obrigatório");
        Decision blocked = blockedOperation(operation);
        if (blocked != null) {
            return blocked;
        }
        Decision unclassified = unclassifiedOperation(operation);
        if (unclassified != null) {
            return unclassified;
        }
        if (operation == AccessOperation.WRITE) {
            return Decision.deny("emergency-read-only",
                    "Acesso de emergência é somente leitura: escrita recebe 403.");
        }
        Decision incidentDecision = evaluateIncident(incident, tenantId);
        if (!incidentDecision.allowed()) {
            return incidentDecision;
        }
        if (!actor.isPlatformOwner()) {
            return Decision.deny("platform-owner-required",
                    "Acesso de emergência exige PLATFORM_OWNER.");
        }
        if (actor.mfaStepUpAt() == null) {
            return Decision.deny("mfa-required",
                    "Acesso de emergência exige reautenticação com MFA no momento.");
        }
        if (!actor.mfaStepUpAt().equals(now)) {
            return Decision.deny("mfa-not-current",
                    "A reautenticação com MFA precisa ser do momento da abertura.");
        }
        return Decision.allow();
    }

    /**
     * Requisição dentro de uma sessão de emergência aberta: somente leitura,
     * sessão do próprio ator, vinculada ao incidente declarado, com o MFA
     * da abertura gravado na sessão.
     */
    public static Decision evaluateEmergencyRequest(SecurityIncidentRef incident, AccessSession session,
                                                    PlatformActor actor, AccessOperation operation,
                                                    Instant now) {
        Objects.requireNonNull(actor, "actor é obrigatório");
        Objects.requireNonNull(operation, "operation é obrigatória");
        Objects.requireNonNull(now, "now é obrigatório");
        Decision blocked = blockedOperation(operation);
        if (blocked != null) {
            return blocked;
        }
        Decision unclassified = unclassifiedOperation(operation);
        if (unclassified != null) {
            return unclassified;
        }
        if (operation == AccessOperation.WRITE) {
            return Decision.deny("emergency-read-only",
                    "Acesso de emergência é somente leitura: escrita recebe 403.");
        }
        if (session == null) {
            return Decision.deny("session-required",
                    "Requisição de emergência exige sessão aberta.");
        }
        Decision incidentDecision = evaluateIncident(incident, session.tenantId());
        if (!incidentDecision.allowed()) {
            return incidentDecision;
        }
        if (!actor.isPlatformOwner()) {
            return Decision.deny("platform-owner-required",
                    "Acesso de emergência exige PLATFORM_OWNER.");
        }
        if (session.mode() != AccessMode.EMERGENCY || !session.incidentId().equals(incident.incidentId())) {
            return Decision.deny("session-invalid",
                    "A sessão não é a sessão de emergência deste incidente.");
        }
        if (!session.actorId().equals(actor.userId())) {
            return Decision.deny("session-invalid",
                    "A sessão de emergência pertence a outro ator de plataforma.");
        }
        if (session.mfaStepUpAt() == null) {
            return Decision.deny("mfa-required",
                    "A sessão de emergência não tem a reautenticação MFA da abertura.");
        }
        if (!session.isOpenAt(now)) {
            return Decision.deny("session-ended",
                    "A sessão de emergência está encerrada.");
        }
        return Decision.allow();
    }

    private static Decision evaluateIncident(SecurityIncidentRef incident, UUID tenantId) {
        if (incident == null) {
            return Decision.deny("incident-not-found",
                    "Incidente de segurança não localizado no registro.");
        }
        if (!incident.tenantId().equals(tenantId)) {
            return Decision.deny("incident-tenant-mismatch",
                    "O incidente é de outro tenant.");
        }
        if (!incident.declared()) {
            return Decision.deny("incident-not-declared",
                    "Sem incidente de segurança declarado, não há acesso de emergência.");
        }
        return Decision.allow();
    }

    /**
     * Lista fechada: só {@code READ} e {@code WRITE} são operações ordinárias
     * classificadas. Qualquer outro valor (hoje: nenhum além dos bloqueados,
     * já tratados em {@link #blockedOperation}; amanhã: um valor novo do enum
     * ainda sem classificação) é negado por padrão.
     */
    private static Decision unclassifiedOperation(AccessOperation operation) {
        if (operation == AccessOperation.READ || operation == AccessOperation.WRITE) {
            return null;
        }
        return Decision.deny("operation-not-classified",
                "Operação fora da lista fechada de operações classificadas: negada por padrão.");
    }

    /** Operações bloqueadas nos dois modos, mesmo com consentimento ou emergência válida. */
    private static Decision blockedOperation(AccessOperation operation) {
        if (operation == AccessOperation.REVEAL_DOCUMENT) {
            return Decision.deny("reveal-document-blocked",
                    "Revelar documento é bloqueado no acesso assistido e no de emergência.");
        }
        if (operation == AccessOperation.EXPORT) {
            return Decision.deny("export-blocked",
                    "Exportar é bloqueado no acesso assistido e no de emergência.");
        }
        return null;
    }
}
