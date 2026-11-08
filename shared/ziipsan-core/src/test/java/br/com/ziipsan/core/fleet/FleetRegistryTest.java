package br.com.ziipsan.core.fleet;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cadastro do veiculo como FLEET_ASSET sobre a P1.35 e verificador do alvo. */
class FleetRegistryTest {

    private FleetRegistry registry() {
        Set<java.util.UUID> known = Set.of(FleetFixtures.ASSET_VAN, FleetFixtures.ASSET_JET);
        return new FleetRegistry((tenant, asset) -> FleetFixtures.TENANT.equals(tenant) && known.contains(asset));
    }

    @Test
    void registersVehicleOverExistingP135Asset() {
        FleetRegistry r = registry();
        r.register(FleetFixtures.TENANT, FleetFixtures.vehicle(FleetFixtures.ASSET_VAN,
                LocalDate.of(2027, 1, 1), Set.of()));
        assertTrue(r.find(FleetFixtures.TENANT, FleetFixtures.ASSET_VAN).isPresent());
    }

    @Test
    void refusesVehicleWhoseAssetDoesNotExistInP135() {
        FleetRegistry r = registry();
        assertThrows(IllegalArgumentException.class, () -> r.register(FleetFixtures.TENANT,
                FleetFixtures.vehicle(java.util.UUID.randomUUID(), LocalDate.of(2027, 1, 1), Set.of())));
    }

    @Test
    void refusesDuplicateFleetRegistrationOfSameAsset() {
        FleetRegistry r = registry();
        r.register(FleetFixtures.TENANT, FleetFixtures.vehicle(FleetFixtures.ASSET_VAN,
                LocalDate.of(2027, 1, 1), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> r.register(FleetFixtures.TENANT,
                FleetFixtures.vehicle(FleetFixtures.ASSET_VAN, LocalDate.of(2027, 1, 1), Set.of())));
    }

    @Test
    void fleetAssetExistenceVerifierIsScopedToTheTenant() {
        FleetRegistry r = registry();
        r.register(FleetFixtures.TENANT, FleetFixtures.vehicle(FleetFixtures.ASSET_VAN,
                LocalDate.of(2027, 1, 1), Set.of()));
        // verificador do alvo FLEET_ASSET da P1.31, registrado pela ED-59
        assertTrue(r.existsFleetTarget(FleetFixtures.TENANT, FleetFixtures.ASSET_VAN));
        assertFalse(r.existsFleetTarget(FleetFixtures.OTHER_TENANT, FleetFixtures.ASSET_VAN));
        assertFalse(r.existsFleetTarget(FleetFixtures.TENANT, FleetFixtures.ASSET_JET));
    }

    @Test
    void vehiclesWithFiltersActiveAndEquipment() {
        FleetRegistry r = registry();
        r.register(FleetFixtures.TENANT, FleetFixtures.vehicle(FleetFixtures.ASSET_VAN,
                LocalDate.of(2027, 1, 1), Set.of()));
        r.register(FleetFixtures.TENANT, FleetFixtures.vehicle(FleetFixtures.ASSET_JET,
                LocalDate.of(2027, 1, 1), Set.of(FleetRegistry.SpecialEquipment.HIDROJATO)));
        assertEquals(1, r.vehiclesWith(FleetFixtures.TENANT,
                Set.of(FleetRegistry.SpecialEquipment.HIDROJATO)).size());
        assertEquals(2, r.vehiclesWith(FleetFixtures.TENANT, Set.of()).size());
        assertEquals(0, r.vehiclesWith(FleetFixtures.TENANT,
                Set.of(FleetRegistry.SpecialEquipment.RETROESCAVADEIRA)).size());
    }

    @Test
    void licensingExpiryIsDateComparison() {
        FleetRegistry.Vehicle v = FleetFixtures.vehicle(FleetFixtures.ASSET_VAN,
                LocalDate.of(2026, 11, 10), Set.of());
        assertFalse(v.licensingExpiredOn(LocalDate.of(2026, 11, 10))); // no dia da validade, vale
        assertTrue(v.licensingExpiredOn(LocalDate.of(2026, 11, 11)));
    }
}
