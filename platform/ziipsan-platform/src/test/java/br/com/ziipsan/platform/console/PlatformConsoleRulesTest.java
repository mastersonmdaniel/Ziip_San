package br.com.ziipsan.platform.console;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras puras do console (P1.6), sem banco. */
class PlatformConsoleRulesTest {

    @Test
    @DisplayName("OWNER suspende por qualquer motivo; FINANCE só por inadimplência contratual; demais, nunca")
    void suspensionMatrix() {
        for (String reason : PlatformConsoleRules.SUSPENSION_REASONS) {
            assertThat(PlatformConsoleRules.canSuspend("PLATFORM_OWNER", reason)).isTrue();
        }
        assertThat(PlatformConsoleRules.canSuspend("PLATFORM_FINANCE", "CONTRACT_DEFAULT")).isTrue();
        assertThat(PlatformConsoleRules.canSuspend("PLATFORM_FINANCE", "REGULATORY_ORDER")).isFalse();
        assertThat(PlatformConsoleRules.canSuspend("PLATFORM_FINANCE", "FRAUD_PREVENTION")).isFalse();
        assertThat(PlatformConsoleRules.canSuspend("PLATFORM_FINANCE", "OTHER")).isFalse();
        assertThat(PlatformConsoleRules.canSuspend("PLATFORM_OPS", "CONTRACT_DEFAULT")).isFalse();
        assertThat(PlatformConsoleRules.canSuspend("PLATFORM_SUPPORT", "CONTRACT_DEFAULT")).isFalse();
        assertThat(PlatformConsoleRules.canSuspend("PLATFORM_OWNER", "INVENTADO")).isFalse();
        assertThat(PlatformConsoleRules.canSuspend("PLATFORM_OWNER", null)).isFalse();
    }

    @Test
    @DisplayName("eliminação só por OWNER; hold (fail-safe) por OWNER ou OPS")
    void deletionMatrix() {
        assertThat(PlatformConsoleRules.canDelete("PLATFORM_OWNER")).isTrue();
        assertThat(PlatformConsoleRules.canDelete("PLATFORM_FINANCE")).isFalse();
        assertThat(PlatformConsoleRules.canDelete("PLATFORM_OPS")).isFalse();
        assertThat(PlatformConsoleRules.canDelete("PLATFORM_SUPPORT")).isFalse();
        assertThat(PlatformConsoleRules.canHoldDeletion("PLATFORM_OWNER")).isTrue();
        assertThat(PlatformConsoleRules.canHoldDeletion("PLATFORM_OPS")).isTrue();
        assertThat(PlatformConsoleRules.canHoldDeletion("PLATFORM_FINANCE")).isFalse();
        assertThat(PlatformConsoleRules.canHoldDeletion("PLATFORM_SUPPORT")).isFalse();
    }

    @Test
    @DisplayName("carência de 30 dias; HELD nunca completa, mesmo com a carência vencida")
    void deletionGrace() {
        Instant requested = Instant.parse("2026-11-03T10:00:00Z");
        Instant grace = PlatformConsoleRules.graceUntil(requested);
        assertThat(grace).isEqualTo(Instant.parse("2026-12-03T10:00:00Z"));
        assertThat(PlatformConsoleRules.deletionMayComplete("DELETION_GRACE",
                Instant.parse("2026-12-03T09:59:59Z"), grace)).isFalse();
        assertThat(PlatformConsoleRules.deletionMayComplete("DELETION_GRACE",
                Instant.parse("2026-12-03T10:00:00Z"), grace)).isTrue();
        assertThat(PlatformConsoleRules.deletionMayComplete("DELETION_HELD",
                Instant.parse("2027-01-01T00:00:00Z"), grace)).isFalse();
        assertThat(PlatformConsoleRules.deletionMayComplete("DELETED",
                Instant.parse("2027-01-01T00:00:00Z"), grace)).isFalse();
    }

    @Test
    @DisplayName("liberar o hold retoma a carência de onde parou, sem zerar nem estender")
    void holdReleaseResumesGrace() {
        Instant grace = Instant.parse("2026-12-03T10:00:00Z");
        Instant holdAt = Instant.parse("2026-11-13T10:00:00Z");
        Instant released = Instant.parse("2026-11-20T10:00:00Z");
        assertThat(PlatformConsoleRules.graceUntilAfterHoldRelease(grace, holdAt, released))
                .isEqualTo(Instant.parse("2026-12-10T10:00:00Z"));
    }

    @Test
    @DisplayName("percentual do checklist: 0 sem passos, 100 com todos, sem arredondar para cima")
    void onboardingPercent() {
        assertThat(PlatformConsoleRules.onboardingPercent(0, 0)).isZero();
        assertThat(PlatformConsoleRules.onboardingPercent(0, 9)).isZero();
        assertThat(PlatformConsoleRules.onboardingPercent(3, 9)).isEqualTo(33);
        assertThat(PlatformConsoleRules.onboardingPercent(9, 9)).isEqualTo(100);
        assertThat(PlatformConsoleRules.onboardingPercent(10, 9)).isEqualTo(100);
    }

    @Test
    @DisplayName("gate de produção: checklist 100% + perfil + nenhuma versão aprovada sem aceite")
    void productionGate() {
        assertThat(PlatformConsoleRules.productionReady(9, 9, true, 0)).isTrue();
        assertThat(PlatformConsoleRules.productionReady(8, 9, true, 0)).isFalse();
        assertThat(PlatformConsoleRules.productionReady(9, 9, false, 0)).isFalse();
        assertThat(PlatformConsoleRules.productionReady(9, 9, true, 1)).isFalse();
        assertThat(PlatformConsoleRules.productionReady(0, 0, true, 0)).isFalse();
    }

    @Test
    @DisplayName("alerta de risco: só classe b fora do padrão; classe a não gera alerta (é recusada antes)")
    void riskAlert() {
        assertThat(PlatformConsoleRules.requiresRiskAlert("b", "2", "3")).isTrue();
        assertThat(PlatformConsoleRules.requiresRiskAlert("b", "2", "2")).isFalse();
        assertThat(PlatformConsoleRules.requiresRiskAlert("a", "2", "3")).isFalse();
        assertThat(PlatformConsoleRules.requiresRiskAlert("c", "2", "3")).isFalse();
        assertThat(PlatformConsoleRules.requiresRiskAlert("d", "2", "3")).isFalse();
        assertThat(PlatformConsoleRules.requiresRiskAlert("b", null, "3")).isFalse();
    }

    @Test
    @DisplayName("hashes: 64 hex minúsculos, determinísticos, e o certificado muda com o tenant")
    void hashes() {
        String hash = PlatformConsoleRules.contentHash("sst.min_crew_confined_space", "2",
                LocalDate.parse("2031-01-01"), null);
        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(PlatformConsoleRules.contentHash("sst.min_crew_confined_space", "2",
                LocalDate.parse("2031-01-01"), null)).isEqualTo(hash);
        assertThat(PlatformConsoleRules.contentHash("sst.min_crew_confined_space", "3",
                LocalDate.parse("2031-01-01"), null)).isNotEqualTo(hash);
        String certA = PlatformConsoleRules.deletionCertificateHash("tenant-a", Instant.parse("2026-11-03T10:00:00Z"));
        String certB = PlatformConsoleRules.deletionCertificateHash("tenant-b", Instant.parse("2026-11-03T10:00:00Z"));
        assertThat(certA).matches("[0-9a-f]{64}");
        assertThat(certB).isNotEqualTo(certA);
    }
}
