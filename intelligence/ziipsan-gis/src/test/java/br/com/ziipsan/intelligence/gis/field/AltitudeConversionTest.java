package br.com.ziipsan.intelligence.gis.field;

import br.com.ziipsan.intelligence.gis.field.AltitudeConversion.VerticalDatum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AltitudeConversionTest {

    // Ondulacao SINTETICA de ensaio (a grade real hgeoHNOR2020 e da
    // integracao): -8,50 m constante so para provar a formula H = h - N.
    private static final AltitudeConversion.GeoidPort SYNTHETIC = (lat, lon) -> -8.50;

    @Test
    void convertsEllipsoidalToNormalDiscountingAntenna() {
        // h bruto 812,40 - antena 2,00 = 810,40; H = 810,40 - (-8,50) = 818,90
        var result = AltitudeConversion.toNormalHeight(
                -26.900001, -48.700001, 812.40, 2.0, VerticalDatum.IMBITUBA, SYNTHETIC);
        assertEquals(818.90, result.heightM(), 1e-9);
        assertEquals(VerticalDatum.IMBITUBA, result.datum());
        assertEquals(-8.50, result.undulationUsedM(), 1e-9);
    }

    @Test
    void refusesConversionWithoutGeoidPort() {
        var ex = assertThrows(IllegalStateException.class, () ->
                AltitudeConversion.toNormalHeight(-26.9, -48.7, 812.4, 2.0, VerticalDatum.IMBITUBA, null));
        assertTrue(ex.getMessage().contains("hgeoHNOR2020"));
    }

    @Test
    void santanaDatumIsCarriedThrough() {
        var result = AltitudeConversion.toNormalHeight(
                0.050001, -51.060001, 20.0, 0.0, VerticalDatum.SANTANA, SYNTHETIC);
        assertEquals(VerticalDatum.SANTANA, result.datum());
        assertEquals(28.50, result.heightM(), 1e-9);
    }
}
