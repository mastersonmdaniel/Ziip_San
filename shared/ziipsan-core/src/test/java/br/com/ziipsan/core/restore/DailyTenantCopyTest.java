package br.com.ziipsan.core.restore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes puros da copia logica diaria (P3.33): retencao do RNF-43
 * calculada a mao — captura em 2027-01-01T03:00Z expira em
 * 2027-02-05T03:00Z (35 dias corridos) — e o manifesto que alimenta a
 * P3.16 e a ED-04.
 */
class DailyTenantCopyTest {

    private static final Instant CAPTURED = Instant.parse("2027-01-01T03:00:00Z");

    private static DailyTenantCopy.CopyManifest manifest() {
        return new DailyTenantCopy.CopyManifest(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "tenant_abc",
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                CAPTURED,
                "copias/tenant_abc/2027-01-01.dump",
                "d".repeat(64),
                123456L);
    }

    @Test
    @DisplayName("expiracao = captura + 35 dias (teto do RNF-43), nem um dia a mais")
    void retentionIsRnf43Cap() {
        assertThat(DailyTenantCopy.RETENTION.toDays()).isEqualTo(35);
        assertThat(manifest().expiresAt()).isEqualTo(Instant.parse("2027-02-05T03:00:00Z"));
    }

    @Test
    @DisplayName("copia vencida nao e fonte de restauracao; no instante exato da expiracao ja venceu")
    void expiredCopyIsNotUsable() {
        DailyTenantCopy.CopyManifest m = manifest();
        assertThat(m.isUsableAsRestoreSource(Instant.parse("2027-02-05T02:59:59Z"))).isTrue();
        assertThat(m.isUsableAsRestoreSource(Instant.parse("2027-02-05T03:00:00Z"))).isFalse();
        assertThat(m.isUsableAsRestoreSource(Instant.parse("2027-03-01T00:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("manifesto JSON (via Json do core): expiracao, consumidores P3.16/ED-04 e crypto-shredding pendente da LC.5 declarados")
    void manifestDeclaresConsumersAndShreddingScope() {
        String json = manifest().manifestJson();
        assertThat(json)
                .contains("\"expiresAt\":\"2027-02-05T03:00:00Z\"")
                .contains("P3.16-exportacao")
                .contains("ED-04-reversao")
                .contains("PENDENTE-LC.5")
                .contains("\"tenantKeyId\":\"44444444-4444-4444-4444-444444444444\"");
    }

    @Test
    @DisplayName("sem tenantKeyId, com schema invalido ou hash invalido: manifesto recusado na construcao")
    void manifestValidation() {
        UUID tenant = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertThatThrownBy(() -> new DailyTenantCopy.CopyManifest(
                tenant, "tenant_abc", null, CAPTURED, "k", "d".repeat(64), 1L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DailyTenantCopy.CopyManifest(
                tenant, "Tenant ABC; DROP", UUID.randomUUID(), CAPTURED, "k", "d".repeat(64), 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DailyTenantCopy.CopyManifest(
                tenant, "tenant_abc", UUID.randomUUID(), CAPTURED, "k", "xyz", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
