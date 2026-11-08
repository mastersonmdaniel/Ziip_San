package br.com.ziipsan.intelligence.gis.field;

import br.com.ziipsan.intelligence.gis.field.FieldDeltaApplier.Delta;
import br.com.ziipsan.intelligence.gis.field.FieldDeltaApplier.FeatureState;
import br.com.ziipsan.intelligence.gis.field.FieldDeltaApplier.Outcome;
import br.com.ziipsan.intelligence.gis.field.GisFieldRules.AccuracyTarget;
import br.com.ziipsan.intelligence.gis.field.GisFieldRules.GnssFix;
import br.com.ziipsan.intelligence.gis.field.GisFieldRules.Solution;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldDeltaApplierTest {

    private static final String ID = "0192a1b4-0000-7000-8000-000000000001";
    private static final GnssFix FIXED_OK = new GnssFix(Solution.RTK_FIXED,
            -26.900001, -48.700001, 812.40, 0.02, 0.03, 1.4, 18, 2.0);
    private static final GnssFix FIXED_COARSE = new GnssFix(Solution.RTK_FIXED,
            -26.900001, -48.700001, 812.40, 0.25, 0.03, 1.4, 18, 2.0);
    private static final AccuracyTarget TARGET = new AccuracyTarget(0.10, 0.05);

    @Test
    void appliesChangedFieldsOnCurrentBase() {
        FeatureState current = new FeatureState(7, Map.of("dn_mm", 100, "operational_status", "OPEN"));
        Delta delta = new Delta(ID, 7L,
                Map.of("operational_status", "CLOSED"), Map.of("operational_status", "OPEN"));
        var result = FieldDeltaApplier.apply(current, delta, new HashSet<>());
        assertEquals(Outcome.APPLIED, result.outcome());
        assertEquals(8, result.newState().version());
        assertEquals("CLOSED", result.newState().attributes().get("operational_status"));
        assertEquals(100, result.newState().attributes().get("dn_mm"));
    }

    @Test
    void resendWithSameClientChangeIdIsDuplicateAndChangesNothing() {
        FeatureState current = new FeatureState(8, Map.of("operational_status", "CLOSED"));
        Delta delta = new Delta(ID, 7L,
                Map.of("operational_status", "CLOSED"), Map.of("operational_status", "OPEN"));
        var result = FieldDeltaApplier.apply(current, delta, Set.of(ID));
        assertEquals(Outcome.DUPLICATE, result.outcome());
        assertEquals(current, result.newState());
    }

    @Test
    void staleBaseConflictsOnlyOnTouchedAttribute() {
        // Servidor avancou para a versao 9: outro editor mudou dn_mm,
        // ninguem mexeu em operational_status desde a base do aparelho.
        FeatureState current = new FeatureState(9, Map.of("dn_mm", 150, "operational_status", "OPEN"));
        Delta untouched = new Delta(ID, 7L,
                Map.of("operational_status", "CLOSED"), Map.of("operational_status", "OPEN"));
        var applied = FieldDeltaApplier.apply(current, untouched, new HashSet<>());
        assertEquals(Outcome.APPLIED, applied.outcome());

        Delta touched = new Delta(ID, 7L,
                Map.of("dn_mm", 200), Map.of("dn_mm", 100));
        var conflict = FieldDeltaApplier.apply(current, touched, new HashSet<>());
        assertEquals(Outcome.CONFLICT, conflict.outcome());
        assertEquals(List.of("dn_mm"), conflict.conflictFields());
        assertEquals(current, conflict.newState());
    }

    @Test
    void staleBaseWithGeometryOnlyChangeAppliesWhenNoAttributeTouched() {
        FeatureState current = new FeatureState(9, Map.of("dn_mm", 150));
        Delta delta = new Delta(ID, 7L, Map.of(), Map.of());
        var result = FieldDeltaApplier.apply(current, delta, new HashSet<>());
        assertEquals(Outcome.APPLIED, result.outcome());
    }

    // --- Correcao m2: o aplicador FECHA o ciclo da idempotencia ---

    @Test
    void appliedRegistersIdAndReturnsReceiptIdSoResendWithSameSetIsDuplicate() {
        // Sonda PROBE-J3b da revisao: aplicar com um conjunto NOVO vazio
        // reaplicava. Agora o apply REGISTRA o id no conjunto e o devolve
        // como id do recibo: reaplicar com o MESMO conjunto e DUPLICATE.
        FeatureState current = new FeatureState(7, Map.of("operational_status", "OPEN"));
        Delta delta = new Delta(ID, 7L,
                Map.of("operational_status", "CLOSED"), Map.of("operational_status", "OPEN"));
        Set<String> seen = new HashSet<>();
        var first = FieldDeltaApplier.apply(current, delta, seen);
        assertEquals(Outcome.APPLIED, first.outcome());
        assertEquals(ID, first.receiptClientChangeId());
        assertTrue(seen.contains(ID));
        var second = FieldDeltaApplier.apply(first.newState(), delta, seen);
        assertEquals(Outcome.DUPLICATE, second.outcome());
        assertEquals(first.newState(), second.newState());
        assertNull(second.receiptClientChangeId());
    }

    @Test
    void immutableSeenSetOnTerminalOutcomeFailsNamed() {
        FeatureState current = new FeatureState(7, Map.of("operational_status", "OPEN"));
        Delta delta = new Delta(ID, 7L,
                Map.of("operational_status", "CLOSED"), Map.of("operational_status", "OPEN"));
        var ex = assertThrows(IllegalArgumentException.class,
                () -> FieldDeltaApplier.apply(current, delta, Set.of()));
        assertTrue(ex.getMessage().contains("MUTAVEL"));
    }

    // --- Correcao M1: INSERT passa pelo aplicador e pelo portao de meta ---

    @Test
    void insertCreatesFeatureStateAtVersionOne() {
        Delta delta = Delta.insert(ID, Map.of("dn_mm", 50), false, FIXED_OK, null, null, null);
        var result = FieldDeltaApplier.apply(null, delta, new HashSet<>());
        assertEquals(Outcome.APPLIED, result.outcome());
        assertEquals(1, result.newState().version());
        assertEquals(50, result.newState().attributes().get("dn_mm"));
        assertEquals(ID, result.receiptClientChangeId());
    }

    @Test
    void insertAsBuiltWithinTargetApplies() {
        Delta delta = Delta.insert(ID, Map.of("dn_mm", 50), true, FIXED_OK, TARGET, null, null);
        var result = FieldDeltaApplier.apply(null, delta, new HashSet<>());
        assertEquals(Outcome.APPLIED, result.outcome());
    }

    @Test
    void insertAsBuiltOutsideTargetIsRejectedWithGateRefusals() {
        // A sonda da revisao: INSERT as-built com h 0,25 contra meta 0,10
        // nascia sem portao. No servidor, agora: REJECTED nomeado.
        Delta delta = Delta.insert(ID, Map.of("dn_mm", 50), true, FIXED_COARSE, TARGET, null, null);
        var result = FieldDeltaApplier.apply(null, delta, new HashSet<>());
        assertEquals(Outcome.REJECTED, result.outcome());
        assertEquals(List.of("H_OUT_OF_TARGET"), result.rejectionReasons());
        assertEquals(0, result.newState().version());
        assertEquals(ID, result.receiptClientChangeId());
    }

    @Test
    void insertAsBuiltWithoutTargetIsRejectedNamed() {
        Delta delta = Delta.insert(ID, Map.of("dn_mm", 50), true, FIXED_OK, null, null, null);
        var result = FieldDeltaApplier.apply(null, delta, new HashSet<>());
        assertEquals(Outcome.REJECTED, result.outcome());
        assertEquals(List.of("AS_BUILT_TARGET_MISSING"), result.rejectionReasons());
    }

    @Test
    void updateAsBuiltOutsideTargetIsRejectedByTheSameGate() {
        FeatureState current = new FeatureState(7, Map.of("dn_mm", 100));
        Delta delta = Delta.update(ID, 7L, Map.of("dn_mm", 150), Map.of("dn_mm", 100),
                true, FIXED_COARSE, TARGET, null, null);
        var result = FieldDeltaApplier.apply(current, delta, new HashSet<>());
        assertEquals(Outcome.REJECTED, result.outcome());
        assertEquals(List.of("H_OUT_OF_TARGET"), result.rejectionReasons());
        assertEquals(current, result.newState());
    }

    // --- Correcao M2: validade do pacote conferida na aplicacao (servidor) ---

    @Test
    void deltaFromExpiredPackageIsRejectedNamedAndChangesNothing() {
        FeatureState current = new FeatureState(7, Map.of("operational_status", "OPEN"));
        Delta delta = Delta.update(ID, 7L,
                Map.of("operational_status", "CLOSED"), Map.of("operational_status", "OPEN"),
                false, null, null,
                Instant.parse("2026-11-09T08:00:00Z"), Instant.parse("2026-11-10T00:00:00Z"));
        var result = FieldDeltaApplier.apply(current, delta, new HashSet<>());
        assertEquals(Outcome.REJECTED, result.outcome());
        assertEquals(List.of("PACKAGE_EXPIRED"), result.rejectionReasons());
        assertEquals(current, result.newState());
    }

    @Test
    void deltaFromValidPackageApplies() {
        FeatureState current = new FeatureState(7, Map.of("operational_status", "OPEN"));
        Delta delta = Delta.update(ID, 7L,
                Map.of("operational_status", "CLOSED"), Map.of("operational_status", "OPEN"),
                false, null, null,
                Instant.parse("2026-11-09T08:00:00Z"), Instant.parse("2026-11-08T12:00:00Z"));
        var result = FieldDeltaApplier.apply(current, delta, new HashSet<>());
        assertEquals(Outcome.APPLIED, result.outcome());
    }
}
