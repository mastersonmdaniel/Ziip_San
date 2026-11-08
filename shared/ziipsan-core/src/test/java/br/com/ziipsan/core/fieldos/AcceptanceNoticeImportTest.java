package br.com.ziipsan.core.fieldos;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AcceptanceNoticeImportTest {
    @Test void signatureAndCodeAcceptanceByType() {
        CustomerAcceptance.requireSatisfied(CustomerAcceptance.Mode.NONE, null);
        CustomerAcceptance.requireSatisfied(CustomerAcceptance.Mode.SIGNATURE,
            new CustomerAcceptance.Evidence(CustomerAcceptance.Mode.SIGNATURE, UUID.randomUUID(), null, null));
        assertThrows(IllegalStateException.class, () -> CustomerAcceptance.requireSatisfied(
            CustomerAcceptance.Mode.SIGNATURE,
            new CustomerAcceptance.Evidence(CustomerAcceptance.Mode.SIGNATURE, null, null, null)));
        CustomerAcceptance.requireSatisfied(CustomerAcceptance.Mode.CODE,
            new CustomerAcceptance.Evidence(CustomerAcceptance.Mode.CODE, null, "4821", "4821"));
        assertThrows(IllegalStateException.class, () -> CustomerAcceptance.requireSatisfied(
            CustomerAcceptance.Mode.CODE,
            new CustomerAcceptance.Evidence(CustomerAcceptance.Mode.CODE, null, "0000", "4821")));
    }
    @Test void enRouteWindowComesFromTenantParameterAndGoesThroughEd41Port() {
        var params = new TestParams();
        params.windowHours = Optional.of(4);
        var sent = new java.util.ArrayList<String>();
        FieldOsPorts.EnRouteNotifier notifier = (t, o, c, s, e) -> { sent.add(s + "/" + e); return true; };
        var start = Instant.parse("2026-03-10T14:00:00Z");
        var w = EnRouteNotice.dispatch(notifier, params, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), start);
        assertEquals(start.plus(java.time.Duration.ofHours(4)), w.end());
        assertEquals(1, sent.size());
    }
    @Test void enRouteWithoutWindowParameterDoesNotInventWindow() {
        var params = new TestParams();
        FieldOsPorts.EnRouteNotifier notifier = (t, o, c, s, e) -> true;
        assertThrows(IllegalStateException.class, () -> EnRouteNotice.dispatch(
            notifier, params, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
    }
    @Test void importsGoThroughP140Importer() {
        FieldOsPorts.ImporterPort importer = (t, kind, refs) -> "batch-" + kind;
        assertEquals("batch-MATERIALS", FieldImport.importBatch(importer, UUID.randomUUID(),
            FieldImport.Kind.MATERIALS, List.of("mat-1")));
        assertThrows(IllegalArgumentException.class, () -> FieldImport.importBatch(importer, UUID.randomUUID(),
            FieldImport.Kind.FIELD_EMPLOYEES, List.of()));
    }
    @Test void materialUseCarriesTaskId() {
        var use = new MaterialUse.TaskMaterialUse("task-7",
            List.of(new MaterialUse.Line("TUBO-20MM", new BigDecimal("2"))));
        assertTrue(use.hasUse());
        assertEquals("task-7", use.taskId());
        assertThrows(IllegalArgumentException.class,
            () -> new MaterialUse.TaskMaterialUse(" ", List.of()));
    }
    @Test void samaeCoverageNamesBothItems() {
        assertEquals(List.of("2.18", "2.12.5"), SamaeFieldCoverage.items());
    }
    static final class TestParams implements FieldOsParameters {
        Optional<Integer> windowHours = Optional.empty();
        public Optional<Boolean> trackOnlyInShift(UUID t) { return Optional.of(true); }
        public Optional<Integer> trackRetentionDays(UUID t) { return Optional.empty(); }
        public Optional<Boolean> photoBlurFaces(UUID t) { return Optional.of(false); }
        public Optional<Integer> appointmentWindowHours(UUID t) { return windowHours; }
        public Optional<CustomerAcceptance.Mode> requiredAcceptance(UUID t, String type) { return Optional.of(CustomerAcceptance.Mode.NONE); }
    }
}
