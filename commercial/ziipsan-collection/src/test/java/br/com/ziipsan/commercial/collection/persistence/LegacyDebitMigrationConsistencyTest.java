package br.com.ziipsan.commercial.collection.persistence;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Guardas textuais da V6_4 (sem banco), no padrao das guardas da P3.1/P3.2/P3.29. */
class LegacyDebitMigrationConsistencyTest {

    private String sql() throws Exception {
        try (InputStream in = getClass()
                .getResourceAsStream("/db/migration/tenant/V6_4__legacy_debit_febraban150.sql")) {
            assertThat(in).as("migration V6_4 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void v64CriaAsTresTabelasERegistraNoTenant() throws Exception {
        String sql = sql();
        for (String table : new String[]{"legacy_debit_authorizations",
                "legacy_debit_conversion_consents", "legacy_debit_entries"}) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).contains("register_tenant_table('" + table + "')");
        }
    }

    @Test
    void v64NaoSemeiaNenhumDado() throws Exception {
        // NENHUM DADO E SEMEADO: nem parametro, nem autorizacao, nem data.
        // (A data de desligamento vive em BcbDebitRegimeRules, nao no SQL.)
        assertThat(sql()).doesNotContain("INSERT INTO");
    }

    @Test
    void v64ExigeMatriculaUnicaPorTenantEValorPositivo() throws Exception {
        String sql = sql();
        assertThat(sql).contains("idx_legacy_debit_auth_enrollment");
        assertThat(sql).contains("CHECK (amount > 0)");
        assertThat(sql).contains("CHECK (proof_hash ~ '^[0-9a-f]{64}$')");
    }
}
