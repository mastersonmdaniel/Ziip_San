package br.com.ziipsan.platform.implantation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistência do SQL pendente da ED-09 (precedente ED-14/ED-17): o arquivo
 * vive em db/pendente porque o §3 não reserva versão para a ED-09 — este
 * teste trava as garantias que o aceite exige do schema de governança do
 * clone. Na integração (número da sequência global atribuído), o caminho
 * muda para db/migration/core e este teste é apontado para ele.
 */
class ImplantationMigrationConsistencyTest {

    private static String sql() throws Exception {
        try (InputStream in = ImplantationMigrationConsistencyTest.class
                .getResourceAsStream("/db/pendente/ED-09__platform_implantation_clone.sql")) {
            assertThat(in).as("SQL pendente da ED-09 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("clone nasce sintético por CHECK e só fica READY com a varredura limpa")
    void cloneGuards() throws Exception {
        String sql = sql();
        assertThat(sql).contains("CHECK (synthetic = TRUE)");
        assertThat(sql).contains("CHECK (status <> 'READY' OR sweep_violation_count = 0)");
        assertThat(sql).contains("CHECK (clone_tenant_id <> source_tenant_id)");
        assertThat(sql).contains("purpose IN ('HOMOLOGATION', 'TRAINING')");
    }

    @Test
    @DisplayName("a simulação registrada é sempre de tenant sintético e do plano de 60 dias")
    void simulationGuards() throws Exception {
        String sql = sql();
        assertThat(sql).contains("CHECK (synthetic_tenant = TRUE)");
        assertThat(sql).contains("CHECK (plan_total_days = 60)");
    }

    @Test
    @DisplayName("nenhuma tabela do SQL guarda dado pessoal nem a correspondência do piloto")
    void noPersonalDataColumns() throws Exception {
        String sql = sql().toLowerCase();
        assertThat(sql).doesNotContain("holder_name");
        assertThat(sql).doesNotContain("document_number");
        assertThat(sql).doesNotContain("original_enrollment");
        assertThat(sql).doesNotContain("substitute_code");
    }
}
