package br.com.ziipsan.core.certification.water46001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes NOVOS da correcao da REVISAO-ED-81 (corretor, sessao
 * ED-81-CORR-1): D4 (amarra fonte x periodo) e D5 (finitude e
 * faixa de ano). Nenhum teste pre-existente foi alterado.
 */
class Ed81CorrectionsTest {

    private static Iso46001Activation act() {
        return Iso46001Activation.enabled("escopo", "ETA-CENTRAL", UUID.randomUUID(), UUID.randomUUID());
    }

    private static OwnUseSource source(int year, int month, double op, double em, double ex) {
        return new OwnUseSource(UUID.randomUUID(), UUID.randomUUID(), year, month, op, em, ex);
    }

    @Test @DisplayName("D4: fonte de dezembro NAO fecha revisao de marco (ano/mes fora do periodo recusa)")
    void sourceMonthOutsideReviewPeriodRefuses() {
        var december = source(2025, 12, 120, 30, 500);
        assertThatThrownBy(() -> new WaterUseReview(act(), "ETA-CENTRAL",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), december,
                "criterio do tenant", List.of(), "2025 (ano-base)", 1000.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do periodo");
        var april = source(2026, 4, 120, 30, 500);
        assertThatThrownBy(() -> new WaterUseReview(act(), "ETA-CENTRAL",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), april,
                "criterio do tenant", List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do periodo");
    }

    @Test @DisplayName("D4: fonte de fevereiro DENTRO de revisao trimestral jan-mar e aceita")
    void sourceMonthInsideSpanningPeriodAccepted() {
        var review = new WaterUseReview(act(), "ETA-CENTRAL",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), source(2026, 2, 120, 30, 500),
                "criterio do tenant", List.of(), "2025 (ano-base)", 1000.0);
        assertThat(review.source().periodMonth()).isEqualTo(2);
        review.close();
        assertThat(review.status()).isEqualTo(WaterUseReview.Status.CLOSED);
    }

    @Test @DisplayName("D5: componente infinito (+Inf) da fonte recusa — total nunca infinito")
    void infiniteComponentsRefuse() {
        assertThatThrownBy(() -> source(2026, 3, Double.POSITIVE_INFINITY, 30, 500))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("operacionais");
        assertThatThrownBy(() -> source(2026, 3, 120, Double.POSITIVE_INFINITY, 500))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("emergenciais");
        assertThatThrownBy(() -> source(2026, 3, 120, 30, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exportada");
    }

    @Test @DisplayName("D5: ano do periodo fora da faixa 1900..2100 recusa")
    void periodYearOutOfRangeRefuses() {
        assertThatThrownBy(() -> source(1899, 3, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1900..2100");
        assertThatThrownBy(() -> source(2101, 3, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1900..2100");
        assertThat(source(2026, 3, 1, 1, 1).periodYear()).isEqualTo(2026);
    }

    @Test @DisplayName("D5: linha de base infinita e economia esperada infinita recusam")
    void infiniteBaselineAndSavingRefuse() {
        assertThatThrownBy(() -> new WaterUseReview(act(), "ETA-CENTRAL",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), source(2026, 3, 1, 1, 1),
                "criterio do tenant", List.of(), "2025", Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("linha de base");
        assertThatThrownBy(() -> new WaterActionPlan.ActionItem("Acao", "operacao",
                LocalDate.of(2026, 6, 30), Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("economia esperada");
    }
}
