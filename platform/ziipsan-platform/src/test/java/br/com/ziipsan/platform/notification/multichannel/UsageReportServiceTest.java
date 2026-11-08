package br.com.ziipsan.platform.notification.multichannel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Agregação do uso medido, calculada à mão sobre fatos declarados. */
class UsageReportServiceTest {

    private static final UUID TENANT = MultichannelTestSupport.TENANT;
    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-03-31T23:59:59Z");
    private static final Instant AT = Instant.parse("2026-03-10T13:00:00Z");

    @Test
    @DisplayName("agrega quantidades por módulo e métrica e soma os custos")
    void aggregatesByModuleAndMetric() {
        List<UsageRecord> records = List.of(
                new UsageRecord(TENANT, "commercial.billing", UsageMetric.MESSAGES, 3, null, null, AT),
                new UsageRecord(TENANT, "commercial.billing", UsageMetric.SMS, 2, new BigDecimal("0.60"), null, AT),
                new UsageRecord(TENANT, "commercial.billing", UsageMetric.SMS, 1, new BigDecimal("0.30"), null, AT),
                new UsageRecord(TENANT, "platform.ai", UsageMetric.AI_TOKENS, 1500, null, null, AT));
        UsageReportService.UsageReport report = UsageReportService.aggregate(TENANT, FROM, TO, records);
        assertThat(report.modules()).hasSize(2);
        UsageReportService.ModuleUsage billing = report.modules().stream()
                .filter(m -> m.moduleCode().equals("commercial.billing")).findFirst().orElseThrow();
        assertThat(billing.quantities()).containsEntry(UsageMetric.MESSAGES, 3L)
                .containsEntry(UsageMetric.SMS, 3L);
        assertThat(billing.totalCost()).isEqualByComparingTo("0.90");
        UsageReportService.ModuleUsage ai = report.modules().stream()
                .filter(m -> m.moduleCode().equals("platform.ai")).findFirst().orElseThrow();
        assertThat(ai.quantities()).containsEntry(UsageMetric.AI_TOKENS, 1500L);
        assertThat(ai.totalCost()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("fato de outro tenant na agregação é erro, não soma silenciosa")
    void rejectsOtherTenantRecord() {
        List<UsageRecord> records = List.of(
                new UsageRecord(UUID.randomUUID(), "commercial.billing", UsageMetric.MESSAGES, 1, null, null, AT));
        assertThatThrownBy(() -> UsageReportService.aggregate(TENANT, FROM, TO, records))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("período sem fatos: relatório sem módulos")
    void emptyPeriodHasNoModules() {
        UsageReportService.UsageReport report = UsageReportService.aggregate(TENANT, FROM, TO, List.of());
        assertThat(report.modules()).isEmpty();
    }

    @Test
    @DisplayName("o serviço lê da porta e devolve o mesmo agregado")
    void serviceReadsFromStore() {
        MultichannelTestSupport.InMemoryUsageStore store = new MultichannelTestSupport.InMemoryUsageStore();
        store.append(new UsageRecord(TENANT, "operations.telemetry", UsageMetric.GIS, 7, null, null, AT));
        UsageReportService.UsageReport report = new UsageReportService(store).report(TENANT, FROM, TO);
        assertThat(report.modules()).singleElement()
                .satisfies(m -> assertThat(m.quantities()).containsEntry(UsageMetric.GIS, 7L));
    }
}
