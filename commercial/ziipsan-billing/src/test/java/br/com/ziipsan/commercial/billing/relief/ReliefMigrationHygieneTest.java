package br.com.ziipsan.commercial.billing.relief;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Grupos 13 e 14 — migrations/RLS/higiene e contabilidade (ED-32):
 * <ul>
 *   <li>M1 declara as 3 tabelas, UNIQUE (tenant_id, id), register_tenant_table
 *       e ZERO seed de negocio;</li>
 *   <li>higiene de fontes: nenhum literal de aliquota em codigo de producao
 *       do pacote relief (a aliquota efectiva e sempre parametro);</li>
 *   <li>ED-32: a geracao preserva categoria/subcategoria/regra de cada linha —
 *       as chaves de segregacao contabil sao as mesmas; so os valores mudam,
 *       logo nenhum de-para novo e necessario.</li>
 * </ul>
 * A prova de aplicacao em PostgreSQL real corre no harness do pacote
 * (prova-pg), nao neste teste unitario.
 */
class ReliefMigrationHygieneTest {

    private String migrationSql() throws IOException {
        try (var in = getClass().getResourceAsStream(
                "/db/migration/tenant/V115__tariff_relief_program.sql")) {
            assertThat(in).as("migration M1 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("grupo 13: M1 — tabelas, ancora tenant, registo RLS e nenhum seed")
    void migrationShape() throws IOException {
        String sql = migrationSql();
        for (String table : List.of("tariff_relief_programs", "tariff_relief_rates", "tariff_relief_links")) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).contains("register_tenant_table('" + table + "')");
            assertThat(sql).contains("uq_" + table + "_tenant_id UNIQUE (tenant_id, id)");
        }
        assertThat(sql).doesNotContain("INSERT INTO");
        assertThat(sql).doesNotContain("ALTER TABLE tariffs ");
        assertThat(sql).doesNotContain("ALTER TABLE tariff_versions ");
        assertThat(sql).doesNotContain("ALTER TABLE bills ");
    }

    @Test
    @DisplayName("grupo 13: higiene — nenhum literal de aliquota no codigo de producao do relief")
    void noRateLiteralInProductionCode() throws IOException {
        Path main = Paths.get("src/main/java/br/com/ziipsan/commercial/billing/relief");
        assertThat(Files.isDirectory(main)).isTrue();
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(main)) {
            sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertThat(sources).isNotEmpty();
        // Literais decimais que nao sejam a fronteira 100 da formula (percentual).
        var pattern = java.util.regex.Pattern.compile("new BigDecimal\\(\"(?!100\\b)\\d+\\.\\d+\"\\)");
        for (Path source : sources) {
            String code = Files.readString(source, StandardCharsets.UTF_8)
                    .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
            assertThat(pattern.matcher(code).find())
                    .as("literal de aliquota em " + source.getFileName()).isFalse();
        }
    }

    @Test
    @DisplayName("grupo 14 (ED-32): geracao preserva as chaves de segregacao (categoria/subcategoria por regra)")
    void generationPreservesAccountingSegregationKeys() {
        var generator = new RelievedVersionGenerator(new RelievedVersionGenerator.GenerationLedger());
        var source = br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1();
        var relieved = generator.generateTariffVersion(ReliefFixtures.program(false), source,
                ReliefFixtures.tenantRate("10"), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 4).relievedVersion();
        assertThat(relieved.rules()).hasSameSizeAs(source.rules());
        for (int i = 0; i < source.rules().size(); i++) {
            assertThat(relieved.rules().get(i).categoryId())
                    .isEqualTo(source.rules().get(i).categoryId());
            assertThat(relieved.rules().get(i).subcategoryId())
                    .isEqualTo(source.rules().get(i).subcategoryId());
        }
        // Snapshot: a proveniencia viaja nas chaves aditivas, nao em chaves contabeis novas.
        var link = generator.generateTariffVersion(ReliefFixtures.program(false), source,
                ReliefFixtures.tenantRate("10"), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 4).link();
        assertThat(ReliefSnapshotKeys.relieved(link).explanationText())
                .contains("Tabela desonerada pelo programa");
    }
}
