package br.com.ziipsan.core.transparency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LaiDeadlinesTest {
    @Test @DisplayName("GUARDA LEGAL: prazo acima do teto da Lei 12.527/2011 e recusado em cada componente (classe c)")
    void aboveLegalCeilingRefused() {
        assertThatThrownBy(() -> new LaiDeadlines(21, 10, 10, 5)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("12.527");
        assertThatThrownBy(() -> new LaiDeadlines(20, 11, 10, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LaiDeadlines(20, 10, 11, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LaiDeadlines(20, 10, 10, 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LaiDeadlines.fromTenantValues(30, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("no teto e abaixo dele vale (tenant ajusta dentro do limite legal, art. 45); zero/negativo recusado")
    void withinCeilingAccepted() {
        assertThat(new LaiDeadlines(20, 10, 10, 5)).isEqualTo(LaiDeadlines.FACTORY);
        var reduced = new LaiDeadlines(15, 5, 7, 3);
        assertThat(reduced.responseDays()).isEqualTo(15);
        assertThat(reduced.appealDecisionDays()).isEqualTo(3);
        assertThatThrownBy(() -> new LaiDeadlines(0, 10, 10, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LaiDeadlines(20, 10, 10, -1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("componente ausente do tenant usa o padrao de fabrica (teto legal), nunca valor maior")
    void absentUsesFactory() {
        assertThat(LaiDeadlines.fromTenantValues(null, null, null, null)).isEqualTo(LaiDeadlines.FACTORY);
        var partial = LaiDeadlines.fromTenantValues(10, null, null, null);
        assertThat(partial.responseDays()).isEqualTo(10);
        assertThat(partial.extensionDays()).isEqualTo(10);
        assertThat(partial.appealDays()).isEqualTo(10);
        assertThat(partial.appealDecisionDays()).isEqualTo(5);
    }
}
