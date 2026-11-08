package br.com.ziipsan.reporting.quality;

import br.com.ziipsan.reporting.registry.ExternalReportRegistrar;
import br.com.ziipsan.reporting.registry.ReportColumn;
import br.com.ziipsan.reporting.registry.ReportColumnType;
import br.com.ziipsan.reporting.registry.ReportDefinition;
import br.com.ziipsan.reporting.registry.ReportFilter;
import br.com.ziipsan.reporting.registry.ReportOrigin;

import java.util.List;
import java.util.UUID;

/**
 * Relatorios da qualidade cadastral continua (P3.32) sobre o nucleo da
 * P3.22: pontuacao por rota, pontuacao por setor e a tendencia do tenant
 * (pontuacao, completude e achados por varredura).
 *
 * <p>Registro pelo mecanismo externo da P3.22 ({@link ExternalReportRegistrar},
 * origem EXTERNAL, por tenant): a P3.32 nao toca o catalogo de fabrica da
 * P3.22. As views {@code rpt_cadastral_quality_*} leem
 * {@code cadastral_quality_scores} juntada a {@code cadastral_quality_runs}
 * e sao criadas pela migration da P3.32 (rascunho na entrega, numeracao da
 * sequencia global pos-P3.12 — ver relatorio da tarefa).
 *
 * <p>Nenhuma coluna e dado pessoal: os relatorios sao agregados por grupo,
 * sem titular, documento ou endereco.
 */
public final class CadastralQualityReports {

    public static final String ROUTE_CODE = "QUAL-001";
    public static final String SECTOR_CODE = "QUAL-002";
    public static final String TREND_CODE = "QUAL-003";

    private CadastralQualityReports() {
    }

    /** Pontuacao e completude por rota de leitura e varredura. */
    public static ReportDefinition byRoute() {
        return new ReportDefinition(ROUTE_CODE,
                "Qualidade cadastral: pontuacao por rota",
                ReportOrigin.EXTERNAL,
                "rpt_cadastral_quality_route",
                "reporting.qual-001:export",
                List.of(
                        col("referencia", ReportColumnType.DATE),
                        col("rota_id", ReportColumnType.TEXT),
                        col("total_unidades", ReportColumnType.LONG),
                        col("unidades_com_achados", ReportColumnType.LONG),
                        col("achados", ReportColumnType.LONG),
                        col("pontos_penalidade", ReportColumnType.LONG),
                        col("pontuacao", ReportColumnType.DECIMAL),
                        col("completude", ReportColumnType.DECIMAL)),
                List.of(filter("referencia")),
                "referencia",
                ReportDefinition.DEFAULT_MAX_ROWS);
    }

    /** Pontuacao e completude por setor comercial e varredura. */
    public static ReportDefinition bySector() {
        return new ReportDefinition(SECTOR_CODE,
                "Qualidade cadastral: pontuacao por setor",
                ReportOrigin.EXTERNAL,
                "rpt_cadastral_quality_sector",
                "reporting.qual-002:export",
                List.of(
                        col("referencia", ReportColumnType.DATE),
                        col("setor_id", ReportColumnType.TEXT),
                        col("total_unidades", ReportColumnType.LONG),
                        col("unidades_com_achados", ReportColumnType.LONG),
                        col("achados", ReportColumnType.LONG),
                        col("pontos_penalidade", ReportColumnType.LONG),
                        col("pontuacao", ReportColumnType.DECIMAL),
                        col("completude", ReportColumnType.DECIMAL)),
                List.of(filter("referencia")),
                "referencia",
                ReportDefinition.DEFAULT_MAX_ROWS);
    }

    /** Tendencia do tenant: uma linha por varredura, com pontuacao e completude. */
    public static ReportDefinition trend() {
        return new ReportDefinition(TREND_CODE,
                "Qualidade cadastral: tendencia do tenant",
                ReportOrigin.EXTERNAL,
                "rpt_cadastral_quality_trend",
                "reporting.qual-003:export",
                List.of(
                        col("referencia", ReportColumnType.DATE),
                        col("unidades_avaliadas", ReportColumnType.LONG),
                        col("achados", ReportColumnType.LONG),
                        col("pontos_penalidade", ReportColumnType.LONG),
                        col("pontuacao", ReportColumnType.DECIMAL),
                        col("completude", ReportColumnType.DECIMAL)),
                List.of(filter("referencia")),
                "referencia",
                ReportDefinition.DEFAULT_MAX_ROWS);
    }

    /** As tres definicoes, na ordem de registro. */
    public static List<ReportDefinition> all() {
        return List.of(byRoute(), bySector(), trend());
    }

    /** Registra as tres no nucleo da P3.22 para o tenant (origem EXTERNAL). */
    public static void registerAll(ExternalReportRegistrar registrar, UUID tenantId) {
        if (registrar == null || tenantId == null) {
            throw new IllegalArgumentException("registrador e tenant sao obrigatorios");
        }
        for (ReportDefinition definition : all()) {
            registrar.register(tenantId, definition);
        }
    }

    private static ReportColumn col(String name, ReportColumnType type) {
        return new ReportColumn(name, type, false);
    }

    private static ReportFilter filter(String column) {
        return new ReportFilter(column, ReportFilter.Operator.EQ_OR_RANGE);
    }
}
