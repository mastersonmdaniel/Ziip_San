package br.com.ziipsan.reporting.indicator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validacoes do {@link IndicatorClosing} espelhando os CHECKs e os
 * NOT NULL da V15_2 (correcao D3 da revisao independente): o record nao
 * aceita em memoria o que o banco recusaria — nulos nos campos
 * obrigatorios, formula_version &lt; 1, periodo invertido e reabertura
 * sem motivo/responsavel/instante.
 */
class IndicatorClosingValidationTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 1, 31);
    private static final Instant NOW = Instant.parse("2026-02-10T12:00:00Z");

    private static IndicatorClosing closing(int formulaVersion, LocalDate start, LocalDate end,
                                            IndicatorClosing.Status status,
                                            UUID reopenedBy, Instant reopenedAt, String reopenReason) {
        return new IndicatorClosing(ID, TENANT, "IND-PERDA", start, end, null,
                new BigDecimal("10"), formulaVersion, "{\"produzido\":\"1000\"}",
                Semaphore.GREEN, status, ACTOR, NOW, reopenedBy, reopenedAt, reopenReason);
    }

    @Test
    void validClosedClosingIsAccepted() {
        IndicatorClosing closing = closing(3, START, END, IndicatorClosing.Status.CLOSED,
                null, null, null);
        assertThat(closing.value()).isEqualByComparingTo("10");
        assertThat(closing.formulaVersion()).isEqualTo(3);
        assertThat(closing.status()).isEqualTo(IndicatorClosing.Status.CLOSED);
    }

    @Test
    void closingWithNullIdentifiersIsRefused() {
        assertThatThrownBy(() -> new IndicatorClosing(null, TENANT, "IND-PERDA", START, END, null,
                new BigDecimal("10"), 1, "{}", Semaphore.GREEN, IndicatorClosing.Status.CLOSED,
                ACTOR, NOW, null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_ID_REQUIRED");
        assertThatThrownBy(() -> new IndicatorClosing(ID, null, "IND-PERDA", START, END, null,
                new BigDecimal("10"), 1, "{}", Semaphore.GREEN, IndicatorClosing.Status.CLOSED,
                ACTOR, NOW, null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("TENANT_REQUIRED");
        assertThatThrownBy(() -> new IndicatorClosing(ID, TENANT, " ", START, END, null,
                new BigDecimal("10"), 1, "{}", Semaphore.GREEN, IndicatorClosing.Status.CLOSED,
                ACTOR, NOW, null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_INDICATOR_REQUIRED");
    }

    @Test
    void closingWithNullValueSnapshotSemaphoreStatusOrInstantIsRefused() {
        assertThatThrownBy(() -> new IndicatorClosing(ID, TENANT, "IND-PERDA", START, END, null,
                null, 1, "{}", Semaphore.GREEN, IndicatorClosing.Status.CLOSED,
                ACTOR, NOW, null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_VALUE_REQUIRED");
        assertThatThrownBy(() -> new IndicatorClosing(ID, TENANT, "IND-PERDA", START, END, null,
                new BigDecimal("10"), 1, null, Semaphore.GREEN, IndicatorClosing.Status.CLOSED,
                ACTOR, NOW, null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_VARIABLE_VALUES_REQUIRED");
        assertThatThrownBy(() -> new IndicatorClosing(ID, TENANT, "IND-PERDA", START, END, null,
                new BigDecimal("10"), 1, "{}", null, IndicatorClosing.Status.CLOSED,
                ACTOR, NOW, null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_SEMAPHORE_REQUIRED");
        assertThatThrownBy(() -> new IndicatorClosing(ID, TENANT, "IND-PERDA", START, END, null,
                new BigDecimal("10"), 1, "{}", Semaphore.GREEN, null,
                ACTOR, NOW, null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_STATUS_REQUIRED");
        assertThatThrownBy(() -> new IndicatorClosing(ID, TENANT, "IND-PERDA", START, END, null,
                new BigDecimal("10"), 1, "{}", Semaphore.GREEN, IndicatorClosing.Status.CLOSED,
                ACTOR, null, null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_CLOSED_AT_REQUIRED");
    }

    @Test
    void closingWithNonPositiveFormulaVersionIsRefused() {
        // A sonda da revisao construiu formulaVersion = -3 sem excecao.
        assertThatThrownBy(() -> closing(-3, START, END, IndicatorClosing.Status.CLOSED,
                null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("FORMULA_VERSION_INVALID");
        assertThatThrownBy(() -> closing(0, START, END, IndicatorClosing.Status.CLOSED,
                null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("FORMULA_VERSION_INVALID");
    }

    @Test
    void closingWithMissingOrInvertedPeriodIsRefused() {
        assertThatThrownBy(() -> closing(1, null, END, IndicatorClosing.Status.CLOSED,
                null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_PERIOD_REQUIRED");
        assertThatThrownBy(() -> closing(1, START, null, IndicatorClosing.Status.CLOSED,
                null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_PERIOD_REQUIRED");
        assertThatThrownBy(() -> closing(1, END, START, IndicatorClosing.Status.CLOSED,
                null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_PERIOD_INVALID");
    }

    @Test
    void reopenedClosingWithoutReasonActorOrInstantIsRefused() {
        // CHECK da V15_2: status REOPENED exige motivo, responsavel e instante.
        assertThatThrownBy(() -> closing(1, START, END, IndicatorClosing.Status.REOPENED,
                null, null, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_REOPEN_DATA_REQUIRED");
        assertThatThrownBy(() -> closing(1, START, END, IndicatorClosing.Status.REOPENED,
                ACTOR, NOW, " "))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("CLOSING_REOPEN_DATA_REQUIRED");
        // Com os tres dados, a reabertura e valida — inclusive via reopened():
        IndicatorClosing reopened = closing(1, START, END, IndicatorClosing.Status.CLOSED,
                null, null, null).reopened(ACTOR, NOW, "leitura corrigida");
        assertThat(reopened.status()).isEqualTo(IndicatorClosing.Status.REOPENED);
        assertThat(reopened.reopenReason()).isEqualTo("leitura corrigida");
        assertThat(reopened.reopenedBy()).isEqualTo(ACTOR);
    }
}
