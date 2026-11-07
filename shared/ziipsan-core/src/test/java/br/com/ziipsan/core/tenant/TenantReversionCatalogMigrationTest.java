package br.com.ziipsan.core.tenant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ED-04: guardas de texto da core V11 (ambiente do tenant no catalogo), no mesmo
 * espirito das guardas de migration dos demais modulos: sem banco, o SQL e lido
 * como texto. O comportamento em banco (DEFAULT aplicado as linhas existentes,
 * indice criado) e provado no ensaio do pacote de reversao e na CI total, nao aqui.
 *
 * <p>Guardas centrais: a V11 ESTENDE public.tenant_catalog (nunca recria a tabela
 * nem a coluna isolation_mode, que nasceu na core V7 com a lista fechada SCHEMA,
 * SHARED, DEDICATED) e nao inventa lista fechada para environment.
 */
class TenantReversionCatalogMigrationTest {

    private static String sql;
    private static String v7;

    private static String read(String path) throws IOException {
        try (InputStream in = TenantReversionCatalogMigrationTest.class.getResourceAsStream(path)) {
            assertThat(in).as("migration no classpath: %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @BeforeAll
    static void load() throws IOException {
        sql = read("/db/migration/core/V11__tenant_isolation_environment.sql");
        v7 = read("/db/migration/core/V7__tenant_shard_catalog.sql");
    }

    @Test
    @DisplayName("a V11 adiciona environment ao catalogo, sem recriar tabela nem coluna existente")
    void extendsCatalogWithoutRecreating() {
        assertThat(sql).contains("ALTER TABLE public.tenant_catalog");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS environment VARCHAR(50) NOT NULL DEFAULT 'SAAS'");
        assertThat(sql).doesNotContain("CREATE TABLE");
        assertThat(sql).doesNotContain("ADD COLUMN IF NOT EXISTS isolation_mode");
        assertThat(sql).doesNotContain("isolation_mode IN");
    }

    @Test
    @DisplayName("o modo de isolamento continua sendo o da V7: SCHEMA, SHARED, DEDICATED, e so esses")
    void isolationModeStillComesFromV7() {
        assertThat(v7).contains("CHECK (isolation_mode IN ('SCHEMA', 'SHARED', 'DEDICATED'))");
        assertThat(sql).contains("'SCHEMA'").contains("'SHARED'").contains("'DEDICATED'");
    }

    @Test
    @DisplayName("environment nao ganha lista fechada inventada: nenhum CHECK de valores na V11")
    void environmentHasNoInventedClosedList() {
        assertThat(sql).doesNotContain("CHECK (environment IN");
    }

    @Test
    @DisplayName("nenhum dado semeado: a migration nao insere, atualiza nem apaga linhas")
    void noSeedData() {
        assertThat(sql).doesNotContainPattern("(?im)^\\s*INSERT\\s+INTO\\b");
        assertThat(sql).doesNotContainPattern("(?im)^\\s*UPDATE\\s+\\w+\\s+SET\\b");
        assertThat(sql).doesNotContainPattern("(?im)^\\s*DELETE\\s+FROM\\b");
    }
}
