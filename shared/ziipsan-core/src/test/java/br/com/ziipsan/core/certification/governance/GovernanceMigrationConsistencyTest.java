package br.com.ziipsan.core.certification.governance;

import org.junit.jupiter.api.Test; import java.nio.file.*; import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class GovernanceMigrationConsistencyTest {
    String sql() throws Exception { Path p=Path.of("src/main/resources/db/migration/tenant/V21_3__certification_governance.sql"); if(!Files.exists(p)) p=Path.of("shared/ziipsan-core").resolve(p); return Files.readString(p); }
    @Test void tablesExistAndRegistered() throws Exception { String s=sql(); for(String t:List.of("cert_risk_criteria","cert_risks","cert_objectives","cert_suppliers","cert_supplier_evaluations","cert_competences","cert_legal_requirements","cert_audit_programs","cert_audit_findings","cert_management_reviews","cert_governance_audit_trail")){ assertThat(s).contains("CREATE TABLE IF NOT EXISTS "+t); assertThat(s).contains("register_tenant_table('"+t+"'"); }
        // D7: UNIQUE (tenant_id, id) em todas as tabelas, no padrao da V21_1.
        assertThat(s).contains("UNIQUE (tenant_id, id)"); }
    @Test void viewsForP322Exist() throws Exception { String s=sql(); for(GovernanceReports.Descriptor d: GovernanceReports.all()) assertThat(s).contains("CREATE OR REPLACE VIEW "+d.viewName());
        // D8: nome nao basta — as 4 views tem security_invoker.
        assertThat(s.split("security_invoker").length-1).isEqualTo(4); }
    @Test void suppliersViewAggregatesRealEvaluations() throws Exception { String s=sql();
        // D1: a view le cert_supplier_evaluations de verdade; NULL constante e proibido.
        assertThat(s).contains("cert_supplier_evaluations"); assertThat(s).doesNotContain("NULL::int AS last_score"); assertThat(s).doesNotContain("NULL::date AS next_evaluation_due"); }
    @Test void pendingViewCoversTheSixItemTypes() throws Exception { String s=sql();
        // D2: paridade com GovernanceService.pendingItems — os 6 tipos na view CERT-002.
        for(String type:List.of("RISK_REVIEW","OBJECTIVE","COMPETENCE_EXPIRY","LEGAL_EVALUATION","AUDIT_FINDING","SUPPLIER_EVALUATION")) assertThat(s).contains("'"+type+"'"); }
    @Test void foreignKeysWithinModuleExist() throws Exception { String s=sql();
        // D7/D6: FKs compostas com tenant — avaliacao->fornecedor, achado->programa, risco->ocorrencia da P2.25.
        assertThat(s).contains("REFERENCES cert_suppliers (tenant_id, id)"); assertThat(s).contains("REFERENCES cert_audit_programs (tenant_id, id)"); assertThat(s).contains("REFERENCES cert_occurrence (tenant_id, id)"); assertThat(s).contains("occurrence_id"); assertThat(s).contains("conformity_evaluated_on"); }
    @Test void checksMatchJavaRules() throws Exception { String s=sql();
        // D9: contingencia em branco nao passa; apetite tem teto da escala; probabilidade/impacto >= 1.
        assertThat(s).contains("char_length(btrim(contingency_plan))"); assertThat(s).contains("appetite_threshold <= max_probability*max_impact"); assertThat(s).contains("probability>=1"); assertThat(s).contains("impact>=1"); }
    @Test void enumsMatchJava() throws Exception { String s=sql(); for(RiskRecord.Category c: RiskRecord.Category.values()) assertThat(s).contains("'"+c.name()+"'"); for(RiskRecord.Kind k: RiskRecord.Kind.values()) assertThat(s).contains("'"+k.name()+"'"); for(SupplierRecord.Type t: SupplierRecord.Type.values()) assertThat(s).contains("'"+t.name()+"'"); for(LegalRequirement.Conformity c: LegalRequirement.Conformity.values()) assertThat(s).contains("'"+c.name()+"'"); for(LegalRequirement.Origin o: LegalRequirement.Origin.values()) assertThat(s).contains("'"+o.name()+"'"); for(AuditAndReview.AuditFinding.Severity sev: AuditAndReview.AuditFinding.Severity.values()) assertThat(s).contains("'"+sev.name()+"'"); }
    @Test void noSeededLegalRequirementOrSupplier() throws Exception { String s=sql();
        // D8: anti-seed generico (qualquer tabela, qualquer caixa das duas grafias usuais) + os dois alvos originais.
        assertThat(s).doesNotContain("INSERT INTO"); assertThat(s).doesNotContain("insert into"); assertThat(s).doesNotContain("INSERT INTO cert_legal_requirements"); assertThat(s).doesNotContain("INSERT INTO cert_suppliers"); }
}
