package br.com.ziipsan.core.certification.quality9001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aceite da ED-69: saída não conforme classificada na ocorrência da P2.25. */
class NonconformingOutputTest {

    private static Iso9001Activation on() {
        return Iso9001Activation.enabled("2026", Iso9001Activation.EditionUse.TARGET,
                "escopo", UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    @DisplayName("classificação nasce ligada à ocorrência e com a disposição decidida")
    void classificationLinksToOccurrence() {
        UUID occurrence = UUID.randomUUID();
        NonconformingOutput out = NonconformingOutput.classify(on(), occurrence, "SYN-PROC-1",
                "desvio sintético", NonconformingOutput.Disposition.SEGREGATION,
                UUID.randomUUID(), LocalDate.of(2026, 3, 1));
        assertThat(out.occurrenceId()).isEqualTo(occurrence);
        assertThat(out.contained()).isTrue();
    }

    @Test
    @DisplayName("concessão não é contenção: a saída segue aceita sob concessão registrada")
    void concessionIsNotContainment() {
        NonconformingOutput out = NonconformingOutput.classify(on(), UUID.randomUUID(), "P1",
                "desvio", NonconformingOutput.Disposition.CONCESSION, UUID.randomUUID(),
                LocalDate.of(2026, 3, 1));
        assertThat(out.contained()).isFalse();
    }

    @Test
    @DisplayName("sem ocorrência da P2.25, não há classificação: o vínculo é a essência do registro")
    void occurrenceIsRequired() {
        assertThatThrownBy(() -> NonconformingOutput.classify(on(), null, "P1", "desvio",
                NonconformingOutput.Disposition.REWORK, UUID.randomUUID(),
                LocalDate.of(2026, 3, 1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("sem descrição do desvio ou sem disposição, a classificação é recusada")
    void descriptionAndDispositionAreRequired() {
        assertThatThrownBy(() -> NonconformingOutput.classify(on(), UUID.randomUUID(), "P1", " ",
                NonconformingOutput.Disposition.REWORK, UUID.randomUUID(),
                LocalDate.of(2026, 3, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NonconformingOutput.classify(on(), UUID.randomUUID(), "P1",
                "desvio", null, UUID.randomUUID(), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("com a norma desligada, a classificação do pacote não nasce (a ocorrência da P2.25 segue livre)")
    void disabledBlocksClassification() {
        assertThatThrownBy(() -> NonconformingOutput.classify(Iso9001Activation.disabled(),
                UUID.randomUUID(), "P1", "desvio", NonconformingOutput.Disposition.SCRAP,
                UUID.randomUUID(), LocalDate.of(2026, 3, 1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
