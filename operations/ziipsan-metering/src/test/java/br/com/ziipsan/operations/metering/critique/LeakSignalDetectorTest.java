package br.com.ziipsan.operations.metering.critique;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §4.4 da estimativa: serie com consumo nocturno continuo gera sinal; serie normal nao gera. */
class LeakSignalDetectorTest {

    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private final LeakSignalDetector detector = new LeakSignalDetector();
    private final LeakDetectionConfig config = LeakDetectionConfig.proofDefaults(); // 02h-03h, > 0.1, 3 noites

    /** Serie de {@code days} dias a comecar em 2026-10-01, com as horas nocturnas ao valor dado por dia. */
    private List<HourlyConsumption> series(List<String> nightValuesByDay) {
        List<HourlyConsumption> points = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 10, 1);
        for (int d = 0; d < nightValuesByDay.size(); d++) {
            LocalDate day = start.plusDays(d);
            BigDecimal night = new BigDecimal(nightValuesByDay.get(d));
            points.add(new HourlyConsumption(day, 2, night));
            points.add(new HourlyConsumption(day, 3, night));
            points.add(new HourlyConsumption(day, 12, new BigDecimal("5.0")));
        }
        return points;
    }

    @Test
    void consumoNocturnoContinuoTresNoitesGeraSinal() {
        Optional<LeakSignal> signal = detector.detect(UNIT, series(List.of("0.5", "0.4", "0.6")), config);
        assertTrue(signal.isPresent());
        assertEquals(LocalDate.of(2026, 10, 1), signal.get().firstNight());
        assertEquals(LocalDate.of(2026, 10, 3), signal.get().lastNight());
        assertEquals(3, signal.get().consecutiveNights());
        assertEquals(0, signal.get().minimumNightMean().compareTo(new BigDecimal("0.4")));
    }

    @Test
    void serieNormalSemFluxoNocturnoNaoGeraSinal() {
        assertTrue(detector.detect(UNIT, series(List.of("0.0", "0.0", "0.0", "0.0")), config).isEmpty());
    }

    @Test
    void duasNoitesNaoChegam() {
        assertTrue(detector.detect(UNIT, series(List.of("0.5", "0.5")), config).isEmpty());
    }

    @Test
    void noiteSemFluxoQuebraASequencia() {
        assertTrue(detector.detect(UNIT, series(List.of("0.5", "0.5", "0.0", "0.5", "0.5")), config).isEmpty());
        Optional<LeakSignal> later = detector.detect(UNIT,
                series(List.of("0.5", "0.0", "0.5", "0.5", "0.5")), config);
        assertTrue(later.isPresent());
        assertEquals(LocalDate.of(2026, 10, 3), later.get().firstNight());
    }

    @Test
    void mediaExactamenteNoLimiarNaoContaComoFluxo() {
        // limiar de prova 0.1, estrito: media 0.1 nao e fluxo continuo
        assertTrue(detector.detect(UNIT, series(List.of("0.1", "0.1", "0.1")), config).isEmpty());
    }

    @Test
    void diaIncompletoNaoContaNemPresume() {
        List<HourlyConsumption> points = new ArrayList<>(series(List.of("0.5", "0.5", "0.5")));
        points.removeIf(p -> p.date().equals(LocalDate.of(2026, 10, 2)) && p.hourOfDay() == 3);
        assertTrue(detector.detect(UNIT, points, config).isEmpty(),
                "sem o ponto das 03h do dia 2, a sequencia de 3 noites nao se fecha");
    }

    @Test
    void determinismo() {
        List<HourlyConsumption> s = series(List.of("0.5", "0.4", "0.6"));
        assertEquals(detector.detect(UNIT, s, config), detector.detect(UNIT, s, config));
    }
}
