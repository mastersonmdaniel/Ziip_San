package br.com.ziipsan.core.fieldos;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FieldOsSyncServiceTest {
    static final UUID TENANT = UUID.randomUUID(), ORDER = UUID.randomUUID();
    static CutPackage.Facts openOk() {
        return new CutPackage.Facts(CutPackage.DebitState.OPEN, false, true, Set.of());
    }
    static final class Harness {
        CutPackage.Facts current = openOk();
        final List<String> calls = new java.util.ArrayList<>();
        final FieldOsSyncService service = new FieldOsSyncService(
            (t, o) -> current,
            (t, o, r) -> calls.add("cancel:" + r),
            (t, task, lines) -> calls.add("stock:" + task),
            (t, o, s) -> calls.add("sac"),
            new TestParamsSupport.SignatureParams());
    }
    static FieldOsSyncService.SyncSubmission submission(boolean cut, boolean withExtras) {
        var snap = cut ? new CutPackage.Snapshot(ORDER, UUID.randomUUID(), "task-1", Instant.now(), openOk()) : null;
        return new FieldOsSyncService.SyncSubmission(ORDER, cut, snap,
            new CustomerAcceptance.Evidence(CustomerAcceptance.Mode.SIGNATURE, UUID.randomUUID(), null, null),
            withExtras ? new MaterialUse.TaskMaterialUse("task-1", List.of(new MaterialUse.Line("REG-1", BigDecimal.ONE))) : null,
            withExtras ? new SatisfactionSurvey(ORDER, "OTIMO", null, Instant.now()) : null);
    }
    @Test void serverRevalidationCancelsPaidAfterIssueWithNoSideEffects() {
        var h = new Harness();
        h.current = new CutPackage.Facts(CutPackage.DebitState.PAID, false, true, Set.of());
        var r = h.service.syncExecution(TENANT, "CORTE", submission(true, true));
        assertEquals(FieldOsSyncService.Outcome.CANCELLED_NO_CONNECTION_EFFECT, r.outcome());
        assertEquals(List.of(CutRevalidation.Reason.DEBIT_PAID_AFTER_ISSUE), r.cancelReasons());
        assertEquals(1, h.calls.size());
        assertTrue(h.calls.get(0).startsWith("cancel:"));
    }
    @Test void acceptedSyncWritesOffStockAndRegistersSatisfaction() {
        var h = new Harness();
        var r = h.service.syncExecution(TENANT, "CORTE", submission(true, true));
        assertTrue(r.accepted());
        assertEquals(List.of("stock:task-1", "sac"), h.calls);
    }
    @Test void syncWithoutAcceptanceEvidenceIsRefused() {
        var h = new Harness();
        var sub = new FieldOsSyncService.SyncSubmission(ORDER, false, null, null, null, null);
        assertThrows(IllegalStateException.class, () -> h.service.syncExecution(TENANT, "CORTE", sub));
        assertTrue(h.calls.isEmpty());
    }
}
