package br.com.ziipsan.platform.engagement;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consistencia do SQL pendente da ED-101 (padrao ED-100): trava a
 * declaracao de pendencia de numeracao, as tabelas e as regras de
 * privacidade gravadas no schema (sem coluna de terceiro; piso >= 5).
 */
class EngagementMigrationConsistencyTest {

    private String sql() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/db/pendente/ED-101__engagement.sql")) {
            assertNotNull(in, "SQL pendente da ED-101 no classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void pendenciaDeNumeracaoDeclarada() throws Exception {
        String sql = sql();
        assertTrue(sql.contains("PENDENTE DE NUMERACAO"));
        assertTrue(sql.contains("ED-101__"));
    }

    @Test
    void tabelasDoEngajamentoPresentes() throws Exception {
        String sql = sql();
        assertTrue(sql.contains("eng_user_enrollments"));
        assertTrue(sql.contains("eng_consumption_goals"));
        assertTrue(sql.contains("eng_peer_reports"));
        assertTrue(sql.contains("eng_goal_milestone_awards"));
        assertTrue(sql.contains("eng_reminder_log"));
    }

    @Test
    void privacidadeGravadaNoSchema() throws Exception {
        String sql = sql();
        // piso do grupo gravado como CHECK >= 5 (o piso da ED-38/O9-3)
        assertTrue(sql.contains("min_group_size    int NOT NULL CHECK (min_group_size >= 5)"));
        // relatorio suprimido nao guarda agregado (CHECK de publicado x suprimido)
        assertTrue(sql.contains("NOT published AND suppression IS NOT NULL"));
        // chaves da ED-38 nao sao redefinidas aqui
        assertTrue(sql.contains("definidos pela ED-38"));
        assertTrue(sql.contains("eng.gamification_milestones"));
        assertTrue(sql.contains("[A DEFINIR pelo tenant]"));
    }
}
