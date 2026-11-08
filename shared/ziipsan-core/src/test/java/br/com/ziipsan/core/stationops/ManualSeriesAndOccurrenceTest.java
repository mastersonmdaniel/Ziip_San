package br.com.ziipsan.core.stationops;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Leituras manuais na serie da ED-17 (origem MANUAL, idempotente) e
 * ocorrencia do diario virando UMA OS pela P1.31 (COE-10).
 */
class ManualSeriesAndOccurrenceTest {

    /** Porta falsa da serie: dedupe pela sampleKey, como a tabela da ED-17. */
    static final class FakeSeries implements StationOpsPorts.ManualSeriesPort {
        final Set<String> keys = new HashSet<>();
        final List<StationOpsPorts.ManualSample> samples = new ArrayList<>();
        @Override public boolean record(UUID tenantId, StationOpsPorts.ManualSample sample) {
            if (!keys.add(sample.sampleKey())) return false;
            samples.add(sample);
            return true;
        }
    }

    /** Porta falsa que falha as primeiras {@code failures} chamadas e depois dedupa como a ED-17. */
    static final class FlakySeries implements StationOpsPorts.ManualSeriesPort {
        final Set<String> keys = new HashSet<>();
        final List<StationOpsPorts.ManualSample> samples = new ArrayList<>();
        int failuresLeft;
        FlakySeries(int failures) { this.failuresLeft = failures; }
        @Override public boolean record(UUID tenantId, StationOpsPorts.ManualSample sample) {
            if (failuresLeft > 0) {
                failuresLeft--;
                throw new RuntimeException("porta da serie fora do ar (fixture)");
            }
            if (!keys.add(sample.sampleKey())) return false;
            samples.add(sample);
            return true;
        }
    }

    static final class FakeOrders implements StationOpsPorts.WorkOrderPort {
        int calls = 0;
        UUID lastSite;
        @Override public UUID openOccurrenceOrder(UUID tenantId, UUID siteId, String desc, String by) {
            calls++;
            lastSite = siteId;
            return UUID.randomUUID();
        }
    }

    private ShiftLog openLog(StationOpsService service) {
        return service.openShift(TestSupport.etaRapida(), TestSupport.pattern(),
                LocalDate.of(2026, 3, 10), "DIA", "operador-1", TestSupport.T0);
    }

    @Test
    void openShiftRefusesShiftOutsideTenantPattern() {
        StationOpsService service = new StationOpsService(new FakeSeries(), new FakeOrders());
        assertThatThrownBy(() -> service.openShift(TestSupport.etaRapida(), TestSupport.pattern(),
                LocalDate.of(2026, 3, 10), "MADRUGADA", "operador-1", TestSupport.T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manualReadingIsWrittenToEd17SeriesWithManualOrigin() {
        FakeSeries series = new FakeSeries();
        StationOpsService service = new StationOpsService(series, new FakeOrders());
        ShiftLog log = openLog(service);
        DiaryReading reading = new DiaryReading(DiaryReading.Kind.TURBIDITY, "F1",
                new BigDecimal("0.40"), "uT", TestSupport.T0);
        assertThat(service.addReading(log, reading)).isTrue();
        assertThat(series.samples).hasSize(1);
        StationOpsPorts.ManualSample sample = series.samples.get(0);
        assertThat(sample.origin()).isEqualTo(StationOpsPorts.SeriesOrigin.MANUAL);
        assertThat(sample.stationCode()).isEqualTo("ETA-01");
        assertThat(sample.tagCode()).isEqualTo("ETA-01:TURBIDITY:F1");
        assertThat(sample.numericValue()).isEqualByComparingTo("0.40");
        // Reenvio da MESMA leitura (mesma chave): nao duplica a serie.
        ShiftLog log2 = openLog(service);
        assertThat(service.addReading(log2, reading)).isFalse();
        assertThat(series.samples).hasSize(1);
    }

    @Test
    void resendOnSameDiaryDoesNotDuplicateDiaryOrSeries() {
        // Correcao ED-87/D1: o teste do autor reenviava sobre um SEGUNDO
        // diario (log2) e mascarava a duplicacao do diario. Aqui o
        // reenvio e sobre o MESMO diario: nenhuma das duas pontas duplica.
        FakeSeries series = new FakeSeries();
        StationOpsService service = new StationOpsService(series, new FakeOrders());
        ShiftLog log = openLog(service);
        DiaryReading reading = new DiaryReading(DiaryReading.Kind.TURBIDITY, "F1",
                new BigDecimal("0.40"), "uT", TestSupport.T0);
        assertThat(service.addReading(log, reading)).isTrue();
        // Reenvio da mesma leitura (mesmo objeto e objeto igual de mesma chave):
        assertThat(service.addReading(log, reading)).isFalse();
        assertThat(service.addReading(log, new DiaryReading(DiaryReading.Kind.TURBIDITY, "F1",
                new BigDecimal("0.40"), "uT", TestSupport.T0))).isFalse();
        assertThat(log.readings()).hasSize(1);
        assertThat(series.samples).hasSize(1);
        // E a fonte do JOR-21 ve UMA entrada, nao duas:
        assertThat(log.processReadings()).hasSize(1);
        assertThat(ProcessControlFeed.toEntries(log, UUID.randomUUID(),
                br.com.ziipsan.core.quality.NormativeCatalog.empty("CAT-TESTE"), "validador-1"))
                .hasSize(1);
    }

    @Test
    void portFailureThenRetryDoesNotDuplicateEitherSide() {
        // Correcao ED-87/D1: falha da porta NAO deixa lancamento orfao
        // no diario; o retry relanca diario e serie exatamente 1x cada.
        FlakySeries series = new FlakySeries(1);
        StationOpsService service = new StationOpsService(series, new FakeOrders());
        ShiftLog log = openLog(service);
        DiaryReading reading = new DiaryReading(DiaryReading.Kind.LEVEL, null,
                new BigDecimal("3.20"), "m", TestSupport.T0);
        assertThatThrownBy(() -> service.addReading(log, reading))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fora do ar");
        assertThat(log.readings()).isEmpty();
        assertThat(series.samples).isEmpty();
        assertThat(service.addReading(log, reading)).isTrue();
        assertThat(log.readings()).hasSize(1);
        assertThat(series.samples).hasSize(1);
        // Reenvio posterior ao retry continua idempotente nas duas pontas:
        assertThat(service.addReading(log, reading)).isFalse();
        assertThat(log.readings()).hasSize(1);
        assertThat(series.samples).hasSize(1);
    }

    @Test
    void occurrenceBecomesExactlyOneWorkOrderForTheStation() {
        FakeOrders orders = new FakeOrders();
        StationOpsService service = new StationOpsService(new FakeSeries(), orders);
        ShiftLog log = openLog(service);
        ShiftLog.DiaryOccurrence occ = service.recordOccurrenceAndOpenOrder(
                log, "vazamento na descarga do filtro F1", TestSupport.T0.plusSeconds(3600), "operador-1");
        assertThat(occ.workOrderId()).isNotNull();
        assertThat(orders.calls).isEqualTo(1);
        assertThat(orders.lastSite).isEqualTo(log.unit().id());
        assertThatThrownBy(() -> service.openOrderFor(log, occ.id()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(orders.calls).isEqualTo(1);
    }

    @Test
    void operationalSiteVerifierRegistersForP131() {
        br.com.ziipsan.core.serviceorder.TargetVerifierRegistry registry =
                new br.com.ziipsan.core.serviceorder.TargetVerifierRegistry();
        assertThat(registry.hasVerifier(br.com.ziipsan.core.serviceorder.TargetType.OPERATIONAL_SITE))
                .isFalse();
        StationTargetRegistration.register(registry, "tenant_abc");
        assertThat(registry.hasVerifier(br.com.ziipsan.core.serviceorder.TargetType.OPERATIONAL_SITE))
                .isTrue();
        assertThat(registry.declarableClasses())
                .contains(br.com.ziipsan.core.serviceorder.TargetType.OPERATIONAL_SITE);
    }
}
