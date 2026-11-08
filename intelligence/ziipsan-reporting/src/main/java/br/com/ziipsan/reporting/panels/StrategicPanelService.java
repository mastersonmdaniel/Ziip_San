package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import br.com.ziipsan.reporting.sinisa.BenchmarkBase;
import br.com.ziipsan.reporting.sinisa.BenchmarkComparison;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ED-49: o painel de indicadores estrategicos (POC-81). As linhas sao as
 * do {@link StandardPanelService} para {@link PanelKind#ESTRATEGICO}
 * (registro da P3.41, sem formula repetida); sobre elas, o benchmark com
 * o SINISA e o da ED-48 (GES-29): a comparacao e a de
 * {@link BenchmarkBase#compare} — mediana, diferenca e posicao da base
 * publica versionada, sem juizo nem limiar inventado — e a secao de
 * compras/RH/orcamento e a leitura do ERP da ED-62 pela
 * {@link ErpIndicatorsPort}, transportada verbatim.
 *
 * <p>Ausencias sao pendencias NOMEADAS em {@code pendingSections}, nunca
 * substituidas: sem base importada ou sem recorte declarado do tenant,
 * nao ha benchmark ({@code benchmark_sinisa}); codigo sem referencia no
 * recorte exato entra nomeado ({@code benchmark_sinisa:<codigo>}) e NAO
 * recebe a mediana de outro recorte; sem adaptador da ED-62 ou sem
 * leitura na competencia, a secao do ERP e
 * {@code indicadores_erp_ed62}.
 */
public final class StrategicPanelService {

    public static final String PENDING_BENCHMARK = "benchmark_sinisa";
    public static final String PENDING_ERP = "indicadores_erp_ed62";

    private final StandardPanelService panels;
    private final ErpIndicatorsPort erpPort;

    /** @param erpPort adaptador da ED-62; {@code null} = secao do ERP pendente declarada. */
    public StrategicPanelService(StandardPanelService panels, ErpIndicatorsPort erpPort) {
        this.panels = panels;
        this.erpPort = erpPort;
    }

    public StrategicPanelView build(UUID tenantId, List<PanelCellRequest> requests,
                                    BenchmarkBase benchmarkBase, BenchmarkSlice slice,
                                    LocalDate competence) {
        if (competence == null) {
            throw new IndicatorValidationException("STRATEGIC_COMPETENCE_REQUIRED",
                    "painel estrategico sem a competencia da leitura do ERP (ED-62)");
        }
        List<PanelRowView> rows = panels.buildPanel(tenantId, PanelKind.ESTRATEGICO, requests);
        List<String> pending = new ArrayList<>();

        List<BenchmarkComparison> benchmark = new ArrayList<>();
        String baseVersion = null;
        if (benchmarkBase == null || slice == null) {
            pending.add(PENDING_BENCHMARK);
        } else {
            baseVersion = benchmarkBase.baseVersion();
            for (PanelRowView row : rows) {
                var comparison = benchmarkBase.compare(row.value(), row.indicatorCode(),
                        slice.sizeClass(), slice.region(), slice.legalNature());
                if (comparison.isPresent()) {
                    benchmark.add(comparison.get());
                } else {
                    pending.add(PENDING_BENCHMARK + ":" + row.indicatorCode());
                }
            }
        }

        List<ErpIndicatorSnapshot> erp = List.of();
        if (erpPort == null) {
            pending.add(PENDING_ERP);
        } else {
            List<ErpIndicatorSnapshot> read = erpPort.strategicIndicators(tenantId, competence);
            if (read == null || read.isEmpty()) {
                pending.add(PENDING_ERP);
            } else {
                erp = List.copyOf(read);
            }
        }

        return new StrategicPanelView(rows, benchmark, baseVersion, erp, pending);
    }
}
