package br.com.ziipsan.operations.metering.hydrometry.renewal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Teste §4.1 da estimativa da II-5: score calculado à mão bate exactamente; desempate determinístico. */
class RenewalScoreEngineTest {

    @Test @DisplayName("caso calculado à mão: 0.40×0.8 + 0.30×0.5 + 0.20×1 + 0.10×0 = 0.67 exacto")
    void handCalculatedScore() {
        // Instalado 2018-01-01, referência 2026-01-01 -> 8 anos completos -> 8/10 = 0.8.
        // Volume 10.000 m³, classe 2 (limiar 20.000) -> 0.5. Tecnologia TEC_A -> 1.
        // Qualidade da água NÃO exposta pela base -> componente 0, applied=false.
        var snap = RenewalFixtures.snapshot("01", "H-001",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2018, 1, 1), 10_000, "TEC_A", LocalDate.of(2020, 1, 1), null);
        var scored = RenewalScoreEngine.score(snap, RenewalFixtures.config(), RenewalFixtures.REFERENCE);

        RenewalFixtures.assertBd("0.8", scored.breakdown().ageComponent());
        RenewalFixtures.assertBd("0.5", scored.breakdown().volumeComponent());
        RenewalFixtures.assertBd("1", scored.breakdown().technologyComponent());
        RenewalFixtures.assertBd("0", scored.breakdown().waterQualityComponent());
        assertFalse(scored.breakdown().waterQualityApplied());
        RenewalFixtures.assertBd("0.32", scored.breakdown().ageContribution());
        RenewalFixtures.assertBd("0.15", scored.breakdown().volumeContribution());
        RenewalFixtures.assertBd("0.20", scored.breakdown().technologyContribution());
        RenewalFixtures.assertBd("0", scored.breakdown().waterQualityContribution());
        RenewalFixtures.assertBd("0.67", scored.totalScore());
        assertEquals(RenewalScoreEngine.Criterion.AGE, scored.dominantCriterion());
    }

    @Test @DisplayName("qualidade da água exposta entra no score: mesmo caso com 0.5 -> total 0.72")
    void waterQualityAppliedWhenExposed() {
        var base = RenewalFixtures.snapshot("01", "H-001",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2018, 1, 1), 10_000, "TEC_A", LocalDate.of(2020, 1, 1), null);
        var withWater = new MeterRenewalSnapshot(base.meterId(), base.meterNumber(), base.unitId(),
                base.status(), base.installedOn(), base.manufactureYear(), base.totalizedVolumeM3(),
                base.accuracyClass(), base.technologyKey(), base.lastVerificationOn(),
                new BigDecimal("0.5"), base.replacementCost());
        var scored = RenewalScoreEngine.score(withWater, RenewalFixtures.config(), RenewalFixtures.REFERENCE);
        assertTrue(scored.breakdown().waterQualityApplied());
        RenewalFixtures.assertBd("0.05", scored.breakdown().waterQualityContribution());
        RenewalFixtures.assertBd("0.72", scored.totalScore());
    }

    @Test @DisplayName("componentes têm teto 1: idade e volume acima da referência não passam de 1")
    void componentsCapAtOne() {
        var snap = RenewalFixtures.snapshot("02", "H-002",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2005, 1, 1), 999_999, "TEC_A", null, null);
        var scored = RenewalScoreEngine.score(snap, RenewalFixtures.config(), RenewalFixtures.REFERENCE);
        RenewalFixtures.assertBd("1", scored.breakdown().ageComponent());
        RenewalFixtures.assertBd("1", scored.breakdown().volumeComponent());
        RenewalFixtures.assertBd("0.90", scored.totalScore());
    }

    @Test @DisplayName("sem installedOn, a idade cai no ano de fabrico (2020 -> 6 anos em 2026-01-01 -> 0.6)")
    void manufactureYearFallback() {
        var snap = new MeterRenewalSnapshot(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000003"), "H-003",
                null, MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                null, 2020, 0, 2, "TEC_B", null, null, null);
        var scored = RenewalScoreEngine.score(snap, RenewalFixtures.config(), RenewalFixtures.REFERENCE);
        RenewalFixtures.assertBd("0.6", scored.breakdown().ageComponent());
    }

    @Test @DisplayName("recusas explícitas: pesos sem soma 1, tecnologia desconhecida, sem base de idade")
    void explicitRefusals() {
        assertThrows(IllegalArgumentException.class, () -> new RenewalScoreConfig(
                new RenewalScoreConfig.RenewalWeights(new BigDecimal("0.40"), new BigDecimal("0.30"),
                        new BigDecimal("0.10"), new BigDecimal("0.10")),
                10, Map.of("2", 20_000L), Map.of("TEC_A", BigDecimal.ONE)));

        var unknownTech = RenewalFixtures.snapshot("04", "H-004",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2018, 1, 1), 0, "TEC_DESCONHECIDA", null, null);
        assertThrows(IllegalArgumentException.class,
                () -> RenewalScoreEngine.score(unknownTech, RenewalFixtures.config(), RenewalFixtures.REFERENCE));

        var noAgeBase = new MeterRenewalSnapshot(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000005"), "H-005",
                null, MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                null, null, 0, 2, "TEC_A", null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> RenewalScoreEngine.score(noAgeBase, RenewalFixtures.config(), RenewalFixtures.REFERENCE));
    }

    @Test @DisplayName("desempate determinístico: scores iguais ordenam por número e id, em qualquer ordem de entrada")
    void deterministicTieBreak() {
        var cfg = RenewalFixtures.config();
        var a = RenewalScoreEngine.score(RenewalFixtures.snapshot("0a", "H-200",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2018, 1, 1), 10_000, "TEC_A", null, null), cfg, RenewalFixtures.REFERENCE);
        var b = RenewalScoreEngine.score(RenewalFixtures.snapshot("0b", "H-100",
                MeterRenewalSnapshot.MeterParkStatus.INSTALLED,
                LocalDate.of(2018, 1, 1), 10_000, "TEC_A", null, null), cfg, RenewalFixtures.REFERENCE);
        RenewalFixtures.assertBd("0.67", a.totalScore());
        RenewalFixtures.assertBd("0.67", b.totalScore());

        var forward = List.of(a, b).stream().sorted(RenewalScoreEngine.SCORE_ORDER).toList();
        var reverse = List.of(b, a).stream().sorted(RenewalScoreEngine.SCORE_ORDER).toList();
        assertEquals("H-100", forward.get(0).snapshot().meterNumber());
        assertEquals(forward, reverse);
    }
}
