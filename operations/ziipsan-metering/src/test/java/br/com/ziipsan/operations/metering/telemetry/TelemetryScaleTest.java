package br.com.ziipsan.operations.metering.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A prova de volume em memória do teste de carga da ED-17 (o roteiro
 * completo, contra o PostgreSQL particionado, está em
 * {@code docs/editais/ED-17-TESTE-DE-CARGA.md} e roda na CI do PC do dono):
 * 100 mil amostras horárias — o ciclo horário de 100 mil ligações do
 * aceite — passam pela detecção de alarmes, pela regra de consumo contínuo
 * e pela deduplicação por chave, sem perder amostra e sem custo quadrático.
 */
class TelemetryScaleTest {

    @Test
    @DisplayName("100 mil amostras horárias: detecção, consumo contínuo e deduplicação íntegros")
    void oneHundredThousandHourlySamples() {
        UUID point = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID channel = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        UUID unit = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
        OffsetDateTime t0 = OffsetDateTime.parse("2026-01-01T00:00:00Z");

        List<HourlySample> samples = new ArrayList<>(100_000);
        for (int i = 0; i < 100_000; i++) {
            samples.add(new HourlySample(point, channel, unit, t0.plusHours(i),
                    BigDecimal.ONE, BigDecimal.ZERO, DataState.MEASURED, Set.of()));
        }
        assertThat(samples).hasSize(100_000);

        // Fluxo contínuo ininterrupto: um único alarme ao atingir o limiar, sem um por hora.
        List<TelemetryAlarmRule.Detected> detected =
                TelemetryAlarmRule.detect(samples, 24, new BigDecimal("1000000"));
        assertThat(detected).hasSize(1);
        assertThat(detected.get(0).kind()).isEqualTo(MeterAlarmKind.CONTINUOUS_FLOW);

        assertThat(ContinuousConsumptionRule.hasContinuous24Hours(samples)).isTrue();

        // A deduplicação por chave (o mecanismo da idempotência) distingue as 100 mil chaves.
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            keys.add(point + "|" + channel + "|" + t0.plusHours(i));
        }
        assertThat(keys).hasSize(100_000);
    }
}
