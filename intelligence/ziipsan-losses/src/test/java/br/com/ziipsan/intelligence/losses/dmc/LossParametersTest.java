package br.com.ziipsan.intelligence.losses.dmc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import br.com.ziipsan.core.parameters.MissingParameterException;
import java.time.LocalTime;

class LossParametersTest {
    @Test @DisplayName("janela: padrao do aceite 02:00-04:00; formato invalido recusa")
    void nightWindow() {
        LossParameters.NightWindow w = LossParameters.nightWindow(null);
        assertThat(w.start()).isEqualTo(LocalTime.of(2, 0));
        assertThat(w.end()).isEqualTo(LocalTime.of(4, 0));
        assertThat(w.hours()).isEqualTo(2.0);
        assertThat(w.contains(LocalTime.of(3, 30))).isTrue();
        assertThat(w.contains(LocalTime.of(4, 0))).isFalse();
        assertThat(LossParameters.nightWindow("01:00-05:00").hours()).isEqualTo(4.0);
        assertThatThrownBy(() -> LossParameters.nightWindow("4h")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LossParameters.nightWindow("04:00-02:00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("uso legitimo noturno: sem padrao de norma — ausente e erro explicito")
    void legitHasNoDefault() {
        assertThatThrownBy(() -> LossParameters.legitNightUseLPerConnH(null))
                .isInstanceOf(MissingParameterException.class);
        assertThat(LossParameters.legitNightUseLPerConnH(1.7)).isEqualTo(1.7);
        assertThat(LossParameters.legitNightUseLPerConnH(0.0)).isEqualTo(0.0);
        assertThatThrownBy(() -> LossParameters.legitNightUseLPerConnH(-1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("meta do DMC: ausente = vazia (NO_TARGET), nunca numero presumido")
    void targetOptional() {
        assertThat(LossParameters.dmcTargetLPerConnDay(null)).isEmpty();
        assertThat(LossParameters.dmcTargetLPerConnDay(120.0)).contains(120.0);
        assertThatThrownBy(() -> LossParameters.dmcTargetLPerConnDay(0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("metodo do nao faturado: sem padrao de fabrica; desconhecido recusa")
    void unbilledMethod() {
        assertThatThrownBy(() -> LossParameters.unbilledEstimateMethod(null))
                .isInstanceOf(MissingParameterException.class);
        assertThat(LossParameters.unbilledEstimateMethod("FLOW_TIME"))
                .isEqualTo(LossParameters.UnbilledEstimateMethod.FLOW_TIME);
        assertThatThrownBy(() -> LossParameters.unbilledEstimateMethod("CHUTE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
