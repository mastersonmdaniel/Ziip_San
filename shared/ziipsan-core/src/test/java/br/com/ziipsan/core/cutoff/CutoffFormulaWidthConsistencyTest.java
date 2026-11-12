package br.com.ziipsan.core.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia da V110 (P-VP5-ED46-01) com o vocabulario de formulas da P3.4
 * (sem banco, no padrao dos testes de consistencia do projeto): a largura da
 * coluna cutoff_compensations.formula apos a V110 comporta o valor mais longo
 * do enum CutoffEnums.UndueCompensationFormula (67 caracteres, ARESC
 * Res. 046/2016) e a V110 nao toca no CHECK da V9_2 nem em nenhuma outra
 * tabela ou coluna.
 */
class CutoffFormulaWidthConsistencyTest {

    private static final String SQL = readSql();

    private static String readSql() {
        try (InputStream in = CutoffFormulaWidthConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V110__cutoff_compensation_formula_width.sql")) {
            assertThat(in).as("V110 no classpath do core").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("V110 alarga formula para VARCHAR(80): cobre o maior valor do enum (67)")
    void widthCoversLongestFormula() {
        Matcher m = Pattern.compile(
                        "ALTER TABLE cutoff_compensations ALTER COLUMN formula TYPE VARCHAR\\((\\d+)\\)")
                .matcher(SQL);
        assertThat(m.find()).as("V110 altera cutoff_compensations.formula").isTrue();
        int width = Integer.parseInt(m.group(1));
        int longest = Arrays.stream(CutoffEnums.UndueCompensationFormula.values())
                .mapToInt(f -> f.name().length())
                .max().orElseThrow();
        assertThat(longest)
                .as("maior valor do enum (ARESC, 67 caracteres)")
                .isEqualTo(67);
        // 80: proximo degrau da escada de larguras do projeto acima de 67
        // (60/80/100/120/200; VARCHAR(80) em V4_10 e V6_3), 13 de folga.
        assertThat(width).as("largura decidida na V110").isEqualTo(80);
        assertThat(width).isGreaterThanOrEqualTo(longest);
    }

    @Test
    @DisplayName("V110 so altera cutoff_compensations.formula: sem DROP, sem CHECK novo, sem outra tabela")
    void onlyFormulaColumn() {
        assertThat(SQL).doesNotContain("DROP ");
        assertThat(SQL).doesNotContain("CHECK (");
        assertThat(SQL).doesNotContain("ALTER TABLE cutoff_orders");
        assertThat(SQL).doesNotContain("ALTER TABLE reconnection_orders");
        assertThat(SQL).doesNotContain("INSERT INTO");
        assertThat(SQL).doesNotContain("UPDATE ");
        assertThat(SQL).doesNotContain("DELETE FROM");
    }
}
