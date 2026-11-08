package br.com.ziipsan.intelligence.gis.drain;

import java.util.List;

/**
 * Relatórios e temático da GIS-13 sobre o cadastro de drenagem:
 *
 * <ul>
 *   <li><b>GIS-003</b> — relatório do cadastro de drenagem (linha
 *       GIS-13 do §13.2: "relatório do cadastro de drenagem com
 *       extensão por material e estado"): extensão (m) e contagem
 *       por material e por estado de conservação, sobre a view
 *       {@code rpt_gis_drenagem_cadastro} do SQL pendente. É o
 *       relatório que a NR 12/2025 pede para a ERI (§11:
 *       "relatório para a ERI"), com material e estado de
 *       conservação de cada trecho;</li>
 *   <li><b>GIS-004</b> — relatório de indicadores operacionais da
 *       drenagem (OPE-32): uma linha por mês, sobre a view
 *       {@code rpt_gis_drenagem_indicadores};</li>
 *   <li><b>temático de conservação</b>: definição fechada do
 *       temático sobre as feições de drenagem pelo campo
 *       {@code condition_rating} (GOOD/FAIR/POOR/CRITICAL/UNKNOWN),
 *       na forma que a GIS-08 (P3.27) registra em
 *       {@code gis_thematic_map} — fonte {@code DRAIN_CONDITION}
 *       sobre a view {@code gis_thematic_drain_condition} do SQL
 *       pendente. As CORES são tokens {@code map-*} da gis_style
 *       (fonte única da P1.30/GIS-06, ADR-0017): esta definição
 *       nomeia as classes e a ordem; nenhum hex é inventado aqui.</li>
 * </ul>
 *
 * <p>As definições seguem o contrato do núcleo de relatórios da
 * P3.22 (view {@code rpt_*}, colunas declaradas, permissão de
 * exportação) no mesmo espírito de {@code GisThematicReportDefinitions}
 * da GIS-08; o registro no núcleo (ExternalReportRegistrar) é fiação
 * de integração, como nas vizinhas — por isso as definições são
 * registros próprios desta classe, sem depender do classpath do
 * reporting para compilar/testar isoladas.
 */
public final class GisDrainReports {

    public static final String CADASTRO_CODE = "GIS-003";
    public static final String INDICADORES_CODE = "GIS-004";

    /** Uma coluna declarada do relatório (contrato da P3.22). */
    public record ReportColumnDef(String name, String type, boolean personal,
                                  boolean summable) {
    }

    /** Uma definição de relatório do cadastro de drenagem. */
    public record DrainReportDefinition(String code, String title, String viewName,
                                        String exportPermission,
                                        List<ReportColumnDef> columns) {
    }

    /** Definição do temático de conservação (GIS-08/GIS-13). */
    public record ConservationThematic(String sourceKind, String viewName,
                                       String categoryField,
                                       List<String> categoriesInOrder,
                                       boolean personal, boolean publishable) {
    }

    private GisDrainReports() {
    }

    public static List<DrainReportDefinition> definitions() {
        return List.of(cadastro(), indicadores());
    }

    /** GIS-003: extensão por material e estado de conservação. */
    public static DrainReportDefinition cadastro() {
        return new DrainReportDefinition(
                CADASTRO_CODE,
                "Cadastro de drenagem: extensão por material e estado de conservação",
                "rpt_gis_drenagem_cadastro",
                "gis.gis-003:export",
                List.of(
                        new ReportColumnDef("material", "TEXT", false, false),
                        new ReportColumnDef("estado_conservacao", "TEXT", false, false),
                        new ReportColumnDef("tipo", "TEXT", false, false),
                        new ReportColumnDef("extensao_m", "DECIMAL", false, true),
                        new ReportColumnDef("arcos", "LONG", false, false),
                        new ReportColumnDef("obstrucao_media_pct", "DECIMAL", false, false)));
    }

    /** GIS-004: indicadores operacionais da drenagem (OPE-32), por mês. */
    public static DrainReportDefinition indicadores() {
        return new DrainReportDefinition(
                INDICADORES_CODE,
                "Indicadores operacionais da drenagem (OPE-32)",
                "rpt_gis_drenagem_indicadores",
                "gis.gis-004:export",
                List.of(
                        new ReportColumnDef("mes", "TEXT", false, false),
                        new ReportColumnDef("ocorrencias_alagamento", "LONG", false, false),
                        new ReportColumnDef("chuva_media_periodo_alagamento_mm",
                                "DECIMAL", false, false),
                        new ReportColumnDef("inspecoes", "LONG", false, false),
                        new ReportColumnDef("limpezas_devidas", "LONG", false, false),
                        new ReportColumnDef("limpezas_concluidas", "LONG", false, false),
                        new ReportColumnDef("pct_pobre_ou_critico", "DECIMAL", false, false),
                        new ReportColumnDef("obstrucao_media_pct", "DECIMAL", false, false)));
    }

    /**
     * Temático de conservação: classes na ordem de severidade do
     * §3.5 (GOOD → CRITICAL, UNKNOWN por último). Não é dado pessoal
     * (é ativo de rede, §12 da GIS-08) e é publicável como os demais
     * temáticos de rede da GIS-08.
     */
    public static ConservationThematic conservationThematic() {
        return new ConservationThematic(
                "DRAIN_CONDITION",
                "gis_thematic_drain_condition",
                "condition_rating",
                List.of("GOOD", "FAIR", "POOR", "CRITICAL", "UNKNOWN"),
                false, true);
    }
}
