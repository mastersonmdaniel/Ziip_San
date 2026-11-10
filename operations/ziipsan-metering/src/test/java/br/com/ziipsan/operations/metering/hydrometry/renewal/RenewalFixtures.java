package br.com.ziipsan.operations.metering.hydrometry.renewal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Massa sintética declarada dos testes da II-5: pesos, referências e
 * limiares são valores DE TESTE escolhidos para o cálculo à mão —
 * nenhum deles é valor de fábrica do núcleo (P-II5-PARAM).
 */
final class RenewalFixtures {
    private RenewalFixtures() {}

    static final LocalDate REFERENCE = LocalDate.of(2026, 1, 1);
    static final int INTERVAL_YEARS = 7;
    static final int HORIZON_DAYS = 90;

    /** Pesos 0.40/0.30/0.20/0.10 (soma 1); idade ref. 10 anos; volumes {"1":30000, "2":20000}; tecnologias TEC_A=1, TEC_B=0.5. */
    static RenewalScoreConfig config() {
        return new RenewalScoreConfig(
                new RenewalScoreConfig.RenewalWeights(
                        new BigDecimal("0.40"), new BigDecimal("0.30"),
                        new BigDecimal("0.20"), new BigDecimal("0.10")),
                10,
                Map.of("1", 30_000L, "2", 20_000L),
                Map.of("TEC_A", new BigDecimal("1"), "TEC_B", new BigDecimal("0.5")));
    }

    static MeterRenewalSnapshot snapshot(String idSuffix, String number,
                                         MeterRenewalSnapshot.MeterParkStatus status,
                                         LocalDate installedOn, long volumeM3, String technology,
                                         LocalDate lastVerificationOn, BigDecimal cost) {
        return new MeterRenewalSnapshot(
                UUID.fromString("00000000-0000-0000-0000-0000000000" + idSuffix),
                number,
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                status, installedOn, null, volumeM3, 2, technology,
                lastVerificationOn, null, cost);
    }

    static AnnualRenewalPlan.RenewalCandidate candidate(MeterRenewalSnapshot snap, RenewalScoreConfig cfg) {
        return new AnnualRenewalPlan.RenewalCandidate(
                RenewalScoreEngine.score(snap, cfg, REFERENCE),
                MetrologicalClock.assess(snap, REFERENCE, INTERVAL_YEARS, HORIZON_DAYS));
    }

    static void assertBd(String expected, BigDecimal actual) {
        org.junit.jupiter.api.Assertions.assertNotNull(actual, "valor BigDecimal nulo");
        org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "esperado " + expected + " mas foi " + actual.toPlainString());
    }
}
