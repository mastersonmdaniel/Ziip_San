package br.com.ziipsan.commercial.billing.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas textuais da V4_17 (P3.28), no modelo das guardas da P2.20: a
 * numeracao sem reserva declarada, as tabelas ligadas a P2.20/P1.31, os
 * CHECKs do laudo, os parametros registrados e nenhuma semente.
 */
class LeakMeterMigrationConsistencyTest {

    private static String sql;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = LeakMeterMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V4_17__leak_review_and_meter_test.sql")) {
            assertThat(in).as("migration V4_17 no classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Test
    @DisplayName("numeracao: V4_17 e a primeira livre da serie do billing e a ausencia de reserva esta declarada")
    void numberingIsDocumented() {
        assertThat(sql).contains("Version: 4.17.0");
        assertThat(sql).contains("P3.28");
        assertThat(sql).contains("NAO nomeia migration");
        assertThat(sql).contains("primeira versao livre");
        assertThat(sql).contains("V4_16");
    }

    @Test
    @DisplayName("tabelas novas existem, ligadas a conta e a revisao da P2.20, e registradas para isolamento")
    void tables() {
        for (String table : new String[]{"leak_reviews", "meter_tests"}) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).contains("register_tenant_table('" + table + "')");
        }
        assertThat(sql).contains("fk_leak_reviews_bill");
        assertThat(sql).contains("fk_leak_reviews_bill_review");
        assertThat(sql).contains("REFERENCES bill_reviews(tenant_id, id)");
        assertThat(sql).contains("idx_leak_reviews_one_open");
    }

    @Test
    @DisplayName("laudo obrigatorio no banco: REPORTED exige veredito, data, erro, referencia e cobranca decidida")
    void reportChecks() {
        assertThat(sql).contains("status <> 'REPORTED' OR (tested_on IS NOT NULL AND measured_error_pct IS NOT NULL");
        assertThat(sql).contains("'NORMAL', 'IRREGULAR', 'INCONCLUSIVE'");
        assertThat(sql).contains("status <> 'GRANTED' OR (revised_water IS NOT NULL");
    }

    @Test
    @DisplayName("parametros leak.* e meter.test_* registrados; leak.max_discount_pct_water fica com a V1_13 da P2.31")
    void parameters() {
        for (String key : new String[]{"leak.discount_rule", "leak.max_discount_pct_sewer",
                "leak.claim_deadline_days", "leak.claim_deadline_anchor", "leak.max_bills_per_12_months",
                "leak.bills_limit_mode", "leak.sewer_by_average", "meter.test_notice_days",
                "meter.test_charge_only_if_normal", "meter.test_review_months"}) {
            assertThat(sql).contains("'" + key + "'");
        }
        assertThat(sql).contains("ON CONFLICT (definition_key) DO NOTHING");
        assertThat(sql).contains("V1_13");
        // A definicao da agua NAO e registrada de novo como linha de VALUES.
        assertThat(sql).doesNotContain("('leak.max_discount_pct_water', 'PERCENT'");
    }

    @Test
    @DisplayName("nenhum dado de negocio semeado: sem INSERT em tabela de tenant")
    void noSeeds() {
        assertThat(sql).doesNotContain("INSERT INTO leak_reviews");
        assertThat(sql).doesNotContain("INSERT INTO meter_tests");
        assertThat(sql).doesNotContain("INSERT INTO bills");
    }
}
