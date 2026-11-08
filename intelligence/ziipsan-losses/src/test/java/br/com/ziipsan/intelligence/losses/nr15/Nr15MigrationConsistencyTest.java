package br.com.ziipsan.intelligence.losses.nr15;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia do SQL pendente da ED-78 com o desenho: FK composta
 * sempre com UNIQUE (tenant_id, id) no pai (licao C3), nenhuma linha
 * semeada, IVI travado igual ao ILI, registro RLS por tabela.
 * O caminho do SQL vem de -Ded78.sql.path (harness) ou da busca a
 * partir do diretorio de trabalho.
 */
class Nr15MigrationConsistencyTest {

    private static String sql() throws IOException {
        String property = System.getProperty("ed78.sql.path");
        if (property != null) {
            return Files.readString(Path.of(property));
        }
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("intelligence/ziipsan-losses/src/main/resources/db/pendente/"
                    + "ED-78__nr15_outputs.sql");
            if (Files.exists(candidate)) return Files.readString(candidate);
            candidate = dir.resolve("ed-78/entrega/intelligence/ziipsan-losses/src/main/resources/"
                    + "db/pendente/ED-78__nr15_outputs.sql");
            if (Files.exists(candidate)) return Files.readString(candidate);
            dir = dir.getParent();
        }
        throw new IllegalStateException("SQL da ED-78 nao localizado (passe -Ded78.sql.path)");
    }

    @Test
    @DisplayName("nenhuma linha semeada e nenhuma constante de norma gravada")
    void noSeedData() throws IOException {
        String sql = sql();
        assertThat(sql).doesNotContain("INSERT INTO");
        assertThat(sql).contains("register_tenant_table('nr15_annual_balances')");
        assertThat(sql).contains("register_tenant_table('nr15_management_plans')");
    }

    @Test
    @DisplayName("toda tabela tem UNIQUE (tenant_id, id) e toda FK e composta (licao C3)")
    void compositeFkDiscipline() throws IOException {
        String sql = sql();
        for (String table : new String[]{"nr15_annual_balances", "nr15_annual_declarations",
                "nr15_management_plans", "nr15_plan_actions", "nr15_plan_metering_evolution",
                "nr15_plan_declared_targets", "nr15_plan_monitoring"}) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).contains("register_tenant_table('" + table + "')");
        }
        long uniques = sql.lines().filter(l -> l.contains("UNIQUE (tenant_id, id)")).count();
        assertThat(uniques).isGreaterThanOrEqualTo(7);
        assertThat(sql).contains("FOREIGN KEY (tenant_id, balance_id)");
        assertThat(sql).contains("FOREIGN KEY (tenant_id, declaration_id)");
        assertThat(sql).contains("FOREIGN KEY (tenant_id, plan_id)");
        assertThat(sql).contains("FOREIGN KEY (tenant_id, revision_of_id)");
        // Nenhuma FK simples para tabela desta entrega.
        assertThat(sql).doesNotContain("REFERENCES nr15_annual_balances (id)");
        assertThat(sql).doesNotContain("REFERENCES nr15_management_plans (id)");
    }

    @Test
    @DisplayName("IVI travado igual ao ILI no banco; publicacao exige canal, referencia e instante")
    void iviEqualsIliAndPublicationGuards() throws IOException {
        String sql = sql();
        assertThat(sql).contains("ck_nr15_balances_ivi_eq_ili");
        assertThat(sql).contains("ivi IS NOT DISTINCT FROM ili");
        assertThat(sql).contains("ck_nr15_declarations_published");
        assertThat(sql).contains("ck_nr15_plans_revision");
        assertThat(sql).contains("ck_nr15_actions_schedule");
    }
}
