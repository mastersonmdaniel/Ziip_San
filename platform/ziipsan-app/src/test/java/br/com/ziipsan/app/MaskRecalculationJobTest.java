package br.com.ziipsan.app;

import br.com.ziipsan.app.job.MaskRecalculationJob;
import br.com.ziipsan.commercial.registry.service.CustomerDocumentService;
import br.com.ziipsan.core.document.MaskPolicy;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.service.MtrDocumentProtection;
import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import br.com.ziipsan.security.crypto.FieldProtector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.12, contra o PostgreSQL real: o job decifra e regrava as máscaras com o teto
 * vigente, e depois da execução nenhuma máscara gravada fica acima do teto (varredura
 * sobre a base).
 *
 * <p>Tenant dedicado, não o compartilhado: o tenant de {@link ProvisionedTenant} contém
 * linhas de teste de schema com cifra falsa (ex.: 'cifrado'), e o job aborta — por
 * desenho — diante de uma linha que não decifra, em vez de pular em silêncio. O tenant
 * dedicado também prova que as migrations da P0.11 aplicam num provisionamento novo.
 */
class MaskRecalculationJobTest extends AppIntegrationTestBase {

    /** Máscara pré-P0.11 (6 dígitos do meio): acima do teto, deve ser recalculada. */
    private static final String OLD_MASK = "***.982.247-**";
    private static final String NEW_MASK = "***.***.247-**";
    private static final String CPF_DIGITS = "52998224725";
    /** Segundo cliente, já no teto (não deve ser regravado): CPF distinto (hash único). */
    private static final String OTHER_CPF_DIGITS = "11144477735";
    private static final String OTHER_NEW_MASK = "***.***.777-**";

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SUFFIX = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String SCHEMA = "it_f11_" + SUFFIX;

    @Autowired
    private MaskRecalculationJob job;

    @Autowired
    private FieldProtector protector;

    @BeforeAll
    static void provision() {
        ProvisionedTenant.ensure();
        var result = new TenantProvisioningService(
                ProvisionedTenant.appDs,
                new JdbcTemplate(ProvisionedTenant.appDs),
                Mockito.mock(br.com.ziipsan.core.audit.AuditLogRepository.class),
                ProvisionedTenant.migratorDs)
                .provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                        .tenantId(TENANT)
                        .schemaName(SCHEMA)
                        .displayName("Tenant do job de recálculo P0.12")
                        .cnpj("98765432000198")
                        .email("f11@teste.com.br")
                        .plan("PRO")
                        .build());
        assertThat(result.success()).as("provisionamento do tenant do job: %s", result.error()).isTrue();
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection(); Statement st = c.createStatement()) {
            if (SCHEMA.startsWith("it_f11_")) {
                st.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }

    @Test
    @DisplayName("job recalcula as máscaras antigas e a varredura não encontra máscara acima do teto")
    void jobRecalculatesAndSweepFindsNoOverCapMask() throws Exception {
        seed();
        TenantContextHolder.setTenant(SCHEMA);
        TenantContextHolder.setTenantId(TENANT);
        try {
            MaskRecalculationJob.Report report = job.run();
            assertThat(report.customersExamined()).isEqualTo(2);
            assertThat(report.customersChanged()).isEqualTo(1);
            assertThat(report.generatorsExamined()).isEqualTo(1);
            assertThat(report.generatorsChanged()).isEqualTo(1);
            assertThat(report.mtrExamined()).isEqualTo(1);
            assertThat(report.mtrChanged()).isEqualTo(1);
        } finally {
            TenantContextHolder.clear();
        }

        List<String> masks = sweepMasks();
        assertThat(masks).containsExactlyInAnyOrder(NEW_MASK, OTHER_NEW_MASK, NEW_MASK, NEW_MASK);
        assertThat(masks).allSatisfy(mask ->
                assertThat(MaskPolicy.violatesCap(mask)).as("máscara acima do teto: %s", mask).isFalse());
        assertThat(maskOf("customers", "name = 'Cliente Job Antigo'")).isEqualTo(NEW_MASK);
        assertThat(maskOf("customers", "name = 'Cliente Job No Teto'")).isEqualTo(OTHER_NEW_MASK);
    }

    /** Semeia, com cifra válida, um cliente com máscara antiga, um já no teto, um gerador e um MTR. */
    private void seed() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
            }
            var customer = protector.protect(TENANT, CustomerDocumentService.FIELD, CPF_DIGITS, OLD_MASK);
            var customerOk = protector.protect(TENANT, CustomerDocumentService.FIELD, OTHER_CPF_DIGITS, OTHER_NEW_MASK);
            var generator = protector.protect(TENANT, MtrDocumentProtection.GENERATOR_DOCUMENT, CPF_DIGITS, OLD_MASK);
            var driver = protector.protect(TENANT, MtrDocumentProtection.DRIVER_CPF, CPF_DIGITS, OLD_MASK);
            UUID generatorId = UUID.randomUUID();
            UUID transporterId = UUID.randomUUID();
            UUID destinationId = UUID.randomUUID();
            UUID wasteClassId = UUID.randomUUID();
            insert(c, "INSERT INTO " + SCHEMA + ".customers (id, tenant_id, person_type, name, document_ciphertext,"
                    + " document_hash, document_masked) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), TENANT, "PF", "Cliente Job Antigo",
                    customer.ciphertext(), customer.hash(), OLD_MASK);
            insert(c, "INSERT INTO " + SCHEMA + ".customers (id, tenant_id, person_type, name, document_ciphertext,"
                    + " document_hash, document_masked) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), TENANT, "PF", "Cliente Job No Teto",
                    customerOk.ciphertext(), customerOk.hash(), OTHER_NEW_MASK);
            insert(c, "INSERT INTO " + SCHEMA + ".waste_generator (id, tenant_id, document_ciphertext, document_hash,"
                    + " document_masked, corporate_name) VALUES (?, ?, ?, ?, ?, ?)",
                    generatorId, TENANT, generator.ciphertext(), generator.hash(), OLD_MASK, "Gerador Job");
            insert(c, "INSERT INTO " + SCHEMA + ".waste_class (id, tenant_id, code, description, hazard_class)"
                    + " VALUES (?, ?, ?, ?, ?)", wasteClassId, TENANT, "F11" + SUFFIX, "Classe do job", "II");
            insert(c, "INSERT INTO " + SCHEMA + ".waste_transporter (id, tenant_id, cnpj, corporate_name)"
                    + " VALUES (?, ?, ?, ?)", transporterId, TENANT, "F11T" + SUFFIX, "Transportadora Job");
            insert(c, "INSERT INTO " + SCHEMA + ".waste_destination (id, tenant_id, cnpj, corporate_name, destination_type)"
                    + " VALUES (?, ?, ?, ?, ?)", destinationId, TENANT, "F11D" + SUFFIX, "Destinador Job", "RECYCLING");
            insert(c, "INSERT INTO " + SCHEMA + ".waste_mtr (id, tenant_id, mtr_number, generator_id, transporter_id,"
                    + " destination_id, waste_class_id, quantity_kg, issued_at,"
                    + " driver_cpf_ciphertext, driver_cpf_hash, driver_cpf_masked) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), TENANT, "F11." + SUFFIX, generatorId, transporterId, destinationId,
                    wasteClassId, new java.math.BigDecimal("100.000"), Timestamp.from(OffsetDateTime.now().toInstant()),
                    driver.ciphertext(), driver.hash(), OLD_MASK);
            c.commit();
        }
    }

    private static void insert(Connection c, String sql, Object... params) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    private String maskOf(String table, String where) throws Exception {
        try (Connection c = asTenant()) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT document_masked FROM " + SCHEMA + "." + table + " WHERE " + where)) {
                assertThat(rs.next()).as("linha esperada em %s (%s)", table, where).isTrue();
                return rs.getString(1);
            } finally {
                c.rollback();
            }
        }
    }

    /** Conexão com a GUC do tenant (sem ela, a RLS FORCE esconde as linhas até do migrador). */
    private static Connection asTenant() throws Exception {
        Connection c = ProvisionedTenant.migratorDs.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
        }
        return c;
    }

    /** Varredura sobre a base: todas as máscaras gravadas nas três colunas do job. */
    private List<String> sweepMasks() throws Exception {
        List<String> masks = new ArrayList<>();
        try (Connection c = asTenant()) {
            try (Statement st = c.createStatement()) {
                for (String sql : List.of(
                        "SELECT document_masked FROM " + SCHEMA + ".customers WHERE document_masked IS NOT NULL",
                        "SELECT document_masked FROM " + SCHEMA + ".waste_generator WHERE document_masked IS NOT NULL",
                        "SELECT driver_cpf_masked FROM " + SCHEMA + ".waste_mtr WHERE driver_cpf_masked IS NOT NULL")) {
                    try (ResultSet rs = st.executeQuery(sql)) {
                        while (rs.next()) {
                            masks.add(rs.getString(1));
                        }
                    }
                }
            } finally {
                c.rollback();
            }
        }
        return masks;
    }
}
