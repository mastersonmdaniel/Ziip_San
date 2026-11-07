package br.com.ziipsan.intelligence.gis.connection;

import br.com.ziipsan.intelligence.gis.connection.AutoServiceLinePlanner.Decision;
import br.com.ziipsan.intelligence.gis.connection.AutoServiceLinePlanner.GeoPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Decisão do ramal automático (GIS-05, P2.23): os mesmos motivos que a
 * função {@code gis_generate_auto_service_lines} da V13_10 devolve.
 */
class AutoServiceLinePlannerTest {

    private static final GeoPoint PROPERTY = new GeoPoint(-46.0, -23.0);

    @Test
    @DisplayName("distância geodésica: ~111 m para 0,001 grau de latitude")
    void haversineSanity() {
        double d = AutoServiceLinePlanner.distanceMeters(PROPERTY, new GeoPoint(-46.0, -22.999));
        assertThat(d).isCloseTo(111.19, within(1.0));
    }

    @Test
    @DisplayName("sem ponto do imóvel, não gera (PROPERTY_WITHOUT_POINT)")
    void noPointNoLine() {
        Decision d = AutoServiceLinePlanner.decide(null, 10.0, 100.0);
        assertThat(d.generate()).isFalse();
        assertThat(d.skipReason()).isEqualTo(AutoServiceLinePlanner.PROPERTY_WITHOUT_POINT);
    }

    @Test
    @DisplayName("sem arco compatível, não gera (NO_COMPATIBLE_ARC)")
    void noArcNoLine() {
        Decision d = AutoServiceLinePlanner.decide(PROPERTY, null, 100.0);
        assertThat(d.generate()).isFalse();
        assertThat(d.skipReason()).isEqualTo(AutoServiceLinePlanner.NO_COMPATIBLE_ARC);
    }

    @Test
    @DisplayName("além de gis.service_line_max_distance_m, não gera (BEYOND_MAX_DISTANCE)")
    void beyondMaxDistance() {
        Decision d = AutoServiceLinePlanner.decide(PROPERTY, 100.01, 100.0);
        assertThat(d.generate()).isFalse();
        assertThat(d.skipReason()).isEqualTo(AutoServiceLinePlanner.BEYOND_MAX_DISTANCE);
        assertThat(d.distanceM()).isEqualTo(100.01);
    }

    @Test
    @DisplayName("no limite (100 m com o padrão de 100 m), gera")
    void atLimitGenerates() {
        Decision d = AutoServiceLinePlanner.decide(PROPERTY, 100.0, 100.0);
        assertThat(d.generate()).isTrue();
        assertThat(d.skipReason()).isNull();
    }

    @Test
    @DisplayName("distância máxima inválida é erro de programação, não decisão")
    void invalidMaxDistance() {
        assertThatThrownBy(() -> AutoServiceLinePlanner.decide(PROPERTY, 1.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("função de rede e tipo de ramal por tipo de ligação (V13_10 e catálogo)")
    void mappingsByKind() {
        assertThat(AutoServiceLinePlanner.networkFunctionFor("WATER")).isEqualTo("WATER_DISTRIBUTION");
        assertThat(AutoServiceLinePlanner.networkFunctionFor("SEWER")).isEqualTo("SEWER_NETWORK");
        assertThat(AutoServiceLinePlanner.featureTypeFor("WATER")).isEqualTo("WATER_SERVICE_LINE");
        assertThat(AutoServiceLinePlanner.featureTypeFor("SEWER")).isEqualTo("SEWER_SERVICE_LINE");
        assertThatThrownBy(() -> AutoServiceLinePlanner.networkFunctionFor("STORM"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
