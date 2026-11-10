package br.com.ziipsan.intelligence.losses.dma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.ziipsan.intelligence.losses.dma.DmaModel.FlowSample;
import br.com.ziipsan.intelligence.losses.dma.MinimumNightFlow.Result;
import br.com.ziipsan.intelligence.losses.dma.MinimumNightFlow.Status;
import br.com.ziipsan.intelligence.losses.dmc.LossParameters;

/** §4.2 da estimativa: minimo nocturno conhecido exacto; janela incompleta = desfecho tipificado. */
class MinimumNightFlowTest {

    private static final LossParameters.NightWindow WINDOW = LossParameters.nightWindow("02:00-04:00");

    /** Uma noite completa a 30 min (4 amostras na janela), minimo 4200.00 as 03:00 + amostras de dia. */
    private static List<FlowSample> fullNight() {
        List<FlowSample> s = new ArrayList<>();
        s.add(new FlowSample(LocalDateTime.of(2026, 10, 15, 1, 0), DmaFixtures.bd("9000")));
        s.add(new FlowSample(LocalDateTime.of(2026, 10, 15, 2, 0), DmaFixtures.bd("5000.50")));
        s.add(new FlowSample(LocalDateTime.of(2026, 10, 15, 2, 30), DmaFixtures.bd("4600")));
        s.add(new FlowSample(LocalDateTime.of(2026, 10, 15, 3, 0), DmaFixtures.bd("4200.00")));
        s.add(new FlowSample(LocalDateTime.of(2026, 10, 15, 3, 30), DmaFixtures.bd("4800")));
        s.add(new FlowSample(LocalDateTime.of(2026, 10, 15, 12, 0), DmaFixtures.bd("15000")));
        return s;
    }

    @Test
    void knownNightMinimumIsExactAndNetSubtractsLegit() {
        Result r = MinimumNightFlow.analyze(fullNight(), WINDOW, 30,
                Optional.of(DmaFixtures.bd("1.70")), 1000);
        assertEquals(Status.COMPLETE, r.status());
        assertEquals(0, r.minimumNightFlowLPerH().orElseThrow().compareTo(DmaFixtures.bd("4200.00")));
        // liquida: 4200.00 - 1.70 x 1000 = 2500.00 exactos
        assertEquals(0, r.netNightFlowLPerH().orElseThrow().compareTo(DmaFixtures.bd("2500.00")));
        assertEquals(4, r.samplesInWindow());
        assertEquals(1, r.nightsEvaluated());
    }

    @Test
    void withoutLegitNetIsAbsentDeclared() {
        Result r = MinimumNightFlow.analyze(fullNight(), WINDOW, 30, Optional.empty(), 1000);
        assertEquals(Status.COMPLETE, r.status());
        assertTrue(r.netNightFlowLPerH().isEmpty());
        assertEquals(List.of(MinimumNightFlow.ABSENT_NET_NIGHT_FLOW), r.absentComponents());
    }

    @Test
    void incompleteWindowIsTypedAndReturnsNoMinimum() {
        List<FlowSample> partial = fullNight();
        partial.remove(2); // falta 02:30 -> 3 de 4 amostras
        Result r = MinimumNightFlow.analyze(partial, WINDOW, 30, Optional.empty(), 1000);
        assertEquals(Status.INCOMPLETE_COVERAGE, r.status());
        assertTrue(r.minimumNightFlowLPerH().isEmpty());
        assertEquals(List.of(LocalDate.of(2026, 10, 15)), r.incompleteNights());
    }

    @Test
    void noSampleInWindowIsTyped() {
        List<FlowSample> day = List.of(
                new FlowSample(LocalDateTime.of(2026, 10, 15, 12, 0), DmaFixtures.bd("15000")));
        Result r = MinimumNightFlow.analyze(day, WINDOW, 30, Optional.empty(), 1000);
        assertEquals(Status.NO_SAMPLE_IN_WINDOW, r.status());
        assertTrue(r.minimumNightFlowLPerH().isEmpty());
    }

    @Test
    void negativeNetIsExposedNotTruncated() {
        Result r = MinimumNightFlow.analyze(fullNight(), WINDOW, 30,
                Optional.of(DmaFixtures.bd("5.00")), 1000);
        assertEquals(0, r.netNightFlowLPerH().orElseThrow().compareTo(DmaFixtures.bd("-800.00")));
        assertTrue(r.legitExceedsMinimum());
    }
}
