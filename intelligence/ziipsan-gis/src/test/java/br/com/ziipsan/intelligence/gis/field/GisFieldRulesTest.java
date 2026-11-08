package br.com.ziipsan.intelligence.gis.field;

import br.com.ziipsan.intelligence.gis.field.GisFieldRules.AccuracyTarget;
import br.com.ziipsan.intelligence.gis.field.GisFieldRules.FieldPhoto;
import br.com.ziipsan.intelligence.gis.field.GisFieldRules.GnssFix;
import br.com.ziipsan.intelligence.gis.field.GisFieldRules.PhotoScan;
import br.com.ziipsan.intelligence.gis.field.GisFieldRules.Solution;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regras do servidor da GIS-15 — massa SINTETICA de ensaio declarada. */
class GisFieldRulesTest {

    private static final GnssFix FIXED = new GnssFix(Solution.RTK_FIXED,
            -26.900001, -48.700001, 812.40, 0.02, 0.03, 1.4, 18, 2.0);
    private static final AccuracyTarget TARGET = new AccuracyTarget(0.10, 0.05);

    @Test
    void captureMethodMirrorsV13List() {
        assertEquals("RTK_FIXED", GisFieldRules.captureMethodFor(Solution.RTK_FIXED));
        assertEquals("RTK_FLOAT", GisFieldRules.captureMethodFor(Solution.RTK_FLOAT));
        assertEquals("GNSS_NAVIGATION", GisFieldRules.captureMethodFor(Solution.AUTONOMOUS));
    }

    @Test
    void fixWithoutPdopIsIncomplete() {
        GnssFix noPdop = new GnssFix(Solution.RTK_FIXED, -26.9, -48.7, 812.4, 0.02, 0.03, null, 18, 2.0);
        assertEquals(List.of("PDOP_MISSING"), GisFieldRules.fixProblems(noPdop));
    }

    @Test
    void asBuiltAcceptsRtkFixedWithinTenantTarget() {
        assertEquals(List.of(), GisFieldRules.asBuiltRefusals(FIXED, TARGET));
    }

    @Test
    void asBuiltRefusedWithoutRtkFixed() {
        GnssFix floating = new GnssFix(Solution.RTK_FLOAT, -26.9, -48.7, 812.4, 0.02, 0.03, 1.4, 18, 2.0);
        assertEquals(List.of("NOT_RTK_FIXED"), GisFieldRules.asBuiltRefusals(floating, TARGET));
    }

    @Test
    void asBuiltRefusedOutsideTenantTarget() {
        GnssFix coarse = new GnssFix(Solution.RTK_FIXED, -26.9, -48.7, 812.4, 0.25, 0.03, 1.4, 18, 2.0);
        assertEquals(List.of("H_OUT_OF_TARGET"), GisFieldRules.asBuiltRefusals(coarse, TARGET));
        GnssFix coarseV = new GnssFix(Solution.RTK_FIXED, -26.9, -48.7, 812.4, 0.02, 0.09, 1.4, 18, 2.0);
        assertEquals(List.of("V_OUT_OF_TARGET"), GisFieldRules.asBuiltRefusals(coarseV, TARGET));
    }

    @Test
    void photoClearedWithHashAzimuthAndBlurIsUsable() {
        FieldPhoto ok = new FieldPhoto("a".repeat(64), -26.9, -48.7, 135.5, PhotoScan.CLEARED, true);
        assertEquals(List.of(), GisFieldRules.photoProblems(ok, Boolean.TRUE));
    }

    @Test
    void photoInQuarantineIsNotUsable() {
        FieldPhoto q = new FieldPhoto("a".repeat(64), -26.9, -48.7, 10, PhotoScan.QUARANTINE, true);
        assertEquals(List.of("SCAN_NOT_CLEARED"), GisFieldRules.photoProblems(q, Boolean.TRUE));
    }

    @Test
    void absentBlurParamIsRefusedNotDefaulted() {
        FieldPhoto ok = new FieldPhoto("a".repeat(64), -26.9, -48.7, 135.5, PhotoScan.CLEARED, false);
        assertEquals(List.of("BLUR_PARAM_MISSING"), GisFieldRules.photoProblems(ok, null));
        assertEquals(List.of("BLUR_REQUIRED_NOT_APPLIED"), GisFieldRules.photoProblems(ok, Boolean.TRUE));
        assertEquals(List.of(), GisFieldRules.photoProblems(ok, Boolean.FALSE));
    }

    // --- Correcao m3: meta ausente e recusa NOMEADA, nao NPE crua ---

    @Test
    void missingTargetIsNamedRefusalNotNullPointer() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> GisFieldRules.asBuiltRefusals(FIXED, null));
        assertTrue(ex.getMessage().contains("AS_BUILT_TARGET_MISSING"));
    }

    // --- Correcao m1: facesBlurred DERIVA da FaceBlurPort (nunca autodeclarado) ---

    @Test
    void capturePhotoDerivesBlurFlagFromThePort() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        GisFieldRules.FaceBlurPort port = (ref) -> { calls.incrementAndGet(); return true; };
        FieldPhoto photo = GisFieldRules.capturePhoto("a".repeat(64), -26.9, -48.7, 135.5,
                PhotoScan.CLEARED, Boolean.TRUE, port, "foto-1");
        assertEquals(1, calls.get());
        assertTrue(photo.facesBlurred());
        assertEquals(List.of(), GisFieldRules.photoProblems(photo, Boolean.TRUE));
    }

    @Test
    void capturePhotoWithPortReturningFalseStaysUnblurredAndRefused() {
        FieldPhoto photo = GisFieldRules.capturePhoto("a".repeat(64), -26.9, -48.7, 135.5,
                PhotoScan.CLEARED, Boolean.TRUE, (ref) -> false, "foto-1");
        assertFalse(photo.facesBlurred());
        assertEquals(List.of("BLUR_REQUIRED_NOT_APPLIED"), GisFieldRules.photoProblems(photo, Boolean.TRUE));
    }

    @Test
    void capturePhotoWithoutPortWhenBlurRequiredIsNamedRefusal() {
        var ex = assertThrows(IllegalStateException.class, () -> GisFieldRules.capturePhoto(
                "a".repeat(64), -26.9, -48.7, 135.5, PhotoScan.CLEARED, Boolean.TRUE, null, "foto-1"));
        assertTrue(ex.getMessage().contains("BLUR_PORT_MISSING"));
    }

    @Test
    void capturePhotoWithAbsentParamIsNamedRefusalAndPortNotInvoked() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        GisFieldRules.FaceBlurPort port = (ref) -> { calls.incrementAndGet(); return true; };
        var ex = assertThrows(IllegalStateException.class, () -> GisFieldRules.capturePhoto(
                "a".repeat(64), -26.9, -48.7, 135.5, PhotoScan.CLEARED, null, port, "foto-1"));
        assertTrue(ex.getMessage().contains("BLUR_PARAM_MISSING"));
        assertEquals(0, calls.get());
    }

    @Test
    void capturePhotoWithoutBlurRequirementDoesNotInvokePort() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        GisFieldRules.FaceBlurPort port = (ref) -> { calls.incrementAndGet(); return true; };
        FieldPhoto photo = GisFieldRules.capturePhoto("a".repeat(64), -26.9, -48.7, 135.5,
                PhotoScan.CLEARED, Boolean.FALSE, port, "foto-1");
        assertEquals(0, calls.get());
        assertFalse(photo.facesBlurred());
        assertEquals(List.of(), GisFieldRules.photoProblems(photo, Boolean.FALSE));
    }

    @Test
    void personalLayerOnlyWithViewPersonal() {
        assertFalse(GisFieldRules.personalLayerAllowed(true, false));
        assertTrue(GisFieldRules.personalLayerAllowed(true, true));
        assertTrue(GisFieldRules.personalLayerAllowed(false, false));
    }
}
