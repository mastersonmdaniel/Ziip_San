package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.reading.ReadingCycleParameters.PhotoRequiredOn;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadingCycleParametersTest {

    @Test
    @DisplayName("read.photo_required_on: as duas opções do aceite parseiam pelo Json do core")
    void parsePhotoRequiredOnBothOptions() {
        assertThat(ReadingCycleParameters.parsePhotoRequiredOn("\"IMPEDIMENT_ONLY\""))
                .isEqualTo(PhotoRequiredOn.IMPEDIMENT_ONLY);
        assertThat(ReadingCycleParameters.parsePhotoRequiredOn("\"EVERY_READING\""))
                .isEqualTo(PhotoRequiredOn.EVERY_READING);
    }

    @Test
    @DisplayName("read.photo_required_on ausente ou fora do catálogo é erro explícito, nunca padrão silencioso")
    void parsePhotoRequiredOnRejectsMissingAndUnknown() {
        assertThatThrownBy(() -> ReadingCycleParameters.parsePhotoRequiredOn(null))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> ReadingCycleParameters.parsePhotoRequiredOn("\"SOMETIMES\""))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> ReadingCycleParameters.parsePhotoRequiredOn("42"))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("read.resample_pct: 0 e 100 são válidos; fora de 0..100 e ausente são recusados")
    void resamplePctDomain() {
        assertThat(ReadingCycleParameters.requireValidResamplePct(new BigDecimal("0"))).isEqualByComparingTo("0");
        assertThat(ReadingCycleParameters.requireValidResamplePct(new BigDecimal("100"))).isEqualByComparingTo("100");
        assertThatThrownBy(() -> ReadingCycleParameters.requireValidResamplePct(new BigDecimal("100.01")))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> ReadingCycleParameters.requireValidResamplePct(new BigDecimal("-1")))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> ReadingCycleParameters.requireValidResamplePct(null))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("read.impediment_attempts: mínimo 1; zero é recusado")
    void impedimentAttemptsDomain() {
        assertThat(ReadingCycleParameters.requireValidImpedimentAttempts(1)).isEqualTo(1);
        assertThatThrownBy(() -> ReadingCycleParameters.requireValidImpedimentAttempts(0))
                .isInstanceOf(MeteringInputException.class);
    }
}
