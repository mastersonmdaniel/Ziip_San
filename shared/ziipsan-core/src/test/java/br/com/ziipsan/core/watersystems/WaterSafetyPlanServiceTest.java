package br.com.ziipsan.core.watersystems;

import br.com.ziipsan.core.certification.governance.RiskRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WaterSafetyPlanServiceTest {

    private InMemorySystemsStore storeWithSupply() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        SystemRegistryService.registerSystem(store, SystemsTestSupport.catalogPort(Map.of()), SystemsTestSupport.supplySystem("SAA-01"));
        SystemRegistryService.registerSystem(store, SystemsTestSupport.catalogPort(Map.of()), SystemsTestSupport.sewageSystem("SES-01"));
        return store;
    }

    private WaterSafetyPlan.Plan draft(InMemorySystemsStore store, String systemCode) {
        WaterSafetyPlan.Plan plan = WaterSafetyPlan.Plan.draft(UUID.randomUUID(), SystemsTestSupport.TENANT, systemCode, 1);
        WaterSafetyPlanService.createDraft(store, plan);
        return plan;
    }

    private WaterSafetyPlan.HazardLink link(UUID planId, UUID riskId) {
        return new WaterSafetyPlan.HazardLink(SystemsTestSupport.TENANT, planId, riskId,
                "medida de controle de teste", "PARAM_A", "acao corretiva de teste");
    }

    @Test void planOnSewageSystemRejected() {
        InMemorySystemsStore store = storeWithSupply();
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> draft(store, "SES-01"));
        assertEquals("PSA_REQUIRES_SUPPLY_SYSTEM", ex.code());
    }

    @Test void planOnMissingSystemRejected() {
        InMemorySystemsStore store = storeWithSupply();
        assertThrows(SystemsExceptions.NotFoundException.class, () -> draft(store, "SAA-INEXISTENTE"));
    }

    @Test void linkToUnknownRiskRejected() {
        InMemorySystemsStore store = storeWithSupply();
        WaterSafetyPlan.Plan plan = draft(store, "SAA-01");
        SystemsExceptions.NotFoundException ex = assertThrows(SystemsExceptions.NotFoundException.class,
                () -> WaterSafetyPlanService.addLink(store, SystemsTestSupport.riskPort(Map.of()),
                        link(plan.id(), UUID.randomUUID())));
        assertEquals("RISK_NOT_FOUND", ex.code());
    }

    @Test void linkToForeignTenantRiskRejected() {
        InMemorySystemsStore store = storeWithSupply();
        WaterSafetyPlan.Plan plan = draft(store, "SAA-01");
        RiskRecord foreign = SystemsTestSupport.risk(UUID.randomUUID(), SystemsTestSupport.TENANT_2,
                RiskRecord.Category.OPERATIONAL, null);
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> WaterSafetyPlanService.addLink(store, SystemsTestSupport.foreignRiskPort(foreign),
                        link(plan.id(), foreign.id())));
        assertEquals("RISK_OTHER_TENANT", ex.code());
    }

    @Test void duplicateLinkRejected() {
        InMemorySystemsStore store = storeWithSupply();
        WaterSafetyPlan.Plan plan = draft(store, "SAA-01");
        RiskRecord risk = SystemsTestSupport.risk(UUID.randomUUID(), SystemsTestSupport.TENANT,
                RiskRecord.Category.GENERAL_DECLARED, null);
        var port = SystemsTestSupport.riskPort(Map.of(risk.id(), risk));
        WaterSafetyPlanService.addLink(store, port, link(plan.id(), risk.id()));
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> WaterSafetyPlanService.addLink(store, port, link(plan.id(), risk.id())));
        assertEquals("RISK_ALREADY_LINKED", ex.code());
    }

    @Test void approveWithoutLinksRejected() {
        InMemorySystemsStore store = storeWithSupply();
        WaterSafetyPlan.Plan plan = draft(store, "SAA-01");
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> WaterSafetyPlanService.approve(store, SystemsTestSupport.riskPort(Map.of()),
                        SystemsTestSupport.TENANT, plan.id(), "responsavel-teste",
                        Instant.parse("2026-02-01T00:00:00Z"), LocalDate.of(2027, 2, 1)));
        assertEquals("PSA_WITHOUT_RISK_LINKS", ex.code());
    }

    @Test void coverageReflectsRealRiskCategoriesAndOccurrences() {
        InMemorySystemsStore store = storeWithSupply();
        WaterSafetyPlan.Plan plan = draft(store, "SAA-01");
        UUID occurrence = UUID.randomUUID();
        RiskRecord operational = SystemsTestSupport.risk(UUID.randomUUID(), SystemsTestSupport.TENANT,
                RiskRecord.Category.OPERATIONAL, occurrence);
        RiskRecord climate = SystemsTestSupport.risk(UUID.randomUUID(), SystemsTestSupport.TENANT,
                RiskRecord.Category.CLIMATE_RESILIENCE, null);
        RiskRecord general = SystemsTestSupport.risk(UUID.randomUUID(), SystemsTestSupport.TENANT,
                RiskRecord.Category.GENERAL_DECLARED, null);
        var port = SystemsTestSupport.riskPort(Map.of(
                operational.id(), operational, climate.id(), climate, general.id(), general));
        WaterSafetyPlanService.addLink(store, port, link(plan.id(), operational.id()));
        WaterSafetyPlanService.addLink(store, port, link(plan.id(), general.id()));

        WaterSafetyPlan.PlanCoverage partial = WaterSafetyPlanService.coverage(
                store, port, SystemsTestSupport.TENANT, plan.id());
        assertEquals(2, partial.linkedRiskCount());
        assertTrue(partial.coversOperationalIncidents());
        assertFalse(partial.coversClimateResilience());
        assertEquals(java.util.List.of(occurrence), partial.linkedOccurrenceIds());

        WaterSafetyPlanService.addLink(store, port, link(plan.id(), climate.id()));
        WaterSafetyPlan.PlanCoverage full = WaterSafetyPlanService.coverage(
                store, port, SystemsTestSupport.TENANT, plan.id());
        assertTrue(full.coversClimateResilience());
        assertEquals(3, full.linkedRiskCount());
    }

    @Test void approveWithLinksSucceedsAndLocksLinks() {
        InMemorySystemsStore store = storeWithSupply();
        WaterSafetyPlan.Plan plan = draft(store, "SAA-01");
        RiskRecord risk = SystemsTestSupport.risk(UUID.randomUUID(), SystemsTestSupport.TENANT,
                RiskRecord.Category.CLIMATE_RESILIENCE, null);
        var port = SystemsTestSupport.riskPort(Map.of(risk.id(), risk));
        WaterSafetyPlanService.addLink(store, port, link(plan.id(), risk.id()));
        WaterSafetyPlan.Plan approved = WaterSafetyPlanService.approve(store, port,
                SystemsTestSupport.TENANT, plan.id(), "responsavel-teste",
                Instant.parse("2026-02-01T00:00:00Z"), LocalDate.of(2027, 2, 1));
        assertEquals(WaterSafetyPlan.Status.APPROVED, approved.status());
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> WaterSafetyPlanService.addLink(store, port, link(plan.id(), UUID.randomUUID())));
        assertEquals("PLAN_NOT_DRAFT", ex.code());
    }

    @Test void hazardLinkRequiresControlAndCorrectiveAction() {
        assertThrows(IllegalArgumentException.class, () -> new WaterSafetyPlan.HazardLink(
                SystemsTestSupport.TENANT, UUID.randomUUID(), UUID.randomUUID(), " ", "PARAM_A", "acao"));
        assertThrows(IllegalArgumentException.class, () -> new WaterSafetyPlan.HazardLink(
                SystemsTestSupport.TENANT, UUID.randomUUID(), UUID.randomUUID(), "medida", "PARAM_A", null));
    }
}
