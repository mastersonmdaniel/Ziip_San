package br.com.ziipsan.platform.statuspage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.18: a regra das 72 h do aceite — janela de manutenção só é
 * publicável com pelo menos 72 h de antecedência. A mesma regra existe
 * como CHECK na V10_4; aqui ela é provada no serviço.
 */
class MaintenanceWindowTest {

    private static final Instant PUBLISHED = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    @DisplayName("P3.18: janela publicada com menos de 72 h de antecedência é recusada")
    void refusesLessThan72HoursNotice() {
        Instant startsIn71h59 = PUBLISHED.plusSeconds(71 * 3600 + 59 * 60);
        assertThatThrownBy(() -> MaintenanceWindow.publish("Janela", null, "api",
                startsIn71h59, startsIn71h59.plusSeconds(3600), PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 h");
        // Publicar "agora" para começar já é o caso extremo da mesma recusa.
        assertThatThrownBy(() -> MaintenanceWindow.publish("Janela", null, "api",
                PUBLISHED.plusSeconds(3600), PUBLISHED.plusSeconds(7200), PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("P3.18: exatamente 72 h é aceito (fronteira), e 73 h também")
    void acceptsExactly72Hours() {
        Instant startsAt72 = PUBLISHED.plus(MaintenanceWindow.MINIMUM_NOTICE);
        MaintenanceWindow exact = MaintenanceWindow.publish("Janela", null, "api",
                startsAt72, startsAt72.plusSeconds(3600), PUBLISHED);
        assertThat(exact.startsAt()).isEqualTo(startsAt72);

        Instant startsAt73 = PUBLISHED.plusSeconds(73 * 3600);
        assertThat(MaintenanceWindow.publish("Janela", null, null,
                startsAt73, startsAt73.plusSeconds(3600), PUBLISHED).componentKey()).isNull();
    }

    @Test
    @DisplayName("P3.18: janela que termina antes de começar é recusada")
    void refusesInvertedWindow() {
        Instant starts = PUBLISHED.plusSeconds(80 * 3600);
        assertThatThrownBy(() -> MaintenanceWindow.publish("Janela", null, "api",
                starts, starts.minusSeconds(1), PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("P3.18: janela global (componentKey nulo) afeta qualquer componente; a de outro, não")
    void globalWindowAffectsAll() {
        Instant starts = PUBLISHED.plusSeconds(80 * 3600);
        MaintenanceWindow global = MaintenanceWindow.publish("Global", null, null,
                starts, starts.plusSeconds(3600), PUBLISHED);
        assertThat(global.affects("api")).isTrue();
        assertThat(global.affects("portal")).isTrue();
        MaintenanceWindow onlyApi = MaintenanceWindow.publish("Só API", null, "api",
                starts, starts.plusSeconds(3600), PUBLISHED);
        assertThat(onlyApi.affects("portal")).isFalse();
        assertThat(global.isUpcomingAt(PUBLISHED)).isTrue();
        assertThat(global.isActiveAt(starts.plusSeconds(60))).isTrue();
        assertThat(global.isActiveAt(starts.plusSeconds(3600))).isFalse();
    }
}
