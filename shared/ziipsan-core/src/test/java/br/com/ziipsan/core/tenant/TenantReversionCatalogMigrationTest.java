package br.com.ziipsan.core.tenant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ED-04: guardas de texto da core V13 (ambiente do tenant no catalogo), no mesmo
 * espirito das guardas de migration dos demais modulos: sem banco, o SQL e lido
 * como texto. O comportamento em banco (DEFAULT aplicado as linhas existentes,
 * indice criado) e provado no ensaio do pacote de reversao e na CI total, nao aqui.
 *
 * <p>Numeracao: a migration foi entregue pela ED-04 como core V11, mas o mapa
 * do plano v3.0 (secao 3) reserva a V11 para a ED-18 e atribui este conteudo a
 * core V13, da LC.15; a renumeracao foi executada pre-publicacao pelo pacote
 * correcao-ci-tenant-v13 (09/11/2026), contra a decisao registrada em
 * DECISOES-DO-DONO.md (secao 3). So o nome do arquivo, o cabecalho do SQL e
 * este caminho mudam; as guardas sao as mesmas.
 *
 * <p>Guardas centrais: a V13 ESTENDE public.tenant_catalog (nunca recria a tabela
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
        sql = read("/db/migration/core/V13__tenant_isolation_environment.sql");
        v7 = read("/db/migration/core/V7__tenant_shard_catalog.sql");
    }

    @Test
    @DisplayName("a V13 adiciona environment ao catalogo, sem recriar tabela nem coluna existente")
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
    @DisplayName("environment nao ganha lista fechada inventada: nenhum CHECK de valores na V13")
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
