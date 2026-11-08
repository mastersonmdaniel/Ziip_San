package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.core.fleet.FuelAndConsumption;
import br.com.ziipsan.reporting.indicator.ConfidenceGrade;
import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import br.com.ziipsan.reporting.indicator.IndicatorVariable;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fonte automatica no lugar da manual da ED-77 (GES-21/OPE-08) e
 * combustivel da ED-59 com o ramo manual preservado.
 */
class AutomaticSourceUpgradeTest {

    private static final UUID TENANT = UUID.randomUUID();

    private static EnergyBill bill() {
        return new EnergyBill(TENANT, UUID.randomUUID(), YearMonth.of(2026, 10),
                new BigDecimal("12000"), null, null, null, null,
                EnergyDataSource.IMPORTED_BILL, "L1");
    }

    @Test
    void telemetriaTemPrecedenciaEGrauAlto() {
        EnergyPorts.TelemetryPort telemetry = (t, inst, month) -> Optional.of(new BigDecimal("11500"));
        var resolved = AutomaticSourceUpgrade.resolveKwh(telemetry, TENANT, bill(), new BigDecimal("9000"));
        assertThat(resolved.kwh()).isEqualByComparingTo("11500");
        assertThat(resolved.source()).isEqualTo(EnergyDataSource.TELEMETRY);
        assertThat(resolved.confidenceGrade()).isEqualTo(ConfidenceGrade.HIGH);
    }

    @Test
    void faturaImportadaSubstituiManualComGrauMedio() {
        var resolved = AutomaticSourceUpgrade.resolveKwh(null, TENANT, bill(), new BigDecimal("9000"));
        assertThat(resolved.kwh()).isEqualByComparingTo("12000");
        assertThat(resolved.source()).isEqualTo(EnergyDataSource.IMPORTED_BILL);
        assertThat(resolved.confidenceGrade()).isEqualTo(ConfidenceGrade.MEDIUM);
    }

    @Test
    void semFonteAutomaticaPermaneceManualComGrauBaixo() {
        var resolved = AutomaticSourceUpgrade.resolveKwh(null, TENANT, null, new BigDecimal("9000"));
        assertThat(resolved.source()).isEqualTo(EnergyDataSource.MANUAL);
        assertThat(resolved.confidenceGrade()).isEqualTo(ConfidenceGrade.LOW);
    }

    @Test
    void semNenhumaFonteRecusa() {
        assertThatThrownBy(() -> AutomaticSourceUpgrade.resolveKwh(null, TENANT, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_DATA_UNAVAILABLE");
    }

    @Test
    void upgradeDeVariavelExigeMapeamentoDeView() {
        assertThatThrownBy(() -> AutomaticSourceUpgrade.upgradeVariableToAutomatic("kwh_total", null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_AUTOMATIC_VIEW_MAPPING_REQUIRED");
        IndicatorVariable upgraded =
                AutomaticSourceUpgrade.upgradeVariableToAutomatic("kwh_total", "rpt_energy_kwh");
        assertThat(upgraded.sourceType()).isEqualTo(IndicatorVariable.SourceType.REPORT_VIEW);
        assertThat(upgraded.reportView()).isEqualTo("rpt_energy_kwh");
    }

    private static FuelAndConsumption.FuelRecord fuel(String liters, LocalDateTime at) {
        return new FuelAndConsumption.FuelRecord(UUID.randomUUID(), 1000,
                new BigDecimal(liters), new BigDecimal("5.00"), at, null);
    }

    @Test
    void combustivelLidoDosRegistrosReaisDaEd59() {
        List<FuelAndConsumption.FuelRecord> records = List.of(
                fuel("40", LocalDateTime.of(2026, 10, 5, 8, 0)),
                fuel("60", LocalDateTime.of(2026, 10, 20, 8, 0)),
                fuel("999", LocalDateTime.of(2026, 11, 2, 8, 0)));
        Optional<BigDecimal> liters = AutomaticSourceUpgrade.fleetLitersFromEd59(
                records, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertThat(liters).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("100"));

        EnergyPorts.FleetFuelPort port = (t, from, to) ->
                AutomaticSourceUpgrade.fleetLitersFromEd59(records, from, to);
        var resolved = AutomaticSourceUpgrade.resolveFuel(port, TENANT,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("7"));
        assertThat(resolved.liters()).isEqualByComparingTo("100");
        assertThat(resolved.source()).isEqualTo(EnergyDataSource.FLEET_ED59);
        assertThat(resolved.confidenceGrade()).isEqualTo(ConfidenceGrade.MEDIUM);
    }

    @Test
    void semDadoDaFrotaCaiNoRamoManualDoAceite() {
        EnergyPorts.FleetFuelPort emptyPort = (t, from, to) -> Optional.empty();
        var resolved = AutomaticSourceUpgrade.resolveFuel(emptyPort, TENANT,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("42"));
        assertThat(resolved.liters()).isEqualByComparingTo("42");
        assertThat(resolved.source()).isEqualTo(EnergyDataSource.MANUAL);
        assertThat(resolved.confidenceGrade()).isEqualTo(ConfidenceGrade.LOW);
    }

    // ---- Correcao D2/D4 (REVISAO-ED-90, sondas P6-P9 e P13) ----

    @Test
    void telemetriaSemFaturaNaoEhIgnoradaP6() {
        UUID installation = UUID.randomUUID();
        YearMonth month = YearMonth.of(2026, 10);
        UUID[] seenTenant = new UUID[1];
        UUID[] seenInstallation = new UUID[1];
        YearMonth[] seenMonth = new YearMonth[1];
        EnergyPorts.TelemetryPort telemetry = (t, inst, m) -> {
            seenTenant[0] = t;
            seenInstallation[0] = inst;
            seenMonth[0] = m;
            return Optional.of(new BigDecimal("777"));
        };
        // Sem fatura nenhuma: a telemetria (instalacao+mes proprios) vence o manual.
        var resolved = AutomaticSourceUpgrade.resolveKwh(telemetry, TENANT, installation, month,
                null, new BigDecimal("9000"));
        assertThat(resolved.kwh()).isEqualByComparingTo("777");
        assertThat(resolved.source()).isEqualTo(EnergyDataSource.TELEMETRY);
        assertThat(resolved.confidenceGrade()).isEqualTo(ConfidenceGrade.HIGH);
        assertThat(seenTenant[0]).isEqualTo(TENANT);
        assertThat(seenInstallation[0]).isEqualTo(installation);
        assertThat(seenMonth[0]).isEqualTo(month);
    }

    @Test
    void telemetriaNegativaOuZeroRecusaP7() {
        EnergyBill bill = bill();
        EnergyPorts.TelemetryPort negative = (t, inst, m) -> Optional.of(new BigDecimal("-500"));
        assertThatThrownBy(() -> AutomaticSourceUpgrade.resolveKwh(negative, TENANT, bill,
                null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_RESOLVED_KWH_INVALID");
        EnergyPorts.TelemetryPort zero = (t, inst, m) -> Optional.of(BigDecimal.ZERO);
        assertThatThrownBy(() -> AutomaticSourceUpgrade.resolveKwh(zero, TENANT, bill,
                new BigDecimal("9000")))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_RESOLVED_KWH_INVALID");
    }

    @Test
    void manualNegativoRecusaP8() {
        assertThatThrownBy(() -> AutomaticSourceUpgrade.resolveKwh(null, TENANT, null,
                new BigDecimal("-100")))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_RESOLVED_KWH_INVALID");
    }

    @Test
    void combustivelNegativoRecusaP9() {
        assertThatThrownBy(() -> AutomaticSourceUpgrade.resolveFuel(null, TENANT,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("-42")))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ESG_RESOLVED_FUEL_INVALID");
        EnergyPorts.FleetFuelPort negativeFleet = (t, from, to) -> Optional.of(new BigDecimal("-5"));
        assertThatThrownBy(() -> AutomaticSourceUpgrade.resolveFuel(negativeFleet, TENANT,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), new BigDecimal("42")))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ESG_RESOLVED_FUEL_INVALID");
    }

    @Test
    void faturaDeOutroTenantRecusaP13() {
        EnergyBill foreignBill = new EnergyBill(UUID.randomUUID(), UUID.randomUUID(),
                YearMonth.of(2026, 10), new BigDecimal("5555"), null, null, null, null,
                EnergyDataSource.MANUAL, null);
        assertThatThrownBy(() -> AutomaticSourceUpgrade.resolveKwh(null, TENANT, foreignBill, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_BILL_TENANT_MISMATCH");
    }

    @Test
    void semFrotaESemManualRecusa() {
        assertThatThrownBy(() -> AutomaticSourceUpgrade.resolveFuel(null, TENANT,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ESG_FUEL_DATA_UNAVAILABLE");
    }
}
