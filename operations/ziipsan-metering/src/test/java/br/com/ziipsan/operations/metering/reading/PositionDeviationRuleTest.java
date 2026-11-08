package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.reading.PositionDeviationRule.GeoPoint;
import br.com.ziipsan.operations.metering.reading.PositionDeviationRule.Outcome;
import br.com.ziipsan.operations.metering.reading.PositionDeviationRule.Result;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Desvio de posição (ED-15): o teste do aceite — desvio entre a posição
 * da leitura (app ED-18) e a do imóvel gera ALERTA, SEM BLOQUEIO.
 * Coordenadas fictícias de teste em torno de Camboriú/SC.
 */
class PositionDeviationRuleTest {

    /** Imóvel: ponto de referência fictício. */
    private static final GeoPoint PROPERTY = new GeoPoint(-27.0250, -48.6550);
    /** Leitura a ~55 m do imóvel (0,0005° de latitude ≈ 55,6 m). */
    private static final GeoPoint NEAR = new GeoPoint(-27.0245, -48.6550);
    /** Leitura a ~1,1 km do imóvel (0,01° de latitude ≈ 1112 m). */
    private static final GeoPoint FAR = new GeoPoint(-27.0150, -48.6550);

    @Test
    @DisplayName("desvio acima do limiar gera alerta e NÃO bloqueia a leitura — teste do aceite")
    void deviationAlertsWithoutBlocking() {
        Result result = PositionDeviationRule.evaluate(FAR, PROPERTY, 100.0);
        assertThat(result.outcome()).isEqualTo(Outcome.ALERT_DEVIATION);
        assertThat(result.alertGenerated()).isTrue();
        assertThat(result.blocked()).isFalse();
        assertThat(result.distanceMetres()).isBetween(1000.0, 1250.0);
    }

    @Test
    @DisplayName("desvio dentro do limiar não gera alerta e também não bloqueia")
    void withinThresholdNoAlert() {
        Result result = PositionDeviationRule.evaluate(NEAR, PROPERTY, 100.0);
        assertThat(result.outcome()).isEqualTo(Outcome.WITHIN_THRESHOLD);
        assertThat(result.alertGenerated()).isFalse();
        assertThat(result.blocked()).isFalse();
        assertThat(result.distanceMetres()).isBetween(40.0, 70.0);
    }

    @Test
    @DisplayName("sem posição da leitura ou do imóvel: sem alerta inventado e sem bloqueio")
    void missingPositionNoAlertNoBlock() {
        assertThat(PositionDeviationRule.evaluate(null, PROPERTY, 100.0).outcome())
                .isEqualTo(Outcome.NO_POSITION);
        Result noProperty = PositionDeviationRule.evaluate(NEAR, null, 100.0);
        assertThat(noProperty.outcome()).isEqualTo(Outcome.NO_POSITION);
        assertThat(noProperty.alertGenerated()).isFalse();
        assertThat(noProperty.blocked()).isFalse();
        assertThat(noProperty.distanceMetres()).isNull();
    }

    @Test
    @DisplayName("limiar ausente (zero ou negativo) é erro explícito — nenhum limiar é presumido")
    void thresholdIsMandatory() {
        assertThatThrownBy(() -> PositionDeviationRule.evaluate(FAR, PROPERTY, 0))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> PositionDeviationRule.evaluate(FAR, PROPERTY, -5))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("coordenada NaN não é posição: é recusada em vez de virar distância silenciosa")
    void nanCoordinateRejected() {
        assertThatThrownBy(() -> new GeoPoint(Double.NaN, -48.6550))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new GeoPoint(-27.0250, Double.POSITIVE_INFINITY))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("distância haversine: o mesmo ponto dista zero")
    void samePointZeroDistance() {
        assertThat(PositionDeviationRule.distanceMetres(PROPERTY, PROPERTY)).isEqualTo(0.0);
    }
}
