package br.com.ziipsan.core.apparentloss;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static br.com.ziipsan.core.apparentloss.LossFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes das correcoes D1–D5 da REVISAO-ED-95 (APROVADO COM
 * RESSALVAS), um por defeito, escritos pelo corretor-2. Nenhum teste
 * pre-existente foi afrouxado: a baseline de 43 testes segue integra.
 */
class CorrectionsD1ToD5Test {

    private static final UUID OTHER_TENANT = UUID.fromString("99999999-9999-9999-9999-999999999999");

    static final class RecordingWorkOrders implements LossPorts.WorkOrderPort {
        final List<String> calls = new ArrayList<>();

        @Override
        public UUID openWorkOrder(UUID tenantId, LossPorts.WorkOrderKind kind, UUID unitId, UUID propertyId, String reason) {
            calls.add(kind.name());
            return UUID.randomUUID();
        }
    }

    static final class MapParams implements LossPorts.TenantParameters {
        @Override
        public Optional<String> resolve(UUID tenantId, String parameterName) {
            return switch (parameterName) {
                case LossParameters.PROPERTY_REGISTRY_SYNC -> Optional.of("MODO-DECLARADO-TESTE");
                case LossParameters.UNREGISTERED_BUILDING_REVIEW_DAYS -> Optional.of("15");
                case LossParameters.FRAUD_RULES -> Optional.of(ALL_RULES_JSON);
                case LossParameters.SELF_REPORT_FINE_WAIVER_ENABLED -> Optional.of("true");
                default -> Optional.empty();
            };
        }
    }

    private static LossPorts.UnitFactsPort factsPort(List<UnitFacts> units) {
        return new LossPorts.UnitFactsPort() {
            @Override
            public List<UnitFacts> factsForTenant(UUID tenantId) {
                return units;
            }

            @Override
            public Optional<UnitFacts> facts(UUID tenantId, UUID unitId) {
                return units.stream()
                        .filter(u -> u.unitId().equals(unitId) && u.tenantId().equals(tenantId))
                        .findFirst();
            }
        };
    }

    private static ApparentLossService service(InMemoryLossStore store, RecordingWorkOrders orders,
                                               List<UnitFacts> units) {
        return new ApparentLossService(store, null, factsPort(units), orders, new MapParams());
    }

    private static UnitFacts abruptDropUnit(UUID uid) {
        return unitFull(uid, null, List.of(bd("20"), bd("20"), bd("5")), List.of(bd("50")),
                null, null, null, null, null, false, null);
    }

    private static CrossMatch.HumanDecision inspectionDecision() {
        return new CrossMatch.HumanDecision(REVIEWER, NOW, CrossMatch.SuggestedAction.INSPECTION);
    }

    // ---------- D1: decisao sobre a suspeita registrada; segunda recusada ----------

    @Test
    void d1SecondDecisionOnSameSuspicionIsRefusedAndDecisionIsRecorded() {
        InMemoryLossStore store = new InMemoryLossStore();
        RecordingWorkOrders orders = new RecordingWorkOrders();
        UUID uid = UUID.randomUUID();
        ApparentLossService svc = service(store, orders, List.of(abruptDropUnit(uid)));
        List<FraudRules.Suspicion> suspicions = svc.evaluateUnit(TENANT, uid, NOW);
        assertEquals(1, suspicions.size());
        FraudRules.Suspicion pending = suspicions.get(0);
        assertFalse(pending.decided());

        UUID orderId = svc.decideSuspicionWithInspection(TENANT, uid, FraudRules.Rule.ABRUPT_DROP,
                inspectionDecision());
        assertEquals(List.of("INSPECTION"), orders.calls);

        FraudRules.Suspicion stored = store.suspicion(TENANT, pending.id()).orElseThrow();
        assertTrue(stored.decided(), "a decisao tem de ficar registrada sobre a suspeita (D1)");
        assertEquals(REVIEWER, stored.decidedBy());
        assertEquals(NOW, stored.decidedAt());
        assertEquals(orderId, stored.inspectionWorkOrderId());
        assertEquals(1, store.suspicions(TENANT).size(), "decidir nao duplica a suspeita no store");

        assertThrows(IllegalStateException.class, () -> svc.decideSuspicionWithInspection(
                TENANT, uid, FraudRules.Rule.ABRUPT_DROP, inspectionDecision()),
                "D1: a segunda decisao sobre a mesma (unidade, regra) e recusada");
        assertThrows(IllegalStateException.class, () -> svc.decideSuspicion(
                TENANT, pending.id(), inspectionDecision()),
                "D1: a segunda decisao sobre o mesmo id e recusada");
        assertEquals(1, orders.calls.size(), "D1: nenhuma segunda OS foi aberta");
    }

    @Test
    void d1SuspicionRecordRefusesPartialDecisionStateAndDoubleTransition() {
        assertThrows(IllegalArgumentException.class, () -> new FraudRules.Suspicion(
                UUID.randomUUID(), TENANT, UUID.randomUUID(), FraudRules.Rule.ABRUPT_DROP,
                "evidencia", NOW, FraudRules.Recommendation.HUMAN_INSPECTION_REVIEW,
                FraudRules.AutomaticEffect.NONE, REVIEWER, null, null),
                "estado de decisao parcial (so decidedBy) nao existe");
        FraudRules.Suspicion decided = new FraudRules.Suspicion(
                UUID.randomUUID(), TENANT, UUID.randomUUID(), FraudRules.Rule.ABRUPT_DROP,
                "evidencia", NOW, FraudRules.Recommendation.HUMAN_INSPECTION_REVIEW,
                FraudRules.AutomaticEffect.NONE)
                .decidedWithInspection(inspectionDecision(), UUID.randomUUID());
        assertThrows(IllegalStateException.class,
                () -> decided.decidedWithInspection(inspectionDecision(), UUID.randomUUID()));
    }

    // ---------- D3: CrossMatch.evaluate recusa entradas de outro tenant ----------

    @Test
    void d3EvaluateRefusesPropertyFromAnotherTenant() {
        UUID pid = UUID.randomUUID();
        PropertyRegistryRecord foreign = new PropertyRegistryRecord(pid, OTHER_TENANT,
                "INSC-ESTRANGEIRA", bd("100"), PropertyRegistryRecord.PropertyUse.RESIDENTIAL,
                "HAB-1", "ALV-1", "base legal declarada pelo ente (massa sintetica)",
                "finalidade declarada pelo ente (massa sintetica)",
                PropertyRegistryRecord.RegistrySource.MUNICIPAL_CONNECTOR, "LOTE-X", NOW);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                CrossMatch.evaluate(TENANT, CrossMatch.AccessChannel.BACKOFFICE,
                        new CrossMatch.Policy(15, bd("10")), List.of(foreign), List.of(), NOW));
        assertTrue(e.getMessage().contains("outro tenant"));
    }

    @Test
    void d3EvaluateRefusesUnitFromAnotherTenant() {
        UUID pid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UnitFacts foreignUnit = new UnitFacts(uid, OTHER_TENANT, pid, UUID.randomUUID(),
                UnitFacts.ConnectionStatus.ACTIVE, PropertyRegistryRecord.PropertyUse.RESIDENTIAL,
                bd("100"), 1, List.of(bd("10")), List.of(bd("100")),
                null, null, null, null, null, false, null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                CrossMatch.evaluate(TENANT, CrossMatch.AccessChannel.BACKOFFICE,
                        new CrossMatch.Policy(15, bd("10")),
                        List.of(property(pid, "100", PropertyRegistryRecord.PropertyUse.RESIDENTIAL)),
                        List.of(foreignUnit), NOW));
        assertTrue(e.getMessage().contains("outro tenant"));
    }

    // ---------- D4: proveniencia no modelo + evaluationChannel modelado ----------

    @Test
    void d4DecidedFindingCarriesDecisionProvenanceAndBackofficeChannel() {
        InMemoryLossStore store = new InMemoryLossStore();
        RecordingWorkOrders orders = new RecordingWorkOrders();
        UUID pid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UnitFacts u = unit(uid, pid, UnitFacts.ConnectionStatus.ACTIVE,
                PropertyRegistryRecord.PropertyUse.COMMERCIAL, "100", 1,
                List.of(bd("10")), List.of(bd("100")));
        ApparentLossService svc = service(store, orders, List.of(u));
        svc.importRegistryRecords(TENANT, List.of(property(pid, "100",
                PropertyRegistryRecord.PropertyUse.RESIDENTIAL)));
        CrossMatch.Finding f = svc.runCrossMatch(TENANT, CrossMatch.AccessChannel.BACKOFFICE, bd("10"), NOW).get(0);
        assertEquals(CrossMatch.AccessChannel.BACKOFFICE, f.evaluationChannel(),
                "o canal da avaliacao mora no modelo (coluna evaluation_channel do SQL)");
        assertNull(f.decidedBy());
        assertNull(f.decidedAt());

        CrossMatch.Finding decided = svc.decideFinding(TENANT, f.id(), inspectionDecision());
        assertEquals(REVIEWER, decided.decidedBy(), "D4: decidedBy viaja no Finding");
        assertEquals(NOW, decided.decidedAt(), "D4: decidedAt viaja no Finding");
        assertEquals(decided, store.finding(TENANT, f.id()).orElseThrow(),
                "o store guarda o achado ja com a proveniencia — saveFinding nao precisa de decisao por fora");

        CrossMatch.Finding g = svc.runCrossMatch(TENANT, CrossMatch.AccessChannel.BACKOFFICE, bd("10"), NOW)
                .stream().filter(x -> !x.id().equals(f.id())).findFirst().orElse(null);
        if (g != null) {
            CrossMatch.Finding dismissed = svc.dismissFinding(TENANT, g.id(), inspectionDecision());
            assertEquals(REVIEWER, dismissed.decidedBy(), "descarte tambem carrega a proveniencia");
        }
    }

    @Test
    void d4FindingRefusesDecidedWithoutProvenanceAndNonBackofficeChannel() {
        assertThrows(IllegalArgumentException.class, () -> new CrossMatch.Finding(
                UUID.randomUUID(), TENANT, CrossMatch.FindingKind.USE_DIVERGENCE,
                UUID.randomUUID(), UUID.randomUUID(), "evidencia",
                CrossMatch.SuggestedAction.BATCH_RE_REGISTRATION, NOW,
                NOW.toLocalDate().plusDays(15), CrossMatch.ReviewStatus.DECIDED, UUID.randomUUID(),
                null, null, CrossMatch.AccessChannel.BACKOFFICE),
                "DECIDED com OS e sem decidedBy/decidedAt e recusado no modelo, como no CHECK do SQL");
        assertThrows(IllegalArgumentException.class, () -> new CrossMatch.Finding(
                UUID.randomUUID(), TENANT, CrossMatch.FindingKind.USE_DIVERGENCE,
                UUID.randomUUID(), UUID.randomUUID(), "evidencia",
                CrossMatch.SuggestedAction.BATCH_RE_REGISTRATION, NOW,
                NOW.toLocalDate().plusDays(15), CrossMatch.ReviewStatus.PENDING_REVIEW, null,
                null, null, CrossMatch.AccessChannel.PORTAL),
                "evaluationChannel fora do backoffice e recusado no modelo");
    }

    // ---------- D5a: decideSuspicion exige acao INSPECTION explicita ----------

    @Test
    void d5aSuspicionDecisionWithNonInspectionActionIsRefusedAndOpensNothing() {
        InMemoryLossStore store = new InMemoryLossStore();
        RecordingWorkOrders orders = new RecordingWorkOrders();
        UUID uid = UUID.randomUUID();
        ApparentLossService svc = service(store, orders, List.of(abruptDropUnit(uid)));
        svc.evaluateUnit(TENANT, uid, NOW);
        for (CrossMatch.SuggestedAction wrong : List.of(CrossMatch.SuggestedAction.FIELD_SURVEY,
                CrossMatch.SuggestedAction.BATCH_RE_REGISTRATION)) {
            assertThrows(IllegalArgumentException.class, () -> svc.decideSuspicionWithInspection(
                    TENANT, uid, FraudRules.Rule.ABRUPT_DROP,
                    new CrossMatch.HumanDecision(REVIEWER, NOW, wrong)),
                    "D5a: acao " + wrong + " nao pode virar OS de fiscalizacao em silencio");
        }
        assertTrue(orders.calls.isEmpty(), "D5a: nenhuma OS nasceu de acao errada");
        assertFalse(store.suspicions(TENANT).get(0).decided(), "a suspeita segue pendente");
    }

    // ---------- D5b: fileSelfReport valida a unidade no tenant ----------

    @Test
    void d5bSelfReportRefusesUnitThatDoesNotExistInTenant() {
        InMemoryLossStore store = new InMemoryLossStore();
        RecordingWorkOrders orders = new RecordingWorkOrders();
        UUID uid = UUID.randomUUID();
        ApparentLossService svc = service(store, orders, List.of(abruptDropUnit(uid)));
        assertThrows(IllegalArgumentException.class, () -> svc.fileSelfReport(
                TENANT, UUID.randomUUID(), "irregularidade declarada (massa sintetica)", NOW),
                "D5b: autodenuncia para unidade inexistente no tenant e recusada");
        assertTrue(store.selfReports(TENANT).isEmpty());
        SelfReport.Case c = svc.fileSelfReport(TENANT, uid,
                "irregularidade declarada (massa sintetica)", NOW);
        assertEquals(SelfReport.CaseStatus.PENDING_HUMAN_REVIEW, c.status());
        assertEquals(1, store.selfReports(TENANT).size());
    }
}
