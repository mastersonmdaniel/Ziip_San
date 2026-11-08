package br.com.ziipsan.reporting.forecast;

import java.util.List;

/**
 * Paineis da projecao no BI da ED-47 (ED-100): definicao declarativa dos
 * paineis e dos conjuntos de dados (views {@code fc_v_*} do SQL pendente
 * desta entrega). Os paineis leem SOMENTE as views, na replica, sob a
 * guarda da ED-47 ({@code ReplicaOnlyGuard} e role por tenant) — esta
 * classe nao abre conexao nem publica nada no Superset: a publicacao e
 * fiacao de integracao sobre estas definicoes.
 */
public final class ForecastBiPanels {

    /** Painel: identificador estavel, titulo, view de dados e o que mostra. */
    public record Panel(String id, String title, String datasetView, String description) {
        public Panel {
            if (id == null || id.isBlank() || title == null || title.isBlank()) {
                throw new ForecastValidationException("PANEL_INVALID", "painel sem id ou titulo");
            }
            if (datasetView == null || !datasetView.startsWith("fc_v_")) {
                throw new ForecastValidationException("PANEL_VIEW_INVALID",
                        "painel " + id + " deve ler uma view fc_v_* da projecao: " + datasetView);
            }
        }
    }

    private ForecastBiPanels() {
    }

    public static List<Panel> all() {
        return List.of(
                new Panel("FC-PNL-01", "Receita projetada por cenario", "fc_v_forecast_monthly",
                        "Receita bruta, deducoes (tarifa social e cashback) e receita liquida por mes e cenario"),
                new Panel("FC-PNL-02", "Arrecadacao projetada por cenario", "fc_v_forecast_monthly",
                        "Arrecadacao do faturamento novo e do estoque inicial por mes e cenario"),
                new Panel("FC-PNL-03", "Caixa projetado por cenario", "fc_v_forecast_cash",
                        "Saldo de caixa projetado acumulado por mes e cenario"),
                new Panel("FC-PNL-04", "Curva de recebimento por faixa de atraso", "fc_v_collection_curve",
                        "Faturado, recebido, taxa de recuperacao e taxa de perda por faixa de atraso"),
                new Panel("FC-PNL-05", "Curva de perda (EXPECTED_LOSS)", "fc_v_loss_curve",
                        "Exposicao e perda esperada por faixa, a curva consumida pela provisao da ED-32"));
    }
}
