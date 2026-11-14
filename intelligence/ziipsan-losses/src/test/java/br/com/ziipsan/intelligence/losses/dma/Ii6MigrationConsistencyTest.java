package br.com.ziipsan.intelligence.losses.dma;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia da numeracao do II-6 (Ciclo 4 do programa 5x PDCA GIS):
 * o SQL saiu de db/pendente/ para a localizacao versionada propria
 * com numero da sequencia global unica (P3.12; nota autoritativa de
 * numeracao de 08/11/2026 — as faixas de modulo, incluindo a V22_x
 * reservada no cabecalho original, estao encerradas).
 * Numeracao final (complemento do Ciclo 4; REVISAO-CICLO-4.md seccao
 * 4.3, pendencia P-PDCAGIS4-02): ED-85 e II-6 numerados NO MESMO ACTO —
 * ED-85 = V113 (cria loss_dmc_profiles, a tabela-mae da FK do II-6) e
 * II-6 = V114, imediatamente a seguir. Justificacao integral:
 * PLANO-CICLO-4.md seccao 2 e RELATORIO-COMPLEMENTO-CICLO-4.md do
 * pacote pdca-gis-ciclo-4.
 */
class Ii6MigrationConsistencyTest {

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 10 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("intelligence/ziipsan-losses/src/main/resources/db"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("raiz do repo nao localizada a partir do diretorio de trabalho");
    }

    private static String versionedSql() throws IOException {
        String property = System.getProperty("ii6.sql.path");
        if (property != null) {
            return Files.readString(Path.of(property));
        }
        return Files.readString(repoRoot().resolve(
                "intelligence/ziipsan-losses/src/main/resources/db/migration/tenant/V114__dma_aggregation.sql"));
    }

    private static String ed85Sql() throws IOException {
        return Files.readString(repoRoot().resolve(
                "intelligence/ziipsan-losses/src/main/resources/db/migration/tenant/V113__losses_dmc_balance.sql"));
    }

    @Test
    @DisplayName("o SQL vive na localizacao versionada V114 e saiu de db/pendente")
    void sqlLivesAtVersionedPathAndLeftPendente() {
        Path root = repoRoot();
        assertThat(root.resolve("intelligence/ziipsan-losses/src/main/resources/db/migration/tenant/"
                + "V114__dma_aggregation.sql")).exists();
        assertThat(root.resolve("intelligence/ziipsan-losses/src/main/resources/db/migration/tenant/"
                + "V113__dma_aggregation.sql")).doesNotExist();
        assertThat(root.resolve("intelligence/ziipsan-losses/src/main/resources/db/pendente/"
                + "II-6__dma_aggregation.sql")).doesNotExist();
    }

    @Test
    @DisplayName("cabecalho declara o numero V114, a sequencia global e a dependencia da ED-85 (V113)")
    void headerDeclaresNumberAndDependency() throws IOException {
        String sql = versionedSql();
        assertThat(sql).contains("V114");
        assertThat(sql).contains("V113__losses_dmc_balance.sql");
        assertThat(sql).contains("loss_dmc_profiles");
        assertThat(sql).contains("ED-85");
        assertThat(sql).doesNotContain("PENDENTE DE NUMERACAO");
    }

    @Test
    @DisplayName("disciplina C3: 3 tabelas com UNIQUE (tenant_id, id), FKs compostas e registo RLS, sem dados semeados")
    void compositeFkDisciplineAndNoSeed() throws IOException {
        String sql = versionedSql();
        for (String table : new String[]{"ii6_dma_definitions", "ii6_dma_members", "ii6_dma_period_balances"}) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).contains("register_tenant_table('" + table + "')");
        }
        long uniques = sql.lines().filter(l -> l.contains("UNIQUE (tenant_id, id)")).count();
        assertThat(uniques).isGreaterThanOrEqualTo(3);
        assertThat(sql).contains("FOREIGN KEY (tenant_id, dma_definition_id)");
        assertThat(sql).contains("FOREIGN KEY (tenant_id, dmc_profile_id)");
        assertThat(sql).contains("REFERENCES loss_dmc_profiles (tenant_id, id)");
        assertThat(sql).doesNotContain("INSERT INTO");
    }

    @Test
    @DisplayName("V114 consta da lista de publicadas (a numeracao actualiza a lista no mesmo acto — nota autoritativa, seccao 2)")
    void v114IsInPublishedList() throws IOException {
        Path list = repoRoot().resolve(
                "platform/ziipsan-platform/src/main/resources/migration-policy/published-tenant-migrations.txt");
        assertThat(Files.readString(list)).contains("V114__dma_aggregation.sql");
    }

    @Test
    @DisplayName("a ED-85 vive na localizacao versionada V113, saiu de db/pendente e cria loss_dmc_profiles")
    void ed85LivesAtV113AndCreatesDmcProfiles() throws IOException {
        Path root = repoRoot();
        assertThat(root.resolve("intelligence/ziipsan-losses/src/main/resources/db/migration/tenant/"
                + "V113__losses_dmc_balance.sql")).exists();
        assertThat(root.resolve("intelligence/ziipsan-losses/src/main/resources/db/pendente/"
                + "ED-85__losses_dmc_balance.sql")).doesNotExist();
        String sql = ed85Sql();
        assertThat(sql).contains("V113");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS loss_dmc_profiles");
        assertThat(sql).doesNotContain("PENDENTE DE NUMERACAO");
    }

    @Test
    @DisplayName("na lista de publicadas, a V113 (ED-85) precede imediatamente a V114 (II-6)")
    void publishedListOrdersEd85ImmediatelyBeforeIi6() throws IOException {
        Path list = repoRoot().resolve(
                "platform/ziipsan-platform/src/main/resources/migration-policy/published-tenant-migrations.txt");
        java.util.List<String> lines = Files.readAllLines(list);
        int ed85 = lines.indexOf("V113__losses_dmc_balance.sql");
        int ii6 = lines.indexOf("V114__dma_aggregation.sql");
        assertThat(ed85).as("V113__losses_dmc_balance.sql na lista de publicadas").isGreaterThanOrEqualTo(0);
        assertThat(ii6).as("V114__dma_aggregation.sql imediatamente apos a ED-85").isEqualTo(ed85 + 1);
    }
}
