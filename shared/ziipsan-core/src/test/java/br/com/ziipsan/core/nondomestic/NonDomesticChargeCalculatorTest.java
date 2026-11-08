package br.com.ziipsan.core.nondomestic;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cobranca complementar (classe d) — valores calculados a mao sobre
 * entradas de fixture (percentual, tarifa, preco e taxa sao dados
 * entregues, nunca de fabrica).
 */
class NonDomesticChargeCalculatorTest {

    private static final YearMonth MAR26 = YearMonth.of(2026, 3);

    @Test
    void volumePercentAppliesPercentToWaterVolumeAtSewerRate() {
        // A mao: 100 m3 de agua x 80% = 80 m3 faturaveis; 80 x 2,50 = 200,00.
        var c = NonDomesticChargeCalculator.byVolumePercent(TestSupport.authorizedDischarger(), MAR26,
                new BigDecimal("100"), new BigDecimal("80"), new BigDecimal("2.50"));
        assertEquals(NonDomesticChargeModel.VOLUME_PERCENT, c.model());
        assertEquals("NONDOMESTIC_EFFLUENT", c.componentCode());
        assertEquals(new BigDecimal("80.000"), c.billableVolumeM3());
        assertEquals(new BigDecimal("200.00"), c.amount());
        assertThrows(IllegalArgumentException.class, () -> NonDomesticChargeCalculator.byVolumePercent(
                TestSupport.authorizedDischarger(), MAR26,
                new BigDecimal("100"), new BigDecimal("0"), new BigDecimal("2.50")));
        assertThrows(IllegalArgumentException.class, () -> NonDomesticChargeCalculator.byVolumePercent(
                TestSupport.authorizedDischarger(), MAR26,
                new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("2.50")));
    }

    @Test
    void pollutantLoadUsesMeasuredLoadAndDeclaredPrice() {
        // A mao: PARAM_A 200 mg/L x 50 m3 / 1000 = 10 kg; PARAM_B 100 x 50 / 1000 = 5 kg;
        // total 15 kg x 4,00/kg = 60,00.
        var measurements = List.of(
                new PollutantLoad.LoadMeasurement("PARAM_A", new BigDecimal("200"), new BigDecimal("50")),
                new PollutantLoad.LoadMeasurement("PARAM_B", new BigDecimal("100"), new BigDecimal("50")));
        assertEquals(new BigDecimal("10.0000"), measurements.get(0).loadKg());
        assertEquals(new BigDecimal("15.0000"), PollutantLoad.totalLoadKg(measurements));
        var c = NonDomesticChargeCalculator.byPollutantLoad(TestSupport.authorizedDischarger(), MAR26,
                measurements, new BigDecimal("4.00"), true);
        assertEquals(new BigDecimal("60.00"), c.amount());
        assertEquals(new BigDecimal("15.0000"), c.totalLoadKg());
    }

    @Test
    void loadBillingRefusedWhenAgencyDoesNotForeseeIt() {
        // Guarda literal do aceite: carga so quando a agencia a previr.
        var measurements = List.of(
                new PollutantLoad.LoadMeasurement("PARAM_A", new BigDecimal("200"), new BigDecimal("50")));
        assertThrows(IllegalStateException.class, () -> NonDomesticChargeCalculator.byPollutantLoad(
                TestSupport.authorizedDischarger(), MAR26, measurements, new BigDecimal("4.00"), false));
    }

    @Test
    void fixedByClassReadsProviderFeeTableAndRefusesMissingClass() {
        DischargerRegistration laundry = new DischargerRegistration(
                java.util.UUID.randomUUID(), TestSupport.TENANT, TestSupport.UNIT_ID,
                DischargerRegistration.Route.COMMERCIAL_UNIT, DischargerRegistration.Kind.LAUNDRY,
                null, "PONTO-LAV-1");
        var c = NonDomesticChargeCalculator.byFixedClass(laundry, MAR26,
                Map.of("LAUNDRY", new BigDecimal("150.00")));
        assertEquals(new BigDecimal("150.00"), c.amount());
        assertEquals("LAUNDRY", c.chargeClassCode());
        assertThrows(IllegalArgumentException.class, () -> NonDomesticChargeCalculator.byFixedClass(
                laundry, MAR26, Map.of("INDUSTRY", new BigDecimal("150.00"))));
        assertThrows(IllegalArgumentException.class, () -> NonDomesticChargeCalculator.byFixedClass(
                laundry, MAR26, Map.of()));
    }

    @Test
    void loadMeasurementRefusesPresumedConcentration() {
        assertThrows(IllegalArgumentException.class,
                () -> new PollutantLoad.LoadMeasurement("PARAM_A", null, new BigDecimal("50")));
        assertThrows(IllegalArgumentException.class,
                () -> PollutantLoad.totalLoadKg(List.of()));
    }
}
