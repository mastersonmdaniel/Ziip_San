package br.com.ziipsan.core.watersystems;

import br.com.ziipsan.core.certification.governance.RiskRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Servico do PSA (ED-79): rascunho sobre sistema SUPPLY, vinculos a riscos reais da P3.31, aprovacao com cobertura. */
public final class WaterSafetyPlanService {
    private WaterSafetyPlanService() {}

    public static WaterSafetyPlan.Plan createDraft(SystemsStore store, WaterSafetyPlan.Plan draft) {
        if (draft.status() != WaterSafetyPlan.Status.DRAFT)
            throw new SystemsExceptions.ValidationException("PLAN_NOT_DRAFT", "o PSA nasce em rascunho");
        WaterSystem system = store.system(draft.tenantId(), draft.supplySystemCode())
                .orElseThrow(() -> new SystemsExceptions.NotFoundException("SYSTEM_NOT_FOUND",
                        "PSA de sistema inexistente: " + draft.supplySystemCode()));
        if (system.kind() != WaterSystem.Kind.SUPPLY)
            throw new SystemsExceptions.ValidationException("PSA_REQUIRES_SUPPLY_SYSTEM",
                    "Plano de Seguranca da Agua e de sistema de ABASTECIMENTO: " + draft.supplySystemCode());
        store.savePlan(draft);
        return draft;
    }

    public static WaterSafetyPlan.HazardLink addLink(SystemsStore store,
                                                     SystemsPorts.RiskRegistryPort risks,
                                                     WaterSafetyPlan.HazardLink link) {
        WaterSafetyPlan.Plan plan = store.plan(link.tenantId(), link.planId())
                .orElseThrow(() -> new SystemsExceptions.NotFoundException("PLAN_NOT_FOUND",
                        "vinculo para PSA inexistente: " + link.planId()));
        if (plan.status() != WaterSafetyPlan.Status.DRAFT)
            throw new SystemsExceptions.ValidationException("PLAN_NOT_DRAFT",
                    "vinculo so entra em PSA em rascunho: " + link.planId());
        RiskRecord risk = risks.findRisk(link.tenantId(), link.riskId())
                .orElseThrow(() -> new SystemsExceptions.NotFoundException("RISK_NOT_FOUND",
                        "risco da P3.31 (GES-20) inexistente neste tenant: " + link.riskId()));
        // O RiskRecord devolvido pela porta e do tenant pedido (contrato da porta);
        // a guarda abaixo fecha o caso de uma fiacao que devolva risco de outro tenant.
        if (!risk.tenantId().equals(link.tenantId()))
            throw new SystemsExceptions.ValidationException("RISK_OTHER_TENANT",
                    "risco " + link.riskId() + " nao pertence ao tenant do PSA");
        boolean duplicate = store.links(link.tenantId(), link.planId()).stream()
                .anyMatch(l -> l.riskId().equals(link.riskId()));
        if (duplicate)
            throw new SystemsExceptions.ValidationException("RISK_ALREADY_LINKED",
                    "risco ja vinculado a este PSA: " + link.riskId());
        store.saveLink(link);
        return link;
    }

    public static WaterSafetyPlan.PlanCoverage coverage(SystemsStore store,
                                                        SystemsPorts.RiskRegistryPort risks,
                                                        UUID tenantId, UUID planId) {
        if (store.plan(tenantId, planId).isEmpty())
            throw new SystemsExceptions.NotFoundException("PLAN_NOT_FOUND", "PSA inexistente: " + planId);
        List<RiskRecord> linked = new ArrayList<>();
        for (WaterSafetyPlan.HazardLink link : store.links(tenantId, planId)) {
            risks.findRisk(tenantId, link.riskId()).ifPresent(linked::add);
        }
        return WaterSafetyPlan.PlanCoverage.of(linked);
    }

    /**
     * Aprova o PSA: sem NENHUM vinculo a risco da P3.31 nao ha plano
     * (o aceite liga o PSA aos riscos — PSA vazio nao aprova). A
     * cobertura por categoria e devolvida no ato via {@link #coverage}
     * para a integracao exibir; esta regra nao presume cobertura de
     * categoria que o tenant nao vinculou.
     */
    public static WaterSafetyPlan.Plan approve(SystemsStore store,
                                               SystemsPorts.RiskRegistryPort risks,
                                               UUID tenantId, UUID planId,
                                               String approvedByRef, Instant at, LocalDate reviewDue) {
        WaterSafetyPlan.Plan plan = store.plan(tenantId, planId)
                .orElseThrow(() -> new SystemsExceptions.NotFoundException("PLAN_NOT_FOUND",
                        "PSA inexistente: " + planId));
        if (plan.status() != WaterSafetyPlan.Status.DRAFT)
            throw new SystemsExceptions.ValidationException("PLAN_NOT_DRAFT",
                    "so PSA em rascunho aprova: " + planId);
        WaterSafetyPlan.PlanCoverage coverage = coverage(store, risks, tenantId, planId);
        if (coverage.linkedRiskCount() == 0)
            throw new SystemsExceptions.ValidationException("PSA_WITHOUT_RISK_LINKS",
                    "PSA sem vinculo a risco da P3.31 (GES-20) nao aprova: " + planId);
        WaterSafetyPlan.Plan approved = plan.approved(approvedByRef, at, reviewDue);
        store.savePlan(approved);
        return approved;
    }
}
