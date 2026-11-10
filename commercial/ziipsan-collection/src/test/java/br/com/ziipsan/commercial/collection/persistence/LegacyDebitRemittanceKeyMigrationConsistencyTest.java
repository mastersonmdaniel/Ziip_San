package br.com.ziipsan.commercial.collection.persistence;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Guardas textuais da V103 (sem banco), no padrao de LegacyDebitMigrationConsistencyTest. */
class LegacyDebitRemittanceKeyMigrationConsistencyTest {

    private String sql() throws Exception {
        try (InputStream in = getClass()
                .getResourceAsStream("/db/migration/tenant/V103__legacy_debit_remittance_key.sql")) {
            assertThat(in).as("migration V103 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void v103AdicionaColunaComComprimentoDerivado() throws Exception {
        // Maximo derivado da formula: UUID 36 + "|" + matricula 40 + "|"
        // + valor NUMERIC(14,2) normalizado 15 + "|" + data ISO 10 = 104.
        assertThat(sql()).contains(
                "ALTER TABLE legacy_debit_entries ADD COLUMN IF NOT EXISTS remittance_key VARCHAR(104)");
    }

    @Test
    void v103BackfillReproduzAFormulaDaChave() throws Exception {
        String sql = sql();
        assertThat(sql).contains("UPDATE legacy_debit_entries");
        assertThat(sql).contains("tenant_id::text || '|' || enrollment_id");
        // stripTrailingZeros().toPlainString() sobre NUMERIC(14,2).
        assertThat(sql).contains("rtrim(rtrim(amount::text, '0'), '.')");
        // LocalDate.toString() e ISO AAAA-MM-DD.
        assertThat(sql).contains("to_char(debit_date, 'YYYY-MM-DD')");
    }

    @Test
    void v103ExpandCriaUnicaPorTenantSemNotNullComGuardaEscopada() throws Exception {
        // Expand-contract (item 46): a V103 e o tempo expand - a coluna
        // nasce anulavel e a obrigatoriedade fisica NAO esta nesta
        // migration (foi realojada para a V104 pendente, ver o teste de
        // consistencia proprio da V104). A unicidade por tenant fica.
        String sql = sql();
        assertThat(sql).doesNotContain(
                "ALTER TABLE legacy_debit_entries ALTER COLUMN remittance_key SET NOT NULL");
        assertThat(sql).doesNotContain("SET NOT NULL");
        // Guarda escopada a valores preenchidos: nulos nao sao duplicados.
        assertThat(sql).contains("WHERE remittance_key IS NOT NULL");
        // Mesmo padrao multi-tenant da V102: (tenant_id, chave de negocio).
        assertThat(sql).contains("CREATE UNIQUE INDEX idx_legacy_entry_remittance_key "
                + "ON legacy_debit_entries(tenant_id, remittance_key)");
    }

    @Test
    void v103DuplicadosFazemAMigrationFalharAltoSemApagarNemSemear() throws Exception {
        String sql = sql();
        assertThat(sql).contains("GROUP BY tenant_id, remittance_key");
        assertThat(sql).contains("HAVING COUNT(*) > 1");
        assertThat(sql).contains("RAISE EXCEPTION");
        // Sem fusao silenciosa e sem semeadura: duplicado nunca e apagado.
        assertThat(sql).doesNotContain("DELETE");
        assertThat(sql).doesNotContain("INSERT");
    }

    @Test
    void v103OrdemBackfillDepoisGuardaDepoisIndiceSemPassoNotNull() throws Exception {
        // Expand-contract (item 46): a ordem na V103 passa a ser
        // backfill -> guarda -> indice unico; o passo da obrigatoriedade
        // fisica saiu desta migration (realojado na V104 pendente).
        String sql = sql();
        int backfill = sql.indexOf("UPDATE legacy_debit_entries");
        int guarda = sql.indexOf("RAISE EXCEPTION");
        int notNull = sql.indexOf(
                "ALTER TABLE legacy_debit_entries ALTER COLUMN remittance_key SET NOT NULL");
        int indice = sql.indexOf("CREATE UNIQUE INDEX idx_legacy_entry_remittance_key");
        assertThat(backfill).isGreaterThanOrEqualTo(0);
        assertThat(guarda).isGreaterThan(backfill);
        assertThat(notNull).isEqualTo(-1);
        assertThat(indice).isGreaterThan(guarda);
    }
}
