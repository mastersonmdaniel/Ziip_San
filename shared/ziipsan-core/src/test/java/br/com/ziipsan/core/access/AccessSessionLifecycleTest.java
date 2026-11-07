package br.com.ziipsan.core.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ciclo de vida da sessão (P3.17): os dois prazos do aceite, calculados à
 * mão — abertura em 2026-11-07T10:00:00Z; revogação às 10:05 fecha a
 * sessão até às 10:06 (1 min); aviso da emergência das 10:00 de 07/11
 * vence às 10:00 de 08/11 (24 h).
 */
class AccessSessionLifecycleTest {

    private static final Instant T0 = Instant.parse("2026-11-07T10:00:00Z");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SUPPORT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID INCIDENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private static AssistedConsent consent() {
        return AssistedConsent.grant(UUID.randomUUID(), TENANT, null,
                UUID.randomUUID(), SUPPORT, "diagnóstico acompanhado",
                T0, Instant.parse("2026-11-07T12:00:00Z"));
    }

    private static AccessSession assistedSession() {
        return AccessSession.openAssisted(UUID.randomUUID(), TENANT,
                new PlatformActor(SUPPORT, "PLATFORM_SUPPORT", null), consent(), T0);
    }

    private static AccessSession emergencySession() {
        return AccessSession.openEmergency(UUID.randomUUID(), TENANT,
                new PlatformActor(OWNER, "PLATFORM_OWNER", T0),
                new SecurityIncidentRef(INCIDENT_ID, TENANT, true, T0), T0);
    }

    @Test
    @DisplayName("revogação: mustEndBy = revogação + 1 min; encerramento devido exatamente nesse instante")
    void revocationClosesSessionWithinOneMinute() {
        Instant revokedAt = Instant.parse("2026-11-07T10:05:00Z");
        AccessSession marked = assistedSession().onConsentRevoked(revokedAt);
        assertThat(marked.mustEndBy()).isEqualTo(Instant.parse("2026-11-07T10:06:00Z"));
        assertThat(AccessSession.REVOCATION_CLOSE_LIMIT).isEqualTo(Duration.ofMinutes(1));
        // 10:05:59 — ainda não devido o encerramento persistido (o portão já nega: ver AccessPolicyTest)
        assertThat(marked.isTerminatedAt(Instant.parse("2026-11-07T10:05:59Z"))).isFalse();
        // 10:06:00 — devido: 1 min depois da revogação, nem um segundo além do teto
        assertThat(marked.isTerminatedAt(Instant.parse("2026-11-07T10:06:00Z"))).isTrue();
        assertThat(marked.isOpenAt(Instant.parse("2026-11-07T10:06:00Z"))).isFalse();
    }

    @Test
    @DisplayName("aviso da emergência: noticeDueAt = abertura + 24 h; vencido só depois, e envio no prazo cumpre")
    void emergencyNoticeWithinTwentyFourHours() {
        AccessSession session = emergencySession();
        assertThat(session.noticeDueAt()).isEqualTo(Instant.parse("2026-11-08T10:00:00Z"));
        assertThat(AccessSession.EMERGENCY_NOTICE_LIMIT).isEqualTo(Duration.ofHours(24));
        // no instante exato do prazo ainda não venceu; um segundo depois, venceu
        assertThat(session.isNoticeOverdue(Instant.parse("2026-11-08T10:00:00Z"))).isFalse();
        assertThat(session.isNoticeOverdue(Instant.parse("2026-11-08T10:00:01Z"))).isTrue();
        // envio registrado às 10:30 do mesmo dia: dentro das 24 h
        AccessSession notified = session.withNoticeSentAt(Instant.parse("2026-11-07T10:30:00Z"));
        assertThat(notified.isNoticeOnTime()).isTrue();
        assertThat(notified.isNoticeOverdue(Instant.parse("2026-11-09T10:00:00Z"))).isFalse();
        // envio um segundo depois do prazo nao cumpre
        AccessSession late = session.withNoticeSentAt(Instant.parse("2026-11-08T10:00:01Z"));
        assertThat(late.isNoticeOnTime()).isFalse();
    }

    @Test
    @DisplayName("vínculos fechados: assistida sem consentimento e emergência sem incidente/OWNER/MFA não nascem")
    void sessionLinkageInvariants() {
        assertThatThrownBy(() -> new AccessSession(UUID.randomUUID(), TENANT, AccessMode.ASSISTED,
                SUPPORT, "PLATFORM_SUPPORT", null, null, null, T0, null,
                null, null, null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("consentId");
        assertThatThrownBy(() -> new AccessSession(UUID.randomUUID(), TENANT, AccessMode.EMERGENCY,
                SUPPORT, "PLATFORM_SUPPORT", null, INCIDENT_ID, null, T0, T0,
                null, null, null, T0.plus(Duration.ofHours(24)), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLATFORM_OWNER");
        assertThatThrownBy(() -> new AccessSession(UUID.randomUUID(), TENANT, AccessMode.EMERGENCY,
                OWNER, "PLATFORM_OWNER", null, INCIDENT_ID, null, T0, T0,
                null, null, null, T0.plus(Duration.ofHours(23)), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("24 h");
    }

    @Test
    @DisplayName("JSON da sessão e do consentimento saem pela classe Json do core, com os ids de vínculo")
    void jsonCarriesLinkageIds() {
        AssistedConsent consent = consent();
        AccessSession assisted = AccessSession.openAssisted(
                UUID.fromString("00000000-0000-0000-0000-0000000000e1"), TENANT,
                new PlatformActor(SUPPORT, "PLATFORM_SUPPORT", null), consent, T0);
        assertThat(assisted.toJson()).contains(
                "\"consentId\":\"" + consent.id() + "\"", "\"mode\":\"ASSISTED\"");
        assertThat(consent.toJson()).contains("\"scope\":\"diagnóstico acompanhado\"");
        AccessSession emergency = emergencySession();
        assertThat(emergency.toJson()).contains(
                "\"incidentId\":\"" + INCIDENT_ID + "\"",
                "\"noticeDueAt\":\"2026-11-08T10:00:00Z\"");
        // payload do aviso (outbox): sem dado pessoal, so ids e instantes
        String payload = AccessSessionService.emergencyNoticePayload(emergency);
        assertThat(payload).contains("\"incidentId\":\"" + INCIDENT_ID + "\"");
        assertThat(AccessSessionService.emergencyNoticeDedupKey(emergency))
                .isEqualTo("emergency-access-notice:" + emergency.id());
    }

    @Test
    @DisplayName("consentimento sem escopo, sem fim ou revogado duas vezes: recusado")
    void consentInvariants() {
        assertThatThrownBy(() -> AssistedConsent.grant(UUID.randomUUID(), TENANT, null,
                UUID.randomUUID(), SUPPORT, " ", T0, T0.plus(Duration.ofHours(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope");
        assertThatThrownBy(() -> AssistedConsent.grant(UUID.randomUUID(), TENANT, null,
                UUID.randomUUID(), SUPPORT, "escopo", T0, T0))
                .isInstanceOf(IllegalArgumentException.class);
        AssistedConsent revoked = consent().revoke(T0.plus(Duration.ofMinutes(5)), UUID.randomUUID());
        assertThatThrownBy(() -> revoked.revoke(T0.plus(Duration.ofMinutes(6)), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }
}
