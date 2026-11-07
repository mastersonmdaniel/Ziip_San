package br.com.ziipsan.commercial.billing.persistence;

import br.com.ziipsan.commercial.billing.domain.TariffImpactSimulationEntity;
import br.com.ziipsan.commercial.billing.domain.TariffVersionEntity;
import jakarta.persistence.Column;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas da migration {@code V4_9__tariff_readjust_review_and_simulation.sql}
 * (P2.29) que NÃO precisam de banco, no padrão de
 * {@code MigrationConsistencyTest} (V4_1): o SQL é lido como texto e
 * conferido contra as entidades e contra o aceite — colunas novas da
 * {@code tariff_versions} existem no ALTER e na entidade, com tipo e
 * precisão; a tabela de simulações bate com a entidade e é registrada para
 * RLS; os INSERTs são só de catálogo de parâmetro (nenhum dado de negócio
 * semeado); as quatro chaves do aceite estão registradas como classe d com
 * aprovação REG; e a trava de publicação de 30 dias está no banco.
 */
class TariffManagementMigrationConsistencyTest {

    private static String sql;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = TariffManagementMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V4_9__tariff_readjust_review_and_simulation.sql")) {
            assertThat(in).as("migration V4_9 no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Test
    @DisplayName("as 11 colunas novas de tariff_versions estão no ALTER e mapeadas em TariffVersionEntity com tipo compatível")
    void versionColumnsMatchTheEntity() {
        Set<String> altered = new LinkedHashSet<>();
        Matcher m = Pattern.compile(
                "ALTER TABLE tariff_versions ADD COLUMN IF NOT EXISTS ([a-z][a-z0-9_]*) +([A-Z]+)(\\((\\d+)(?:,(\\d+))?\\))?")
                .matcher(sql);
        while (m.find()) {
            altered.add(m.group(1));
        }
        assertThat(altered).containsExactlyInAnyOrder(
                "change_type", "source_version_id", "base_date", "index_code", "index_pct", "factor_x_pct",
                "applied_pct", "readjust_formula", "approval_mode", "approved_on", "calculation_method");

        for (String column : altered) {
            Field field = fieldForColumn(TariffVersionEntity.class, column);
            assertThat(field).as("TariffVersionEntity mapeia %s", column).isNotNull();
            Column c = field.getAnnotation(Column.class);
            if (field.getType() == BigDecimal.class) {
                assertThat(sql).as("%s NUMERIC(9,4)", column)
                        .contains(column + " NUMERIC(9,4)");
                assertThat(c.precision()).as("%s precision", column).isEqualTo(9);
                assertThat(c.scale()).as("%s scale", column).isEqualTo(4);
            }
            if (field.getType() == String.class && !column.equals("calculation_method")) {
                Matcher vm = Pattern.compile(column + " VARCHAR\\((\\d+)\\)").matcher(sql);
                assertThat(vm.find()).as("%s VARCHAR no SQL", column).isTrue();
                assertThat(c.length()).as("%s length", column).isEqualTo(Integer.parseInt(vm.group(1)));
            }
            if (field.getType() == LocalDate.class) {
                assertThat(sql).as("%s DATE no SQL", column).contains(column + " DATE");
            }
            if (field.getType() == UUID.class) {
                assertThat(sql).as("%s UUID no SQL", column).contains(column + " UUID");
            }
        }
    }

    @Test
    @DisplayName("a tabela de simulações bate com TariffImpactSimulationEntity e é registrada (RLS)")
    void simulationTableMatchesTheEntity() {
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS tariff_impact_simulations");
        assertThat(sql).contains("SELECT register_tenant_table('tariff_impact_simulations');");
        for (Field f : TariffImpactSimulationEntity.class.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null) {
                assertThat(sql).as("coluna %s no CREATE TABLE", c.name()).contains(c.name());
            }
        }
        assertThat(sql).as("simulação referencia a versão-base, nunca a conta")
                .contains("REFERENCES tariff_versions(tenant_id, id)")
                .doesNotContain("REFERENCES bills");
    }

    @Test
    @DisplayName("nenhum dado de negócio semeado: INSERTs só no catálogo público de parâmetros")
    void onlyParameterCatalogInserts() {
        Matcher m = Pattern.compile("INSERT INTO (\\S+)").matcher(sql);
        Set<String> targets = new LinkedHashSet<>();
        while (m.find()) {
            targets.add(m.group(1));
        }
        assertThat(targets).containsExactlyInAnyOrder(
                "public.parameter_definitions", "public.parameter_legal_limits");
        assertThat(sql).doesNotContainPattern("(?i)\\bUPDATE\\s+\\w+\\s+SET\\b");
        assertThat(sql).doesNotContainPattern("(?i)\\bDELETE\\s+FROM\\b");
    }

    @Test
    @DisplayName("as quatro chaves do aceite estão no catálogo (bill.tariff_readjust ampliado + 3 chaves novas)")
    void parameterKeysAreRegistered() {
        for (String key : new String[]{"bill.tariff_readjust", "tariff.readjust_formula",
                "tariff.tacit_approval_days", "tariff.history_years_public"}) {
            assertThat(sql).as("chave %s registrada", key).contains("'" + key + "'");
        }
        assertThat(sql).as("classe d e aprovação REG declaradas")
                .contains("'OBJECT', 'd'").contains("'REG'");
        assertThat(sql).as("piso de 30 dias e de 5 anos nos limites legais")
                .contains("('bill.tariff_readjust', 'FLOOR', '30'")
                .contains("('tariff.history_years_public', 'FLOOR', '5'");
    }

    @Test
    @DisplayName("a trava de publicação está no banco e a vigência sem sobreposição continua sendo a EXCLUDE da V4_1")
    void publicationLockAndExcludeReference() {
        assertThat(sql).contains("chk_tariff_versions_publication_30d");
        assertThat(sql).contains("valid_from >= published_on + 30");
        assertThat(sql).as("a V4_9 não cria uma segunda estrutura de vigência")
                .doesNotContain("CREATE TABLE IF NOT EXISTS tariff_versions")
                .contains("excl_tariff_versions_no_overlap");
        assertThat(sql).as("a FK da versão de origem é composta (tenant_id, id)")
                .contains("FOREIGN KEY (tenant_id, source_version_id) REFERENCES tariff_versions(tenant_id, id)");
    }

    @Test
    @DisplayName("higiene do SQL: só ASCII, sem CHAR(n), CREATE TABLE sempre IF NOT EXISTS")
    void sqlHygiene() {
        assertThat(sql.chars().allMatch(ch -> ch < 128)).as("só ASCII").isTrue();
        assertThat(sql).doesNotContainPattern("(?<!VAR)CHAR\\(");
        assertThat(sql).doesNotContainPattern("(?i)\\bCREATE TABLE (?!IF NOT EXISTS)");
    }

    private static Field fieldForColumn(Class<?> entity, String column) {
        for (Field f : entity.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            if (c != null && c.name().equals(column)) {
                return f;
            }
        }
        return null;
    }
}
