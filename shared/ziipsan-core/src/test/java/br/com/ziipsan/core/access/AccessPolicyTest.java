package br.com.ziipsan.core.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regras puras do acesso assistido e de emergência (P3.17; ADR-0020).
 * Instantes conferidos à mão: abertura em 2026-11-07T10:00:00Z (sábado);
 * consentimento vigente das 10:00 às 12:00 do mesmo dia.
 */
class AccessPolicyTest {

    private static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID SUPPORT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID INCIDENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private static AssistedConsent consent() {
        return AssistedConsent.grant(UUID.randomUUID(), TENANT, TICKET,
                UUID.randomUUID(), SUPPORT, "atendimento do chamado",
                T0, Instant.parse("2026-11-07T12:00:00Z"));
    }

    private static PlatformActor support() {
        return new PlatformActor(SUPPORT, "PLATFORM_SUPPORT", null);
    }

    private static PlatformActor ownerWithMfaAt(Instant mfaAt) {
        return new PlatformActor(OWNER, "PLATFORM_OWNER", mfaAt);
    }

    private static SecurityIncidentRef declaredIncident() {
        return new SecurityIncidentRef(INCIDENT_ID, TENANT, true, T0);
    }

    // ------------------------------------------------------------------
    // Assistido
    // ------------------------------------------------------------------

    @Test
    @DisplayName("assistido sem consentimento: negado (consent-required)")
    void assistedWithoutConsentDenied() {
        AccessPolicy.Decision d = AccessPolicy.evaluateAssistedOpening(
                null, TENANT, support(), AccessOperation.READ, TICKET, T0);
        assertThat(d.allowed()).isFalse();
        assertThat(d.code()).isEqualTo("consent-required");
    }

    @Test
    @DisplayName("assistido com consentimento vigente para o ator, o tenant e o chamado: permitido")
    void assistedWithValidConsentAllowed() {
        AccessPolicy.Decision d = AccessPolicy.evaluateAssistedOpening(
                consent(), TENANT, support(), AccessOperation.READ, TICKET, T0);
        assertThat(d.allowed()).isTrue();
        // Escrita de suporte dentro do consentimento nao e vedada pelo aceite:
        // as vedacoes sao revelar documento e exportar (testes abaixo).
        assertThat(AccessPolicy.evaluateAssistedOpening(
                consent(), TENANT, support(), AccessOperation.WRITE, TICKET, T0).allowed()).isTrue();
    }

    @Test
    @DisplayName("consentimento de outro tenant, de outro beneficiário ou de outro chamado: negado")
    void assistedConsentMismatchDenied() {
        assertThat(AccessPolicy.evaluateAssistedOpening(
                consent(), OTHER_TENANT, support(), AccessOperation.READ, TICKET, T0).code())
                .isEqualTo("consent-tenant-mismatch");
        PlatformActor otherSupport = new PlatformActor(UUID.randomUUID(), "PLATFORM_SUPPORT", null);
        assertThat(AccessPolicy.evaluateAssistedOpening(
                consent(), TENANT, otherSupport, AccessOperation.READ, TICKET, T0).code())
                .isEqualTo("consent-actor-mismatch");
        assertThat(AccessPolicy.evaluateAssistedOpening(
                consent(), TENANT, support(), AccessOperation.READ, UUID.randomUUID(), T0).code())
                .isEqualTo("consent-scope-mismatch");
    }

    @Test
    @DisplayName("consentimento revogado nega desde o instante da revogação; expirado e futuro também negam")
    void assistedConsentNotActiveDenied() {
        AssistedConsent revoked = consent().revoke(Instant.parse("2026-11-07T10:05:00Z"), UUID.randomUUID());
        // 10:04:59 — antes da revogação, ainda vigente
        assertThat(AccessPolicy.evaluateAssistedOpening(revoked, TENANT, support(),
                AccessOperation.READ, TICKET, Instant.parse("2026-11-07T10:04:59Z")).allowed()).isTrue();
        // 10:05:00 — no instante da revogação, já negado (dentro do teto de 1 min)
        AccessPolicy.Decision atRevocation = AccessPolicy.evaluateAssistedOpening(revoked, TENANT,
                support(), AccessOperation.READ, TICKET, Instant.parse("2026-11-07T10:05:00Z"));
        assertThat(atRevocation.allowed()).isFalse();
        assertThat(atRevocation.code()).isEqualTo("consent-revoked");
        // expirado: 12:00 é o fim da vigência [10:00, 12:00)
        assertThat(AccessPolicy.evaluateAssistedOpening(consent(), TENANT, support(),
                AccessOperation.READ, TICKET, Instant.parse("2026-11-07T12:00:00Z")).code())
                .isEqualTo("consent-expired");
        // ainda não vigente: 09:59:59
        assertThat(AccessPolicy.evaluateAssistedOpening(consent(), TENANT, support(),
                AccessOperation.READ, TICKET, Instant.parse("2026-11-07T09:59:59Z")).code())
                .isEqualTo("consent-not-yet-valid");
    }

    @Test
    @DisplayName("revelar documento e exportar: bloqueados mesmo com consentimento vigente")
    void assistedRevealAndExportBlocked() {
        assertThat(AccessPolicy.evaluateAssistedOpening(consent(), TENANT, support(),
                AccessOperation.REVEAL_DOCUMENT, TICKET, T0).code())
                .isEqualTo("reveal-document-blocked");
        assertThat(AccessPolicy.evaluateAssistedOpening(consent(), TENANT, support(),
                AccessOperation.EXPORT, TICKET, T0).code())
                .isEqualTo("export-blocked");
    }

    // ------------------------------------------------------------------
    // Emergência
    // ------------------------------------------------------------------

    @Test
    @DisplayName("emergência sem incidente localizado ou sem declaração: negada")
    void emergencyWithoutDeclaredIncidentDenied() {
        assertThat(AccessPolicy.evaluateEmergencyOpening(
                null, TENANT, ownerWithMfaAt(T0), AccessOperation.READ, T0).code())
                .isEqualTo("incident-not-found");
        SecurityIncidentRef undeclared = new SecurityIncidentRef(INCIDENT_ID, TENANT, false, null);
        assertThat(AccessPolicy.evaluateEmergencyOpening(
                undeclared, TENANT, ownerWithMfaAt(T0), AccessOperation.READ, T0).code())
                .isEqualTo("incident-not-declared");
        SecurityIncidentRef otherTenant = new SecurityIncidentRef(INCIDENT_ID, OTHER_TENANT, true, T0);
        assertThat(AccessPolicy.evaluateEmergencyOpening(
                otherTenant, TENANT, ownerWithMfaAt(T0), AccessOperation.READ, T0).code())
                .isEqualTo("incident-tenant-mismatch");
    }

    @Test
    @DisplayName("emergência sem PLATFORM_OWNER: negada, mesmo com incidente declarado e MFA")
    void emergencyWithoutOwnerDenied() {
        PlatformActor supportOwner = new PlatformActor(SUPPORT, "PLATFORM_SUPPORT", T0);
        assertThat(AccessPolicy.evaluateEmergencyOpening(
                declaredIncident(), TENANT, supportOwner, AccessOperation.READ, T0).code())
                .isEqualTo("platform-owner-required");
        PlatformActor ops = new PlatformActor(SUPPORT, "PLATFORM_OPS", T0);
        assertThat(AccessPolicy.evaluateEmergencyOpening(
                declaredIncident(), TENANT, ops, AccessOperation.READ, T0).allowed()).isFalse();
    }

    @Test
    @DisplayName("emergência sem MFA ou com MFA de outro momento: negada; com MFA no momento: permitida")
    void emergencyMfaAtTheMoment() {
        assertThat(AccessPolicy.evaluateEmergencyOpening(
                declaredIncident(), TENANT, ownerWithMfaAt(null), AccessOperation.READ, T0).code())
                .isEqualTo("mfa-required");
        // MFA de 5 minutos antes nao e "no momento" da abertura das 10:00
        assertThat(AccessPolicy.evaluateEmergencyOpening(
                declaredIncident(), TENANT, ownerWithMfaAt(Instant.parse("2026-11-07T09:55:00Z")),
                AccessOperation.READ, T0).code())
                .isEqualTo("mfa-not-current");
        assertThat(AccessPolicy.evaluateEmergencyOpening(
                declaredIncident(), TENANT, ownerWithMfaAt(T0), AccessOperation.READ, T0).allowed())
                .isTrue();
    }

    @Test
    @DisplayName("emergência: qualquer escrita recebe 403 (emergency-read-only), revelar e exportar bloqueados")
    void emergencyWriteForbidden() {
        assertThat(AccessPolicy.evaluateEmergencyOpening(
                declaredIncident(), TENANT, ownerWithMfaAt(T0), AccessOperation.WRITE, T0).code())
                .isEqualTo("emergency-read-only");
        assertThat(AccessPolicy.evaluateEmergencyOpening(
                declaredIncident(), TENANT, ownerWithMfaAt(T0),
                AccessOperation.REVEAL_DOCUMENT, T0).code())
                .isEqualTo("reveal-document-blocked");
        assertThat(AccessPolicy.evaluateEmergencyOpening(
                declaredIncident(), TENANT, ownerWithMfaAt(T0), AccessOperation.EXPORT, T0).code())
                .isEqualTo("export-blocked");
    }

    @Test
    @DisplayName("papel fora do domínio da P0.43 é dado inválido, nunca um papel presumido")
    void unknownPlatformRoleRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new PlatformActor(SUPPORT, "ADMIN", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P0.43");
    }
}
