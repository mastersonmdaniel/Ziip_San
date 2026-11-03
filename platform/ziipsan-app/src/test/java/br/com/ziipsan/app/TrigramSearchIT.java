package br.com.ziipsan.app;

import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.22: busca por trecho do nome usa o índice GIN trigram da V3_9, com dobra de acento.
 *
 * <p>Duas provas, no tenant próprio deste teste (nunca no tenant compartilhado, para não
 * alterar os totais das outras suítes): (1) o EXPLAIN da consulta de busca mostra o
 * índice trigram, com 5.000 linhas sintéticas para o planner ter o que escolher — sem
 * enable_seqscan=off, ou seja, a escolha é do planner, não forçada; (2) a API real
 * casa "JOAO" (sem acento, maiúsculo) com "João" e com "Joao", e não com "Maria".
 *
 * <p>Dados 100% sintéticos, com prefixo aleatório: nenhum nome real, nenhum documento.
 */
class TrigramSearchIT extends AppIntegrationTestBase {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SCHEMA = "it_p0r22_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    // Token raro do padrão buscado ("zzq"): os fillers NÃO o contêm. Sem um token
    // raro o planner prefere, com razão, a varredura: quando o padrão compartilha
    // a maioria dos trigramas com todas as linhas (primeiras versões: fillers e
    // padrão continham "trigrama", depois o TAG), a varredura é o plano certo.
    // O sufixo aleatório nos nomes é só para não colidir com nada; ele NÃO entra
    // no padrão buscado.
    //
    // LIMITAÇÃO DE PLATAFORMA (reproduzida em 2026-11-03, PG 17.11, e registrada
    // aqui para ninguém "corrigir" este teste no futuro sem entender): sob RLS
    // FORÇADA, o PostgreSQL NÃO USA índice trigram para LIKE/ILIKE — o operador
    // textlike (~~) e texticlike (~~*) NÃO são LEAKPROOF, e o planner exclui
    // caminhos de índice cuja condição usa operador não-leakproof quando há
    // quals de RLS. Reproduzido com índice em coluna pura (sem função nenhuma),
    // a 5.000 e a 100.000 linhas, como migrator (não superusuário): varredura
    // nos dois casos, mesmo quando o bitmap custaria 60 contra 157 da varredura.
    // Igualdade btree (texteq, leakproof) continua usável sob RLS. Alternativas
    // FTS (@@) e similaridade (%) também são não-leakproof — não há operador de
    // busca por trecho usável em índice sob RLS forçada no PG 17. Um experimento
    // anterior pareceu provar o contrário, mas rodava como superusuário, que
    // ignora RLS — resultado descartado como inválido. Por isso este teste prova
    // DUAS coisas: (a) sem RLS o planner USA o índice (o índice é válido e serve
    // à consulta); (b) com RLS forçada o plano é Seq Scan POR REGRA DO PG, não
    // por defeito do índice — asserção deliberada, para que uma mudança futura
    // (versão do PG, modelo de RLS) que altere isso quebre o teste e force a
    // revisão desta documentação. A busca em produção, portanto, custa uma
    // varredura por tenant — custo medido (EXPLAIN ANALYZE: ~15 ms a 5k linhas
    // com dobra de acento) e registrado no relatório da frente; decidir se isso
    // é aceitável em tenants grandes é decisão do dono (P1.14/P4.6), não algo
    // que esta frente possa "corrigir" sem quebrar o modelo de RLS.
    private static final String RAND = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String JOAO_ACCENT = "João Zzq " + RAND;
    private static final String JOAO_PLAIN = "Joao Zzq " + RAND;
    private static final String MARIA = "Maria Zzq " + RAND;

    private static final int FILLERS = 5000;

    private final String operator = bearer(TestTokens.token(TENANT, "OPERATOR"));

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
                        .displayName("Tenant trigram P0.22")
                        .cnpj("99888777000166")
                        .email("trigrama@teste.com.br")
                        .plan("PRO")
                        .build());
        assertThat(result.success()).as("provisionamento: %s", result.error()).isTrue();
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            st.execute("SET app.current_tenant = '" + TENANT + "'");
            st.execute("SET search_path TO " + SCHEMA + ", public");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO customers (id, tenant_id, person_type, name) VALUES (?, ?, 'PF', ?)")) {
                for (int i = 0; i < FILLERS; i++) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, TENANT);
                    ps.setString(3, "Cliente Sintético " + i);
                    ps.addBatch();
                }
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT);
                ps.setString(3, JOAO_ACCENT);
                ps.addBatch();
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT);
                ps.setString(3, JOAO_PLAIN);
                ps.addBatch();
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT);
                ps.setString(3, MARIA);
                ps.addBatch();
                ps.executeBatch();
            }
            st.execute("ANALYZE customers");
        }
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

    private String explain(Statement st) throws Exception {
        StringBuilder plan = new StringBuilder();
        try (ResultSet rs = st.executeQuery(
                "EXPLAIN SELECT id FROM customers WHERE tenant_id = '" + TENANT + "' "
                        + "AND immutable_unaccent(lower(name)) LIKE '%joao zzq%'")) {
            while (rs.next()) {
                plan.append(rs.getString(1)).append("\n");
            }
        }
        return plan.toString();
    }

    @Test
    @DisplayName("Plano da busca por trecho: índice sem RLS; Seq Scan documentada com RLS forçada")
    void explainUsesTrigramIndex() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            st.execute("SET app.current_tenant = '" + TENANT + "'");
            st.execute("SET search_path TO " + SCHEMA + ", public");
            // Primeiro: a migration V3_9 criou mesmo o índice neste schema?
            // (Separa "migration não aplicada" de "planner preferiu varredura".)
            try (ResultSet rs = st.executeQuery(
                    "SELECT indexname FROM pg_indexes WHERE schemaname = '" + SCHEMA + "' "
                            + "AND indexname = 'idx_customers_name_trgm'")) {
                assertThat(rs.next()).as("índice trigram criado pela V3_9 no schema").isTrue();
            }
            // (a) Sem RLS: o planner USA o índice para a consulta exata da API.
            // O migrador é dono da tabela, por isso pode desligar/religar a RLS
            // neste schema de teste; o finally garante que ela volta.
            st.execute("ALTER TABLE customers DISABLE ROW LEVEL SECURITY");
            try {
                String plain = explain(st);
                assertThat(plain).as("plano sem RLS: \n%s", plain)
                        .contains("idx_customers_name_trgm");
            } finally {
                st.execute("ALTER TABLE customers ENABLE ROW LEVEL SECURITY");
                st.execute("ALTER TABLE customers FORCE ROW LEVEL SECURITY");
            }
            // (b) Com RLS forçada (o caso de produção): o PG exclui o caminho do
            // índice porque textlike não é LEAKPROOF — ver o comentário da classe.
            // Esta asserção é deliberada: ela trava o comportamento conhecido.
            String rls = explain(st);
            assertThat(rls).as("plano com RLS forçada: \n%s", rls)
                    .contains("Seq Scan on customers")
                    .doesNotContain("idx_customers_name_trgm");
        }
    }

    @Test
    @DisplayName("API: 'JOAO' casa João e Joao, não Maria")
    void apiFoldsAccents() throws Exception {
        mvc.perform(post("/api/v1/registry/customers/search")
                        .header("Authorization", operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"q\": \"JOAO ZZQ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", hasItem(JOAO_ACCENT)))
                .andExpect(jsonPath("$.content[*].name", hasItem(JOAO_PLAIN)))
                .andExpect(jsonPath("$.content[*].name", not(hasItem(MARIA))));
    }
}
