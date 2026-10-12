package br.com.sigsan.intelligence.quality.scada;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScadaTopicTest {

    private static final String TENANT = "44444444-4444-4444-4444-444444444444";

    @Test
    @DisplayName("data e alert: tenant, tipo e tag extraídos do tópico")
    void parsesValidTopics() {
        ScadaTopic data = ScadaTopic.parse("sigsan/" + TENANT + "/scada/data/ETA1.CLORO_RES").orElseThrow();
        assertThat(data.tenantId()).isEqualTo(UUID.fromString(TENANT));
        assertThat(data.type()).isEqualTo(ScadaTopic.Type.DATA);
        assertThat(data.tag()).isEqualTo("ETA1.CLORO_RES");

        ScadaTopic alert = ScadaTopic.parse("sigsan/" + TENANT + "/scada/alert/EEAB-02:pressao").orElseThrow();
        assertThat(alert.type()).isEqualTo(ScadaTopic.Type.ALERT);
        assertThat(alert.tag()).isEqualTo("EEAB-02:pressao");
    }

    @ParameterizedTest(name = "[{0}] é descartado")
    @NullSource
    @ValueSource(strings = {
            "",
            // formato que o serviço assinava antes: nunca casava com data/alert
            "sigsan/" + TENANT + "/quality/ETA1",
            "sigsan/tenant_alpha/scada/data/ETA1",                 // schema em vez de UUID
            "sigsan/44444444-4444-4444-4444-44444444444/scada/data/ETA1", // UUID incompleto
            "sigsan/1-1-1-1-1/scada/data/ETA1",                    // forma abreviada que UUID.fromString aceita
            "sigsan/44444444-4444-4444-4444-44444444444A/scada/data/ETA1",
            "sigsan/" + "AAAAAAAA-4444-4444-4444-444444444444" + "/scada/data/ETA1", // maiúsculas
            "sigsan/" + TENANT + "/scada/command/ETA1",            // tipo desconhecido
            "sigsan/" + TENANT + "/scada/data",                    // sem tag
            "sigsan/" + TENANT + "/scada/data/",                   // tag vazia
            "sigsan/" + TENANT + "/scada/data/ETA1/extra",         // nível a mais
            "sigsan/" + TENANT + "/scada/data/+",                  // curinga MQTT
            "sigsan/" + TENANT + "/scada/data/ETA 1",              // espaço
            "outro/" + TENANT + "/scada/data/ETA1",
    })
    void rejectsInvalidTopics(String topic) {
        assertThat(ScadaTopic.parse(topic)).isEmpty();
    }

    @Test
    @DisplayName("tópico de comando fica fora da assinatura: o serviço não recebe o que publica")
    void commandTopicIsOutsideSubscription() {
        String topic = ScadaTopic.command(UUID.fromString(TENANT), "ETA1.BOMBA");
        assertThat(topic).isEqualTo("sigsan/" + TENANT + "/command/ETA1.BOMBA");
        assertThat(ScadaTopic.parse(topic)).isEmpty();
        assertThat(ScadaTopic.SUBSCRIPTION).isEqualTo("sigsan/+/scada/+/+");
    }

    @Test
    @DisplayName("comando sem tenant ou com tag inválida é recusado")
    void commandValidation() {
        assertThatThrownBy(() -> ScadaTopic.command(null, "ETA1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScadaTopic.command(UUID.fromString(TENANT), "a/b"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
