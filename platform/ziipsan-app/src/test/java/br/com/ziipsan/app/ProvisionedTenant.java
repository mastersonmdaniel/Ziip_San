package br.com.ziipsan.app;

import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.core.migration.CoreMigrationRunner;
import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Banco virgem + migration de core + um tenant provisionado com todos os módulos, uma única vez por
 * JVM de teste. As classes de teste de integração compartilham este tenant e criam os próprios dados
 * dentro dele.
 *
 * <p>O schema do tenant é descartado ao fim da JVM. O banco precisa ser virgem (ver
 * {@code scripts/reset-it-db.sh}): a migration de core roda sobre {@code public}.
 *
 * <p>Reexecução entre JVMs (P1-A, rodada 3 do CI total): o CI roda surefire e failsafe na mesma
 * invocação sobre o mesmo banco. Cada JVM gera TENANT_ID/SCHEMA aleatórios, mas o CNPJ do tenant
 * compartilhado é fixo — e o hook de shutdown da JVM anterior dropa só o schema, deixando a linha
 * do catálogo. Sem limpeza, o segundo provisionamento esbarra no UNIQUE tenant_catalog_cnpj_key
 * ("Falha ao registrar tenant no catalogo"). Por isso ensure() remove antes qualquer resíduo do
 * tenant compartilhado (vertical + catálogo + schema órfão) e o hook de shutdown passa a remover
 * também vertical + catálogo. Bancos de IT são descartáveis; nada disso toca código de produto.
 */
final class ProvisionedTenant {

    static final UUID TENANT_ID = UUID.randomUUID();
    static final String SCHEMA = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    /** CNPJ sintético fixo do tenant compartilhado — a chave da limpeza entre JVMs. */
    static final String SHARED_TENANT_CNPJ = "12345678000199";

    static String url;
    static String appUser;
    static String appPassword;
    static String migratorUser;
    static String migratorPassword;
    static DataSource appDs;
    static DataSource migratorDs;

    private static boolean ready;

    private ProvisionedTenant() {
    }

    static synchronized void ensure() {
        if (ready) {
            return;
        }
        url = env("ZIIPSAN_IT_JDBC_URL", "jdbc:postgresql://host.docker.internal:55433/ziipsan");
        appUser = env("ZIIPSAN_IT_DB_USER", "ziipsan_app");
        // P0.3: senhas sem valor padrao versionado (ci-linux.sh exporta efemeras).
        appPassword = requireEnv("ZIIPSAN_IT_DB_PASSWORD");
        appDs = new DriverManagerDataSource(url, appUser, appPassword);
        migratorUser = env("ZIIPSAN_IT_MIGRATOR_USER", "ziipsan_migrator");
        migratorPassword = requireEnv("ZIIPSAN_IT_MIGRATOR_PASSWORD");
        migratorDs = new DriverManagerDataSource(url, migratorUser, migratorPassword);

        // 1. Migration de core em banco virgem (cria public.tenant_catalog e public.audit_log).
        new CoreMigrationRunner(migratorDs).run(null);

        // 1b. Resíduo da JVM anterior (surefire antes do failsafe, mesmo banco): apaga
        // vertical + catálogo + schema órfão do tenant compartilhado, identificado pelo CNPJ
        // fixo. Sem isso o provisionamento abaixo falha no UNIQUE tenant_catalog_cnpj_key.
        cleanSharedTenantResidue();

        // 2. Provisionamento: aplica todas as migrations de tenant de todos os módulos.
        TenantProvisioningService service = new TenantProvisioningService(
                appDs,
                new JdbcTemplate(appDs),
                Mockito.mock(AuditLogRepository.class),
                migratorDs);

        var result = service.provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                .tenantId(TENANT_ID)
                .schemaName(SCHEMA)
                .displayName("Concessionaria de Integracao")
                .cnpj(SHARED_TENANT_CNPJ)
                .email("it@teste.com.br")
                .phone("11999999999")
                .plan("PRO")
                // Com endereço e configurações de verdade: Map.toString() gerava "{street=...}", que o
                // cast ::jsonb recusa. Os testes antigos passavam null e nunca exercitaram isso.
                .address(java.util.Map.of("street", "Rua das Águas", "number", 100, "city", "Cidade \"Teste\""))
                .settings(java.util.Map.of("timezone", "America/Sao_Paulo", "billing", java.util.Map.of("dueDay", 10)))
                // P0.25: provisionamento sem seed de admin; usuario e papeis voltam na P0.13.
                .build());

        assertThat(result.success())
                .as("o provisionamento do tenant com todos os módulos deve concluir: %s", result.error())
                .isTrue();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
                if (SCHEMA.startsWith("it_")) {
                    st.execute("DROP SCHEMA IF EXISTS " + quoteIdentifier(SCHEMA) + " CASCADE");
                }
            } catch (Exception ignored) {
                // melhor esforço: o banco de integração é descartável
            }
            try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
                st.execute("DELETE FROM platform.tenant_vertical WHERE tenant_id = '" + TENANT_ID + "'");
                st.execute("DELETE FROM public.tenant_catalog WHERE id = '" + TENANT_ID + "'");
            } catch (Exception ignored) {
                // melhor esforço: a limpeza de ensure() cobre o resíduo na próxima JVM
            }
        }));
        ready = true;
    }

    /**
     * Remove o resíduo do tenant compartilhado deixado por uma JVM anterior no mesmo banco:
     * linhas de vertical e catálogo pelo CNPJ fixo, mais o schema órfão (o hook de shutdown
     * antigo dropava só o schema; o novo apaga tudo, mas a limpeza aqui cobre ambos).
     * Identificadores vindos do banco são citados com aspas duplicadas antes de interpolar.
     */
    private static void cleanSharedTenantResidue() {
        try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement();
             var rs = st.executeQuery(
                     "SELECT id, schema_name FROM public.tenant_catalog WHERE cnpj = '" + SHARED_TENANT_CNPJ + "'")) {
            var stale = new java.util.ArrayList<String[]>();
            while (rs.next()) {
                stale.add(new String[]{rs.getString(1), rs.getString(2)});
            }
            for (String[] row : stale) {
                st.execute("DELETE FROM platform.tenant_vertical WHERE tenant_id = '" + row[0] + "'");
                st.execute("DELETE FROM public.tenant_catalog WHERE id = '" + row[0] + "'");
                if (row[1] != null && row[1].startsWith("it_")) {
                    st.execute("DROP SCHEMA IF EXISTS " + quoteIdentifier(row[1]) + " CASCADE");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao limpar residuo do tenant compartilhado", e);
        }
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }

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
}
