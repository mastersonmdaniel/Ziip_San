package br.com.ziipsan.operations.metering.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A leitura de {@code ami.alert_thresholds} pela classe {@code Json} do core (ED-17). */
class AmiAlertThresholdsTest {

    @Test
    @DisplayName("o documento publicado é lido com ações por tipo e limiar de pico")
    void parsesPublishedShape() {
        AmiAlertThresholds thresholds = AmiAlertThresholds.parse(
                "{\"alarmActions\":{\"CONTINUOUS_FLOW\":\"BOTH\",\"BATTERY_LOW\":\"NOTIFY_CUSTOMER\"},"
                        + "\"peakHourlyLitres\":850.5}");

        assertThat(thresholds.actionFor(MeterAlarmKind.CONTINUOUS_FLOW)).hasValue(AlarmAction.BOTH);
        assertThat(thresholds.actionFor(MeterAlarmKind.BATTERY_LOW)).hasValue(AlarmAction.NOTIFY_CUSTOMER);
        assertThat(thresholds.actionFor(MeterAlarmKind.TAMPERING)).isEmpty();
        assertThat(thresholds.peakHourlyLitres()).isEqualByComparingTo("850.5");
    }

    @Test
    @DisplayName("documento vazio é configuração válida sem ação e sem pico — nada é presumido")
    void emptyDocumentMeansNoConfiguration() {
        AmiAlertThresholds thresholds = AmiAlertThresholds.parse("{}");
        assertThat(thresholds.alarmActions()).isEmpty();
        assertThat(thresholds.peakHourlyLitres()).isNull();
    }

    @Test
    @DisplayName("ação desconhecida e JSON inválido são erro explícito, nunca configuração ignorada")
    void invalidConfigurationFails() {
        assertThatThrownBy(() -> AmiAlertThresholds.parse("{\"alarmActions\":{\"TAMPERING\":\"EXPLODE\"}}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AmiAlertThresholds.parse("não é json"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AmiAlertThresholds.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
