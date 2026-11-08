package br.com.ziipsan.intelligence.gis.thematic;

import br.com.ziipsan.reporting.registry.ReportColumn;
import br.com.ziipsan.reporting.registry.ReportColumnType;
import br.com.ziipsan.reporting.registry.ReportDefinition;
import br.com.ziipsan.reporting.registry.ReportFilter;
import br.com.ziipsan.reporting.registry.ReportOrigin;

import java.util.List;

/**
 * Os dois relatórios do cadastro da GIS-08 no núcleo da P3.22:
 * extensão de rede (GIS-001, sobre {@code rpt_gis_extensao_rede}) e
 * precisão do cadastro (GIS-002, sobre
 * {@code rpt_gis_precisao_cadastro}). As definições seguem o
 * contrato do núcleo: view {@code rpt_*}, permissão
 * {@code gis.<código>:export} e colunas declaradas — o que não está
 * declarado não vira SQL (ReportQueryBuilder).
 *
 * <p>Registro: relatórios de módulo externo entram por tenant via
 * {@code ExternalReportRegistrar} (a P3.22 persiste a definição com
 * o tenant_id); {@link GisRegistryReportService#registerReports}
 * faz esse registro. Nenhuma coluna é pessoal: extensão e precisão
 * são de ativos (§12, item 1) — por isso {@code personal = false}
 * em todas.
 *
 * <p>Data do relatório de extensão no núcleo: a view é a foto na
 * data corrente (regra de dois tempos do §3.7 com CURRENT_DATE).
 * "Em qualquer data" é o endpoint do GIS
 * ({@link GisRegistryReportService#extensionAt}), que chama a
 * função {@code gis_network_extension_at(date)} — o construtor de
 * consultas do núcleo não expressa a regra
 * {@code installed_on <= data E abandoned_on > data} com nulos.
 */
public final class GisThematicReportDefinitions {

    public static final String EXTENSION_CODE = "GIS-001";
    public static final String PRECISION_CODE = "GIS-002";

    private GisThematicReportDefinitions() {
    }

    public static List<ReportDefinition> definitions() {
        return List.of(extension(), precision());
    }

    public static ReportDefinition extension() {
        return new ReportDefinition(
                EXTENSION_CODE,
                "Extensão de rede por domínio, função, material, DN, década e setor",
                ReportOrigin.EXTERNAL,
                "rpt_gis_extensao_rede",
                "gis.gis-001:export",
                List.of(
                        new ReportColumn("dominio", ReportColumnType.TEXT, false),
                        new ReportColumn("funcao", ReportColumnType.TEXT, false),
                        new ReportColumn("material", ReportColumnType.TEXT, false),
                        new ReportColumn("dn_mm", ReportColumnType.LONG, false),
                        new ReportColumn("decada_inicio", ReportColumnType.LONG, false),
                        new ReportColumn("setor_codigo", ReportColumnType.TEXT, false),
                        new ReportColumn("setor", ReportColumnType.TEXT, false),
                        new ReportColumn("extensao_m", ReportColumnType.DECIMAL, false, true),
                        new ReportColumn("arcos", ReportColumnType.LONG, false)),
                List.of(
                        new ReportFilter("dominio", ReportFilter.Operator.EQ_OR_RANGE),
                        new ReportFilter("funcao", ReportFilter.Operator.EQ_OR_RANGE),
                        new ReportFilter("material", ReportFilter.Operator.EQ_OR_RANGE),
                        new ReportFilter("dn_mm", ReportFilter.Operator.EQ_OR_RANGE),
                        new ReportFilter("decada_inicio", ReportFilter.Operator.EQ_OR_RANGE),
                        new ReportFilter("setor_codigo", ReportFilter.Operator.EQ_OR_RANGE)),
                "dominio",
                ReportDefinition.DEFAULT_MAX_ROWS);
    }

    public static ReportDefinition precision() {
        return new ReportDefinition(
                PRECISION_CODE,
                "Precisão do cadastro por classe, método e grupo",
                ReportOrigin.EXTERNAL,
                "rpt_gis_precisao_cadastro",
                "gis.gis-002:export",
                List.of(
                        new ReportColumn("classe", ReportColumnType.TEXT, false),
                        new ReportColumn("metodo_captura", ReportColumnType.TEXT, false),
                        new ReportColumn("grupo_precisao", ReportColumnType.TEXT, false),
                        new ReportColumn("dominio", ReportColumnType.TEXT, false),
                        new ReportColumn("feicoes", ReportColumnType.LONG, false)),
                List.of(
                        new ReportFilter("classe", ReportFilter.Operator.EQ_OR_RANGE),
                        new ReportFilter("metodo_captura", ReportFilter.Operator.EQ_OR_RANGE),
                        new ReportFilter("grupo_precisao", ReportFilter.Operator.EQ_OR_RANGE),
                        new ReportFilter("dominio", ReportFilter.Operator.EQ_OR_RANGE)),
                "classe",
                ReportDefinition.DEFAULT_MAX_ROWS);
    }
}
