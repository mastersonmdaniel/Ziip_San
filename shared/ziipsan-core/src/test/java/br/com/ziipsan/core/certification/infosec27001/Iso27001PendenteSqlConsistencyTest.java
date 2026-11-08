package br.com.ziipsan.core.certification.infosec27001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistência entre o SQL pendente da ED-73 e as réguas do código:
 * tabelas existem, enums do banco casam com os enums Java, NÃO há DELETE
 * (desligar não apaga registro) e nenhum título literal do Anexo A foi
 * semeado (o único título presente é o marcador [A DEFINIR]).
 */
class Iso27001PendenteSqlConsistencyTest {

    private static String sql() throws Exception {
        try (InputStream in = Iso27001PendenteSqlConsistencyTest.class
                .getResourceAsStream("/db/pendente/ED-73__certification_infosec_iso27001.sql")) {
            assertThat(in).as("SQL pendente da ED-73 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("as nove tabelas do pacote estão no SQL pendente e no RLS")
    void tablesExist() throws Exception {
        String sql = sql();
        for (String table : List.of("cert_i27001_soa_entries", "cert_i27001_soa_evidence",
                "cert_i27001_shared_responsibility", "cert_i27001_matrix_evidence",
                "cert_i27001_recert_campaigns", "cert_i27001_recert_items",
                "cert_i27001_evidence_pack_items", "cert_i27001_clause_assessments",
                "cert_i27001_clause_evidence")) {
            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(sql).contains("register_tenant_table('" + table + "')");
        }
    }

    @Test
    @DisplayName("sem DELETE nem DROP: desligar a norma não apaga registro")
    void noDelete() throws Exception {
        String upper = sql().toUpperCase();
        assertThat(upper).doesNotContain("DELETE FROM").doesNotContain("DROP TABLE")
                .doesNotContain("TRUNCATE");
    }

    @Test
    @DisplayName("enums do banco casam com os enums Java")
    void enumsMatch() throws Exception {
        String sql = sql();
        for (AnnexAControlEntry.Applicability a : AnnexAControlEntry.Applicability.values()) {
            assertThat(sql).contains("'" + a.name() + "'");
        }
        for (AnnexAControlEntry.ImplementationStatus s : AnnexAControlEntry.ImplementationStatus.values()) {
            assertThat(sql).contains("'" + s.name() + "'");
        }
        for (SharedResponsibilityEntry.Responsibility r : SharedResponsibilityEntry.Responsibility.values()) {
            assertThat(sql).contains("'" + r.name() + "'");
        }
        for (AccessRecertificationCampaign.ManagerDecision d : AccessRecertificationCampaign.ManagerDecision.values()) {
            assertThat(sql).contains("'" + d.name() + "'");
        }
        for (SupplierEvidencePack.ArtifactKind k : SupplierEvidencePack.ArtifactKind.values()) {
            assertThat(sql).contains("'" + k.name() + "'");
        }
        for (IsmsClauseAssessment.Conformity c : IsmsClauseAssessment.Conformity.values()) {
            assertThat(sql).contains("'" + c.name() + "'");
        }
        // Correção D4(b) da revisão: os dois EvidenceKind (SoA e cláusulas)
        // agora também são iterados e travados contra o SQL.
        for (AnnexAControlEntry.EvidenceKind k : AnnexAControlEntry.EvidenceKind.values()) {
            assertThat(sql).contains("'" + k.name() + "'");
        }
        for (IsmsClauseAssessment.EvidenceKind k : IsmsClauseAssessment.EvidenceKind.values()) {
            assertThat(sql).contains("'" + k.name() + "'");
        }
    }

    @Test
    @DisplayName("correções D2/D3: SQL impõe padrão das chaves, coerência título×pendente e FK matriz→SoA")
    void sqlPatternTitleAndForeignKeyChecks() throws Exception {
        String sql = sql();
        // D2(a): padrão de referência (o mesmo do Java) em control_key (×2) e clause_number.
        assertThat(sql).contains("control_key ~ '^[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*$'")
                .contains("clause_number ~ '^[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*$'");
        // D2(b): title_pending é verdadeiro se e somente se o título é o marcador.
        assertThat(sql).contains("CHECK (title_pending = (title = '[A DEFINIR]'))");
        // D3: a matriz só aponta para controle declarado na SoA do mesmo tenant.
        assertThat(sql).contains("fk_i27001_matrix_soa_control")
                .contains("REFERENCES cert_i27001_soa_entries(tenant_id, control_key)");
        // D4(a): o teto Java do CatalogGrant é o VARCHAR(120) destas colunas.
        assertThat(sql).contains("role_name VARCHAR(120)").contains("scope_name VARCHAR(120)")
                .contains("permission_name VARCHAR(120)");
        assertThat(AccessRecertificationCampaign.CatalogGrant.GRANT_FIELD_MAX).isEqualTo(120);
    }

    @Test
    @DisplayName("o título da SoA nasce com o marcador [A DEFINIR] e a pendência está declarada no SQL")
    void titlesPendingDeclared() throws Exception {
        String sql = sql();
        assertThat(sql).contains("'[A DEFINIR]'").contains("title_pending")
                .contains("decisão do dono sobre licença");
    }

    @Test
    @DisplayName("hash do pacote de evidência tem trava de formato SHA-256 no banco")
    void evidenceHashCheck() throws Exception {
        assertThat(sql()).contains("sha256_hex ~ '^[0-9a-f]{64}$'");
    }
}
