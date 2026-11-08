package br.com.ziipsan.reporting.recadastramento;

import br.com.ziipsan.reporting.registry.ExternalReportRegistrar;
import br.com.ziipsan.reporting.registry.ReportColumn;
import br.com.ziipsan.reporting.registry.ReportColumnType;
import br.com.ziipsan.reporting.registry.ReportDefinition;
import br.com.ziipsan.reporting.registry.ReportFilter;
import br.com.ziipsan.reporting.registry.ReportOrigin;

import java.util.List;
import java.util.UUID;

/**
 * Os CINCO relatorios do recadastramento (ED-16; levantamento 18d-E11,
 * linhas 225 a 229 da matriz — os mesmos cinco da secao 2.28 do SAMAE na
 * parte de recadastramento), sobre o nucleo da P3.22:
 *
 * <ul>
 *   <li>REC-001 — diferencas cadastrais com necessidade de
 *       recadastramento (TR-684/SG-910): os achados da fila da P3.32
 *       ainda abertos ou ja agrupados em lote;</li>
 *   <li>REC-002 — erros no recadastramento (TR-685/SG-911): as
 *       submissoes de coleta recusadas, por codigo de erro;</li>
 *   <li>REC-003 — ocorrencias de recadastramento incorreto
 *       (TR-686/SG-912): os campos STALE/SUPERSEDED, cuja aplicacao foi
 *       recusada porque o cadastro mudou depois da coleta;</li>
 *   <li>REC-004 — unidades recentes com necessidade de recadastramento
 *       (TR-687/SG-913): unidades com achado aberto, ordenadas da
 *       criacao mais recente para a mais antiga. "Recente" e a ORDEM e
 *       o filtro por periodo de criacao — nenhum limiar de dias foi
 *       inventado nesta entrega;</li>
 *   <li>REC-005 — eficiencia dos recadastradores (TR-688/SG-914):
 *       coletados, aprovados, rejeitados, aplicados e erros por
 *       recadastrador e dia, com a taxa de aprovacao calculada na
 *       view como aprovados sobre coletados: a coluna
 *       {@code aprovados} da view conta os campos em APPROVED ou
 *       APPLIED — o campo aplicado ja foi aprovado e entra em
 *       {@code aprovados}, NAO e somado de novo; {@code aplicados}
 *       e apenas a contagem separada dos campos em APPLIED.</li>
 * </ul>
 *
 * <p>Registro pelo mecanismo externo da P3.22 ({@link ExternalReportRegistrar},
 * origem EXTERNAL, por tenant): a ED-16 nao toca o catalogo de fabrica
 * da P3.22 (o catalogo completo de relatorios e da ED-46). As views
 * {@code rpt_recadastramento_*} estao no SQL pendente desta entrega
 * (sem numero de migration reservado no plano) com colunas identicas
 * as declaradas aqui.
 *
 * <p>LGPD: REC-001/002/004/005 nao expoem valor de campo nenhum (so
 * regra, campo, codigo e contagens; o detalhe do achado da P3.32 ja
 * nasce sem dado pessoal em claro). No REC-003 os valores comparados
 * SAO o conteudo do relatorio (sem eles a ocorrencia nao se audita) e
 * por isso as tres colunas de valor sao {@code personal = true}: na
 * exportacao passam pelo mascaramento do teto do usuario (P1.12), e o
 * valor de documento nelas ja e a mascara desde a coleta.
 */
public final class RecadastramentoReports {

    public static final String DIFFERENCES_CODE = "REC-001";
    public static final String ERRORS_CODE = "REC-002";
    public static final String INCORRECT_CODE = "REC-003";
    public static final String RECENT_UNITS_CODE = "REC-004";
    public static final String EFFICIENCY_CODE = "REC-005";

    private RecadastramentoReports() {
    }

    /** SG-910: diferencas cadastrais com necessidade de recadastramento. */
    public static ReportDefinition differences() {
        return new ReportDefinition(DIFFERENCES_CODE,
                "Recadastramento: diferencas cadastrais com necessidade de recadastramento",
                ReportOrigin.EXTERNAL,
                "rpt_recadastramento_differences",
                "reporting.rec-001:export",
                List.of(
                        col("referencia", ReportColumnType.DATE, false),
                        col("unidade_id", ReportColumnType.TEXT, false),
                        col("regra", ReportColumnType.TEXT, false),
                        col("severidade", ReportColumnType.TEXT, false),
                        col("situacao", ReportColumnType.TEXT, false),
                        col("detalhe", ReportColumnType.TEXT, false)),
                List.of(filter("referencia")),
                "referencia",
                ReportDefinition.DEFAULT_MAX_ROWS);
    }

    /** SG-911: erros no recadastramento. */
    public static ReportDefinition errors() {
        return new ReportDefinition(ERRORS_CODE,
                "Recadastramento: erros no recadastramento",
                ReportOrigin.EXTERNAL,
                "rpt_recadastramento_errors",
                "reporting.rec-002:export",
                List.of(
                        col("referencia", ReportColumnType.DATE, false),
                        col("lote_id", ReportColumnType.TEXT, false),
                        col("unidade_id", ReportColumnType.TEXT, false),
                        col("campo", ReportColumnType.TEXT, false),
                        col("codigo_erro", ReportColumnType.TEXT, false),
                        col("coletor_id", ReportColumnType.TEXT, false)),
                List.of(filter("referencia")),
                "referencia",
                ReportDefinition.DEFAULT_MAX_ROWS);
    }

    /** SG-912: ocorrencias de recadastramento incorreto. */
    public static ReportDefinition incorrect() {
        return new ReportDefinition(INCORRECT_CODE,
                "Recadastramento: ocorrencias de recadastramento incorreto",
                ReportOrigin.EXTERNAL,
                "rpt_recadastramento_incorrect",
                "reporting.rec-003:export",
                List.of(
                        col("referencia", ReportColumnType.DATE, false),
                        col("lote_id", ReportColumnType.TEXT, false),
                        col("unidade_id", ReportColumnType.TEXT, false),
                        col("campo", ReportColumnType.TEXT, false),
                        col("valor_na_coleta", ReportColumnType.TEXT, true),
                        col("valor_coletado", ReportColumnType.TEXT, true),
                        col("valor_na_aplicacao", ReportColumnType.TEXT, true),
                        col("coletor_id", ReportColumnType.TEXT, false)),
                List.of(filter("referencia")),
                "referencia",
                ReportDefinition.DEFAULT_MAX_ROWS);
    }

    /** SG-913: unidades recentes com necessidade de recadastramento. */
    public static ReportDefinition recentUnits() {
        return new ReportDefinition(RECENT_UNITS_CODE,
                "Recadastramento: unidades recentes com necessidade de recadastramento",
                ReportOrigin.EXTERNAL,
                "rpt_recadastramento_recent_units",
                "reporting.rec-004:export",
                List.of(
                        col("unidade_criada_em", ReportColumnType.DATE, false),
                        col("unidade_id", ReportColumnType.TEXT, false),
                        col("regra", ReportColumnType.TEXT, false),
                        col("severidade", ReportColumnType.TEXT, false),
                        col("data_achado", ReportColumnType.DATE, false)),
                List.of(filter("unidade_criada_em")),
                "unidade_criada_em",
                ReportDefinition.DEFAULT_MAX_ROWS);
    }

    /** SG-914: eficiencia dos recadastradores. */
    public static ReportDefinition collectorEfficiency() {
        return new ReportDefinition(EFFICIENCY_CODE,
                "Recadastramento: eficiencia dos recadastradores",
                ReportOrigin.EXTERNAL,
                "rpt_recadastramento_collector_efficiency",
                "reporting.rec-005:export",
                List.of(
                        col("referencia", ReportColumnType.DATE, false),
                        col("coletor_id", ReportColumnType.TEXT, false),
                        col("coletados", ReportColumnType.LONG, false),
                        col("aprovados", ReportColumnType.LONG, false),
                        col("rejeitados", ReportColumnType.LONG, false),
                        col("aplicados", ReportColumnType.LONG, false),
                        col("erros", ReportColumnType.LONG, false),
                        // Razao: nunca somavel entre grupos (regra do nucleo).
                        new ReportColumn("taxa_aprovacao", ReportColumnType.DECIMAL, false, false)),
                List.of(filter("referencia")),
                "referencia",
                ReportDefinition.DEFAULT_MAX_ROWS);
    }

    /** As cinco definicoes, na ordem dos itens SG-910 a SG-914. */
    public static List<ReportDefinition> all() {
        return List.of(differences(), errors(), incorrect(), recentUnits(), collectorEfficiency());
    }

    /** Registra as cinco no nucleo da P3.22 para o tenant (origem EXTERNAL). */
    public static void registerAll(ExternalReportRegistrar registrar, UUID tenantId) {
        if (registrar == null || tenantId == null) {
            throw new IllegalArgumentException("registrador e tenant sao obrigatorios");
        }
        for (ReportDefinition definition : all()) {
            registrar.register(tenantId, definition);
        }
    }

    private static ReportColumn col(String name, ReportColumnType type, boolean personal) {
        return new ReportColumn(name, type, personal);
    }

    private static ReportFilter filter(String column) {
        return new ReportFilter(column, ReportFilter.Operator.EQ_OR_RANGE);
    }
}
