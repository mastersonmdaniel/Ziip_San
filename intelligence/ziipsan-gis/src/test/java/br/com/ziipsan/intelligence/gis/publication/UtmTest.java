package br.com.ziipsan.intelligence.gis.publication;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * GIS-06 (P3.25): conversão UTM SIRGAS 2000 (GRS80) da pesquisa por
 * coordenada. Pontos de conferência independentes da implementação:
 * a origem falsa do fuso (500000 E, 10000000 N no hemisfério sul)
 * cai exatamente no meridiano central sobre o Equador; e a ida e
 * volta de um ponto no fuso 22S fecha em menos de 1 m.
 */
class UtmTest {

    @Test
    @DisplayName("origem falsa do fuso 22S é o meridiano central (-51) no Equador")
    void falseOrigin() {
        Utm.LonLat lonLat = Utm.toLonLat(500_000, 10_000_000, 22, true);
        assertThat(lonLat.lon()).isCloseTo(-51.0, within(1e-6));
        assertThat(lonLat.lat()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("ida e volta no fuso 22S fecha em menos de 1 m")
    void roundTrip() {
        Utm.UtmPoint utm = Utm.fromLonLat(-48.633, -26.905);
        assertThat(utm.zone()).isEqualTo(22);
        assertThat(utm.southernHemisphere()).isTrue();
        Utm.LonLat back = Utm.toLonLat(utm.easting(), utm.northing(),
                utm.zone(), utm.southernHemisphere());
        assertThat(back.lon()).isCloseTo(-48.633, within(1e-5));
        assertThat(back.lat()).isCloseTo(-26.905, within(1e-5));
    }

    @Test
    @DisplayName("meridiano central por fuso e fuso inválido recusado")
    void centralMeridian() {
        assertThat(Utm.centralMeridianDeg(22)).isEqualTo(-51.0);
        assertThat(Utm.centralMeridianDeg(1)).isEqualTo(-177.0);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> Utm.centralMeridianDeg(61));
    }
}
