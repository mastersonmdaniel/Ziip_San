package br.com.ziipsan.core.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Portão (P3.17): TODA tentativa é auditada antes da decisão valer.
 * Dublês em memória das portas; relógio mutável de teste sobre
 * {@code Clock.fixed} por trecho — nenhum teste dorme.
 *
 * <p>Instantes à mão: abertura 2026-11-07T10:00:00Z; revogação às 10:05;
 * aviso da emergência devido 2026-11-08T10:00:00Z (conferido no ciclo de
 * vida, em {@code AccessSessionLifecycleTest}).
 */
class AccessGuardTest {

    private static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SUPPORT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID INCIDENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    /** Relógio de teste: o teste anda com ele, sem dormir. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class FakeAudit implements AccessAuditPort {
        final List<AccessAuditEntry> tenant = new ArrayList<>();
        final List<AccessAuditEntry> platform = new ArrayList<>();

        @Override
        public void appendTenantAudit(AccessAuditEntry entry) {
            tenant.add(entry);
        }

        @Override
        public void appendPlatformAudit(AccessAuditEntry entry) {
            platform.add(entry);
        }
    }

    private static final class FakeIncidentRegistry implements IncidentSessionRegistry {
        final List<AccessSession> sessions = new ArrayList<>();

        @Override
        public void registerSessionInIncident(AccessSession session) {
            sessions.add(session);
        }
    }

    private static final class FakeNotice implements EmergencyNoticePort {
        final List<AccessSession> sessions = new ArrayList<>();

        @Override
        public void scheduleTenantNotice(AccessSession session) {
            sessions.add(session);
        }
    }

    /** Auditoria que sempre falha: o portão tem de barrar o acesso (fail-hard). */
    private static final class ThrowingAudit implements AccessAuditPort {
        @Override
        public void appendTenantAudit(AccessAuditEntry entry) {
            throw new IllegalStateException("falha de auditoria simulada");
        }

        @Override
        public void appendPlatformAudit(AccessAuditEntry entry) {
            throw new IllegalStateException("falha de auditoria simulada");
        }
    }

    private record Rig(MutableClock clock, FakeAudit audit, FakeIncidentRegistry registry,
                       FakeNotice notice, AccessGuard guard) {
        static Rig at(Instant now) {
            MutableClock clock = new MutableClock(now);
            FakeAudit audit = new FakeAudit();
            FakeIncidentRegistry registry = new FakeIncidentRegistry();
            FakeNotice notice = new FakeNotice();
            return new Rig(clock, audit, registry, notice,
                    new AccessGuard(audit, registry, notice, clock));
        }
    }

    private static AssistedConsent consent() {
        return AssistedConsent.grant(UUID.randomUUID(), TENANT, TICKET,
                UUID.randomUUID(), SUPPORT, "atendimento do chamado",
                T0, Instant.parse("2026-11-07T12:00:00Z"));
    }

    private static SecurityIncidentRef declaredIncident() {
        return new SecurityIncidentRef(INCIDENT_ID, TENANT, true, T0);
    }

    @Test
    @DisplayName("assistido sem consentimento: 403 e a tentativa está no audit_log do tenant")
    void assistedDeniedWithoutConsentIsAudited() {
        Rig rig = Rig.at(T0);
        PlatformActor support = new PlatformActor(SUPPORT, "PLATFORM_SUPPORT", null);
        assertThatThrownBy(() -> rig.guard().openAssisted(
                null, TENANT, support, AccessOperation.READ, TICKET, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("sem consentimento")
                .extracting(e -> ((AccessDeniedException) e).status()).isEqualTo(403);
        assertThat(rig.audit().tenant).hasSize(1);
        AccessAuditPort.AccessAuditEntry entry = rig.audit().tenant.get(0);
        assertThat(entry.allowed()).isFalse();
        assertThat(entry.decisionCode()).isEqualTo("consent-required");
        assertThat(entry.consentId()).isNull();
        assertThat(entry.tenantId()).isEqualTo(TENANT);
        // assistido nao escreve em audit_event: essa trilha e so da emergencia
        assertThat(rig.audit().platform).isEmpty();
    }

    @Test
    @DisplayName("assistido com consentimento: sessão abre e cada requisição cai no audit_log com o consentId")
    void assistedRequestsAuditedWithConsentId() {
        Rig rig = Rig.at(T0);
        AssistedConsent consent = consent();
        PlatformActor support = new PlatformActor(SUPPORT, "PLATFORM_SUPPORT", null);
        AccessSession session = rig.guard().openAssisted(
                consent, TENANT, support, AccessOperation.READ, TICKET, UUID.randomUUID());
        rig.guard().authorizeAssistedRequest(
                consent, session, support, AccessOperation.READ, UUID.randomUUID());
        rig.guard().authorizeAssistedRequest(
                consent, session, support, AccessOperation.WRITE, UUID.randomUUID());
        assertThat(rig.audit().tenant).hasSize(3);
        assertThat(rig.audit().tenant).allSatisfy(e -> {
            assertThat(e.allowed()).isTrue();
            assertThat(e.consentId()).isEqualTo(consent.id());
            assertThat(e.detailsJson()).contains("\"consentId\":\"" + consent.id() + "\"");
        });
        // revelar documento dentro da sessão: 403 e auditado com o consentId
        assertThatThrownBy(() -> rig.guard().authorizeAssistedRequest(
                consent, session, support, AccessOperation.REVEAL_DOCUMENT, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .extracting(e -> ((AccessDeniedException) e).code())
                .isEqualTo("reveal-document-blocked");
        assertThat(rig.audit().tenant).hasSize(4);
        assertThat(rig.audit().tenant.get(3).allowed()).isFalse();
        assertThat(rig.audit().tenant.get(3).consentId()).isEqualTo(consent.id());
    }

    @Test
    @DisplayName("revogação: a requisição seguinte é negada e auditada, e a sessão tem mustEndBy = revogação + 1 min")
    void revocationDeniesNextRequest() {
        Rig rig = Rig.at(T0);
        AssistedConsent consent = consent();
        PlatformActor support = new PlatformActor(SUPPORT, "PLATFORM_SUPPORT", null);
        AccessSession session = rig.guard().openAssisted(
                consent, TENANT, support, AccessOperation.READ, TICKET, UUID.randomUUID());
        Instant revokedAt = Instant.parse("2026-11-07T10:05:00Z");
        AssistedConsent revoked = consent.revoke(revokedAt, UUID.randomUUID());
        rig.clock().set(revokedAt);
        assertThatThrownBy(() -> rig.guard().authorizeAssistedRequest(
                revoked, session, support, AccessOperation.READ, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .extracting(e -> ((AccessDeniedException) e).code()).isEqualTo("consent-revoked");
        AccessAuditPort.AccessAuditEntry denial = rig.audit().tenant.get(1);
        assertThat(denial.allowed()).isFalse();
        assertThat(denial.consentId()).isEqualTo(consent.id());
        // encerramento persistido devido 1 min depois da revogação, nem além
        AccessSession marked = session.onConsentRevoked(revokedAt);
        assertThat(marked.isTerminatedAt(Instant.parse("2026-11-07T10:06:00Z"))).isTrue();
    }

    @Test
    @DisplayName("emergência sem incidente declarado: 403 auditado no audit_log do tenant E em audit_event")
    void emergencyDeniedWithoutIncidentAuditedInBoth() {
        Rig rig = Rig.at(T0);
        PlatformActor owner = new PlatformActor(OWNER, "PLATFORM_OWNER", T0);
        SecurityIncidentRef undeclared = new SecurityIncidentRef(INCIDENT_ID, TENANT, false, null);
        assertThatThrownBy(() -> rig.guard().openEmergency(
                undeclared, TENANT, owner, AccessOperation.READ, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .extracting(e -> ((AccessDeniedException) e).code())
                .isEqualTo("incident-not-declared");
        assertThat(rig.audit().tenant).hasSize(1);
        assertThat(rig.audit().platform).hasSize(1);
        assertThat(rig.audit().tenant.get(0).incidentId()).isEqualTo(INCIDENT_ID);
        assertThat(rig.audit().platform.get(0).incidentId()).isEqualTo(INCIDENT_ID);
        assertThat(rig.audit().platform.get(0).detailsJson())
                .contains("\"incidentId\":\"" + INCIDENT_ID + "\"");
        // negada: nenhuma sessão registrada no incidente e nenhum aviso agendado
        assertThat(rig.registry().sessions).isEmpty();
        assertThat(rig.notice().sessions).isEmpty();
    }

    @Test
    @DisplayName("emergência válida: sessão registrada no incidente, aviso agendado e requisições auditadas nos dois")
    void emergencyValidFlow() {
        Rig rig = Rig.at(T0);
        PlatformActor owner = new PlatformActor(OWNER, "PLATFORM_OWNER", T0);
        AccessSession session = rig.guard().openEmergency(
                declaredIncident(), TENANT, owner, AccessOperation.READ, UUID.randomUUID());
        assertThat(session.mode()).isEqualTo(AccessMode.EMERGENCY);
        assertThat(session.incidentId()).isEqualTo(INCIDENT_ID);
        assertThat(session.noticeDueAt()).isEqualTo(Instant.parse("2026-11-08T10:00:00Z"));
        // a sessão esta no registro do incidente e o aviso ao tenant foi agendado (outbox)
        assertThat(rig.registry().sessions).containsExactly(session);
        assertThat(rig.notice().sessions).containsExactly(session);
        // leitura permitida; escrita recebe 403 — as duas auditadas nos dois lugares
        rig.guard().authorizeEmergencyRequest(
                declaredIncident(), session, owner, AccessOperation.READ, UUID.randomUUID());
        assertThatThrownBy(() -> rig.guard().authorizeEmergencyRequest(
                declaredIncident(), session, owner, AccessOperation.WRITE, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .extracting(e -> ((AccessDeniedException) e).status()).isEqualTo(403);
        assertThat(rig.audit().tenant).hasSize(3);
        assertThat(rig.audit().platform).hasSize(3);
        assertThat(rig.audit().tenant).allSatisfy(
                e -> assertThat(e.incidentId()).isEqualTo(INCIDENT_ID));
        assertThat(rig.audit().platform.get(2).decisionCode()).isEqualTo("emergency-read-only");
        assertThat(rig.audit().platform.get(2).allowed()).isFalse();
    }

    @Test
    @DisplayName("emergência sem PLATFORM_OWNER e sem MFA no momento: 403 auditado nos dois lugares")
    void emergencyDeniedForRoleAndMfaAuditedInBoth() {
        Rig rig = Rig.at(T0);
        PlatformActor support = new PlatformActor(SUPPORT, "PLATFORM_SUPPORT", T0);
        assertThatThrownBy(() -> rig.guard().openEmergency(
                declaredIncident(), TENANT, support, AccessOperation.READ, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .extracting(e -> ((AccessDeniedException) e).code())
                .isEqualTo("platform-owner-required");
        PlatformActor ownerStaleMfa = new PlatformActor(OWNER, "PLATFORM_OWNER",
                Instant.parse("2026-11-07T09:55:00Z"));
        assertThatThrownBy(() -> rig.guard().openEmergency(
                declaredIncident(), TENANT, ownerStaleMfa, AccessOperation.READ, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .extracting(e -> ((AccessDeniedException) e).code()).isEqualTo("mfa-not-current");
        assertThat(rig.audit().tenant).hasSize(2);
        assertThat(rig.audit().platform).hasSize(2);
        assertThat(rig.registry().sessions).isEmpty();
    }

    // ------------------------------------------------------------------
    // Testes acrescentados pela revisão cruzada independente
    // ------------------------------------------------------------------

    @Test
    @DisplayName("auditoria que falha barra o acesso (fail-hard): exceção, sem sessão, sem registro, sem aviso")
    void auditFailureBlocksAccess() {
        MutableClock clock = new MutableClock(T0);
        FakeIncidentRegistry registry = new FakeIncidentRegistry();
        FakeNotice notice = new FakeNotice();
        AccessGuard guard = new AccessGuard(new ThrowingAudit(), registry, notice, clock);
        PlatformActor support = new PlatformActor(SUPPORT, "PLATFORM_SUPPORT", null);
        // mesmo com consentimento vigente, sem trilha não há acesso assistido
        assertThatThrownBy(() -> guard.openAssisted(
                consent(), TENANT, support, AccessOperation.READ, TICKET, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falha de auditoria simulada");
        PlatformActor owner = new PlatformActor(OWNER, "PLATFORM_OWNER", T0);
        assertThatThrownBy(() -> guard.openEmergency(
                declaredIncident(), TENANT, owner, AccessOperation.READ, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("falha de auditoria simulada");
        assertThat(registry.sessions).isEmpty();
        assertThat(notice.sessions).isEmpty();
    }

    @Test
    @DisplayName("emergência em requisição: revelar documento e exportar bloqueados e auditados nos dois lugares")
    void emergencyRequestRevealAndExportBlocked() {
        Rig rig = Rig.at(T0);
        PlatformActor owner = new PlatformActor(OWNER, "PLATFORM_OWNER", T0);
        AccessSession session = rig.guard().openEmergency(
                declaredIncident(), TENANT, owner, AccessOperation.READ, UUID.randomUUID());
        assertThatThrownBy(() -> rig.guard().authorizeEmergencyRequest(
                declaredIncident(), session, owner, AccessOperation.REVEAL_DOCUMENT, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .extracting(e -> ((AccessDeniedException) e).code())
                .isEqualTo("reveal-document-blocked");
        assertThatThrownBy(() -> rig.guard().authorizeEmergencyRequest(
                declaredIncident(), session, owner, AccessOperation.EXPORT, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .extracting(e -> ((AccessDeniedException) e).code())
                .isEqualTo("export-blocked");
        // abertura + as duas negações, nas duas trilhas, todas com o incidentId
        assertThat(rig.audit().tenant).hasSize(3);
        assertThat(rig.audit().platform).hasSize(3);
        assertThat(rig.audit().tenant).allSatisfy(
                e -> assertThat(e.incidentId()).isEqualTo(INCIDENT_ID));
        assertThat(rig.audit().platform).allSatisfy(
                e -> assertThat(e.incidentId()).isEqualTo(INCIDENT_ID));
        assertThat(rig.audit().tenant.get(1).allowed()).isFalse();
        assertThat(rig.audit().tenant.get(2).allowed()).isFalse();
    }

    @Test
    @DisplayName("assistido em sessão encerrada: negado (session-ended) e auditado com o consentId")
    void assistedRequestOnEndedSessionDenied() {
        Rig rig = Rig.at(T0);
        AssistedConsent consent = consent();
        PlatformActor support = new PlatformActor(SUPPORT, "PLATFORM_SUPPORT", null);
        AccessSession session = rig.guard().openAssisted(
                consent, TENANT, support, AccessOperation.READ, TICKET, UUID.randomUUID());
        AccessSession ended = session.end(T0, "ATENDIMENTO_CONCLUIDO");
        assertThatThrownBy(() -> rig.guard().authorizeAssistedRequest(
                consent, ended, support, AccessOperation.READ, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class)
                .extracting(e -> ((AccessDeniedException) e).code()).isEqualTo("session-ended");
        assertThat(rig.audit().tenant).hasSize(2);
        assertThat(rig.audit().tenant.get(1).allowed()).isFalse();
        assertThat(rig.audit().tenant.get(1).decisionCode()).isEqualTo("session-ended");
        assertThat(rig.audit().tenant.get(1).consentId()).isEqualTo(consent.id());
    }
}
