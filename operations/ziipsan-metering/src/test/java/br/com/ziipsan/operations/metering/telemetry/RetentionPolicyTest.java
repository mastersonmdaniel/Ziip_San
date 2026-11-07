package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A retenção por resolução da ED-17, calculada só dos parâmetros do tenant. */
class RetentionPolicyTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    private static TelemetryParameters parameters(int rawDays, int hourlyMonths, int amiDays, String rollup) {
        return new TelemetryParameters() {
            @Override public int alarmLeakHours(UUID tenantId) { return 0; }
            @Override public BigDecimal alarmReverseFlowLitres(UUID tenantId) { return null; }
            @Override public String amiAlertThresholdsJson(UUID tenantId) { return null; }
            @Override public boolean telemetryPriorityOverManual(UUID tenantId) { return false; }
            @Override public boolean hourlyProfileVisibleToCustomer(UUID tenantId) { return false; }
            @Override public int amiRetentionDays(UUID tenantId) { return amiDays; }
            @Override public int retentionRawDays(UUID tenantId) { return rawDays; }
            @Override public int retentionHourlyMonths(UUID tenantId) { return hourlyMonths; }
            @Override public String rollupPolicy(UUID tenantId) { return rollup; }
        };
    }

    @Test
    @DisplayName("os cortes saem dos três parâmetros e a política de agregação é carregada no plano")
    void planComesFromParameters() {
        RetentionPolicy.RetentionPlan plan = RetentionPolicy.plan(
                parameters(30, 13, 90, "DAILY"), TENANT, NOW);

        assertThat(plan.rawBefore()).isEqualTo(Instant.parse("2026-10-11T12:00:00Z"));
        assertThat(plan.personalDataBefore()).isEqualTo(Instant.parse("2026-08-12T12:00:00Z"));
        assertThat(plan.hourlyMonthsBefore()).isEqualTo(YearMonth.of(2025, 10));
        assertThat(plan.rollupPolicy()).isEqualTo("DAILY");
    }

    @Test
    @DisplayName("prazo não positivo ou política ausente é erro explícito")
    void invalidRetentionFails() {
        assertThatThrownBy(() -> RetentionPolicy.plan(parameters(0, 13, 90, "DAILY"), TENANT, NOW))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> RetentionPolicy.plan(parameters(30, 13, 90, " "), TENANT, NOW))
                .isInstanceOf(MeteringInputException.class);
    }
}
