package br.com.ziipsan.core.certification.governance;

import java.util.List;
/** Relatorios do modulo no nucleo da P3.22: views rpt_cert_* da V21_3 + definicoes declarativas (registro via ExternalReportRegistrar na fiacao). */
public final class GovernanceReports {
    private GovernanceReports(){}
    public record Descriptor(String code, String viewName, List<String> columns) {}
    public static List<Descriptor> all(){ return List.of(
      new Descriptor("CERT-001","rpt_cert_risks",List.of("tenant_id","title","kind","category","probability","impact","score","review_due")),
      new Descriptor("CERT-002","rpt_cert_pending",List.of("tenant_id","item_type","item_ref","due_date","detail")),
      new Descriptor("CERT-003","rpt_cert_suppliers",List.of("tenant_id","legal_name","supplier_type","last_score","next_evaluation_due")),
      new Descriptor("CERT-004","rpt_cert_legal_conformity",List.of("tenant_id","title","origin","conformity","evaluation_due"))); }
}
