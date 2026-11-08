package br.com.ziipsan.reporting.catalog;

import br.com.ziipsan.reporting.registry.ReportDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia migration x codigo da ED-46, no padrao dos testes de
 * consistencia do projeto: a DDL pronta (artefato pendente
 * ED-46__complete_catalog.sql em db/pendente/, numeracao pendente
 * declarada) contem as views das definicoes, a seed das definicoes com
 * as MESMAS colunas do Java e a seed do catalogo com o MESMO numero de
 * itens de {@link CompleteReportCatalog}.
 */
class Ed46MigrationConsistencyTest {

    private static String sql;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = Ed46MigrationConsistencyTest.class.getClassLoader()
                .getResourceAsStream("db/pendente/ED-46__complete_catalog.sql")) {
            assertThat(in).as("migration da ED-46 no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void numeracaoNaoFoiInventada() {
        // Sem reserva nominal no plano para a ED-46: o arquivo e o artefato
        // pendente em db/pendente/ e o cabecalho declara a pendencia.
        // Nenhum V15_3+ pode aparecer.
        assertThat(sql).contains("NUMERACAO PENDENTE");
        assertThat(sql).doesNotContain("V15_3", "V15_4", "V15_5");
    }

    @Test
    void todaDefinicaoNovaTemViewESeedNaMigration() {
        for (ReportDefinition def : Ed46ReportDefinitions.all()) {
            assertThat(sql).contains("CREATE OR REPLACE VIEW " + def.viewName());
            assertThat(sql).contains("'" + def.code() + "',");
            String seed = seedSegment(def.code());
            for (var column : def.columns()) {
                assertThat(seed).as("coluna %s da seed de %s", column.name(), def.code())
                        .contains("\"name\": \"" + column.name() + "\"");
            }
            assertThat(seed).contains(def.requiredPermission());
            assertThat(seed).contains(def.viewName());
        }
    }

    @Test
    void seedDoCatalogoBateComOCatalogoEmCodigo() {
        Matcher m = Pattern.compile("INSERT INTO report_catalog_entries").matcher(sql);
        long inserts = m.results().count();
        assertThat(inserts).isEqualTo(CompleteReportCatalog.all().size());
        for (CatalogEntry e : CompleteReportCatalog.all()) {
            assertThat(sql).contains("VALUES ('" + e.id() + "'");
        }
    }

    @Test
    void todaViewExecutavelDoCatalogoExisteNaMigration() {
        assertThat(CompleteReportCatalog.all().stream()
                .filter(e -> e.status() == CatalogStatus.EXECUTABLE)
                .filter(e -> e.viewName() != null))
                .allSatisfy(e -> assertThat(sql)
                        .contains("CREATE OR REPLACE VIEW " + e.viewName()));
    }

    private static String seedSegment(String code) {
        int start = sql.indexOf("VALUES (NULL, '" + code + "'");
        assertThat(start).as("seed de " + code).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf("ON CONFLICT DO NOTHING", start);
        return sql.substring(start, end);
    }
}
