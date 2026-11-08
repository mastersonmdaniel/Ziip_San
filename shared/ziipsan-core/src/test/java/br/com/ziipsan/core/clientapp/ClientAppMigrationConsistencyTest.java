package br.com.ziipsan.core.clientapp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia da core V11_1 (ED-39) com os enums do Java: os literais do
 * CHECK de plataforma/estado e os parametros novos existem no SQL, e o
 * SQL nao semeia conta de loja, identificador real nem certificado.
 * Le o arquivo da migration no caminho do repo (integracao); nesta VM a
 * prova roda sobre a copia da entrega (ver relatorio).
 */
class ClientAppMigrationConsistencyTest {

    private static String sql() throws IOException {
        // Procura a migration subindo a partir do diretorio de trabalho:
        // no repo, o caminho e shared/...; no pacote local da entrega,
        // entrega/shared/... — robusto ao CWD do harness.
        String inRepo = "shared/ziipsan-core/src/main/resources/db/migration/core/V11_1__client_app_config.sql";
        String inPackage = "entrega/" + inRepo;
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path a = dir.resolve(inRepo);
            if (Files.exists(a)) {
                return Files.readString(a);
            }
            Path b = dir.resolve(inPackage);
            if (Files.exists(b)) {
                return Files.readString(b);
            }
            dir = dir.getParent();
        }
        throw new IOException("V11_1__client_app_config.sql nao localizada a partir do diretorio de trabalho");
    }

    @Test
    @DisplayName("V11_1 cria platform.client_app_config com plataformas e estados dos enums")
    void tableAndChecks() throws IOException {
        String sql = sql();
        assertThat(sql).contains("platform.client_app_config");
        assertThat(sql).contains("'ANDROID'").contains("'IOS'");
        for (String s : new String[]{"DRAFT", "IN_REVIEW", "PUBLISHED", "SUSPENDED", "UNPUBLISHED"}) {
            assertThat(sql).contains("'" + s + "'");
        }
        assertThat(sql).contains("uq_client_app_tenant_platform");
        assertThat(sql).contains("uq_client_app_identifier_platform");
    }

    @Test
    @DisplayName("D5: CHECK de PUBLISHED exige versao, build e conta; semver e build>0 espelhados no SQL")
    void publishedCheckMirrorsJava() throws IOException {
        String sql = sql();
        assertThat(sql).contains("published_build IS NOT NULL");
        assertThat(sql).contains("published_build > 0");
        assertThat(sql).contains("published_version ~ '^[0-9]+[.][0-9]+[.][0-9]+$'");
    }

    @Test
    @DisplayName("V11_1 cadastra as contas de loja sem fabrica e nao semeia dado real")
    void parametersWithoutFactory() throws IOException {
        String sql = sql();
        assertThat(sql).contains("app.client_android_store_account");
        assertThat(sql).contains("app.client_ios_store_account");
        // Sem INSERT de linha de configuracao: nenhum tenant/app semeado.
        assertThat(sql).doesNotContain("INSERT INTO platform.client_app_config");
    }
}
