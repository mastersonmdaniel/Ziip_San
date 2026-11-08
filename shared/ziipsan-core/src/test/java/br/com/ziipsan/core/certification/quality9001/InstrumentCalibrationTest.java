package br.com.ziipsan.core.certification.quality9001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aceite da ED-69: calibração de instrumentos com validade e bloqueio. */
class InstrumentCalibrationTest {

    private static Iso9001Activation on() {
        return Iso9001Activation.enabled("2026", Iso9001Activation.EditionUse.TARGET,
                "escopo", UUID.randomUUID(), UUID.randomUUID());
    }

    private static InstrumentCalibration calibration() {
        return InstrumentCalibration.register(on(), "SYN-INST-1", "instrumento sintético",
                LocalDate.of(2026, 1, 10), LocalDate.of(2027, 1, 10), "CERT-SYN-1",
                UUID.randomUUID());
    }

    @Test
    @DisplayName("dentro da validade (inclusive no último dia), o instrumento é utilizável")
    void validInstrumentIsUsable() {
        InstrumentCalibration c = calibration();
        assertThat(c.validOn(LocalDate.of(2026, 6, 1))).isTrue();
        assertThat(c.validOn(LocalDate.of(2027, 1, 10))).isTrue();
        assertThat(c.blockedOn(LocalDate.of(2027, 1, 10))).isFalse();
        assertThatCode(() -> c.requireUsableOn(LocalDate.of(2027, 1, 10)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("calibração vencida bloqueia: no dia seguinte à validade o uso é recusado")
    void expiredCalibrationBlocksUse() {
        InstrumentCalibration c = calibration();
        assertThat(c.blockedOn(LocalDate.of(2027, 1, 11))).isTrue();
        assertThatThrownBy(() -> c.requireUsableOn(LocalDate.of(2027, 1, 11)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bloqueado");
    }

    @Test
    @DisplayName("uso antes da data da calibração também está bloqueado")
    void useBeforeCalibrationIsBlocked() {
        assertThat(calibration().blockedOn(LocalDate.of(2026, 1, 9))).isTrue();
    }

    @Test
    @DisplayName("validade anterior à calibração é incoerente e recusada no registro")
    void validityBeforeCalibrationRefused() {
        assertThatThrownBy(() -> InstrumentCalibration.register(on(), "I1", "inst",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), "CERT", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem referência de certificado, o registro é recusado")
    void certificateRefIsRequired() {
        assertThatThrownBy(() -> InstrumentCalibration.register(on(), "I1", "inst",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), " ", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("com a norma desligada, calibração do pacote não nasce")
    void disabledBlocksRegistration() {
        assertThatThrownBy(() -> InstrumentCalibration.register(Iso9001Activation.disabled(),
                "I1", "inst", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), "CERT",
                UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }
}
