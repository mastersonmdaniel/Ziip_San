package br.com.ziipsan.reporting.forecast;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForecastBiPanelsTest {

    @Test
    void paineisCobremReceitaArrecadacaoCaixaCurvas() {
        var panels = ForecastBiPanels.all();

        assertThat(panels).hasSize(5);
        assertThat(panels.stream().map(ForecastBiPanels.Panel::id).collect(Collectors.toSet()))
                .isEqualTo(Set.of("FC-PNL-01", "FC-PNL-02", "FC-PNL-03", "FC-PNL-04", "FC-PNL-05"));
        assertThat(panels).allSatisfy(p -> assertThat(p.datasetView()).startsWith("fc_v_"));
        assertThat(panels.stream().map(ForecastBiPanels.Panel::datasetView).collect(Collectors.toSet()))
                .contains("fc_v_forecast_monthly", "fc_v_forecast_cash",
                        "fc_v_collection_curve", "fc_v_loss_curve");
    }

    @Test
    void painelForaDaViewDaProjecaoERecusado() {
        assertThatThrownBy(() -> new ForecastBiPanels.Panel("X", "Titulo", "forecast_monthly", "desc"))
                .isInstanceOf(ForecastValidationException.class)
                .hasMessageContaining("PANEL_VIEW_INVALID");
    }
}
