package br.com.ziipsan.core.fleet;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Massa SINTETICA declarada da ED-59 (nunca dado real). */
final class FleetFixtures {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID OTHER_TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID ASSET_VAN = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID ASSET_JET = UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final UUID DRIVER = UUID.fromString("55555555-5555-5555-5555-555555555555");
    static final UUID HELPER = UUID.fromString("66666666-6666-6666-6666-666666666666");
    static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 10);

    private FleetFixtures() {
    }

    static FleetRegistry.Vehicle vehicle(UUID assetId, LocalDate licensingUntil,
                                         Set<FleetRegistry.SpecialEquipment> equipment) {
        return new FleetRegistry.Vehicle(assetId, "ABC1D23", licensingUntil,
                FleetRegistry.VehicleStatus.ACTIVE, equipment, 2_000);
    }

    static DriverDocuments.DriverLicense license(UUID personId, LocalDate validUntil) {
        return new DriverDocuments.DriverLicense(personId, validUntil, "D");
    }

    static FieldCrewScheduling.FieldCrew crew() {
        return new FieldCrewScheduling.FieldCrew(UUID.fromString("77777777-7777-7777-7777-777777777777"),
                "Equipe Alfa", java.util.List.of(DRIVER, HELPER), DRIVER);
    }
}
