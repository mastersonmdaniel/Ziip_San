package br.com.ziipsan.intelligence.losses.anomaly;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parametros anomaly.* (ED-94): nenhum limiar tem padrao de fabrica. */
class AnomalyParametersTest {

    static final String FULL_SENSITIVITY =
            "deviationK=3.0;minBaselineSamples=4;continuousHours=24;zeroHours=72;pressureDropPercent=25";

    static AnomalyParameters.AnomalyConfig config(String rules, String sensitivity) {
        return new AnomalyParameters.AnomalyConfig(
                AnomalyParameters.rulesEnabled(rules), AnomalyParameters.sensitivity(sensitivity));
    }

    @Test
    void rulesEnabledAll() {
        assertEquals(Set.of(AnomalyRule.values()),
                AnomalyParameters.rulesEnabled("ALL"));
    }

    @Test
    void rulesEnabledSubset() {
        Set<AnomalyRule> enabled = AnomalyParameters.rulesEnabled(
                "CONTINUOUS_CONSUMPTION, ZERO_CONSUMPTION");
        assertEquals(Set.of(AnomalyRule.CONTINUOUS_CONSUMPTION, AnomalyRule.ZERO_CONSUMPTION), enabled);
    }

    @Test
    void rulesEnabledMissingIsExplicitError() {
        MissingParameterException e = assertThrows(MissingParameterException.class,
                () -> AnomalyParameters.rulesEnabled(null));
        assertEquals("anomaly.rules_enabled", e.definitionKey());
        assertThrows(MissingParameterException.class, () -> AnomalyParameters.rulesEnabled("  "));
    }

    @Test
    void rulesEnabledUnknownRuleRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AnomalyParameters.rulesEnabled("MAGIC_BLACK_BOX"));
        assertTrue(e.getMessage().contains("MAGIC_BLACK_BOX"));
    }

    @Test
    void sensitivityParsesAllNamedThresholds() {
        AnomalyParameters.Sensitivity s = AnomalyParameters.sensitivity(FULL_SENSITIVITY);
        assertEquals(3.0, s.deviationK());
        assertEquals(4, s.minBaselineSamples());
        assertEquals(24, s.continuousHours());
        assertEquals(72, s.zeroHours());
        assertEquals(25.0, s.pressureDropPercent());
        assertEquals(0.25, s.pressureDropFraction());
    }

    @Test
    void sensitivityMissingIsExplicitError() {
        MissingParameterException e = assertThrows(MissingParameterException.class,
                () -> AnomalyParameters.sensitivity(null));
        assertEquals("anomaly.sensitivity", e.definitionKey());
    }

    @Test
    void sensitivityMissingFieldNamesTheField() {
        MissingParameterException e = assertThrows(MissingParameterException.class,
                () -> AnomalyParameters.sensitivity(
                        "deviationK=3.0;minBaselineSamples=4;continuousHours=24;zeroHours=72"));
        assertTrue(e.getMessage().contains("pressureDropPercent"),
                "a ausencia deve nomear o campo: " + e.getMessage());
    }

    @Test
    void sensitivityUnknownFieldAndBadValuesRefused() {
        assertThrows(IllegalArgumentException.class, () -> AnomalyParameters.sensitivity(
                FULL_SENSITIVITY + ";magicThreshold=9"));
        assertThrows(IllegalArgumentException.class, () -> AnomalyParameters.sensitivity(
                "deviationK=0;minBaselineSamples=4;continuousHours=24;zeroHours=72;pressureDropPercent=25"));
        assertThrows(IllegalArgumentException.class, () -> AnomalyParameters.sensitivity(
                "deviationK=3.0;minBaselineSamples=1;continuousHours=24;zeroHours=72;pressureDropPercent=25"));
        assertThrows(IllegalArgumentException.class, () -> AnomalyParameters.sensitivity(
                "deviationK=3.0;minBaselineSamples=4;continuousHours=24;zeroHours=72;pressureDropPercent=101"));
    }

    @Test
    void sensitivityAboveCeilingsIsRefusedNeverSilentlyAccepted() {
        // correcao da revisao cruzada (teto validado): fora da faixa =
        // recusa nomeando o campo — nada de limiar infinito que desliga
        // a regra em silencio, nem percentual acima de 100
        IllegalArgumentException k = assertThrows(IllegalArgumentException.class,
                () -> AnomalyParameters.sensitivity(
                        "deviationK=21;minBaselineSamples=4;continuousHours=24;zeroHours=72;pressureDropPercent=25"));
        assertTrue(k.getMessage().contains("deviationK"), k.getMessage());
        assertThrows(IllegalArgumentException.class, () -> AnomalyParameters.sensitivity(
                "deviationK=Infinity;minBaselineSamples=4;continuousHours=24;zeroHours=72;pressureDropPercent=25"));
        IllegalArgumentException base = assertThrows(IllegalArgumentException.class,
                () -> AnomalyParameters.sensitivity(
                        "deviationK=3.0;minBaselineSamples=521;continuousHours=24;zeroHours=72;pressureDropPercent=25"));
        assertTrue(base.getMessage().contains("minBaselineSamples"), base.getMessage());
        assertThrows(IllegalArgumentException.class, () -> AnomalyParameters.sensitivity(
                "deviationK=3.0;minBaselineSamples=4;continuousHours=8761;zeroHours=72;pressureDropPercent=25"));
        assertThrows(IllegalArgumentException.class, () -> AnomalyParameters.sensitivity(
                "deviationK=3.0;minBaselineSamples=4;continuousHours=24;zeroHours=8761;pressureDropPercent=25"));
        // a construcao direta do record recusa igual (a faixa vive no record)
        assertThrows(IllegalArgumentException.class,
                () -> new AnomalyParameters.Sensitivity(1_000_000.0, 4, 24, 72, 25.0));
    }

    @Test
    void sensitivityExactlyAtCeilingsIsAccepted() {
        AnomalyParameters.Sensitivity s = AnomalyParameters.sensitivity(
                "deviationK=20;minBaselineSamples=520;continuousHours=8760;zeroHours=8760;pressureDropPercent=100");
        assertEquals(20.0, s.deviationK());
        assertEquals(520, s.minBaselineSamples());
        assertEquals(8760, s.continuousHours());
        assertEquals(8760, s.zeroHours());
        assertEquals(1.0, s.pressureDropFraction());
    }

    @Test
    void snapshotIsStableAndComplete() {
        AnomalyParameters.AnomalyConfig cfg = config("ALL", FULL_SENSITIVITY);
        String snap = cfg.snapshot();
        assertTrue(snap.contains("deviationK=3.0"));
        assertTrue(snap.contains("continuousHours=24"));
        assertTrue(snap.contains("SAME_HOUR_WEEKDAY_DEVIATION"));
        assertEquals(snap, config("ALL", FULL_SENSITIVITY).snapshot());
    }
}
