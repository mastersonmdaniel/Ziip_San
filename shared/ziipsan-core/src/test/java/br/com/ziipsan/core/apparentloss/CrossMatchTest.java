package br.com.ziipsan.core.apparentloss;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.core.apparentloss.LossFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class CrossMatchTest {

    private static final CrossMatch.Policy POLICY = new CrossMatch.Policy(15, bd("10"));

    @Test
    void builtPropertyWithoutConnectionEntersReviewQueueWithDeadline() {
        UUID pid = UUID.randomUUID();
        List<CrossMatch.Finding> out = CrossMatch.evaluate(TENANT, CrossMatch.AccessChannel.BACKOFFICE,
                POLICY, List.of(property(pid, "120", PropertyRegistryRecord.PropertyUse.RESIDENTIAL)),
                List.of(), NOW);
        assertEquals(1, out.size());
        CrossMatch.Finding f = out.get(0);
        assertEquals(CrossMatch.FindingKind.BUILT_WITHOUT_CONNECTION, f.kind());
        assertEquals(CrossMatch.SuggestedAction.FIELD_SURVEY, f.suggestedAction());
        assertEquals(CrossMatch.ReviewStatus.PENDING_REVIEW, f.status());
        assertEquals(NOW.toLocalDate().plusDays(15), f.reviewDueDate());
        assertNull(f.workOrderId());
    }

    @Test
    void vacantLotWithoutConnectionDoesNotGenerateFinding() {
        List<CrossMatch.Finding> out = CrossMatch.evaluate(TENANT, CrossMatch.AccessChannel.BACKOFFICE,
                POLICY, List.of(property(UUID.randomUUID(), "0", PropertyRegistryRecord.PropertyUse.OTHER)),
                List.of(), NOW);
        assertTrue(out.isEmpty());
    }

    @Test
    void zeroConsumptionWithActiveConnectionIsFlaggedForInspection() {
        UUID pid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UnitFacts u = unit(uid, pid, UnitFacts.ConnectionStatus.ACTIVE,
                PropertyRegistryRecord.PropertyUse.RESIDENTIAL, "120", 1,
                List.of(bd("10"), bd("0")), List.of(bd("100"), bd("100")));
        List<CrossMatch.Finding> out = CrossMatch.evaluate(TENANT, CrossMatch.AccessChannel.BACKOFFICE,
                POLICY, List.of(property(pid, "120", PropertyRegistryRecord.PropertyUse.RESIDENTIAL)),
                List.of(u), NOW);
        assertEquals(1, out.size());
        assertEquals(CrossMatch.FindingKind.ZERO_CONSUMPTION_ACTIVE_CONNECTION, out.get(0).kind());
        assertEquals(CrossMatch.SuggestedAction.INSPECTION, out.get(0).suggestedAction());
    }

    @Test
    void areaDivergenceBeyondDeclaredToleranceIsFlaggedForBatchReRegistration() {
        UUID pid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UnitFacts u = unit(uid, pid, UnitFacts.ConnectionStatus.ACTIVE,
                PropertyRegistryRecord.PropertyUse.RESIDENTIAL, "200", 1,
                List.of(bd("10")), List.of(bd("100")));
        List<CrossMatch.Finding> out = CrossMatch.evaluate(TENANT, CrossMatch.AccessChannel.BACKOFFICE,
                POLICY, List.of(property(pid, "100", PropertyRegistryRecord.PropertyUse.RESIDENTIAL)),
                List.of(u), NOW);
        assertEquals(1, out.size());
        assertEquals(CrossMatch.FindingKind.AREA_DIVERGENCE, out.get(0).kind());
        assertEquals(CrossMatch.SuggestedAction.BATCH_RE_REGISTRATION, out.get(0).suggestedAction());
    }

    @Test
    void areaCheckDoesNotRunWithoutDeclaredTolerance() {
        UUID pid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UnitFacts u = unit(uid, pid, UnitFacts.ConnectionStatus.ACTIVE,
                PropertyRegistryRecord.PropertyUse.RESIDENTIAL, "900", 1,
                List.of(bd("10")), List.of(bd("100")));
        List<CrossMatch.Finding> out = CrossMatch.evaluate(TENANT, CrossMatch.AccessChannel.BACKOFFICE,
                new CrossMatch.Policy(15, null),
                List.of(property(pid, "100", PropertyRegistryRecord.PropertyUse.RESIDENTIAL)),
                List.of(u), NOW);
        assertTrue(out.isEmpty(), "sem tolerancia declarada, a checagem de area nao pode presumir numero");
    }

    @Test
    void useDivergenceBetweenRegistryAndCategoryIsFlagged() {
        UUID pid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UnitFacts u = unit(uid, pid, UnitFacts.ConnectionStatus.ACTIVE,
                PropertyRegistryRecord.PropertyUse.COMMERCIAL, "100", 1,
                List.of(bd("10")), List.of(bd("100")));
        List<CrossMatch.Finding> out = CrossMatch.evaluate(TENANT, CrossMatch.AccessChannel.BACKOFFICE,
                POLICY, List.of(property(pid, "100", PropertyRegistryRecord.PropertyUse.RESIDENTIAL)),
                List.of(u), NOW);
        assertEquals(1, out.size());
        assertEquals(CrossMatch.FindingKind.USE_DIVERGENCE, out.get(0).kind());
    }

    @Test
    void matchingPropertyAndUnitGenerateNoFinding() {
        UUID pid = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        UnitFacts u = unit(uid, pid, UnitFacts.ConnectionStatus.ACTIVE,
                PropertyRegistryRecord.PropertyUse.RESIDENTIAL, "102", 1,
                List.of(bd("10")), List.of(bd("100")));
        List<CrossMatch.Finding> out = CrossMatch.evaluate(TENANT, CrossMatch.AccessChannel.BACKOFFICE,
                POLICY, List.of(property(pid, "100", PropertyRegistryRecord.PropertyUse.RESIDENTIAL)),
                List.of(u), NOW);
        assertTrue(out.isEmpty());
    }

    @Test
    void crossMatchIsRestrictedToBackofficeAdr0034() {
        for (CrossMatch.AccessChannel channel : List.of(CrossMatch.AccessChannel.PORTAL,
                CrossMatch.AccessChannel.PUBLIC_API, CrossMatch.AccessChannel.FIELD_APP)) {
            IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                    CrossMatch.evaluate(TENANT, channel, POLICY, List.of(), List.of(), NOW));
            assertTrue(e.getMessage().contains("backoffice"));
        }
    }

    @Test
    void pendingFindingCannotCarryWorkOrderAndDecidedRequiresOne() {
        CrossMatch.Finding f = new CrossMatch.Finding(UUID.randomUUID(), TENANT,
                CrossMatch.FindingKind.USE_DIVERGENCE, UUID.randomUUID(), UUID.randomUUID(),
                "evidencia", CrossMatch.SuggestedAction.BATCH_RE_REGISTRATION, NOW,
                NOW.toLocalDate().plusDays(15), CrossMatch.ReviewStatus.PENDING_REVIEW, null);
        assertThrows(IllegalArgumentException.class, () -> new CrossMatch.Finding(
                UUID.randomUUID(), TENANT, CrossMatch.FindingKind.USE_DIVERGENCE,
                UUID.randomUUID(), UUID.randomUUID(), "evidencia",
                CrossMatch.SuggestedAction.BATCH_RE_REGISTRATION, NOW,
                NOW.toLocalDate().plusDays(15), CrossMatch.ReviewStatus.PENDING_REVIEW, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new CrossMatch.Finding(
                UUID.randomUUID(), TENANT, CrossMatch.FindingKind.USE_DIVERGENCE,
                UUID.randomUUID(), UUID.randomUUID(), "evidencia",
                CrossMatch.SuggestedAction.BATCH_RE_REGISTRATION, NOW,
                NOW.toLocalDate().plusDays(15), CrossMatch.ReviewStatus.DECIDED, null));
        assertEquals(CrossMatch.ReviewStatus.DECIDED, f.decided(
                new CrossMatch.HumanDecision(REVIEWER, NOW, CrossMatch.SuggestedAction.INSPECTION),
                UUID.randomUUID()).status());
    }
}
