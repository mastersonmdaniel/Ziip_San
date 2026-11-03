package br.com.ziipsan.app;

import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.platform.tenant.TenantMigrationRollout;
import br.com.ziipsan.platform.tenant.TenantMigrator;
import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.14 no PostgreSQL real: rollout com TenantMigrator de verdade sobre tenants
 * provisionados de verdade.
 *
 * <p>Falha determinística sem quebrar dado: o checksum de uma migration já
 * aplicada no histórico do Flyway do tenant é adulterado (+1). Na próxima
 * tentativa o Flyway recusa na validação, antes de aplicar qualquer coisa —
 * é a falha que um tenant com histórico divergente teria em produção, sem
 * precisar de migration quebrada de verdade no classpath.
 *
 * <p>O rollout recebe a lista explícita de tenants (não a consulta do catálogo
 * do DatabaseMigrator): o tenant compartilhado do ProvisionedTenant e os de
 * outros testes não entram na onda e não são afetados. Cada tenant de prova é
 * removido ao fim do teste (schema + linha do catálogo).
 */
class TenantMigrationRolloutIntegrationTest {

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    private record Probe(String schema, UUID tenantId) {
    }

    private Probe provision(String schema) {
        UUID tenantId = UUID.randomUUID();
        TenantProvisioningService service = new TenantProvisioningService(
                ProvisionedTenant.appDs,
                new JdbcTemplate(ProvisionedTenant.appDs),
                Mockito.mock(AuditLogRepository.class),
                ProvisionedTenant.migratorDs);
        var result = service.provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                .tenantId(tenantId)
                .schemaName(schema)
                .displayName("Tenant de prova do rollout P1.14")
                .email("rollout@teste.invalid")
                .plan("PRO")
                .build());
        assertThat(result.success())
                .as("provisionamento de %s deve concluir: %s", schema, result.error())
                .isTrue();
        return new Probe(schema, tenantId);
    }

    private void corruptHistory(Probe probe) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            int updated = st.executeUpdate(
                    "UPDATE \"" + probe.schema() + "\".flyway_schema_history SET checksum = checksum + 1 "
                            + "WHERE installed_rank = (SELECT MIN(installed_rank) FROM \""
                            + probe.schema() + "\".flyway_schema_history "
                            + "WHERE success AND type = 'SQL' AND checksum IS NOT NULL)");
            assertThat(updated).as("alguma migration aplicada deve ter o checksum adulterado").isOne();
        }
    }

    private void cleanup(Probe... probes) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            for (Probe probe : probes) {
                st.execute("DROP SCHEMA IF EXISTS \"" + probe.schema() + "\" CASCADE");
                st.execute("DELETE FROM public.tenant_catalog WHERE id = '" + probe.tenantId() + "'");
            }
        }
    }

    private String catalogStatus(Probe probe) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT migration_status FROM public.tenant_catalog WHERE id = ?")) {
            ps.setObject(1, probe.tenantId());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    private String catalogError(Probe probe) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT migration_error, migration_attempted_at FROM public.tenant_catalog WHERE id = ?")) {
            ps.setObject(1, probe.tenantId());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getTimestamp(2)).as("tentativa registrada").isNotNull();
                return rs.getString(1);
            }
        }
    }

    private TenantMigrationRollout.RolloutResult runRollout(List<Probe> probes) {
        JdbcTemplate jdbc = new JdbcTemplate(ProvisionedTenant.migratorDs);
        TenantMigrator migrator = new TenantMigrator(ProvisionedTenant.migratorDs);
        TenantMigrationRollout rollout = new TenantMigrationRollout(jdbc,
                (schema, tenantId) -> {
                    var result = migrator.migrate(schema, tenantId);
                    return new TenantMigrationRollout.MigrationOutcome(
                            result.migrationsExecuted, result.targetSchemaVersion);
                },
                2);
        return rollout.run(probes.stream()
                .map(p -> new TenantMigrationRollout.Tenant(p.tenantId(), p.schema()))
                .toList());
    }

    @Test
    @DisplayName("tenant com falha vai para quarentena sem parar os demais")
    void failedTenantIsQuarantinedAndOthersContinue() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Probe canary = provision("p1a_canary_" + suffix);
        Probe bad = provision("p1b_bad_" + suffix);
        Probe good = provision("p1c_good_" + suffix);
        try {
            corruptHistory(bad);

            var result = runRollout(List.of(canary, bad, good));

            assertThat(result.abortedAfterCanary()).isFalse();
            assertThat(result.succeeded()).extracting(r -> r.tenant().schemaName())
                    .containsExactlyInAnyOrder(canary.schema(), good.schema());
            assertThat(result.quarantined()).hasSize(1);
            assertThat(result.quarantined().get(0).tenant().schemaName()).isEqualTo(bad.schema());
            assertThat(result.quarantined().get(0).error()).isNotBlank();

            assertThat(catalogStatus(canary)).isEqualTo("SUCCEEDED");
            assertThat(catalogStatus(good)).isEqualTo("SUCCEEDED");
            assertThat(catalogStatus(bad)).isEqualTo("QUARANTINED");
            assertThat(catalogError(bad)).isNotBlank();
            assertThat(catalogError(canary)).isNull();
        } finally {
            cleanup(canary, bad, good);
        }
    }

    @Test
    @DisplayName("canário falha: onda abortada, demais nem tentados (continuam PENDING)")
    void canaryFailureAbortsWave() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Probe canary = provision("p1d_canary_" + suffix);
        Probe good = provision("p1e_good_" + suffix);
        try {
            corruptHistory(canary);

            var result = runRollout(List.of(canary, good));

            assertThat(result.abortedAfterCanary()).isTrue();
            assertThat(result.quarantined()).hasSize(1);
            assertThat(catalogStatus(canary)).isEqualTo("QUARANTINED");
            // O tenant bom nem foi tentado: segue com o default da V14_1.
            assertThat(catalogStatus(good)).isEqualTo("PENDING");
            try (Connection c = ProvisionedTenant.migratorDs.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT migration_attempted_at FROM public.tenant_catalog WHERE id = ?")) {
                ps.setObject(1, good.tenantId());
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getTimestamp(1)).as("tenant não tentado não tem tentativa registrada").isNull();
                }
            }
        } finally {
            cleanup(canary, good);
        }
    }
}
