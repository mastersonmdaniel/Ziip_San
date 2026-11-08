package br.com.ziipsan.commercial.billing.persistence;

import br.com.ziipsan.commercial.billing.domain.AnnualDischargeStatementEntity;
import br.com.ziipsan.commercial.billing.domain.WaterQualityResultEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia entre as entidades da P2.24 e o DDL PENDENTE delas — sem
 * banco, como nos demais testes de consistencia do modulo. A P2.24 nao
 * tem versao Flyway reservada no plano v3.0 SS3: o DDL fica em
 * {@code db/migration-pendente/} e este teste garante (a) que entidade e
 * DDL nao divergem e (b) que o arquivo continua FORA de
 * {@code db/migration/tenant/} e marcado como pendente — promover o DDL
 * a migration exige a reserva de versao e a atualizacao deste teste.
 */
class P224DraftDdlConsistencyTest {

    private static final String DDL_RESOURCE =
            "/db/migration-pendente/P2.24__DDL-PENDENTE-water_quality_results-e-annual_discharge_statements.sql";

    private static final List<Class<?>> ENTITIES = List.of(
            WaterQualityResultEntity.class, AnnualDischargeStatementEntity.class);

    private static String ddl;

    @BeforeAll
    static void loadDdl() throws IOException {
        try (InputStream in = P224DraftDdlConsistencyTest.class.getResourceAsStream(DDL_RESOURCE)) {
            assertThat(in).as("DDL pendente da P2.24 no classpath: " + DDL_RESOURCE).isNotNull();
            ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("o DDL continua marcado como pendente e fora do diretorio de migrations do Flyway")
    void ddlStaysMarkedAsPending() {
        assertThat(ddl).contains("NAO E UMA MIGRATION FLYWAY");
        assertThat(ddl).contains("NAO reserva versao para a P2.24");
        assertThat(P224DraftDdlConsistencyTest.class.getResourceAsStream(
                "/db/migration/tenant/P2.24__DDL-PENDENTE-water_quality_results-e-annual_discharge_statements.sql"))
                .as("o DDL pendente nao pode estar no diretorio varrido pelo Flyway")
                .isNull();
    }

    @Test
    @DisplayName("toda tabela e toda coluna das entidades existem no DDL pendente, com a RLS registrada")
    void entitiesMatchDraftDdl() {
        for (Class<?> entity : ENTITIES) {
            String table = entity.getAnnotation(Table.class).name();
            assertThat(ddl).contains("CREATE TABLE IF NOT EXISTS " + table + " (");
            assertThat(ddl).contains("register_tenant_table('" + table + "')");
            for (Field field : entity.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                if (column == null) {
                    continue;
                }
                assertThat(ddl)
                        .as("coluna %s da entidade %s no DDL", column.name(), entity.getSimpleName())
                        .contains(column.name());
            }
        }
    }

    @Test
    @DisplayName("as unicidades do desenho estao no DDL: resultado por (mes, sistema, parametro, fonte) e declaracao por (unidade, ano)")
    void uniqueKeysMatchDesign() {
        assertThat(ddl).contains("uq_water_quality_results_key");
        assertThat(ddl).contains(
                "ON water_quality_results(tenant_id, reference_month, supply_system, parameter_key, source)");
        assertThat(ddl).contains("uq_annual_discharge_statements_year");
        assertThat(ddl).contains("ON annual_discharge_statements(tenant_id, unit_id, reference_year)");
    }

    @Test
    @DisplayName("os dominios fechados do DDL sao os literais dos enums do codigo")
    void ddlDomainsMatchEnums() {
        assertThat(ddl).contains("source IN ('LAB_SPREADSHEET', 'QUALITY_MODULE')");
        assertThat(ddl).contains("channel IN ('INVOICE_SPACE', 'ANNEX_DOCUMENT', 'PORTAL')");
        assertThat(ddl).contains("emission_trigger IN ('MAY_CYCLE', 'LATE_SETTLEMENT')");
    }
}
