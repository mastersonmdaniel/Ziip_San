package br.com.ziipsan.platform.tenant;

import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.core.migration.CoreMigrationRunner;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova de que o provisionamento de tenant funciona de verdade contra PostgreSQL real.
 *
 * Foi aqui que estavam os bugs mais graves do projeto: schema pertencendo a role errada,
 * GRANT aplicado antes das tabelas existirem, e Flyway sem public no search_path. Nenhum
 * deles falhava em build. Todos quebram silenciosamente ou com excecao em producao.
 */
class TenantProvisioningServiceTest {

    private static DataSource appDataSource;
    private static DataSource migratorDataSource;

    /**
     * Sufixo único por execução. Os testes fazem DROP SCHEMA CASCADE; com nome fixo, rodar
     * `mvn test` numa máquina de desenvolvimento derrubaria o schema de um cliente real.
     */
    private static final String RUN_ID = Long.toHexString(System.nanoTime() & 0xFFFFF);

    private static final String SCHEMA = "itest_p1_" + RUN_ID;
    private static final String SCHEMA_B = "itest_p2_" + RUN_ID;

    /**
     * Hash bcrypt real (custo 12) da senha de teste "senha-de-teste-ziipsan", gerado com
     * spring-security-crypto (BCrypt.hashpw), que não está no classpath deste módulo. O provisionamento
     * recusa qualquer valor que não seja um bcrypt completo.
     */

    /** Senha de teste sem valor padrao (P0.3): variavel ausente ou vazia falha explicita. */
    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Variavel " + name + " nao definida: exporte-a antes de rodar o teste "
                            + "(scripts/ci-linux.sh gera uma senha efemera a cada execucao)");
        }
        return value;
    }

    /** Impede que um DROP CASCADE atinja um schema que não é deste teste. */
    private static void assertTestSchema(String schema) {
        if (!schema.startsWith("itest_")) {
            throw new IllegalStateException(
                    "Recusando destruir o schema " + schema + ": nao pertence a este teste");
        }
    }


    @BeforeAll
    static void connect() {
        String url = System.getenv().getOrDefault(
                "ZIIPSAN_IT_JDBC_URL", "jdbc:postgresql://host.docker.internal:55433/ziipsan");
        appDataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                url,
                System.getenv().getOrDefault("ZIIPSAN_IT_DB_USER", "ziipsan_app"),
                // P0.3: senha sem valor padrao versionado (ver requireEnv abaixo).
                requireEnv("ZIIPSAN_IT_DB_PASSWORD"));
        migratorDataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                url,
                System.getenv().getOrDefault("ZIIPSAN_IT_MIGRATOR_USER", "ziipsan_migrator"),
                requireEnv("ZIIPSAN_IT_MIGRATOR_PASSWORD"));

        // Migration de core real (idempotente), não uma cópia das tabelas: o teste precisa ver os
        // mesmos privilégios e a mesma RLS de public.audit_log que a produção.
        new CoreMigrationRunner(migratorDataSource).run(null);
    }

    @BeforeEach
    void cleanup() throws SQLException {
        try (Connection conn = migratorDataSource.getConnection(); Statement st = conn.createStatement()) {
            assertTestSchema(SCHEMA);
            st.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            assertTestSchema(SCHEMA_B);
            st.execute("DROP SCHEMA IF EXISTS " + SCHEMA_B + " CASCADE");
            st.execute("DELETE FROM public.tenant_catalog WHERE schema_name IN ('"
                    + SCHEMA + "', '" + SCHEMA_B + "')");
        }
    }

    private TenantProvisioningService service() {
        JdbcTemplate jdbc = new JdbcTemplate(appDataSource);
        return new TenantProvisioningService(
                appDataSource,
                jdbc,
                Mockito.mock(AuditLogRepository.class),
                migratorDataSource);
    }

    private static String cnpjFor(UUID tenantId) {
        return String.format("%014d", Math.floorMod(tenantId.getMostSignificantBits(), 100_000_000_000_000L));
    }

    /**
     * Provisiona e exige sucesso. Os testes chamavam provisionTenant e ignoravam o resultado: com
     * o provisionamento falhando, asserções do tipo "nenhuma linha com senha no audit_log" passavam
     * no vazio.
     */
    private void provisionOk(UUID tenantId, String schema) {
        var result = service().provisionTenant(request(tenantId, schema));
        assertThat(result.success())
                .as("provisionamento de %s: %s", schema, result.error())
                .isTrue();
    }

    private TenantProvisioningService.TenantProvisioningRequest request(UUID tenantId, String schema) {
        return TenantProvisioningService.TenantProvisioningRequest.builder()
                .tenantId(tenantId)
                .schemaName(schema)
                .displayName("Concessionaria Teste")
                // CNPJ distinto por tenant: public.tenant_catalog tem UNIQUE (cnpj). Com um CNPJ fixo,
                // o provisionamento colidia com linhas deixadas por outros testes e falhava em silêncio.
                .cnpj(cnpjFor(tenantId))
                .email("contato@teste.com.br")
                .phone("11999999999")
                .plan("PRO")
                .build();
    }

    private int count(String sql, Object... args) {
        Integer result = new JdbcTemplate(appDataSource).queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    /**
     * Conta linhas no contexto de um tenant.
     *
     * A GUC app.current_tenant precisa estar na MESMA conexão da leitura: set_config com
     * is_local=true vale só dentro da transação, e o pool pode entregar outra conexão.
     */
    private int countAsTenant(UUID tenantId, String sql, Object... args) {
        try (Connection conn = appDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (Statement st = conn.createStatement()) {
                    st.execute("SELECT set_config('app.current_tenant', '" + tenantId + "', true)");
                }
                for (int i = 0; i < args.length; i++) {
                    ps.setObject(i + 1, args[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                conn.rollback();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao contar linhas", e);
        }
    }

    private boolean schemaExists() {
        return schemaExists(SCHEMA);
    }

    private boolean schemaExists(String schema) {
        Integer n = new JdbcTemplate(migratorDataSource).queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schema);
        return n != null && n > 0;
    }

    /**
     * Insere uma ordem de serviço no schema, com o tenant como contexto.
     *
     * O commit e explicito: a GUC app.current_tenant e local a transacao, entao o INSERT tem
     * de ser confirmado para sobreviver. O rollbackso serve para descartar a GUC.
     */
    /** P0.25: service_orders sai da V1_1 (volta na P1.31); o seed de isolamento usa consumer_units. */
    private void seedConsumerUnit(String schema, UUID tenantId, String matricula) {
        try (Connection conn = migratorDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + tenantId + "', true)");
                st.execute("INSERT INTO " + schema
                        + ".consumer_units (tenant_id, matricula) VALUES ('" + tenantId
                        + "', '" + matricula + "')");
            }
            conn.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao semear unidade consumidora", e);
        }
    }

    @Test
    @DisplayName("provisiona schema com todas as tabelas de tenant")
    void provisionsSchemaWithAllTables() {
        UUID tenantId = UUID.randomUUID();

        TenantProvisioningService.TenantProvisioningResult result =
                service().provisionTenant(request(tenantId, SCHEMA));

        assertThat(result.success())
                .as("provisionamento falhou: %s", result.error())
                .isTrue();
        assertThat(result.schemaName()).isEqualTo(SCHEMA);
        assertThat(schemaExists()).isTrue();

        // P0.25: a V1_1 reescrita cria só consumer_units e addresses (users/roles/
        // documents/service_orders voltam em P0.13/P1.31); + flyway_schema_history = 3.
        int tables = count("SELECT count(*) FROM information_schema.tables WHERE table_schema = ?", SCHEMA);
        assertThat(tables)
                .as("o template de tenant tem consumer_units, addresses e flyway_schema_history; "
                        + "0 significaria Flyway não achou migration")
                .isGreaterThanOrEqualTo(3);
        int consumerUnits = count("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name = 'consumer_units'", SCHEMA);
        int addresses = count("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name = 'addresses'", SCHEMA);
        assertThat(consumerUnits).as("consumer_units (raiz do cadastro) deve existir").isEqualTo(1);
        assertThat(addresses).as("addresses deve existir").isEqualTo(1);
    }

    @Test
    @DisplayName("grava histórico de migration, provando que o Flyway rodou de verdade")
    void recordsFlywayHistory() {
        provisionOk(UUID.randomUUID(), SCHEMA);

        int history = count("SELECT count(*) FROM " + SCHEMA + ".flyway_schema_history");
        assertThat(history)
                .as("sem histórico, nenhuma migration foi aplicada")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("registra tenant no catálogo público")
    void registersTenantInCatalog() {
        UUID tenantId = UUID.randomUUID();

        provisionOk(tenantId, SCHEMA);

        int catalog = count("SELECT count(*) FROM public.tenant_catalog WHERE schema_name = ? AND id = ?",
                SCHEMA, tenantId);
        assertThat(catalog).isEqualTo(1);
    }

    @Test
    @DisplayName("todas as tabelas de negócio têm RLS habilitada")
    void enablesRowLevelSecurityOnEveryTable() {
        provisionOk(UUID.randomUUID(), SCHEMA);

        // flyway_schema_history é a única exceção: guarda o histórico de migration, que por
        // definição é visível ao deploy. Não tem dado de tenant, então não precisa de RLS.
        Integer businessTables = count("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name <> 'flyway_schema_history'", SCHEMA);
        Integer withRls = count("""
                SELECT count(*) FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind = 'r' AND c.relrowsecurity
                """, SCHEMA);

        assertThat(withRls)
                .as("tabela de negócio sem RLS é tabela que vaza dados entre tenants")                .isEqualTo(businessTables);
    }

    @Test
    @DisplayName("role da aplicação não é dona do schema de tenant")
    void appRoleIsNotSchemaOwner() {
        provisionOk(UUID.randomUUID(), SCHEMA);

        String owner = new JdbcTemplate(migratorDataSource).queryForObject(
                "SELECT nspowner::regrole::text FROM pg_namespace WHERE nspname = ?",
                String.class, SCHEMA);

        assertThat(owner)
                .as("dono de schema ignora RLS; a app não pode ser dona")
                .isNotEqualTo("ziipsan_app");
    }

    @Test
    @DisplayName("a RLS distingue dono, estranho e ausência de contexto")
    void appRoleSeesOnlyItsOwnRows() {
        UUID tenantId = UUID.randomUUID();
        provisionOk(tenantId, SCHEMA);

        // Duas ordens no MESMO schema, de dois tenant_id diferentes. Sem semear nada, o
        // resultado seria zero por ausência de dado, não por RLS — o teste passaria mesmo
        // com as policies removidas.
        seedConsumerUnit(SCHEMA, tenantId, "UC-DO-DONO");
        seedConsumerUnit(SCHEMA, UUID.randomUUID(), "UC-DO-ESTRANHO");

        assertThat(countAsTenant(tenantId, "SELECT count(*) FROM " + SCHEMA + ".consumer_units"))
                .as("o dono enxerga somente a própria unidade")
                .isEqualTo(1);

        assertThat(countAsTenant(UUID.randomUUID(),
                "SELECT count(*) FROM " + SCHEMA + ".consumer_units"))
                .as("tenant que não é o dono não enxerga nada")
                .isZero();

        assertThat(count("SELECT count(*) FROM " + SCHEMA + ".consumer_units"))
                .as("sem contexto de tenant, a RLS nega tudo")
                .isZero();
    }

    @Test
    @DisplayName("nome de schema com SQL injection é neutralizado e provisiona com nome seguro")
    void rejectsSqlInjectionInSchemaName() {
        String malicious = "evil; DROP TABLE users; --";

        TenantProvisioningService.TenantProvisioningResult result =
                service().provisionTenant(request(UUID.randomUUID(), malicious));

        // A versão anterior deste teste afirmava apenas que schemaName não continha ";".
        // Como TenantProvisioningResult.failure() devolve schemaName = null, QUALQUER falha
        // satisfazia a asserção — inclusive a remoção total da sanitização. O teste era verde
        // sem provar nada.
        assertThat(result.success())
                .as("a entrada maliciosa deve ser normalizada e o tenant provisionado: %s",
                        result.error())
                .isTrue();

        String created = result.schemaName();
        assertThat(created)
                .as("o nome normalizado não pode conter ponto e vírgula nem espaço")
                .doesNotContain(";")
                .doesNotContain(" ")
                .matches("^[a-z_][a-z0-9_]*$");

        // O schema com o nome normalizado existe de fato.
        assertThat(schemaExists(created))
                .as("o tenant deve ter sido criado sob o nome sanitizado")
                .isTrue();

        // E o mais importante: o catálogo público sobreviveu. Um bug de sanitização poderia
        // ter executado DROP TABLE users ou algo pior.
        int catalogRows = new JdbcTemplate(migratorDataSource).queryForObject(
                "SELECT count(*) FROM public.tenant_catalog WHERE schema_name = ?",
                Integer.class, created);
        assertThat(catalogRows)
                .as("o tenant deve estar registrado")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("schema reservado do PostgreSQL é rejeitado antes de qualquer DDL")
    void rejectsReservedSchemaName() {
        // Sem esta proteção, provisionTenant("public") criaria as tabelas de tenant dentro de
        // public e, em caso de falha posterior, o rollback executaria
        // DROP SCHEMA public CASCADE — apagando tenant_catalog e audit_log do banco inteiro.
        for (String reservado : new String[]{"public", "information_schema", "pg_catalog"}) {
            assertThatThrownBy(() -> service().provisionTenant(request(UUID.randomUUID(), reservado)))
                    .as("schema reservado %s deve ser rejeitado", reservado)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reservado");
        }

        // O catálogo continua de pé depois das tentativas.
        int catalogTable = new JdbcTemplate(migratorDataSource).queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_name='tenant_catalog'",
                Integer.class);
        assertThat(catalogTable)
                .as("nenhuma tentativa pode derrubar a tabela do catálogo")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("P0.25: reprovisionamento não destrói dado de tenant já existente")
    void reprovisionKeepsExistingTenantData() {
        UUID tenantId = UUID.randomUUID();

        // 1. Tenant provisionado com sucesso, com dado de negócio.
        provisionOk(tenantId, SCHEMA);
        seedConsumerUnit(SCHEMA, tenantId, "UC-DO-CLIENTE");

        // 2. Reprovisionamento do mesmo tenant (operação legítima, sem passo Keycloak desde P0.25).
        TenantProvisioningService.TenantProvisioningResult result =
                service().provisionTenant(request(tenantId, SCHEMA));

        assertThat(result.success())
                .as("o reprovisionamento deve concluir: %s", result.error())
                .isTrue();

        // 3. O dado do cliente tem que continuar lá.
        int unidades = countAsTenant(tenantId,
                "SELECT count(*) FROM " + SCHEMA + ".consumer_units");
        assertThat(unidades)
                .as("reprovisionamento não pode destruir dado de tenant em produção")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("provisionamento duas vezes é idempotente no catálogo")
    void reprovisioningUpdatesCatalog() {
        UUID tenantId = UUID.randomUUID();
        TenantProvisioningService svc = service();

        svc.provisionTenant(request(tenantId, SCHEMA));
        TenantProvisioningService.TenantProvisioningResult second =
                svc.provisionTenant(request(tenantId, SCHEMA));

        assertThat(second.success()).isTrue();

        int catalog = count("SELECT count(*) FROM public.tenant_catalog WHERE schema_name = ?", SCHEMA);
        assertThat(catalog)
                .as("ON CONFLICT (schema_name) deve atualizar, não duplicar")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("audit_log compartilhado não vaza histórico entre tenants")
    void sharedAuditLogIsIsolatedBetweenTenants() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        provisionOk(tenantA, SCHEMA);
        provisionOk(tenantB, SCHEMA_B);

        // O trigger de auditoria grava automaticamente. Cada provisionamento gera registros
        // para o seu tenant.
        int aVê = countAsTenant(tenantA, "SELECT count(*) FROM public.audit_log");
        int bVê = countAsTenant(tenantB, "SELECT count(*) FROM public.audit_log");

        assertThat(aVê)
                .as("o tenant A enxerga o próprio histórico")
                .isGreaterThan(0);
        assertThat(bVê)
                .as("o tenant B enxerga o próprio histórico")
                .isGreaterThan(0);

        // Não existe "total real" independente de RLS: public.audit_log pertence ao
        // ziipsan_migrator e tem FORCE ROW LEVEL SECURITY, então nem o migrador enxerga linhas
        // sem contexto de tenant (uma versão anterior deste teste lia o total pelo migrador e
        // obtinha 0). A prova de isolamento é por tenant: tudo o que o tenant enxerga é dele, e
        // nada de outro tenant aparece.
        assertThat(countAsTenant(tenantA,
                "SELECT count(*) FROM public.audit_log WHERE tenant_id <> ?", tenantA))
                .as("o tenant A não enxerga nenhuma linha de outro tenant")
                .isZero();
        assertThat(countAsTenant(tenantB,
                "SELECT count(*) FROM public.audit_log WHERE tenant_id <> ?", tenantB))
                .as("o tenant B não enxerga nenhuma linha de outro tenant")
                .isZero();
        assertThat(countAsTenant(tenantA,
                "SELECT count(*) FROM public.audit_log WHERE tenant_id = ?", tenantA))
                .as("todas as linhas visíveis ao tenant A pertencem a ele")
                .isEqualTo(aVê);
        assertThat(countAsTenant(tenantB,
                "SELECT count(*) FROM public.audit_log WHERE tenant_id = ?", tenantB))
                .as("todas as linhas visíveis ao tenant B pertencem a ele")
                .isEqualTo(bVê);

        assertThat(count("SELECT count(*) FROM public.audit_log"))
                .as("sem contexto de tenant, audit_log é inacessível")
                .isZero();
    }

    @Test
    @DisplayName("trigger de auditoria não grava senha em texto claro")
    void auditTriggerDoesNotLeakPasswords() {
        UUID tenantId = UUID.randomUUID();
        provisionOk(tenantId, SCHEMA);

        int comSenha = countAsTenant(tenantId,
                "SELECT count(*) FROM public.audit_log "
                        + "WHERE new_values::text LIKE '%password_hash%' "
                        + "OR new_values::text LIKE '%mfa_secret%'");

        assertThat(comSenha)
                .as("P0.25: sem seed de admin; nenhum hash ou segredo pode ir para o audit_log")
                .isZero();
    }

    @Test
    @DisplayName("schema fica isolado: linhas de um tenant não aparecem para outro")
    void provisionedTenantIsIsolatedFromOthers() throws SQLException {
        UUID tenantId = UUID.randomUUID();
        provisionOk(tenantId, SCHEMA);

        seedConsumerUnit(SCHEMA, tenantId, "UC-ISOLADA");
        int visible = countAsTenant(tenantId, "SELECT count(*) FROM " + SCHEMA + ".consumer_units");
        assertThat(visible)
                .as("com o próprio tenant no contexto, a unidade dele deve aparecer")
                .isEqualTo(1);

        int semContexto = count("SELECT count(*) FROM " + SCHEMA + ".consumer_units");
        assertThat(semContexto)
                .as("sem contexto de tenant a RLS nega tudo")
                .isZero();

        UUID outro = UUID.randomUUID();
        assertThat(countAsTenant(outro, "SELECT count(*) FROM " + SCHEMA + ".consumer_units"))
                .as("tenant que não é o dono não vê nada")
                .isZero();
    }
}
