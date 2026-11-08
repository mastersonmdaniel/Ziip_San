package br.com.ziipsan.core.fleet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Consumo medio, desvio e apropriacao do combustivel a OS. */
class FuelConsumptionTest {

    private static FuelAndConsumption.FuelRecord rec(long odo, String liters, String price, int day) {
        return new FuelAndConsumption.FuelRecord(FleetFixtures.ASSET_VAN, odo, new BigDecimal(liters),
                new BigDecimal(price), LocalDateTime.of(2026, 10, day, 8, 0), null);
    }

    // 1000km/40L(partida), 1500/50L, 2000/40L -> media (2000-1000)/(50+40) = 11,1111 km/L
    private static final List<FuelAndConsumption.FuelRecord> RECORDS =
            List.of(rec(1_000, "40", "6.00", 1), rec(1_500, "50", "6.00", 10), rec(2_000, "40", "5.50", 20));

    @Test
    void averageConsumptionIsKnownValue() {
        Optional<BigDecimal> avg = FuelAndConsumption.averageKmPerLiter(RECORDS, FleetFixtures.ASSET_VAN);
        assertTrue(avg.isPresent());
        assertEquals(new BigDecimal("11.1111"), avg.get());
    }

    @Test
    void averageNeedsTwoRecordsAndOdometerAdvance() {
        assertTrue(FuelAndConsumption.averageKmPerLiter(List.of(RECORDS.get(0)), FleetFixtures.ASSET_VAN).isEmpty());
        assertTrue(FuelAndConsumption.averageKmPerLiter(List.of(), FleetFixtures.ASSET_VAN).isEmpty());
    }

    @Test
    void totalFuelCostIsSumOfRecords() {
        // 40x6 + 50x6 + 40x5,50 = 240 + 300 + 220 = 760,00
        assertEquals(new BigDecimal("760.00"),
                FuelAndConsumption.totalFuelCost(RECORDS, FleetFixtures.ASSET_VAN));
    }

    @Test
    void deviationAlertWhenLastSegmentWorsensBeyondTenantLimit() {
        // base: (1500-1000)/50 = 10 km/L; ultimo trecho: (1900-1500)/50 = 8 km/L -> piora de 20%
        List<FuelAndConsumption.FuelRecord> rs = List.of(
                rec(1_000, "40", "6.00", 1), rec(1_500, "50", "6.00", 10), rec(1_900, "50", "6.00", 20));
        Optional<FuelAndConsumption.DeviationAlert> alert =
                FuelAndConsumption.deviationAlert(rs, FleetFixtures.ASSET_VAN, Optional.of(new BigDecimal("10")));
        assertTrue(alert.isPresent());
        assertEquals(new BigDecimal("20.00"), alert.get().deviationPercent());
        assertEquals(new BigDecimal("8.0000"), alert.get().lastSegmentKmPerLiter());
        // limite maior que o desvio: sem alerta; sem limite do tenant: sem alerta
        assertTrue(FuelAndConsumption.deviationAlert(rs, FleetFixtures.ASSET_VAN,
                Optional.of(new BigDecimal("25"))).isEmpty());
        assertTrue(FuelAndConsumption.deviationAlert(rs, FleetFixtures.ASSET_VAN, Optional.empty()).isEmpty());
    }

    @Test
    void improvementDoesNotAlert() {
        // ultimo trecho dos RECORDS: (2000-1500)/40 = 12,5 km/L, melhor que a base 10 -> sem alerta
        assertTrue(FuelAndConsumption.deviationAlert(RECORDS, FleetFixtures.ASSET_VAN,
                Optional.of(new BigDecimal("5"))).isEmpty());
    }

    @Test
    void fuelCostForOrderIsApportionedByConsumption() {
        // 55 km a 11 km/L = 5 L x 6,00 = 30,00
        assertEquals(new BigDecimal("30.00"), FuelAndConsumption.fuelCostForOrder(
                55, new BigDecimal("11"), new BigDecimal("6.00")));
    }
}
