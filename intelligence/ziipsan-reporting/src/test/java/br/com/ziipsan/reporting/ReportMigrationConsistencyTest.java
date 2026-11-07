package br.com.ziipsan.reporting;

import br.com.ziipsan.reporting.registry.ReportRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia migration x catalogo: toda definicao do registro tem a view
 * criada na V15_1 e a linha de fabrica semeada, e a migration e a reservada
 * da tarefa (V15_1, faixa reporting do plano secao 3).
 */
class ReportMigrationConsistencyTest {

    private String migration() throws Exception {
        Path path = Path.of("src/main/resources/db/migration/tenant/V15_1__reporting_core_and_views.sql");
        if (!Files.exists(path)) {
            path = Path.of("intelligence/ziipsan-reporting").resolve(path);
        }
        return Files.readString(path);
    }

    @Test
    void todaDefinicaoTemViewESeedNaMigration() throws Exception {
        String sql = migration();
        for (var definition : new ReportRegistry().all()) {
            assertThat(sql).as("view de %s", definition.code()).contains(definition.viewName());
            assertThat(sql).as("seed de %s", definition.code())
                    .contains("'" + definition.code() + "'");
            assertThat(sql).as("permissao de %s", definition.code())
                    .contains(definition.requiredPermission());
        }
    }

    @Test
    void migrationAlinhadaABaseDasEntidades() throws Exception {
        // Correcao B6: ReportSchedule/ReportExecution estendem
        // TenantAwareEntity/BaseEntity (version @Version nullable=false,
        // tenant_schema; BaseEntity ainda traz updated_at/created_by/
        // updated_by). A V15_1 tem de criar TODAS essas colunas, senao o
        // SQL do repositorio quebra em runtime.
        String sql = migration();
        String executionsBlock = sql.substring(sql.indexOf("CREATE TABLE IF NOT EXISTS report_executions"),
                sql.indexOf("CREATE TABLE IF NOT EXISTS report_schedules"));
        String schedulesBlock = sql.substring(sql.indexOf("CREATE TABLE IF NOT EXISTS report_schedules"),
                sql.indexOf("SELECT register_tenant_table('report_executions'"));
        for (String block : List.of(executionsBlock, schedulesBlock)) {
            assertThat(block).contains("version BIGINT").contains("tenant_schema")
                    .contains("created_by").contains("updated_by").contains("updated_at");
        }
    }

    @Test
    void viewsDeIsencaoEDevolucaoLeemTabelasReais() throws Exception {
        // Correcoes B1/B2: nada de coluna status em
        // optional_category_enrollments nem tabela payment_refunds
        // inventada - as views leem as fontes reais (V4_4 e V6_5/P3.29).
        String sql = migration();
        assertThat(sql).contains("optional_category_enrollments")
                .contains("category_code AS categoria_codigo")
                .contains("refund_requests")
                .contains("unidentified_payments");
    }

    @Test
    void migrationCriaAsTresTabelasDoNucleo() throws Exception {
        String sql = migration();
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS report_definitions")
                .contains("CREATE TABLE IF NOT EXISTS report_executions")
                .contains("CREATE TABLE IF NOT EXISTS report_schedules")
                .contains("register_tenant_table('report_executions'")
                .contains("register_tenant_table('report_schedules'");
        // views leem com RLS do tenant (security_invoker), nunca como dono
        assertThat(sql).contains("security_invoker = true");
    }
}
