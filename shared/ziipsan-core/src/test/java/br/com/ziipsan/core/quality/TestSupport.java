package br.com.ziipsan.core.quality;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Massa SINTETICA dos testes da ED-50: parametros genericos PARAM_A/
 * PARAM_B com limites arbitrarios de fixture (10, 2 a 8, texto
 * "AUSENTE") — NAO sao valores de nenhuma norma. Os limites reais do
 * catalogo entram por carga revisada pelo responsavel tecnico.
 */
final class TestSupport {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID POINT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID CONSUMER_POINT = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    static final Instant T0 = Instant.parse("2026-01-10T10:00:00Z");

    private TestSupport() {}

    static ParameterStandard maxStandard(String key, String max, boolean reviewed, boolean risk) {
        return new ParameterStandard(key, "Parametro " + key, "u", ParameterStandard.Comparator.MAX,
                null, new BigDecimal(max), null, "NORMA-SINTETICA [FIXTURE]", risk, reviewed,
                reviewed ? "RT-FIXTURE" : null, JAN, null);
    }

    static ParameterStandard rangeStandard(String key, boolean reviewed) {
        return new ParameterStandard(key, "Parametro " + key, "u", ParameterStandard.Comparator.RANGE,
                new BigDecimal("2"), new BigDecimal("8"), null, "NORMA-SINTETICA [FIXTURE]", false,
                reviewed, reviewed ? "RT-FIXTURE" : null, JAN, null);
    }

    static ParameterStandard qualitativeStandard(String key, boolean reviewed) {
        return new ParameterStandard(key, "Parametro " + key, null,
                ParameterStandard.Comparator.QUALITATIVE_EQUALS, null, null, "AUSENTE",
                "NORMA-SINTETICA [FIXTURE]", false, reviewed, reviewed ? "RT-FIXTURE" : null, JAN, null);
    }

    static NormativeCatalog catalog(ParameterStandard... standards) {
        return new NormativeCatalog("FIXTURE-1", java.util.List.of(standards));
    }

    /** Amostra de rotina levada ate RECEIVED_BY_LAB. */
    static Sample receivedSample(String label) {
        Sample s = Sample.planned(UUID.randomUUID(), TENANT, POINT, Sample.SampleType.ROUTINE,
                label, UUID.randomUUID(), null);
        s.collect("COLETOR-1", T0);
        s.sealAndDispatch("COLETOR-1", T0.plusSeconds(600));
        s.receiveByLab("LAB-1", T0.plusSeconds(3600));
        return s;
    }

    static AnalysisResult numericResult(Sample s, String key, String value, boolean validated) {
        AnalysisResult r = AnalysisResult.firstVersion(UUID.randomUUID(), s, key,
                new BigDecimal(value), null, "u", "METODO-FIXTURE", "ANALISTA-1", T0.plusSeconds(7200));
        return validated ? r.validate("SUPERVISOR-1") : r;
    }

    static AnalysisResult qualitativeResult(Sample s, String key, String value, boolean validated) {
        AnalysisResult r = AnalysisResult.firstVersion(UUID.randomUUID(), s, key,
                null, value, null, "METODO-FIXTURE", "ANALISTA-1", T0.plusSeconds(7200));
        return validated ? r.validate("SUPERVISOR-1") : r;
    }
}
