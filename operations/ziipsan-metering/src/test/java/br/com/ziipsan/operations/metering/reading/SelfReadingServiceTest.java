package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.reading.ReadingCycleParameters.PhotoRequiredOn;
import br.com.ziipsan.operations.metering.reading.SelfReadingService.Critique;
import br.com.ziipsan.operations.metering.reading.SelfReadingService.Outcome;
import br.com.ziipsan.operations.metering.reading.SelfReadingService.Verdict;
import br.com.ziipsan.operations.metering.rules.ExpectedReadingRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SelfReadingServiceTest {

    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    /** Faixa esperada 1000..1100 (mostrador de 5 dígitos). */
    private static final ExpectedReadingRange RANGE = new ExpectedReadingRange(1000, 1100, 5);

    @Test
    @DisplayName("autoleitura dentro da faixa é aceita sem crítica")
    void withinRangeAccepted() {
        Outcome outcome = SelfReadingService.evaluate(UNIT, 1050, RANGE, false,
                PhotoRequiredOn.IMPEDIMENT_ONLY);
        assertThat(outcome.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(outcome.critiques()).isEmpty();
    }

    @Test
    @DisplayName("autoleitura fora da faixa sai com crítica (não é aceita em silêncio nem recusada)")
    void outOfRangeGetsCritique() {
        Outcome outcome = SelfReadingService.evaluate(UNIT, 1500, RANGE, false,
                PhotoRequiredOn.IMPEDIMENT_ONLY);
        assertThat(outcome.verdict()).isEqualTo(Verdict.ACCEPTED_WITH_CRITIQUE);
        assertThat(outcome.critiques()).containsExactly(Critique.OUT_OF_EXPECTED_RANGE);
    }

    @Test
    @DisplayName("autoleitura ausente ou negativa é recusada pela crítica")
    void missingOrNegativeRejected() {
        assertThat(SelfReadingService.evaluate(UNIT, null, RANGE, true,
                PhotoRequiredOn.IMPEDIMENT_ONLY).critiques()).containsExactly(Critique.READING_MISSING);
        assertThat(SelfReadingService.evaluate(UNIT, -1, RANGE, true,
                PhotoRequiredOn.IMPEDIMENT_ONLY).verdict()).isEqualTo(Verdict.REJECTED);
    }

    @Test
    @DisplayName("com read.photo_required_on=EVERY_READING, autoleitura sem foto é recusada")
    void photoRequiredWhenParameterSaysEveryReading() {
        Outcome outcome = SelfReadingService.evaluate(UNIT, 1050, RANGE, false,
                PhotoRequiredOn.EVERY_READING);
        assertThat(outcome.verdict()).isEqualTo(Verdict.REJECTED);
        assertThat(outcome.critiques()).containsExactly(Critique.PHOTO_REQUIRED);
        assertThat(SelfReadingService.evaluate(UNIT, 1050, RANGE, true,
                PhotoRequiredOn.EVERY_READING).verdict()).isEqualTo(Verdict.ACCEPTED);
    }
}
