package br.com.ziipsan.platform.audit;

import br.com.ziipsan.platform.audit.AuditTrailIntegrityService.AuditRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditTrailIntegrityTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final LocalDate DAY = LocalDate.of(2024, 1, 10);

    private static AuditRow row(String entityType, String action, boolean success) {
        return new AuditRow(
                UUID.randomUUID(),
                OffsetDateTime.of(2024, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC),
                entityType, UUID.randomUUID(), action,
                UUID.randomUUID(), UUID.randomUUID(),
                "{\"name\":\"[redacted]\"}", "{\"name\":\"[redacted]\"}", null, success);
    }

    @Test
    @DisplayName("cadeia: mesmas linhas, mesma raiz; ordem de entrada nao importa")
    void deterministicAndOrderIndependent() {
        AuditRow a = row("customers", "CREATE", true);
        AuditRow b = row("customers", "UPDATE", true);
        String forward = AuditTrailIntegrityService.chainRoot(TENANT, DAY, List.of(a, b));
        String reversed = AuditTrailIntegrityService.chainRoot(TENANT, DAY, List.of(b, a));
        assertThat(forward).isEqualTo(reversed);
        assertThat(forward).hasSize(64);
    }

    @Test
    @DisplayName("cadeia: qualquer campo alterado muda a raiz (o caso do acesso privilegiado)")
    void anyFieldChangeChangesRoot() {
        AuditRow original = row("customers", "UPDATE", true);
        String base = AuditTrailIntegrityService.chainRoot(TENANT, DAY, List.of(original));
        AuditRow tampered = new AuditRow(original.id(), original.createdAt(), original.entityType(),
                original.entityId(), original.action(), original.userId(), original.correlationId(),
                original.oldValues(), original.newValues(), original.changedFields(), false);
        assertThat(AuditTrailIntegrityService.chainRoot(TENANT, DAY, List.of(tampered)))
                .isNotEqualTo(base);
    }

    @Test
    @DisplayName("cadeia: dia vazio tem raiz deterministica (prova ausencia) e difere de dia com linhas")
    void emptyDayHasDeterministicRoot() {
        String empty1 = AuditTrailIntegrityService.chainRoot(TENANT, DAY, List.of());
        String empty2 = AuditTrailIntegrityService.chainRoot(TENANT, DAY, List.of());
        assertThat(empty1).isEqualTo(empty2);
        assertThat(empty1).isNotEqualTo(
                AuditTrailIntegrityService.chainRoot(TENANT, DAY, List.of(row("x", "CREATE", true))));
    }

    @Test
    @DisplayName("cadeia: outro tenant ou outro dia, outra raiz")
    void tenantAndDayArePartOfRoot() {
        List<AuditRow> rows = List.of(row("customers", "CREATE", true));
        String base = AuditTrailIntegrityService.chainRoot(TENANT, DAY, rows);
        assertThat(AuditTrailIntegrityService.chainRoot(UUID.randomUUID(), DAY, rows)).isNotEqualTo(base);
        assertThat(AuditTrailIntegrityService.chainRoot(TENANT, DAY.plusDays(1), rows)).isNotEqualTo(base);
    }

    @Test
    @DisplayName("chave da raiz: audit-roots/<tenant>/<dia>.json")
    void rootKey() {
        assertThat(AuditTrailIntegrityService.rootKey(TENANT, DAY))
                .isEqualTo("audit-roots/11111111-1111-4111-8111-111111111111/2024-01-10.json");
    }
}
