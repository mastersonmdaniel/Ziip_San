package br.com.ziipsan.core.certification.lims17025;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Multirregras de Westgard sobre media 100 / desvio 2 (fixture sintetica). */
class WestgardControlTest {

    private static final Instant T0 = TestSupport.T0;

    private static List<WestgardControl.QcObservation> run(String... values) {
        List<WestgardControl.QcObservation> out = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            out.add(new WestgardControl.QcObservation(new BigDecimal(values[i]), T0.plusSeconds(60L * i)));
        }
        return out;
    }

    private static WestgardControl.Evaluation eval(String... values) {
        return WestgardControl.evaluate(TestSupport.qcMaterial(), run(values));
    }

    @Test void inControlRun() {
        WestgardControl.Evaluation e = eval("100", "101", "99", "100.5");
        assertEquals(WestgardControl.Status.IN_CONTROL, e.status());
        assertTrue(e.violatedRules().isEmpty());
        assertTrue(e.releasesResults());
    }

    @Test void oneBeyond2sIsWarningOnly() {
        // 105 = +2,5 DP: alem de 2 DP, dentro de 3 DP
        WestgardControl.Evaluation e = eval("100", "105");
        assertEquals(WestgardControl.Status.WARNING, e.status());
        assertEquals(List.of(WestgardControl.Rule.R_1_2S), e.violatedRules());
        assertTrue(e.releasesResults());
    }

    @Test void oneBeyond3sRejects() {
        // 107 = +3,5 DP
        WestgardControl.Evaluation e = eval("100", "107");
        assertEquals(WestgardControl.Status.OUT_OF_CONTROL, e.status());
        assertTrue(e.violatedRules().contains(WestgardControl.Rule.R_1_3S));
        assertFalse(e.releasesResults());
    }

    @Test void twoConsecutiveBeyond2sSameSideRejects() {
        WestgardControl.Evaluation e = eval("105", "105.5");
        assertEquals(WestgardControl.Status.OUT_OF_CONTROL, e.status());
        assertTrue(e.violatedRules().contains(WestgardControl.Rule.R_2_2S));
    }

    @Test void range4sRejects() {
        // +2,5 DP seguido de -2,5 DP: amplitude de 5 DP
        WestgardControl.Evaluation e = eval("105", "95");
        assertEquals(WestgardControl.Status.OUT_OF_CONTROL, e.status());
        assertTrue(e.violatedRules().contains(WestgardControl.Rule.R_R_4S));
    }

    @Test void fourConsecutiveBeyond1sSameSideRejects() {
        // todos acima de +1 DP (102) do mesmo lado
        WestgardControl.Evaluation e = eval("103", "102.5", "103.5", "102.2");
        assertEquals(WestgardControl.Status.OUT_OF_CONTROL, e.status());
        assertTrue(e.violatedRules().contains(WestgardControl.Rule.R_4_1S));
    }

    @Test void tenConsecutiveSameSideRejects() {
        WestgardControl.Evaluation e = eval("100.5", "101", "100.2", "100.8", "101.2",
                "100.4", "100.9", "101.1", "100.3", "100.6");
        assertEquals(WestgardControl.Status.OUT_OF_CONTROL, e.status());
        assertTrue(e.violatedRules().contains(WestgardControl.Rule.R_10_X));
    }

    @Test void emptyRunAndBadMaterialAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> WestgardControl.evaluate(TestSupport.qcMaterial(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new WestgardControl.QcMaterial(
                "QC", TestSupport.PARAM, new BigDecimal("100"), BigDecimal.ZERO, "u"));
    }
}
