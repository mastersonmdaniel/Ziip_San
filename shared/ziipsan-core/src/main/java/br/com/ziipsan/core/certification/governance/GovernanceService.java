package br.com.ziipsan.core.certification.governance;

import br.com.ziipsan.core.json.Json;
import java.time.LocalDate; import java.util.*;
/**
 * Governanca da qualidade (P3.31; V21_3): riscos/oportunidades, objetivos (P3.41),
 * auditoria interna (relatorio pelo motor da P2.30), analise critica, competencia
 * (OPE-14/P1.31), fornecedores e requisitos legais, com painel de pendencias que
 * alimenta "minhas tarefas" da P2.28 pela leitura {@link #pendingItems}.
 */
public final class GovernanceService {
    private final GovernanceStore store; private final GovernancePorts.IndicatorCatalog indicators;
    private final GovernancePorts.AuditReportTemplateEngine templates;
    private final GovernancePorts.TenantParameters parameters;
    public GovernanceService(GovernanceStore s, GovernancePorts.IndicatorCatalog i, GovernancePorts.AuditReportTemplateEngine t){this(s,i,t,null);}
    public GovernanceService(GovernanceStore s, GovernancePorts.IndicatorCatalog i, GovernancePorts.AuditReportTemplateEngine t, GovernancePorts.TenantParameters p){store=s;indicators=i;templates=t;parameters=p;}
    /** Correcao D4: deny-by-default, como a P3.41 — so EDITOR/APPROVER escrevem; null e READER sao recusados. */
    private static void write(GovernanceAccess a,String op){ if(a!=GovernanceAccess.EDITOR && a!=GovernanceAccess.APPROVER) throw new GovernanceAccessDeniedException(op,"escrita exige EDITOR ou APPROVER declarado"); }
    private void trail(UUID t,String action,Map<String,Object> d,UUID actor){ store.audit(t,action,Json.write(d),actor); }

    public void declareCriterion(GovernanceAccess a,UUID t,RiskCriterion c,UUID actor){ write(a,"declareCriterion"); store.saveCriterion(t,c); trail(t,"RISK_CRITERION",Map.of("appetite",c.appetiteThreshold()),actor); }
    public RiskRecord registerRisk(GovernanceAccess a,RiskRecord r,UUID actor){ write(a,"registerRisk");
        RiskCriterion c=store.criterion(r.tenantId()).orElseThrow(()->new GovernanceValidationException("RISK_CRITERION_NOT_DECLARED","tenant sem criterio de risco declarado: registro recusado"));
        int score=c.score(r.probability(),r.impact()); store.saveRisk(r);
        Map<String,Object> detail=new LinkedHashMap<>(); detail.put("score",score); detail.put("withinAppetite",c.withinAppetite(score));
        // Correcao D6: incidente operacional/cibernetico resolve o canal da agencia pelo parametro do
        // tenant (reg.incident_report_channel) no momento do registro — a constante e consumida aqui,
        // e a resolucao (nome do parametro + se resolveu) entra na trilha. O valor nunca e embutido.
        if(r.category()==RiskRecord.Category.OPERATIONAL||r.category()==RiskRecord.Category.CYBER){
            detail.put("agencyChannelParameter",RiskRecord.AGENCY_CHANNEL_PARAMETER);
            detail.put("agencyChannelResolved",agencyReportChannel(r.tenantId()).isPresent());
            if(r.occurrenceId()!=null) detail.put("occurrenceId",r.occurrenceId().toString());
        }
        trail(r.tenantId(),"RISK",detail,actor); return r; }
    public int riskScore(UUID t,RiskRecord r){ RiskCriterion c=store.criterion(t).orElseThrow(()->new GovernanceValidationException("RISK_CRITERION_NOT_DECLARED","sem criterio declarado")); return c.score(r.probability(),r.impact()); }
    /** Canal de comunicacao a agencia do tenant, resolvido pelo parametro reg.incident_report_channel
     *  (correcao D6). Vazio quando a porta de parametros nao esta fiada ou o tenant nao declarou o canal. */
    public Optional<String> agencyReportChannel(UUID tenantId){ if(parameters==null) return Optional.empty(); return parameters.resolve(tenantId,RiskRecord.AGENCY_CHANNEL_PARAMETER); }

    public ObjectiveRecord registerObjective(GovernanceAccess a,ObjectiveRecord o,UUID actor){ write(a,"registerObjective");
        if(indicators==null||!indicators.exists(o.tenantId(),o.indicatorCode())) throw new GovernanceValidationException("OBJECTIVE_INDICATOR_NOT_IN_P341","indicador "+o.indicatorCode()+" inexistente no registro da P3.41 (JOR-19)");
        store.saveObjective(o); trail(o.tenantId(),"OBJECTIVE",Map.of("indicator",o.indicatorCode()),actor); return o; }

    public SupplierRecord registerSupplier(GovernanceAccess a,SupplierRecord s,UUID actor){ write(a,"registerSupplier"); store.saveSupplier(s); trail(s.tenantId(),"SUPPLIER",Map.of("type",s.type().name()),actor); return s; }
    public SupplierEvaluation evaluateSupplier(GovernanceAccess a,UUID tenantId,SupplierEvaluation e,UUID actor){ write(a,"evaluateSupplier");
        if(!e.tenantId().equals(tenantId)) throw new GovernanceValidationException("SUPPLIER_EVALUATION_TENANT_MISMATCH","avaliacao pertence a outro tenant");
        if(store.suppliers(tenantId).stream().noneMatch(s->s.id().equals(e.supplierId()))) throw new GovernanceValidationException("SUPPLIER_NOT_FOUND","avaliacao sobre cadastro inexistente neste tenant");
        store.saveEvaluation(e); trail(tenantId,"SUPPLIER_EVALUATION",Map.of("score",e.score()),actor); return e; }

    public CompetenceRecord registerCompetence(GovernanceAccess a,CompetenceRecord c,UUID actor){ write(a,"registerCompetence"); store.saveCompetence(c); trail(c.tenantId(),"COMPETENCE",Map.of("code",c.competenceCode()),actor); return c; }
    /** Verificacao de habilitacao consumida pelos tipos de OS da P1.31 (OPE-14). */
    public boolean isQualified(UUID tenantId,UUID personId,String osType,LocalDate on){ return store.competences(tenantId).stream().anyMatch(c->c.personId().equals(personId)&&c.qualifies(osType,on)); }

    public LegalRequirement registerLegal(GovernanceAccess a,LegalRequirement l,UUID actor){ write(a,"registerLegal"); store.saveLegal(l); trail(l.tenantId(),"LEGAL",Map.of("origin",l.origin().name()),actor); return l; }
    public int importFactoryCatalog(GovernanceAccess a,UUID t,GovernancePorts.SanitationLegalCatalog cat,UUID actor){ write(a,"importFactoryCatalog");
        List<LegalRequirement> items=cat.factoryRequirements(t);
        // Porta de fabrica e confiavel so dentro do contrato: item de outro tenant ou de outra origem
        // e recusado explicitamente, nunca importado em silencio (observacao da revisao).
        for(LegalRequirement x: items){ if(!t.equals(x.tenantId())||x.origin()!=LegalRequirement.Origin.SANITATION_FACTORY_CATALOG)
            throw new GovernanceValidationException("FACTORY_CATALOG_ITEM_INVALID","item do catalogo de fabrica fora do tenant ou da origem SANITATION_FACTORY_CATALOG"); }
        items.forEach(x->store.saveLegal(x)); trail(t,"LEGAL_FACTORY_IMPORT",Map.of("count",items.size()),actor); return items.size(); }
    public void evaluateConformity(GovernanceAccess a,UUID t,UUID legalId,LegalRequirement.Conformity c,LocalDate on,UUID actor){ write(a,"evaluateConformity");
        if(store.legal(t).stream().noneMatch(l->l.id().equals(legalId))) throw new GovernanceValidationException("LEGAL_NOT_FOUND","requisito inexistente neste tenant");
        store.saveConformity(legalId,c,on); trail(t,"LEGAL_CONFORMITY",Map.of("conformity",c.name()),actor); }

    public void planAudit(GovernanceAccess a,AuditAndReview.AuditProgram p,UUID actor){ write(a,"planAudit"); store.saveProgram(p); trail(p.tenantId(),"AUDIT_PROGRAM",Map.of("title",p.title()),actor); }
    /** Correcao D5: o programa tem de existir e pertencer ao tenant — achado orfao e recusado
     *  explicitamente (AUDIT_PROGRAM_NOT_FOUND), nunca gravado para sumir das leituras. */
    public void addFinding(GovernanceAccess a,UUID t,AuditAndReview.AuditFinding f,UUID actor){ write(a,"addFinding");
        if(!f.tenantId().equals(t)) throw new GovernanceValidationException("AUDIT_FINDING_TENANT_MISMATCH","achado pertence a outro tenant");
        store.program(f.programId()).filter(p->p.tenantId().equals(t)).orElseThrow(()->new GovernanceValidationException("AUDIT_PROGRAM_NOT_FOUND","programa de auditoria inexistente neste tenant: achado recusado"));
        store.saveFinding(f); trail(t,"AUDIT_FINDING",Map.of("severity",f.severity().name()),actor); }
    /** Relatorio de auditoria pelo motor de modelos da P2.30; payload JSON somente via Json do core. */
    public String renderAuditReport(UUID t){ if(templates==null) throw new GovernanceValidationException("TEMPLATE_ENGINE_NOT_WIRED","motor de modelos da P2.30 nao fiado");
        List<Map<String,Object>> fs=store.findings(t).stream().map(f->Map.<String,Object>of("description",f.description(),"severity",f.severity().name(),"due",f.dueDate().toString(),"closed",f.closed())).toList();
        return templates.render("AUDIT_INTERNAL_REPORT", Json.write(Map.of("tenantId",t.toString(),"findings",fs))); }
    public void recordReview(GovernanceAccess a,AuditAndReview.ManagementReview r,UUID actor){ if(a!=GovernanceAccess.APPROVER) throw new GovernanceAccessDeniedException("recordReview","APPROVER","analise critica exige APPROVER"); store.saveReview(r); trail(r.tenantId(),"MANAGEMENT_REVIEW",Map.of("decisions",r.decisions().size()),actor); }

    public record PendingItem(String itemType, UUID itemRef, LocalDate dueDate, String detail) {}
    /** Painel de pendencias e vencimentos; itens entram em "minhas tarefas" da P2.28 (leitura, sem criar work_item aqui).
     *  Os 6 tipos produzidos aqui sao os mesmos 6 da view rpt_cert_pending (correcao D2). */
    public List<PendingItem> pendingItems(UUID t, LocalDate horizon){ List<PendingItem> out=new ArrayList<>();
        store.risks(t).forEach(r->{ if(!r.reviewDue().isAfter(horizon)) out.add(new PendingItem("RISK_REVIEW",r.id(),r.reviewDue(),r.title())); });
        store.objectives(t).forEach(o->{ if(!o.dueDate().isAfter(horizon)) out.add(new PendingItem("OBJECTIVE",o.id(),o.dueDate(),o.title())); });
        store.competences(t).forEach(c->{ if(!c.validUntil().isAfter(horizon)) out.add(new PendingItem("COMPETENCE_EXPIRY",c.id(),c.validUntil(),c.competenceCode())); });
        store.legal(t).forEach(l->{ if(!l.evaluationDue().isAfter(horizon)) out.add(new PendingItem("LEGAL_EVALUATION",l.id(),l.evaluationDue(),l.title())); });
        store.findings(t).stream().filter(f->!f.closed()).forEach(f->{ if(!f.dueDate().isAfter(horizon)) out.add(new PendingItem("AUDIT_FINDING",f.id(),f.dueDate(),f.description())); });
        // Correcao D7: a referencia da pendencia e o id da AVALIACAO (antes era o id do fornecedor).
        store.suppliers(t).forEach(s->store.evaluations(s.id()).forEach(e->{ if(!e.nextEvaluationDue().isAfter(horizon)) out.add(new PendingItem("SUPPLIER_EVALUATION",e.id(),e.nextEvaluationDue(),s.legalName())); }));
        return out.stream().sorted(Comparator.comparing(PendingItem::dueDate)).toList(); }
}
