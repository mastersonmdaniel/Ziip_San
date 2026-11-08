package br.com.ziipsan.reporting.catalog;

import br.com.ziipsan.reporting.registry.ReportColumn;
import br.com.ziipsan.reporting.registry.ReportColumnType;
import br.com.ziipsan.reporting.registry.ReportDefinition;
import br.com.ziipsan.reporting.registry.ReportFilter;
import br.com.ziipsan.reporting.registry.ReportOrigin;

import java.util.List;

/**
 * Definicoes declarativas dos relatorios NOVOS da ED-46 (os itens da
 * 23d §5.2 e os POC sem cobertura na P3.22 cuja fonte ja existe). Seguem
 * o mesmo contrato da P3.22: view {@code rpt_*} declarada, colunas e
 * filtros em whitelist, permissao {@code reporting.<codigo>:export} e a
 * regra unica da P1.12 na exportacao (o nucleo da P3.22 executa estas
 * definicoes quando elas sao registradas no {@code ReportRegistry} pela
 * fiacao de integracao - ver RELATORIO-ED-46, pendencia de fiacao).
 *
 * <p>NENHUM valor regulatorio esta nestas definicoes: prazos, percentuais
 * e formulas vivem nos parametros do tenant/perfil (P2.31) e no calculo
 * da tarefa-fonte (P3.4). Relatorio le o que a fonte persistiu; nao
 * recalcula regra de agencia.
 */
public final class Ed46ReportDefinitions {

    private Ed46ReportDefinitions() {
    }

    public static List<ReportDefinition> all() {
        return List.of(
            def("RPT-101", "Produtividade por atendente", "rpt_produtividade_atendente",
                    "reporting.rpt-101:export", "atendente_id",
                    List.of(col("atendente_id", ReportColumnType.TEXT, false),
                            col("protocolos", ReportColumnType.LONG, false),
                            col("encerrados", ReportColumnType.LONG, false),
                            col("abertos", ReportColumnType.LONG, false),
                            col("encerrados_no_prazo", ReportColumnType.LONG, false),
                            col("tempo_medio_horas", ReportColumnType.DECIMAL, false)),
                    List.of("atendente_id")),
            def("RPT-102", "Reclamações por origem externa do protocolo", "rpt_reclamacoes_origem_externa",
                    "reporting.rpt-102:export", "origem",
                    List.of(col("origem", ReportColumnType.TEXT, false),
                            col("protocolos", ReportColumnType.LONG, false),
                            col("encerrados", ReportColumnType.LONG, false),
                            col("fora_do_prazo", ReportColumnType.LONG, false),
                            col("percentual_fora_do_prazo", ReportColumnType.DECIMAL, false),
                            col("tempo_medio_horas", ReportColumnType.DECIMAL, false)),
                    List.of("origem")),
            def("RPT-103", "Compensações pagas ao usuário por tipo de usuário e de compensação",
                    "rpt_compensacoes_usuario",
                    "reporting.rpt-103:export", "tipo_usuario",
                    List.of(col("tipo_usuario", ReportColumnType.TEXT, false),
                            col("tipo_compensacao", ReportColumnType.TEXT, false),
                            col("formula", ReportColumnType.TEXT, false),
                            col("compensacoes", ReportColumnType.LONG, false),
                            col("valor_total", ReportColumnType.MONEY, false)),
                    List.of("tipo_usuario", "tipo_compensacao")),
            def("RPT-104", "Tempo entre leituras por leiturista", "rpt_tempo_entre_leituras_leiturista",
                    "reporting.rpt-104:export", "data",
                    List.of(col("leiturista_id", ReportColumnType.TEXT, false),
                            col("data", ReportColumnType.DATE, false),
                            col("leituras", ReportColumnType.LONG, false),
                            col("tempo_total_horas", ReportColumnType.DECIMAL, false),
                            col("intervalo_medio_segundos", ReportColumnType.DECIMAL, false)),
                    List.of("data", "leiturista_id")),
            def("RPT-105", "Acessos a dados pessoais por usuário", "rpt_acessos_dados_usuario",
                    "reporting.rpt-105:export", "usuario_id",
                    List.of(col("usuario_id", ReportColumnType.TEXT, false),
                            col("acessos", ReportColumnType.LONG, false),
                            col("exportacoes", ReportColumnType.LONG, false),
                            col("negados", ReportColumnType.LONG, false),
                            col("cotas_excedidas", ReportColumnType.LONG, false),
                            col("ultimo_acesso", ReportColumnType.TIMESTAMP, false)),
                    List.of("usuario_id"))
        );
    }

    private static ReportDefinition def(String code, String title, String view, String permission,
                                        String sort, List<ReportColumn> columns, List<String> filters) {
        return new ReportDefinition(code, title, ReportOrigin.BUILTIN, view, permission,
                columns, filters.stream().map(f -> new ReportFilter(f, ReportFilter.Operator.EQ_OR_RANGE)).toList(),
                sort, ReportDefinition.DEFAULT_MAX_ROWS);
    }

    private static ReportColumn col(String name, ReportColumnType type, boolean personal) {
        return new ReportColumn(name, type, personal);
    }
}
