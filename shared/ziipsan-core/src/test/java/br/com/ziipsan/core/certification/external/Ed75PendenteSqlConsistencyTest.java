package br.com.ziipsan.core.certification.external;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Consistencia do SQL pendente da ED-75 com as regras do codigo. */
class Ed75PendenteSqlConsistencyTest {

    private String sql() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(
                "/db/pendente/ED-75__certification_evidence_external_access.sql")) {
            assertThat(in).as("SQL pendente da ED-75 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void registersRegExternalAccessAsClassBDefaultOff() throws Exception {
        String sql = sql();
        assertThat(sql).contains("'reg.external_access', 'BOOLEAN', 'b', 'false'");
        assertThat(sql).contains("ON CONFLICT (definition_key) DO NOTHING");
        assertThat(sql).contains("cert.external_auditor_access");
        assertThat(sql).contains("cert.evidence_pack_personal_data");
    }

    @Test
    void sixTablesRegisteredForRlsAndNoDelete() throws Exception {
        String sql = sql();
        for (String table : new String[]{"cert_ext_evidence_packs",
                "cert_ext_evidence_pack_standards", "cert_ext_evidence_items",
                "cert_ext_supplier_evidence", "cert_ext_access_grants",
                "cert_ext_read_log"}) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).contains("register_tenant_table('" + table + "')");
        }
        assertThat(sql).doesNotContain("DELETE FROM");
        assertThat(sql).doesNotContain("DROP TABLE");
    }

    @Test
    void personalDataImpossibleAndExpiryMandatoryInSql() throws Exception {
        String sql = sql();
        assertThat(sql).contains("'AGGREGATE', 'MASKED', 'PSEUDONYMIZED'");
        assertThat(sql).contains("personal_data BOOLEAN NOT NULL DEFAULT false "
                + "CHECK (personal_data = false)");
        assertThat(sql).contains("expires_at TIMESTAMPTZ NOT NULL");
        assertThat(sql).contains("CHECK (expires_at > valid_from)");
        assertThat(sql).contains("CHECK (granted_by <> accepted_by)");
        assertThat(sql).contains("profile <> 'REGULATOR_SERVICE_OWNER' OR evidence_standards = ''");
    }

    @Test
    void duplicateEvidenceBarredInSqlLikeInMemory() throws Exception {
        // Dobra R5-A (F1-B2): a nao-duplicacao vivia so em memoria
        // (EvidencePack); o SQL repete a guarda com UNIQUE idempotente,
        // no padrao do uq_ext_pack_standard, com tenant_id.
        String sql = sql();
        assertThat(sql).contains("CREATE UNIQUE INDEX IF NOT EXISTS "
                + "uq_ext_items_pack_standard_ref");
        assertThat(sql).contains("ON cert_ext_evidence_items"
                + "(tenant_id, pack_id, standard, evidence_ref)");
        assertThat(sql).contains("CREATE UNIQUE INDEX IF NOT EXISTS "
                + "uq_ext_supplier_pack_kind_ref");
        assertThat(sql).contains("ON cert_ext_supplier_evidence"
                + "(tenant_id, pack_id, kind, evidence_ref)");
    }

    @Test
    void evidenceRefWithoutSurroundingSpacesInSqlLikeInMemory() throws Exception {
        // Dobra R6-A (R6-A1): EvidenceItem e SupplierEvidenceItem
        // fazem trim() do ref no construtor, mas o CHECK antigo so
        // exigia btrim(evidence_ref) <> '' e o UNIQUE comparava o ref
        // cru — "SYN-qms" e " SYN-qms" eram duplicata em memoria e
        // duas linhas no banco. O CHECK estendido exige tambem
        // evidence_ref = btrim(evidence_ref), nas DUAS tabelas.
        String sql = sql();
        String check = "CHECK (btrim(evidence_ref) <> '' "
                + "AND evidence_ref = btrim(evidence_ref))";
        assertThat(sql).contains(check);
        int items = sql.indexOf("CREATE TABLE IF NOT EXISTS cert_ext_evidence_items");
        int supplier = sql.indexOf("CREATE TABLE IF NOT EXISTS cert_ext_supplier_evidence");
        assertThat(items).isGreaterThanOrEqualTo(0);
        assertThat(supplier).isGreaterThan(items);
        assertThat(sql.substring(items, supplier)).contains(check);
        assertThat(sql.substring(supplier)).contains(check);
    }
}
