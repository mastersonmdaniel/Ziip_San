package br.com.ziipsan.commercial.fiscal.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistência da V4_7 (parte NFS-e da P3.6): as tabelas e os CHECKs espelham os enums do
 * código, e a parte da NFAg-e (P2.6, ainda não executada) NÃO é inventada aqui.
 */
class NfseMigrationConsistencyTest {

    private static String v47() throws IOException {
        try (InputStream in = NfseMigrationConsistencyTest.class.getResourceAsStream(
                "/db/migration/tenant/V4_7__nfse_avulsa_parte.sql")) {
            assertThat(in).as("migration V4_7 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("V4_7 cria só as 2 tabelas da NFS-e avulsa, com as 3 etapas do Ato Conjunto 4/2026")
    void nfseTablesAndStages() throws IOException {
        String sql = v47();
        assertThat(sql).contains("fiscal_avulso_service_types").contains("fiscal_nfse_avulsas");
        assertThat(sql).contains("ISS_FORA_HIPOTESES_ESPECIAIS")
                .contains("DEMAIS_HIPOTESES").contains("SIMPLES_NACIONAL");
        assertThat(sql).contains("register_tenant_table('fiscal_avulso_service_types')")
                .contains("register_tenant_table('fiscal_nfse_avulsas')");
    }

    @Test
    @DisplayName("V4_7 não cria tabela de NFAg-e: a parte da P2.6 fica para a P2.6")
    void noNfageTableInvented() throws IOException {
        String sql = v47().toLowerCase();
        assertThat(sql).doesNotContain("create table if not exists fiscal_nfage");
        assertThat(sql).doesNotContain("create table fiscal_nfage");
        // Trava reforçada na revisão cruzada da P3.6: qualquer CREATE TABLE de tabela
        // nfage (com/sem IF NOT EXISTS, espaços/quebras variáveis, aspas ou schema)
        // reprova — a forma literal anterior deixava passar variações de formatação.
        assertThat(sql).doesNotContainPattern(
                "create\\s+table\\s+(if\\s+not\\s+exists\\s+)?[\"\\w.]*nfage");
        assertThat(v47()).contains("P2.6");
    }
}
