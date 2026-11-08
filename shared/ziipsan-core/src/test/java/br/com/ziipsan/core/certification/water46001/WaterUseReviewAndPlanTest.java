package br.com.ziipsan.core.certification.water46001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaterUseReviewAndPlanTest {

    private static Iso46001Activation act() {
        return Iso46001Activation.enabled("escopo", "ETA-CENTRAL", UUID.randomUUID(), UUID.randomUUID());
    }

    private static OwnUseSource source(double op, double em, double ex) {
        return new OwnUseSource(UUID.randomUUID(), UUID.randomUUID(), 2026, 3, op, em, ex);
    }

    @Test @DisplayName("revisao com base declarada fecha; variacao = (650-1000)/1000 = -35%")
    void reviewWithBaseline() {
        var review = new WaterUseReview(act(), "ETA-CENTRAL",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), source(120, 30, 500),
                "usos acima de 10% do uso proprio do periodo (criterio do tenant)",
                List.of("processo da ETA"), "2025 (ano-base declarado)", 1000.0);
        assertThat(review.hasBaseline()).isTrue();
        assertThat(review.variationVsBaselinePctOrNull()).isEqualTo(-35.0);
        review.close();
        assertThat(review.status()).isEqualTo(WaterUseReview.Status.CLOSED);
    }

    @Test @DisplayName("sem linha de base: [A DEFINIR] — revisao nao fecha e variacao e ausente, nunca inventada")
    void reviewWithoutBaseline() {
        var review = new WaterUseReview(act(), "ETA-CENTRAL",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), source(120, 30, 500),
                "criterio do tenant", List.of(), null, null);
        assertThat(review.hasBaseline()).isFalse();
        assertThat(review.variationVsBaselinePctOrNull()).isNull();
        assertThatThrownBy(review::close)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("[A DEFINIR]");
    }

    @Test @DisplayName("linha de base pela metade (rotulo sem valor) recusa; criterio de significancia e obrigatorio")
    void reviewGuards() {
        assertThatThrownBy(() -> new WaterUseReview(act(), "ETA-CENTRAL",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), source(1, 1, 1),
                "criterio", List.of(), "2025", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("juntos");
        assertThatThrownBy(() -> new WaterUseReview(act(), "ETA-CENTRAL",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), source(1, 1, 1),
                " ", List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("significancia");
        assertThatThrownBy(() -> new WaterUseReview(act(), "ETA-CENTRAL",
                LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 1), source(1, 1, 1),
                "criterio", List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invertido");
    }

    @Test @DisplayName("plano: economia esperada ausente em uma acao torna o total ausente ([A DEFINIR])")
    void planSaving() {
        var plan = new WaterActionPlan(act(), UUID.randomUUID(), "ETA-CENTRAL", "Plano 2026",
                List.of(new WaterActionPlan.ActionItem("Acao um", "operacao",
                                LocalDate.of(2026, 6, 30), 40.0),
                        new WaterActionPlan.ActionItem("Acao dois", "manutencao",
                                LocalDate.of(2026, 9, 30), null)));
        assertThat(plan.totalExpectedSavingM3OrNull()).isNull();
        var full = new WaterActionPlan(act(), UUID.randomUUID(), "ETA-CENTRAL", "Plano 2026",
                List.of(new WaterActionPlan.ActionItem("Acao um", "operacao",
                        LocalDate.of(2026, 6, 30), 40.0),
                        new WaterActionPlan.ActionItem("Acao dois", "manutencao",
                                LocalDate.of(2026, 9, 30), 10.0)));
        assertThat(full.totalExpectedSavingM3OrNull()).isEqualTo(50.0);
        assertThatThrownBy(() -> new WaterActionPlan(act(), null, "ETA-CENTRAL", "Plano",
                List.of(new WaterActionPlan.ActionItem("A", "o", LocalDate.of(2026, 1, 1), null))))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("P3.31");
        assertThatThrownBy(() -> new WaterActionPlan(act(), UUID.randomUUID(), "ETA-CENTRAL", "Plano",
                List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sem nenhuma acao");
    }
}
