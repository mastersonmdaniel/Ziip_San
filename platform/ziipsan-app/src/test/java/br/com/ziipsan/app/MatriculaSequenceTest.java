package br.com.ziipsan.app;

import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.4: matrícula gerada por sequência por tenant (V3_10), no lugar do
 * {@code MAX(CAST(matricula AS bigint))} com trava consultiva.
 *
 * <p>Provas, no tenant próprio deste teste (nunca no tenant compartilhado, para não alterar
 * os totais das outras suítes):
 * (1) a migration V3_10 criou a sequência no schema do tenant e um tenant vazio começa em 1;
 * (2) dois lotes paralelos de 25 nextval cada (pela role da aplicação, que é quem gera a
 * matrícula em produção) devolvem 50 valores distintos e contíguos, sem espera por trava e
 * sem duplicata — nextval nunca bloqueia, ao contrário da trava consultiva anterior;
 * (3) a expressão de semeadura da V3_10, reaplicada sobre uma matrícula alta pré-existente,
 * faz o próximo valor saltar para além dela (o Flyway executa cada migration uma única vez,
 * então o caso "tenant existente migrado agora" é provado reaplicando a expressão, não
 * reexecutando a migration).
 *
 * <p>Ressalva: sequência não é contígua sob rollback — um nextval consumido por transação
 * desfeita não é reutilizado. "Sem lacuna" aqui vale para lotes confirmados; o teste usa
 * nextval fora de transação desfeita, como a geração em produção.
 *
 * <p>Dados 100% sintéticos; o schema do teste é descartado no tearDown.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MatriculaSequenceTest extends AppIntegrationTestBase {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SCHEMA = "it_p1mat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    private static final String SEQ = SCHEMA + ".registry_matricula_seq";

    @BeforeAll
    static void setUp() throws Exception {
        ProvisionedTenant.ensure();
        var result = new TenantProvisioningService(
                ProvisionedTenant.appDs,
                new JdbcTemplate(ProvisionedTenant.appDs),
                Mockito.mock(AuditLogRepository.class),
                ProvisionedTenant.migratorDs)
                .provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                        .tenantId(TENANT)
                        .schemaName(SCHEMA)
                        .displayName("Tenant matrícula P1.4")
                        .cnpj("99888777000166")
                        .email("matricula@teste.com.br")
                        .plan("PRO")
                        .build());
        assertThat(result.success()).as("provisionamento: %s", result.error()).isTrue();
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            // P1-A (integracao): tenant_vertical referencia o catalogo (FK) desde que o
            // provisionamento passou a ativar o pacote sanitation; apagar antes, senao o
            // DELETE do catalogo viola a FK e a linha orfa quebra o migrate posterior.
            st.execute("DELETE FROM public.tenant_vertical WHERE tenant_id = (SELECT id FROM public.tenant_catalog WHERE schema_name = '" + SCHEMA + "')");
            st.execute("DELETE FROM public.tenant_catalog WHERE schema_name = '" + SCHEMA + "'");
        }
    }

    private static long nextvalAsApp() throws Exception {
        try (Connection c = ProvisionedTenant.appDs.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT nextval('" + SEQ + "')")) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    @Test
    @Order(1)
    @DisplayName("V3_10 criou a sequência e um tenant vazio começa em 1")
    void sequenceExistsAndStartsAtOne() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT sequence_name FROM information_schema.sequences "
                             + "WHERE sequence_schema = '" + SCHEMA + "' "
                             + "AND sequence_name = 'registry_matricula_seq'")) {
            assertThat(rs.next()).as("sequência criada pela V3_10 no schema").isTrue();
        }
        assertThat(nextvalAsApp()).as("primeira matrícula do tenant vazio").isEqualTo(1L);
    }

    @Test
    @Order(2)
    @DisplayName("dois lotes paralelos: 50 valores distintos e contíguos, sem duplicata")
    void twoParallelBatchesYieldDistinctContiguousValues() throws Exception {
        // Baseline: consome um valor para ancorar a faixa (o teste anterior já consumiu o 1).
        long baseline = nextvalAsApp();
        Callable<List<Long>> batch = () -> {
            List<Long> values = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                values.add(nextvalAsApp());
            }
            return values;
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<List<Long>>> futures;
        try {
            futures = pool.invokeAll(List.of(batch, batch));
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).as("lotes concluem sem travar").isTrue();
        }
        List<Long> all = new ArrayList<>();
        for (Future<List<Long>> f : futures) {
            all.addAll(f.get());
        }
        assertThat(all).hasSize(50).doesNotHaveDuplicates();
        List<Long> sorted = new ArrayList<>(all);
        Collections.sort(sorted);
        assertThat(sorted.get(0)).as("primeiro valor após a baseline").isEqualTo(baseline + 1);
        assertThat(sorted.get(sorted.size() - 1) - sorted.get(0))
                .as("faixa contígua: sem lacuna nos lotes confirmados").isEqualTo(49L);
    }

    @Test
    @Order(3)
    @DisplayName("semeadura: matrícula alta pré-existente empurra o próximo valor para além dela")
    void seedingSkipsPastPreExistingHighMatricula() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            st.execute("SET app.current_tenant = '" + TENANT + "'");
            st.execute("SET search_path TO " + SCHEMA + ", public");
            st.execute("INSERT INTO consumer_units (id, tenant_id, matricula, dv) VALUES ('"
                    + UUID.randomUUID() + "', '" + TENANT + "', '777001', '0')");
            // A expressão de semeadura da V3_10, reaplicada (o Flyway não reexecuta a migration).
            st.execute("SELECT CASE WHEN COALESCE(m, 0) > 0 THEN setval('" + SEQ + "', m, true) "
                    + "ELSE setval('" + SEQ + "', 1, false) END "
                    + "FROM (SELECT MAX(CAST(matricula AS bigint)) AS m FROM consumer_units "
                    + "WHERE matricula ~ '^[0-9]+$' AND length(matricula) <= 18) x");
        }
        assertThat(nextvalAsApp()).as("próxima matrícula após 777001").isEqualTo(777002L);
    }
}
