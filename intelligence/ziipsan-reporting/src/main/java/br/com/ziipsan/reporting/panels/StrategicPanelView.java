package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.reporting.sinisa.BenchmarkComparison;

import java.util.List;

/**
 * ED-49: o painel de indicadores estrategicos montado — linhas do
 * registro (P3.41), comparacoes de benchmark com o SINISA (ED-48,
 * GES-29) e a secao lida do ERP (ED-62). {@code benchmarkBaseVersion} e a
 * versao da base publica usada ({@code null} quando nao ha base);
 * {@code pendingSections} nomeia cada secao que nao pode ser montada por
 * falta da fonte (base/recorte de benchmark, leitura do ERP ou codigo
 * sem referencia no recorte) — ausencia declarada, nunca mediana de
 * outro recorte nem valor zero no lugar.
 */
public record StrategicPanelView(List<PanelRowView> rows, List<BenchmarkComparison> benchmark,
                                 String benchmarkBaseVersion, List<ErpIndicatorSnapshot> erpIndicators,
                                 List<String> pendingSections) {

    public StrategicPanelView {
        rows = rows == null ? List.of() : List.copyOf(rows);
        benchmark = benchmark == null ? List.of() : List.copyOf(benchmark);
        erpIndicators = erpIndicators == null ? List.of() : List.copyOf(erpIndicators);
        pendingSections = pendingSections == null ? List.of() : List.copyOf(pendingSections);
    }
}
