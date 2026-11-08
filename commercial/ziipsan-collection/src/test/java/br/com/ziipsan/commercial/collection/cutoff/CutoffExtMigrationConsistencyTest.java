package br.com.ziipsan.commercial.collection.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Guarda do SQL pendente: tabelas, CHECK da supressao e parametros sem numero de migration. */
class CutoffExtMigrationConsistencyTest {

    private static String sql() throws Exception {
        Path module = Path.of("").toAbsolutePath();
        Path file = module.resolve("src/main/resources/db/pendente/PENDENTE__cutoff_ext_levels_priority_letters.sql");
        if (!Files.exists(file)) {
            file = module.resolve("entrega/commercial/ziipsan-collection/src/main/resources/db/pendente/PENDENTE__cutoff_ext_levels_priority_letters.sql");
        }
        assertThat(Files.exists(file)).as("SQL pendente da ED-29").isTrue();
        return Files.readString(file);
    }

    @Test
    @DisplayName("SQL cobre niveis, plano diario, notificacoes pos-corte, cartas e liberacao de esgoto")
    void tablesAndChecks() throws Exception {
        String sql = sql();
        for (String table : new String[]{"cutoff_level_executions", "cutoff_daily_plans",
                "cutoff_daily_plan_items", "cut_unit_notices", "collection_letters", "sewer_release_letters"}) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).contains("register_tenant_table('" + table + "')");
        }
        assertThat(sql).contains("'VALVE', 'BRANCH', 'SUPPRESSION'");
        assertThat(sql).contains("frozen_variables JSONB");
    }

    @Test
    @DisplayName("SQL declara os 7 parametros novos, todos sem padrao de fabrica, e nenhum numero de versao")
    void parametersDeclaredWithoutNumber() throws Exception {
        String sql = sql();
        for (String key : new String[]{CutoffExtParameters.DAILY_ORDER_LIMIT, CutoffExtParameters.PRIORITY_ORDER,
                CutoffExtParameters.LEVEL_SCHEDULE, CutoffExtParameters.COLLECTION_LETTER_TEMPLATE,
                CutoffExtParameters.CUT_UNIT_NOTICE_TEMPLATE, CutoffExtParameters.SEWER_RELEASE_TEMPLATE,
                CutoffExtParameters.RULER_CUTOFF_STEP_CODE}) {
            assertThat(sql).contains("'" + key + "'");
        }
        assertThat(sql).contains("PENDENTE DE NUMERO");
        assertThat(sql).doesNotContainPattern("V\\d+_\\d+__cutoff_ext");
    }
}
