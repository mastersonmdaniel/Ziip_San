package br.com.ziipsan.core.certification.governance;

import org.junit.jupiter.api.Test; import java.time.LocalDate; import java.util.*;
import static org.assertj.core.api.Assertions.*; import static org.junit.jupiter.api.Assertions.*;
class GovernanceServiceTest {
    static final UUID T=UUID.randomUUID(), A=UUID.randomUUID();
    GovernanceService svc(Set<String> indicators){ return new GovernanceService(new InMemoryGovernanceStore(),(t,c)->indicators.contains(c),(tpl,payload)->"rendered:"+tpl+":"+payload); }
    GovernanceService base(){ GovernanceService s=svc(Set.of("IND-001")); s.declareCriterion(GovernanceAccess.EDITOR,T,new RiskCriterion(5,5,10,"matriz 5x5 declarada pelo tenant"),A); return s; }
    @Test void riskWithoutCriterionIsRefused(){ GovernanceService s=svc(Set.of()); assertThrows(GovernanceValidationException.class,()->s.registerRisk(GovernanceAccess.EDITOR,new RiskRecord(UUID.randomUUID(),T,"x",RiskRecord.Kind.RISK,RiskRecord.Category.GENERAL_DECLARED,2,2,null,null,null,LocalDate.now()),A)); }
    @Test void continuityAndCyberRisksRequireContingencyPlan(){ assertThrows(GovernanceValidationException.class,()->new RiskRecord(UUID.randomUUID(),T,"falta de energia na ETA",RiskRecord.Kind.RISK,RiskRecord.Category.OPERATIONAL,3,4,null,null,null,LocalDate.now().plusDays(30))); assertThrows(GovernanceValidationException.class,()->new RiskRecord(UUID.randomUUID(),T,"incidente cibernetico",RiskRecord.Kind.RISK,RiskRecord.Category.CYBER,3,4,"  ",null,null,LocalDate.now())); }
    @Test void riskScoreUsesTenantCriterionAndAgencyChannelIsParameter(){ GovernanceService s=base(); RiskRecord r=s.registerRisk(GovernanceAccess.EDITOR,new RiskRecord(UUID.randomUUID(),T,"rompimento de adutora",RiskRecord.Kind.RISK,RiskRecord.Category.OPERATIONAL,4,4,"atendimento em modo degradado",null,null,LocalDate.now().plusDays(10)),A); assertEquals(16,s.riskScore(T,r)); assertEquals("reg.incident_report_channel",RiskRecord.AGENCY_CHANNEL_PARAMETER); assertTrue(s.agencyReportChannel(T).isEmpty()); }
    @Test void climateRiskIsRegistered(){ GovernanceService s=base(); s.registerRisk(GovernanceAccess.EDITOR,new RiskRecord(UUID.randomUUID(),T,"evento climatico extremo",RiskRecord.Kind.RISK,RiskRecord.Category.CLIMATE_RESILIENCE,2,5,null,null,null,LocalDate.now().plusDays(5)),A); assertEquals(1,s.pendingItems(T,LocalDate.now().plusDays(6)).size()); }
    @Test void objectiveRequiresExistingP341Indicator(){ GovernanceService s=base(); assertThrows(GovernanceValidationException.class,()->s.registerObjective(GovernanceAccess.EDITOR,new ObjectiveRecord(UUID.randomUUID(),T,"meta","IND-XXX",LocalDate.now(),"ops"),A)); ObjectiveRecord ok=s.registerObjective(GovernanceAccess.EDITOR,new ObjectiveRecord(UUID.randomUUID(),T,"meta","IND-001",LocalDate.now(),"ops"),A);
        // D8: o caminho de sucesso prova persistencia — o objetivo aparece no painel de pendencias.
        assertTrue(s.pendingItems(T,LocalDate.now()).stream().anyMatch(p->p.itemType().equals("OBJECTIVE")&&p.itemRef().equals(ok.id()))); }
    @Test void supplierEvaluationForEachType(){ InMemoryGovernanceStore store=new InMemoryGovernanceStore(); GovernanceService s=new GovernanceService(store,(t,c)->Set.of("IND-001").contains(c),(tpl,payload)->"rendered:"+tpl+":"+payload); s.declareCriterion(GovernanceAccess.EDITOR,T,new RiskCriterion(5,5,10,"matriz 5x5 declarada pelo tenant"),A);
        List<UUID> evaluationIds=new ArrayList<>();
        for(SupplierRecord.Type ty: SupplierRecord.Type.values()){ SupplierRecord sup=s.registerSupplier(GovernanceAccess.EDITOR,new SupplierRecord(UUID.randomUUID(),T,"F "+ty,"doc",ty,"contato"),A); SupplierEvaluation ev=s.evaluateSupplier(GovernanceAccess.EDITOR,T,new SupplierEvaluation(UUID.randomUUID(),T,sup.id(),LocalDate.now(),80,"criterio declarado",LocalDate.now().plusDays(90)),A); evaluationIds.add(ev.id());
            // D8: assevera efeito — a avaliacao ficou gravada para aquele fornecedor, com a nota dada.
            assertEquals(1,store.evaluations(sup.id()).size()); assertEquals(80,store.evaluations(sup.id()).get(0).score()); assertEquals(T,store.evaluations(sup.id()).get(0).tenantId()); }
        // E as 4 reavaliacoes aparecem no painel referenciando o id da AVALIACAO (D7), nao o do fornecedor.
        List<GovernanceService.PendingItem> pend=s.pendingItems(T,LocalDate.now().plusDays(100)).stream().filter(p->p.itemType().equals("SUPPLIER_EVALUATION")).toList();
        assertEquals(4,pend.size()); assertTrue(pend.stream().allMatch(p->evaluationIds.contains(p.itemRef()))); }
    @Test void competenceFeedsOsQualificationAndExpires(){ GovernanceService s=base(); UUID p=UUID.randomUUID(); s.registerCompetence(GovernanceAccess.EDITOR,new CompetenceRecord(UUID.randomUUID(),T,p,"COMP-1",Set.of("OS-LEAK"),LocalDate.now().plusDays(10),"ev"),A); assertTrue(s.isQualified(T,p,"OS-LEAK",LocalDate.now())); assertFalse(s.isQualified(T,p,"OS-LEAK",LocalDate.now().plusDays(11))); assertFalse(s.isQualified(T,p,"OS-OTHER",LocalDate.now())); }
    @Test void legalConformityAndFactoryImportDoNotInventLaws(){ InMemoryGovernanceStore store=new InMemoryGovernanceStore(); GovernanceService s=new GovernanceService(store,(t,c)->Set.of("IND-001").contains(c),(tpl,payload)->"rendered:"+tpl+":"+payload); s.declareCriterion(GovernanceAccess.EDITOR,T,new RiskCriterion(5,5,10,"matriz 5x5 declarada pelo tenant"),A); LegalRequirement l=s.registerLegal(GovernanceAccess.EDITOR,new LegalRequirement(UUID.randomUUID(),T,"requisito declarado","fonte declarada",LegalRequirement.Origin.TENANT_DECLARED,LocalDate.now().plusDays(3)),A); LocalDate evalDay=LocalDate.now(); s.evaluateConformity(GovernanceAccess.EDITOR,T,l.id(),LegalRequirement.Conformity.CONFORMING,evalDay,A);
        // D8/D7: a conformidade e LIDA DE VOLTA, com a data da avaliacao preservada no record.
        assertEquals(LegalRequirement.Conformity.CONFORMING,store.conformities(T).get(l.id()));
        LegalRequirement back=store.legal(T).stream().filter(x->x.id().equals(l.id())).findFirst().orElseThrow(); assertEquals(LegalRequirement.Conformity.CONFORMING,back.conformity()); assertEquals(evalDay,back.conformityEvaluatedOn());
        assertEquals(0,s.importFactoryCatalog(GovernanceAccess.EDITOR,T,t->List.of(),A)); }
    @Test void auditReportGoesThroughTemplateEngineWithJsonPayload(){ GovernanceService s=base(); AuditAndReview.AuditProgram p=new AuditAndReview.AuditProgram(UUID.randomUUID(),T,"programa",LocalDate.now()); s.planAudit(GovernanceAccess.EDITOR,p,A); s.addFinding(GovernanceAccess.EDITOR,T,new AuditAndReview.AuditFinding(UUID.randomUUID(),T,p.id(),"achado",AuditAndReview.AuditFinding.Severity.HIGH,LocalDate.now(),false),A); String out=s.renderAuditReport(T); assertTrue(out.contains("AUDIT_INTERNAL_REPORT")); assertTrue(out.contains("\"findings\"")); }
    @Test void managementReviewRequiresApproverAndReaderCannotWrite(){ GovernanceService s=base(); assertThrows(GovernanceAccessDeniedException.class,()->s.recordReview(GovernanceAccess.EDITOR,new AuditAndReview.ManagementReview(UUID.randomUUID(),T,LocalDate.now(),List.of("decisao"),"ata"),A)); assertThrows(GovernanceAccessDeniedException.class,()->s.registerSupplier(GovernanceAccess.READER,new SupplierRecord(UUID.randomUUID(),T,"F","d",SupplierRecord.Type.SERVICE,"c"),A)); }
    // --- Correcoes da revisao cruzada ---
    @Test void writeIsDenyByDefaultNullAndReaderAreRefused(){ GovernanceService s=base();
        // D4: null nao escreve (antes passava — fail-open); READER tambem nao, em mais de uma operacao.
        assertThrows(GovernanceAccessDeniedException.class,()->s.registerSupplier(null,new SupplierRecord(UUID.randomUUID(),T,"F","d",SupplierRecord.Type.SERVICE,"c"),A));
        assertThrows(GovernanceAccessDeniedException.class,()->s.declareCriterion(null,T,new RiskCriterion(3,3,5,"criterio"),A));
        assertThrows(GovernanceAccessDeniedException.class,()->s.registerCompetence(GovernanceAccess.READER,new CompetenceRecord(UUID.randomUUID(),T,UUID.randomUUID(),"C",Set.of("OS-X"),LocalDate.now(),"ev"),A));
        assertThrows(GovernanceAccessDeniedException.class,()->s.registerLegal(null,new LegalRequirement(UUID.randomUUID(),T,"r","fonte",LegalRequirement.Origin.TENANT_DECLARED,LocalDate.now()),A)); }
    @Test void findingWithUnknownOrForeignProgramIsRefusedExplicitly(){ GovernanceService s=base();
        // D5: programa inexistente — recusa nomeada, e nada fica gravado para sumir das leituras.
        GovernanceValidationException ex=assertThrows(GovernanceValidationException.class,()->s.addFinding(GovernanceAccess.EDITOR,T,new AuditAndReview.AuditFinding(UUID.randomUUID(),T,UUID.randomUUID(),"achado orfao",AuditAndReview.AuditFinding.Severity.LOW,LocalDate.now(),false),A));
        assertEquals("AUDIT_PROGRAM_NOT_FOUND",ex.code());
        // Programa de OUTRO tenant tambem e recusado.
        UUID other=UUID.randomUUID(); AuditAndReview.AuditProgram foreign=new AuditAndReview.AuditProgram(UUID.randomUUID(),other,"programa alheio",LocalDate.now()); s.planAudit(GovernanceAccess.EDITOR,foreign,A);
        assertThrows(GovernanceValidationException.class,()->s.addFinding(GovernanceAccess.EDITOR,T,new AuditAndReview.AuditFinding(UUID.randomUUID(),T,foreign.id(),"achado cruzado",AuditAndReview.AuditFinding.Severity.LOW,LocalDate.now(),false),A));
        assertTrue(s.pendingItems(T,LocalDate.now().plusYears(1)).stream().noneMatch(p->p.itemType().equals("AUDIT_FINDING"))); }
    @Test void conformitiesAreIsolatedByTenant(){ InMemoryGovernanceStore store=new InMemoryGovernanceStore(); GovernanceService s=new GovernanceService(store,(t,c)->true,(tpl,payload)->"x");
        UUID t2=UUID.randomUUID();
        LegalRequirement l1=s.registerLegal(GovernanceAccess.EDITOR,new LegalRequirement(UUID.randomUUID(),T,"req t1","fonte",LegalRequirement.Origin.TENANT_DECLARED,LocalDate.now()),A);
        LegalRequirement l2=s.registerLegal(GovernanceAccess.EDITOR,new LegalRequirement(UUID.randomUUID(),t2,"req t2","fonte",LegalRequirement.Origin.TENANT_DECLARED,LocalDate.now()),A);
        s.evaluateConformity(GovernanceAccess.EDITOR,T,l1.id(),LegalRequirement.Conformity.CONFORMING,LocalDate.now(),A);
        s.evaluateConformity(GovernanceAccess.EDITOR,t2,l2.id(),LegalRequirement.Conformity.NON_CONFORMING,LocalDate.now(),A);
        // D3: cada tenant le SO as suas conformidades.
        assertEquals(Map.of(l1.id(),LegalRequirement.Conformity.CONFORMING),store.conformities(T));
        assertEquals(Map.of(l2.id(),LegalRequirement.Conformity.NON_CONFORMING),store.conformities(t2)); }
    @Test void agencyChannelIsResolvedFromTenantParameterAtRiskRegistration(){ InMemoryGovernanceStore store=new InMemoryGovernanceStore();
        GovernanceService s=new GovernanceService(store,(t,c)->Set.of("IND-001").contains(c),(tpl,payload)->"rendered:"+tpl+":"+payload,(t,name)->RiskRecord.AGENCY_CHANNEL_PARAMETER.equals(name)?Optional.of("canal-de-teste-do-tenant"):Optional.empty());
        s.declareCriterion(GovernanceAccess.EDITOR,T,new RiskCriterion(5,5,10,"matriz 5x5 declarada pelo tenant"),A);
        // D6: a porta resolve o canal pelo nome do parametro; o valor vem do tenant, nunca do codigo.
        assertEquals(Optional.of("canal-de-teste-do-tenant"),s.agencyReportChannel(T));
        UUID occurrence=UUID.randomUUID();
        s.registerRisk(GovernanceAccess.EDITOR,new RiskRecord(UUID.randomUUID(),T,"contaminacao",RiskRecord.Kind.RISK,RiskRecord.Category.OPERATIONAL,3,5,"modo degradado",null,occurrence,LocalDate.now().plusDays(10)),A);
        // A resolucao e o vinculo com a ocorrencia da P2.25 entram na trilha do registro.
        assertTrue(store.auditTrail(T).stream().anyMatch(x->x.contains("reg.incident_report_channel")&&x.contains(occurrence.toString()))); }
    @Test void pendingPanelCoversTheSixItemTypes(){ GovernanceService s=base(); LocalDate due=LocalDate.now().plusDays(5); LocalDate horizon=LocalDate.now().plusDays(10);
        s.registerRisk(GovernanceAccess.EDITOR,new RiskRecord(UUID.randomUUID(),T,"risco",RiskRecord.Kind.RISK,RiskRecord.Category.GENERAL_DECLARED,2,2,null,null,null,due),A);
        s.registerObjective(GovernanceAccess.EDITOR,new ObjectiveRecord(UUID.randomUUID(),T,"meta","IND-001",due,"ops"),A);
        s.registerCompetence(GovernanceAccess.EDITOR,new CompetenceRecord(UUID.randomUUID(),T,UUID.randomUUID(),"COMP",Set.of("OS-X"),due,"ev"),A);
        s.registerLegal(GovernanceAccess.EDITOR,new LegalRequirement(UUID.randomUUID(),T,"req","fonte",LegalRequirement.Origin.TENANT_DECLARED,due),A);
        AuditAndReview.AuditProgram p=new AuditAndReview.AuditProgram(UUID.randomUUID(),T,"programa",LocalDate.now()); s.planAudit(GovernanceAccess.EDITOR,p,A);
        s.addFinding(GovernanceAccess.EDITOR,T,new AuditAndReview.AuditFinding(UUID.randomUUID(),T,p.id(),"achado aberto",AuditAndReview.AuditFinding.Severity.MEDIUM,due,false),A);
        SupplierRecord sup=s.registerSupplier(GovernanceAccess.EDITOR,new SupplierRecord(UUID.randomUUID(),T,"F","d",SupplierRecord.Type.SERVICE,"c"),A);
        s.evaluateSupplier(GovernanceAccess.EDITOR,T,new SupplierEvaluation(UUID.randomUUID(),T,sup.id(),LocalDate.now(),70,"criterio",due),A);
        // D2 (lado Java): os 6 tipos que a view rpt_cert_pending tambem tem de produzir.
        Set<String> types=new HashSet<>(); s.pendingItems(T,horizon).forEach(p2->types.add(p2.itemType()));
        assertEquals(Set.of("RISK_REVIEW","OBJECTIVE","COMPETENCE_EXPIRY","LEGAL_EVALUATION","AUDIT_FINDING","SUPPLIER_EVALUATION"),types); }
    @Test void factoryCatalogItemOutsideTenantOrOriginIsRefused(){ GovernanceService s=base();
        LegalRequirement wrongOrigin=new LegalRequirement(UUID.randomUUID(),T,"item","fonte",LegalRequirement.Origin.TENANT_DECLARED,LocalDate.now());
        GovernanceValidationException ex=assertThrows(GovernanceValidationException.class,()->s.importFactoryCatalog(GovernanceAccess.EDITOR,T,t->List.of(wrongOrigin),A));
        assertEquals("FACTORY_CATALOG_ITEM_INVALID",ex.code());
        LegalRequirement okItem=new LegalRequirement(UUID.randomUUID(),T,"item de fabrica","fonte do pacote sanitation",LegalRequirement.Origin.SANITATION_FACTORY_CATALOG,LocalDate.now());
        assertEquals(1,s.importFactoryCatalog(GovernanceAccess.EDITOR,T,t->List.of(okItem),A)); }
}
