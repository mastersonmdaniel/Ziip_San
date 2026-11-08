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
 * P3.8 — casos do pentest local de acesso assistido e de emergencia
 * contra o portao REAL da P3.17 (mesmo pacote do core, onde vivem as
 * portas package-private): toda negacao e auditada ANTES de valer, e a
 * emergencia exige incidente declarado + PLATFORM_OWNER + MFA no momento.
 * Instantes a mao, relogio injetado — nenhum teste dorme.
 */
class HomologationSecurityAccessTest {

    private static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SUPPORT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID INCIDENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void set(Instant instant) { this.now = instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final class FakeAudit implements AccessAuditPort {
        final List<AccessAuditEntry> tenant = new ArrayList<>();
        final List<AccessAuditEntry> platform = new ArrayList<>();
        @Override public void appendTenantAudit(AccessAuditEntry entry) { tenant.add(entry); }
        @Override public void appendPlatformAudit(AccessAuditEntry entry) { platform.add(entry); }
    }

    private static final class FakeRegistry implements IncidentSessionRegistry {
        final List<AccessSession> sessions = new ArrayList<>();
        @Override public void registerSessionInIncident(AccessSession session) { sessions.add(session); }
    }

    private static final class FakeNotice implements EmergencyNoticePort {
        final List<AccessSession> sessions = new ArrayList<>();
        @Override public void scheduleTenantNotice(AccessSession session) { sessions.add(session); }
    }

    private record Rig(MutableClock clock, FakeAudit audit, FakeRegistry registry, FakeNotice notice, AccessGuard guard) {
        static Rig at(Instant now) {
            MutableClock clock = new MutableClock(now);
            FakeAudit audit = new FakeAudit();
            FakeRegistry registry = new FakeRegistry();
            FakeNotice notice = new FakeNotice();
            return new Rig(clock, audit, registry, notice, new AccessGuard(audit, registry, notice, clock));
        }
    }

    private static AssistedConsent consent() {
        return AssistedConsent.grant(UUID.randomUUID(), TENANT, TICKET, UUID.randomUUID(), SUPPORT,
                "atendimento do chamado", T0, Instant.parse("2026-11-07T12:00:00Z"));
    }

    @Test
    @DisplayName("SEC assistido: sem consentimento, negado (403) e auditado; revogado, a proxima requisicao cai")
    void assistidoNegadoEAuditado() {
        Rig rig = Rig.at(T0);
        PlatformActor support = new PlatformActor(SUPPORT, "PLATFORM_SUPPORT", null);
        assertThatThrownBy(() -> rig.guard().openAssisted(null, TENANT, support,
                AccessOperation.READ, TICKET, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(rig.audit().tenant).hasSize(1);
        assertThat(rig.audit().tenant.get(0).allowed()).isFalse();

        AssistedConsent consent = consent();
        AccessSession session = rig.guard().openAssisted(consent, TENANT, support,
                AccessOperation.READ, TICKET, UUID.randomUUID());
        AssistedConsent revoked = consent.revoke(Instant.parse("2026-11-07T10:05:00Z"), UUID.randomUUID());
        rig.clock().set(Instant.parse("2026-11-07T10:05:00Z"));
        assertThatThrownBy(() -> rig.guard().authorizeAssistedRequest(revoked, session, support,
                AccessOperation.READ, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(rig.audit().tenant).allSatisfy(e -> assertThat(e.consentId())
                .isIn(null, consent.id()));
        // Correcao D7: a negacao pos-revogacao e asseverada na ENTRADA NOVA
        // (tamanho da trilha + ultima entrada negada), nao so no conjunto.
        assertThat(rig.audit().tenant).hasSize(3);
        var postRevocation = rig.audit().tenant.get(2);
        assertThat(postRevocation.allowed()).isFalse();
        assertThat(postRevocation.operation()).isEqualTo(AccessOperation.READ);
        assertThat(postRevocation.consentId()).isEqualTo(consent.id());
        // Revelar documento e exportar: bloqueados MESMO com consentimento vigente.
        Rig rig2 = Rig.at(T0);
        AssistedConsent consent2 = consent();
        AccessSession session2 = rig2.guard().openAssisted(consent2, TENANT, support,
                AccessOperation.READ, TICKET, UUID.randomUUID());
        assertThatThrownBy(() -> rig2.guard().authorizeAssistedRequest(consent2, session2, support,
                AccessOperation.EXPORT, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> rig2.guard().authorizeAssistedRequest(consent2, session2, support,
                AccessOperation.REVEAL_DOCUMENT, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        // As duas negacoes entraram na trilha, negadas, com a operacao certa.
        assertThat(rig2.audit().tenant).hasSize(3);
        assertThat(rig2.audit().tenant.get(1).operation()).isEqualTo(AccessOperation.EXPORT);
        assertThat(rig2.audit().tenant.get(1).allowed()).isFalse();
        assertThat(rig2.audit().tenant.get(2).operation()).isEqualTo(AccessOperation.REVEAL_DOCUMENT);
        assertThat(rig2.audit().tenant.get(2).allowed()).isFalse();
        assertThat(rig2.audit().tenant.get(2).consentId()).isEqualTo(consent2.id());
    }

    @Test
    @DisplayName("SEC emergencia: sem incidente declarado, sem OWNER ou sem MFA no momento — negado e auditado nos dois trilhos")
    void emergenciaNegadaEAuditada() {
        SecurityIncidentRef undeclared = new SecurityIncidentRef(INCIDENT_ID, TENANT, false, null);
        SecurityIncidentRef declared = new SecurityIncidentRef(INCIDENT_ID, TENANT, true, T0);

        Rig r1 = Rig.at(T0);
        PlatformActor owner = new PlatformActor(OWNER, "PLATFORM_OWNER", T0);
        assertThatThrownBy(() -> r1.guard().openEmergency(undeclared, TENANT, owner,
                AccessOperation.READ, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(r1.audit().tenant).hasSize(1);
        assertThat(r1.audit().platform).hasSize(1);
        assertThat(r1.registry().sessions).isEmpty();
        assertThat(r1.notice().sessions).isEmpty();

        Rig r2 = Rig.at(T0);
        PlatformActor supportNotOwner = new PlatformActor(SUPPORT, "PLATFORM_SUPPORT", T0);
        assertThatThrownBy(() -> r2.guard().openEmergency(declared, TENANT, supportNotOwner,
                AccessOperation.READ, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        // D7: auditoria asseverada tambem em r2/r3 — negacao nos DOIS trilhos.
        assertThat(r2.audit().tenant).hasSize(1);
        assertThat(r2.audit().platform).hasSize(1);
        assertThat(r2.audit().tenant.get(0).allowed()).isFalse();
        assertThat(r2.audit().platform.get(0).allowed()).isFalse();
        assertThat(r2.audit().platform.get(0).incidentId()).isEqualTo(INCIDENT_ID);
        assertThat(r2.registry().sessions).isEmpty();

        Rig r3 = Rig.at(T0);
        PlatformActor ownerStaleMfa = new PlatformActor(OWNER, "PLATFORM_OWNER",
                Instant.parse("2026-11-07T09:00:00Z"));
        assertThatThrownBy(() -> r3.guard().openEmergency(declared, TENANT, ownerStaleMfa,
                AccessOperation.READ, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(r3.audit().tenant).hasSize(1);
        assertThat(r3.audit().platform).hasSize(1);
        assertThat(r3.audit().tenant.get(0).allowed()).isFalse();
        assertThat(r3.audit().platform.get(0).allowed()).isFalse();
        assertThat(r3.audit().platform.get(0).incidentId()).isEqualTo(INCIDENT_ID);
        assertThat(r3.registry().sessions).isEmpty();
    }

    @Test
    @DisplayName("SEC emergencia valida abre sessao registrada e com aviso agendado; escrita e revelar documento dentro dela recebem 403")
    void emergenciaEscritaBloqueada() {
        Rig rig = Rig.at(T0);
        SecurityIncidentRef declared = new SecurityIncidentRef(INCIDENT_ID, TENANT, true, T0);
        PlatformActor owner = new PlatformActor(OWNER, "PLATFORM_OWNER", T0);
        AccessSession session = rig.guard().openEmergency(declared, TENANT, owner,
                AccessOperation.READ, UUID.randomUUID());
        assertThat(rig.registry().sessions).containsExactly(session);
        assertThat(rig.notice().sessions).containsExactly(session);
        assertThatThrownBy(() -> rig.guard().authorizeEmergencyRequest(declared, session, owner,
                AccessOperation.WRITE, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        // Simetria com o assistido (D7): revelar documento tambem e bloqueado
        // na emergencia, mesmo com incidente declarado e sessao valida.
        assertThatThrownBy(() -> rig.guard().authorizeEmergencyRequest(declared, session, owner,
                AccessOperation.REVEAL_DOCUMENT, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(rig.audit().platform).allSatisfy(e -> assertThat(e.incidentId()).isEqualTo(INCIDENT_ID));
        // Abertura permitida + as duas negacoes, nos dois trilhos.
        assertThat(rig.audit().tenant).hasSize(3);
        assertThat(rig.audit().platform).hasSize(3);
        assertThat(rig.audit().platform.get(0).allowed()).isTrue();
        assertThat(rig.audit().platform.get(1).operation()).isEqualTo(AccessOperation.WRITE);
        assertThat(rig.audit().platform.get(1).allowed()).isFalse();
        assertThat(rig.audit().platform.get(2).operation()).isEqualTo(AccessOperation.REVEAL_DOCUMENT);
        assertThat(rig.audit().platform.get(2).allowed()).isFalse();
    }
}
