package br.com.ziipsan.core.fleet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Validades, alerta fleet.docs_alert_days e multas por veiculo/condutor. */
class DocumentsAndFinesTest {

    private static final LocalDate REF = LocalDate.of(2026, 11, 10);

    @Test
    void expiredIsPureDateComparisonWithoutParameter() {
        assertEquals(DriverDocuments.DocumentStatus.EXPIRED,
                DriverDocuments.statusOf(LocalDate.of(2026, 11, 9), REF, Optional.empty()));
        assertEquals(DriverDocuments.DocumentStatus.VALID,
                DriverDocuments.statusOf(LocalDate.of(2026, 11, 10), REF, Optional.empty()));
    }

    @Test
    void expiringSoonOnlyExistsWithTenantAlertWindow() {
        LocalDate soon = LocalDate.of(2026, 11, 25); // 15 dias apos a referencia
        assertEquals(DriverDocuments.DocumentStatus.EXPIRING_SOON,
                DriverDocuments.statusOf(soon, REF, Optional.of(30)));
        assertEquals(DriverDocuments.DocumentStatus.VALID,
                DriverDocuments.statusOf(soon, REF, Optional.empty())); // sem parametro, sem "vencendo"
        assertEquals(DriverDocuments.DocumentStatus.VALID,
                DriverDocuments.statusOf(LocalDate.of(2026, 12, 31), REF, Optional.of(30)));
    }

    @Test
    void alertsCoverLicensesAndVehicleLicensing() {
        var licenses = List.of(FleetFixtures.license(FleetFixtures.DRIVER, LocalDate.of(2026, 11, 25)));
        var vehicles = List.of(
                FleetFixtures.vehicle(FleetFixtures.ASSET_VAN, LocalDate.of(2026, 11, 1), Set.of()),
                FleetFixtures.vehicle(FleetFixtures.ASSET_JET, LocalDate.of(2027, 6, 1), Set.of()));
        List<DriverDocuments.ExpiryAlert> alerts = DriverDocuments.alerts(licenses, vehicles, REF, Optional.of(30));
        assertEquals(2, alerts.size());
        assertEquals(DriverDocuments.DocumentStatus.EXPIRING_SOON, alerts.get(0).status());
        assertEquals("DRIVER_LICENSE", alerts.get(0).documentKind());
        assertEquals(DriverDocuments.DocumentStatus.EXPIRED, alerts.get(1).status());
        assertEquals("VEHICLE_LICENSING", alerts.get(1).documentKind());
    }

    @Test
    void finesTotalByVehicleAndByDriver() {
        UUID other = UUID.fromString("88888888-8888-8888-8888-888888888888");
        List<DriverDocuments.TrafficFine> fines = List.of(
                new DriverDocuments.TrafficFine(UUID.randomUUID(), FleetFixtures.ASSET_VAN, FleetFixtures.DRIVER,
                        new BigDecimal("200.00"), LocalDateTime.of(2026, 10, 1, 9, 0), "excesso de velocidade"),
                new DriverDocuments.TrafficFine(UUID.randomUUID(), FleetFixtures.ASSET_VAN, null,
                        new BigDecimal("150.00"), LocalDateTime.of(2026, 10, 2, 9, 0), "autuacao do veiculo sem condutor identificado"),
                new DriverDocuments.TrafficFine(UUID.randomUUID(), other, FleetFixtures.DRIVER,
                        new BigDecimal("80.00"), LocalDateTime.of(2026, 10, 3, 9, 0), "estacionamento irregular"));
        assertEquals(new BigDecimal("350.00"), DriverDocuments.totalByVehicle(fines, FleetFixtures.ASSET_VAN));
        // por condutor: so as identificadas (200 + 80); a de 150 sem condutor nao entra
        assertEquals(new BigDecimal("280.00"), DriverDocuments.totalByDriver(fines, FleetFixtures.DRIVER));
    }

    @Test
    void parametersHaveNoFactoryBehavior() {
        // sem valor do tenant: checklist nao exigido, sem janela de alerta, sem limite de desvio
        org.junit.jupiter.api.Assertions.assertFalse(FleetParameters.checklistRequired(Optional.empty()));
        org.junit.jupiter.api.Assertions.assertTrue(FleetParameters.docsAlertDays(Optional.empty()).isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(
                FleetParameters.consumptionDeviationPercent(Optional.empty()).isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), FleetParameters.checklistItems(null));
    }
}
