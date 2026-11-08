package br.com.ziipsan.commercial.billing.crisis;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrisisParametersTest {

    @Test @DisplayName("crisis.rotation_enabled / tariff_flag_enabled: booleanos; ausencia e erro")
    void flags() {
        assertThat(CrisisParameters.rotationEnabled("true")).isTrue();
        assertThat(CrisisParameters.tariffFlagEnabled("false")).isFalse();
        assertThatThrownBy(() -> CrisisParameters.rotationEnabled(null))
                .isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> CrisisParameters.tariffFlagEnabled("\"true\""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("cloro: 0,5 aceito, ampliar aceito, reduzir recusado, ausencia e erro")
    void chlorine() {
        assertThat(CrisisParameters.tankerMinChlorineMgL("0.5")).isEqualByComparingTo(new BigDecimal("0.5"));
        assertThat(CrisisParameters.tankerMinChlorineMgL("0.8")).isEqualByComparingTo(new BigDecimal("0.8"));
        assertThatThrownBy(() -> CrisisParameters.tankerMinChlorineMgL("0.4"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("0,5");
        assertThatThrownBy(() -> CrisisParameters.tankerMinChlorineMgL(null))
                .isInstanceOf(MissingParameterException.class);
    }

    @Test @DisplayName("connection.temporary_max_days: sem fabrica - ausencia e erro; positivo lido")
    void temporaryMaxDays() {
        assertThat(CrisisParameters.temporaryMaxDays("90")).isEqualTo(90);
        assertThatThrownBy(() -> CrisisParameters.temporaryMaxDays("0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CrisisParameters.temporaryMaxDays(null))
                .isInstanceOf(MissingParameterException.class);
    }

    @Test @DisplayName("event.* (classe b): booleano por efeito; chave fora da familia recusada")
    void eventEffects() {
        assertThat(CrisisParameters.eventEffect("true", "event.suspend_cut")).isTrue();
        assertThatThrownBy(() -> CrisisParameters.eventEffect("true", "crisis.rotation_enabled"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("tariff.contingency_enabled (classe b, com aceite): leitura tipada")
    void contingencyEnabled() {
        assertThat(CrisisParameters.contingencyEnabled("true")).isTrue();
        assertThatThrownBy(() -> CrisisParameters.contingencyEnabled(null))
                .isInstanceOf(MissingParameterException.class);
    }
}
