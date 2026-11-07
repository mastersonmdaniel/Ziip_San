package br.com.ziipsan.core.field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas da core V11 (ED-18) sem banco, no padrao dos MigrationConsistencyTest do
 * projeto: a tabela e exatamente a do mapa de migrations do plano v3.0, os estados
 * sao os do ADR-0030 e a migration nao cria a V11_1 (que e da ED-39).
 */
class AppDeviceMigrationConsistencyTest {

    private static final String SQL = readSql();

    private static String readSql() {
        try (InputStream in = AppDeviceMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/core/V11__app_device.sql")) {
            assertThat(in).as("migration core V11 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("a V11 cria platform.app_device com os estados do plano e sem dado pessoal alem do sub")
    void tableAndStates() {
        assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS platform.app_device");
        assertThat(SQL).contains("'PENDING_APPROVAL'", "'ACTIVE'", "'BLOCKED'", "'RETIRED'");
        assertThat(SQL).contains("user_sub VARCHAR(255)");
        // Colunas de dado pessoal nao existem: a guarda mira DEFINICOES de coluna
        // (a palavra em comentario nao e coluna — a versao original desta guarda
        // reprovava no comentario "e-mail, CPF ou telefone" do cabecalho).
        assertThat(SQL).doesNotContain("cpf VARCHAR", "email VARCHAR", "telefone VARCHAR", "full_name");
    }

    @Test
    @DisplayName("a V11 registra os parametros da ED-18 e nao semeia titular de loja")
    void parameters() {
        assertThat(SQL).contains("'read.ocr_mode'", "'read.ocr_tolerance_digits'", "'app.store_account_owner'");
        assertThat(SQL).contains("'[\"OFF\", \"SUGGEST\", \"REQUIRE\"]'");
    }

    @Test
    @DisplayName("a V11 cabe os vereditos de atestacao e as colunas do BaseEntity da entidade")
    void entityFit() {
        // MEETS_DEVICE_INTEGRITY tem 22 caracteres e MEETS_BASIC_INTEGRITY, 21:
        // VARCHAR(20) recusaria todo registro com atestacao.
        assertThat(SQL).contains("attestation_verdict VARCHAR(25)");
        // AppDevice estende BaseEntity: created_by/updated_by sao colunas mapeadas.
        assertThat(SQL).contains("created_by UUID", "updated_by UUID");
    }

    @Test
    @DisplayName("a V11 nao e a V11_1 da ED-39: nenhuma tabela de configuracao de app do cliente")
    void notEd39() {
        assertThat(SQL).doesNotContain("app_client", "customer_app", "store_account_id");
    }
}
