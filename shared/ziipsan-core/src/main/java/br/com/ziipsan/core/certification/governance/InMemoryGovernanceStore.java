package br.com.ziipsan.core.certification.governance;

import java.time.LocalDate; import java.util.*; import java.util.stream.Collectors;
public final class InMemoryGovernanceStore implements GovernanceStore {
    private final Map<UUID,RiskCriterion> criteria=new HashMap<>(); private final List<RiskRecord> risks=new ArrayList<>();
    private final List<ObjectiveRecord> objectives=new ArrayList<>(); private final List<SupplierRecord> suppliers=new ArrayList<>();
    private final List<SupplierEvaluation> evaluations=new ArrayList<>(); private final List<CompetenceRecord> competences=new ArrayList<>();
    private final List<LegalRequirement> legal=new ArrayList<>();
    private final Map<UUID,AuditAndReview.AuditProgram> programs=new HashMap<>(); private final List<AuditAndReview.AuditFinding> findings=new ArrayList<>();
    private final List<AuditAndReview.ManagementReview> reviews=new ArrayList<>(); private final Map<UUID,List<String>> trail=new HashMap<>();
    public void saveCriterion(UUID t,RiskCriterion c){criteria.put(t,c);} public Optional<RiskCriterion> criterion(UUID t){return Optional.ofNullable(criteria.get(t));}
    public void saveRisk(RiskRecord r){risks.add(r);} public List<RiskRecord> risks(UUID t){return risks.stream().filter(r->r.tenantId().equals(t)).toList();}
    public void saveObjective(ObjectiveRecord o){objectives.add(o);} public List<ObjectiveRecord> objectives(UUID t){return objectives.stream().filter(o->o.tenantId().equals(t)).toList();}
    public void saveSupplier(SupplierRecord s){suppliers.add(s);} public List<SupplierRecord> suppliers(UUID t){return suppliers.stream().filter(s->s.tenantId().equals(t)).toList();}
    public void saveEvaluation(SupplierEvaluation e){evaluations.add(e);} public List<SupplierEvaluation> evaluations(UUID id){return evaluations.stream().filter(e->e.supplierId().equals(id)).toList();}
    public void saveCompetence(CompetenceRecord c){competences.add(c);} public List<CompetenceRecord> competences(UUID t){return competences.stream().filter(c->c.tenantId().equals(t)).toList();}
    public void saveLegal(LegalRequirement l){legal.add(l);}
    /** Conformidade vive no proprio record (correcao D7): a data {@code on} e gravada, nunca descartada. */
    public void saveConformity(UUID id,LegalRequirement.Conformity c,LocalDate on){
        for(int i=0;i<legal.size();i++){ if(legal.get(i).id().equals(id)){ legal.set(i, legal.get(i).withConformity(c,on)); return; } }
    }
    public List<LegalRequirement> legal(UUID t){return legal.stream().filter(l->l.tenantId().equals(t)).toList();}
    /** Correcao D3: so as conformidades dos requisitos DESTE tenant (antes devolvia as de todos). */
    public Map<UUID,LegalRequirement.Conformity> conformities(UUID t){return legal.stream().filter(l->l.tenantId().equals(t)).collect(Collectors.toMap(LegalRequirement::id, LegalRequirement::conformity));}
    public void saveProgram(AuditAndReview.AuditProgram p){programs.put(p.id(),p);} public void saveFinding(AuditAndReview.AuditFinding f){findings.add(f);}
    public Optional<AuditAndReview.AuditProgram> program(UUID id){return Optional.ofNullable(programs.get(id));}
    public List<AuditAndReview.AuditProgram> programs(UUID t){return programs.values().stream().filter(p->p.tenantId().equals(t)).toList();}
    public List<AuditAndReview.AuditFinding> findings(UUID t){return findings.stream().filter(f->f.tenantId().equals(t)).toList();}
    public void saveReview(AuditAndReview.ManagementReview r){reviews.add(r);} public List<AuditAndReview.ManagementReview> reviews(UUID t){return reviews.stream().filter(r->r.tenantId().equals(t)).toList();}
    public void audit(UUID t,String a,String d,UUID actor){trail.computeIfAbsent(t,k->new ArrayList<>()).add(a+":"+d);}
    public List<String> auditTrail(UUID t){return List.copyOf(trail.getOrDefault(t,List.of()));}
}
