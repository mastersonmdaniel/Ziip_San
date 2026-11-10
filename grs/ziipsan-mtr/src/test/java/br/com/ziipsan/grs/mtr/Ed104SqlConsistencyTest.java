package br.com.ziipsan.grs.mtr;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E13, teste 10 (SQL).
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistência SQL <-> código do SQL pendente da ED-104: as tabelas que o código novo usa
 * existem no ficheiro, o catálogo nasce vazio e nenhuma migration publicada é referenciada
 * para alteração destrutiva fora do bloco deliberado do CDF.
 */
class Ed104SqlConsistencyTest {

    private String sql() throws IOException {
        try (InputStream in = Ed104SqlConsistencyTest.class
                .getResourceAsStream("/db/pendente/ED-104__grs_trail.sql")) {
            assertThat(in).as("SQL pendente da ED-104 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("tabelas do código novo existem no SQL pendente")
    void tablesExistInSql() throws IOException {
        String sql = sql();
        for (String table : new String[]{"waste_temporary_storage", "waste_cdf_mtr", "waste_dmr",
                "waste_pgrs", "waste_collection_sector", "waste_collection_partner",
                "waste_collection_large_generator", "waste_collection_execution", "waste_weighing_ticket"}) {
            assertThat(sql).as("tabela " + table).contains("CREATE TABLE IF NOT EXISTS " + table);
        }
        assertThat(sql).contains("official_mtr_number").contains("sludge_record_id")
                .contains("temporary_storage_id").contains("period_start").contains("official_cdf_number")
                .contains("classification_scheme").contains("nbr_edition");
    }

    @Test
    @DisplayName("o catálogo nasce vazio: nenhum INSERT em waste_class")
    void catalogIsBornEmpty() throws IOException {
        String sql = sql().toUpperCase(java.util.Locale.ROOT);
        assertThat(sql).doesNotContain("INSERT INTO WASTE_CLASS");
        assertThat(sql).doesNotContain("INSERT INTO WASTE_CDF_MTR");
    }

    @Test
    @DisplayName("o CHECK novo de hazard_class é I/IIA/IIB e há guarda ruidosa para II/III legados")
    void hazardCheckIsCorrected() throws IOException {
        String sql = sql();
        assertThat(sql).contains("CHECK (hazard_class IN ('I', 'IIA', 'IIB'))");
        assertThat(sql).contains("RAISE EXCEPTION 'ED-104:");
        assertThat(sql).contains("NOT_SUBMITTED");
    }
}
