package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.reading.PhotoAndImpedimentRule.Verdict;
import br.com.ziipsan.operations.metering.reading.ReadingCycleParameters.PhotoRequiredOn;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoAndImpedimentRuleTest {

    @Test
    @DisplayName("IMPEDIMENT_ONLY: leitura comum sem foto é aceita; impedimento sem foto é recusado")
    void photoOnlyOnImpediment() {
        assertThat(PhotoAndImpedimentRule.evaluate(true, false, false, 0,
                PhotoRequiredOn.IMPEDIMENT_ONLY, 2)).isEqualTo(Verdict.ACCEPTED);
        assertThat(PhotoAndImpedimentRule.evaluate(false, true, false, 2,
                PhotoRequiredOn.IMPEDIMENT_ONLY, 2)).isEqualTo(Verdict.REJECTED_PHOTO_REQUIRED);
    }

    @Test
    @DisplayName("EVERY_READING: toda leitura sem foto é recusada; com foto é aceita")
    void photoOnEveryReading() {
        assertThat(PhotoAndImpedimentRule.evaluate(true, false, false, 0,
                PhotoRequiredOn.EVERY_READING, 1)).isEqualTo(Verdict.REJECTED_PHOTO_REQUIRED);
        assertThat(PhotoAndImpedimentRule.evaluate(true, false, true, 0,
                PhotoRequiredOn.EVERY_READING, 1)).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    @DisplayName("visita sem leitura e sem impedimento é entrada incoerente: recusada, não tratada como leitura")
    void visitNeitherReadingNorImpedimentRefused() {
        assertThatThrownBy(() -> PhotoAndImpedimentRule.evaluate(false, false, false, 0,
                PhotoRequiredOn.EVERY_READING, 1))
                .isInstanceOf(MeteringInputException.class)
                .hasMessageContaining("sem leitura e sem impedimento");
        // mesmo com foto e tentativas, a visita vazia continua incoerente
        assertThatThrownBy(() -> PhotoAndImpedimentRule.evaluate(false, false, true, 3,
                PhotoRequiredOn.IMPEDIMENT_ONLY, 1))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("impedimento só é aceito depois do número de tentativas de read.impediment_attempts")
    void impedimentNeedsAttempts() {
        // parâmetro = 3 tentativas: com 2, o impedimento volta para nova tentativa
        assertThat(PhotoAndImpedimentRule.evaluate(false, true, true, 2,
                PhotoRequiredOn.IMPEDIMENT_ONLY, 3)).isEqualTo(Verdict.REJECTED_IMPEDIMENT_ATTEMPTS);
        // com as 3 tentativas feitas (e a foto do impedimento), o impedimento é aceito
        assertThat(PhotoAndImpedimentRule.evaluate(false, true, true, 3,
                PhotoRequiredOn.IMPEDIMENT_ONLY, 3)).isEqualTo(Verdict.ACCEPTED);
        // tentativas a mais também aceitam
        assertThat(PhotoAndImpedimentRule.evaluate(false, true, true, 5,
                PhotoRequiredOn.IMPEDIMENT_ONLY, 3)).isEqualTo(Verdict.ACCEPTED);
    }
}
