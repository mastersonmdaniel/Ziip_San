package br.com.ziipsan.core.tenant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LC.15: guardas de texto da core V13_1 (conta da instancia dedicada no
 * catalogo), no mesmo espirito das guardas da V13
 * ({@link TenantReversionCatalogMigrationTest}): sem banco, o SQL e lido
 * como texto. O comportamento em banco (CHECK, indice parcial) e provado
 * na CI total, nao aqui.
 *
 * <p>Contexto: o mapa do plano v3.0 atribui a core V13, da LC.15, o
 * registro da instancia dedicada no catalogo (modo de isolamento,
 * ambiente e conta). A V13 publicada entregou ambiente/modo e reservou
 * a conta a LC.15; esta V13_1 e essa parte, sem reescrever a V13.
 */
class TenantDedicatedInstanceMigrationTest {

    private static String sql;

    private static String read(String path) throws IOException {
        try (InputStream in = TenantDedicatedInstanceMigrationTest.class.getResourceAsStream(path)) {
            assertThat(in).as("migration no classpath: %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @BeforeAll
    static void load() throws IOException {
        sql = read("/db/migration/core/V13_1__tenant_dedicated_instance_account.sql");
    }

    @Test
    @DisplayName("a V13_1 adiciona a conta da instancia dedicada ao catalogo, sem recriar tabela nem colunas existentes")
    void addsDedicatedAccountWithoutRecreating() {
        assertThat(sql).contains("ALTER TABLE public.tenant_catalog");
        assertThat(sql).contains("ADD COLUMN IF NOT EXISTS dedicated_aws_account_id VARCHAR(12)");
        assertThat(sql).doesNotContain("CREATE TABLE");
        assertThat(sql).doesNotContain("ADD COLUMN IF NOT EXISTS isolation_mode");
        assertThat(sql).doesNotContain("ADD COLUMN IF NOT EXISTS environment");
    }

    @Test
    @DisplayName("o formato da conta e travado: 12 digitos ou NULL")
    void accountFormatIsChecked() {
        assertThat(sql).contains("CHECK (dedicated_aws_account_id IS NULL OR dedicated_aws_account_id ~ '^[0-9]{12}$')");
    }

    @Test
    @DisplayName("nenhum dado semeado: a migration nao insere, atualiza nem apaga linhas")
    void noSeedData() {
        assertThat(sql).doesNotContainPattern("(?im)^\\s*INSERT\\s+INTO\\b");
        assertThat(sql).doesNotContainPattern("(?im)^\\s*UPDATE\\s+\\w+\\s+SET\\b");
        assertThat(sql).doesNotContainPattern("(?im)^\\s*DELETE\\s+FROM\\b");
    }

    @Test
    @DisplayName("a coluna nasce anulavel: tenants sem instancia dedicada ficam com NULL")
    void columnIsNullableByDesign() {
        assertThat(sql).doesNotContain("dedicated_aws_account_id VARCHAR(12) NOT NULL");
        assertThat(sql).contains("WHERE dedicated_aws_account_id IS NOT NULL");
    }
}
