package br.com.ziipsan.core.certification.assetmgmt55001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistência entre o SQL pendente da ED-72 e as réguas do código:
 * tabelas existem e estão no RLS, enums do banco casam com os enums Java,
 * NÃO há DELETE (desligar não apaga registro), a frota (VEHICLE) não
 * entra, e toda FK composta (tenant_id, X) tem o UNIQUE (tenant_id, ...)
 * do pai declarado no mesmo arquivo (lição da cascata C3 — a revisão
 * aplicará este SQL em PostgreSQL real).
 */
class Iso55001PendenteSqlConsistencyTest {

    private static final List<String> TABLES = List.of(
            "cert_i55001_asset_records", "cert_i55001_asset_fields",
            "cert_i55001_strategic_plans", "cert_i55001_decision_criteria",
            "cert_i55001_decision_records", "cert_i55001_investment_plans",
            "cert_i55001_investment_lines", "cert_i55001_predictive_signals",
            "cert_i55001_maintenance_links", "cert_i55001_clause_assessments",
            "cert_i55001_clause_evidence");

    private static String sql() throws Exception {
        try (InputStream in = Iso55001PendenteSqlConsistencyTest.class
                .getResourceAsStream("/db/pendente/ED-72__certification_assetmgmt_iso55001.sql")) {
            assertThat(in).as("SQL pendente da ED-72 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("as onze tabelas do pacote estão no SQL pendente e no RLS")
    void tablesExist() throws Exception {
        String sql = sql();
        for (String table : TABLES) {
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
    @DisplayName("enums do banco casam com os enums Java; frota (VEHICLE) fora")
    void enumsMatch() throws Exception {
        String sql = sql();
        for (StrategicAssetPlan.DecisionOutcome o : StrategicAssetPlan.DecisionOutcome.values()) {
            assertThat(sql).contains("'" + o.name() + "'");
        }
        for (AssetInvestmentPlan.ActionKind k : AssetInvestmentPlan.ActionKind.values()) {
            assertThat(sql).contains("'" + k.name() + "'");
        }
        for (PredictiveActionRule.Direction d : PredictiveActionRule.Direction.values()) {
            assertThat(sql).contains("'" + d.name() + "'");
        }
        for (MaintenanceLinks.PmTriggerKind t : MaintenanceLinks.PmTriggerKind.values()) {
            assertThat(sql).contains("'" + t.name() + "'");
        }
        for (MaintenanceLinks.SpareStatusRead s : MaintenanceLinks.SpareStatusRead.values()) {
            assertThat(sql).contains("'" + s.name() + "'");
        }
        for (AmsClauseAssessment.Conformity c : AmsClauseAssessment.Conformity.values()) {
            assertThat(sql).contains("'" + c.name() + "'");
        }
        for (AmsClauseAssessment.EvidenceKind k : AmsClauseAssessment.EvidenceKind.values()) {
            assertThat(sql).contains("'" + k.name() + "'");
        }
        for (AssetLifecycleRecord.FieldSource s : AssetLifecycleRecord.FieldSource.values()) {
            assertThat(sql).contains("'" + s.name() + "'");
        }
        for (String lifecycle : AssetLifecycleRecord.LIFECYCLES) {
            assertThat(sql).contains("'" + lifecycle + "'");
        }
        for (AssetLifecycleRecord.AssetKind k : AssetLifecycleRecord.AssetKind.values()) {
            if (k == AssetLifecycleRecord.AssetKind.VEHICLE) {
                assertThat(sql).doesNotContain("'VEHICLE'");
            } else {
                assertThat(sql).contains("'" + k.name() + "'");
            }
        }
    }

    @Test
    @DisplayName("toda tabela pai de FK composta declara UNIQUE (tenant_id, id) — lição C3")
    void compositeFkParentsHaveUnique() throws Exception {
        String sql = sql();
        for (String parent : List.of("cert_i55001_asset_records", "cert_i55001_strategic_plans",
                "cert_i55001_investment_plans", "cert_i55001_clause_assessments")) {
            assertThat(sql).contains("UNIQUE (tenant_id, id)");
            assertThat(sql).contains("REFERENCES " + parent + "(tenant_id, id)");
        }
        // A FK composta por chave natural (critério) tem o UNIQUE irmao:
        assertThat(sql).contains("UNIQUE (tenant_id, plan_id, criterion_key)");
        // Correção D1: a FK referencia a chave QUÁDRUPLA (com o desfecho).
        assertThat(sql).contains(
                "REFERENCES cert_i55001_decision_criteria(tenant_id, plan_id, criterion_key, outcome)");
    }

    @Test
    @DisplayName("D1/D2/D3: FK quádrupla com outcome, CHECK de field_source e trigger de evidência")
    void correctionGuardsPresent() throws Exception {
        String sql = sql();
        // D1: amarração decisão -> critério inclui o DESFECHO nas duas pontas.
        assertThat(sql).contains("UNIQUE (tenant_id, plan_id, criterion_key, outcome)");
        assertThat(sql).contains("FOREIGN KEY (tenant_id, plan_id, criterion_key, outcome)");
        assertThat(sql).contains(
                "REFERENCES cert_i55001_decision_criteria(tenant_id, plan_id, criterion_key, outcome)");
        // D2: origem do campo coincidente amarrada aos valores presentes.
        assertThat(sql).contains("field_source = CASE");
        assertThat(sql).contains("THEN 'REGULATORY_NR_ANA'");
        assertThat(sql).contains("THEN 'REGULATORY_ONLY'");
        assertThat(sql).contains("ELSE 'COE05_ONLY' END");
        // D3: CONFORMING sem evidência recusado também no banco (trigger nomeada).
        assertThat(sql).contains("cert_i55001_check_conforming_evidence");
        assertThat(sql).contains("trg_i55001_conforming_requires_evidence");
        assertThat(sql).contains("trg_i55001_evidence_removal_guard");
    }

    @Test
    @DisplayName("nenhum título literal de norma semeado: só o marcador [A DEFINIR]")
    void noNormTitlesSeeded() throws Exception {
        String sql = sql();
        assertThat(sql).contains("'[A DEFINIR]'");
        assertThat(sql).contains("title_pending = (own_words_title = '[A DEFINIR]')");
    }
}
