package br.com.ziipsan.platform.training;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistência do SQL pendente da ED-11 (precedente ED-09/ED-14/ED-17): o
 * arquivo vive em db/pendente porque o §3 não reserva versão para a ED-11
 * — este teste trava as garantias que o aceite exige do schema de
 * governança do treinamento. Na integração (número da sequência global
 * atribuído, depois do SQL da ED-09), o caminho muda para
 * db/migration/core e este teste é apontado para ele.
 */
class TrainingMigrationConsistencyTest {

    private static String sql() throws Exception {
        try (InputStream in = TrainingMigrationConsistencyTest.class
                .getResourceAsStream("/db/pendente/ED-11__platform_training.sql")) {
            assertThat(in).as("SQL pendente da ED-11 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("o plano é de 40 h por CHECK e CONCLUDED exige as 40 h e todos os participantes")
    void completionGuards() throws Exception {
        String sql = sql();
        assertThat(sql).contains("CHECK (planned_hours = 40)");
        assertThat(sql).contains("CHECK (status <> 'CONCLUDED' OR delivered_hours >= planned_hours)");
        assertThat(sql).contains("CHECK (status <> 'CONCLUDED' OR participants_completed = participants_enrolled)");
    }

    @Test
    @DisplayName("pré-treinamento sintético por CHECK, READY só com varredura limpa e clone referenciado")
    void preTrainingGuards() throws Exception {
        String sql = sql();
        assertThat(sql).contains("CHECK (synthetic = TRUE)");
        assertThat(sql).contains("CHECK (status <> 'READY' OR sweep_violation_count = 0)");
        assertThat(sql).contains("CHECK (source <> 'MASKED_CLONE' OR clone_id IS NOT NULL)");
        assertThat(sql).contains("CHECK (fiscal_production_allowed = FALSE)");
        // R1: massa sintética não tem clone — coerência origem x clone por CHECK.
        assertThat(sql).contains("CHECK (source <> 'SYNTHETIC_MASS' OR clone_id IS NULL)");
        // R1: a finalidade TRAINING do clone é travada por trigger (CHECK
        // não lê tenant_clone): recusa na escrita do pré-treinamento e na
        // troca de finalidade de um clone já em uso como ambiente.
        assertThat(sql).contains("pretraining_clone_training_guard");
        assertThat(sql).contains("IF v_purpose <> 'TRAINING' THEN");
        assertThat(sql).contains("CREATE TRIGGER trg_pretraining_clone_training");
        assertThat(sql).contains("BEFORE INSERT OR UPDATE ON platform.pretraining_tenant");
        assertThat(sql).contains("tenant_clone_training_purpose_guard");
        assertThat(sql).contains("CREATE TRIGGER trg_tenant_clone_training_purpose");
        assertThat(sql).contains("BEFORE UPDATE OF purpose ON platform.tenant_clone");
    }

    @Test
    @DisplayName("customização aceita exige decisão e nota de reversão; nenhuma tabela guarda dado pessoal")
    void customizationGuardsAndNoPersonalData() throws Exception {
        String sql = sql();
        assertThat(sql).contains("CHECK (status <> 'ACCEPTED' OR (decided_by IS NOT NULL");
        // R2: string vazia/só-espaços não conta como decisão nem como nota
        // de reversão — mesma estrictez do isBlank do Java.
        assertThat(sql).contains("length(trim(decided_by)) > 0");
        assertThat(sql).contains("length(trim(rollback_note)) > 0");
        String lower = sql.toLowerCase();
        assertThat(lower).doesNotContain("participant_name");
        assertThat(lower).doesNotContain("holder_name");
        assertThat(lower).doesNotContain("document_number");
        assertThat(lower).doesNotContain("original_enrollment");
    }
}
