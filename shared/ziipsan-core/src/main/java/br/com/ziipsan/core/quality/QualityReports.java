package br.com.ziipsan.core.quality;

import java.util.List;

/**
 * Cobertura SAMAE 2.26 e relatorios 2.28.117 a 2.28.126 no nucleo da
 * ED-50 (padrao {@code SamaeFieldCoverage} da ED-20 e do catalogo da
 * ED-46/P3.22): os 10 relatorios de qualidade sao DEFINICOES sobre as
 * fontes desta entrega, para registro no catalogo de relatorios da
 * P3.22/ED-46 pela integracao — nenhuma consulta nem tela e duplicada
 * aqui. Os codigos SG-1007..SG-1016 e os titulos vem do levantamento
 * 18g (bloco 2.4, TR-780..TR-789 / SG-1007..SG-1016); a numeracao
 * 2.28.117..2.28.126 e a do aceite da ED-50, na mesma ordem.
 *
 * <p>Itens da secao 2.26 (SG-789..SG-821) FORA desta entrega, por
 * mandado do aceite: SG-810 paradas da ETA/supervisorio (ED-87/
 * telemetria), SG-811 produtos de dosagem (ED-89), SG-822 ISO/IEC
 * 17025 (ED-51). Os demais itens 2.26 sao cobertos pelo nucleo
 * (catalogo, plano, custodia, recoleta, fechamento, publicacao) ou
 * pela fiacao de telas/API nomeada na documentacao da entrega.
 */
public final class QualityReports {

    public record ReportDefinition(String samaeItem, String code, String title, String source) {}

    private QualityReports() {}

    public static List<ReportDefinition> definitions() {
        return List.of(
                new ReportDefinition("2.28.117", "SG-1007",
                        "Relatorio detalhado de analise dos parametros de qualidade de agua, antes do fechamento mensal",
                        "entradas do fechamento (amostras, resultados e avaliacoes) do mes aberto"),
                new ReportDefinition("2.28.118", "SG-1008",
                        "Relatorio detalhado de analise dos parametros de qualidade de agua, apos o fechamento mensal",
                        "fechamento mensal FECHADO (MonthlyClosing)"),
                new ReportDefinition("2.28.119", "SG-1009",
                        "Relatorio detalhado com os dados anuais de qualidade de agua",
                        "fechamentos mensais fechados do ano (agregacao das linhas por parametro)"),
                new ReportDefinition("2.28.120", "SG-1010",
                        "Relatorio detalhado do cronograma mensal de coleta para analises laboratoriais",
                        "plano de amostragem (SamplingPlan) do mes"),
                new ReportDefinition("2.28.121", "SG-1011",
                        "Relatorio detalhado de acompanhamento das analises laboratoriais",
                        "amostras e cadeia de custodia (Sample) por estado"),
                new ReportDefinition("2.28.122", "SG-1012",
                        "Relatorio detalhado de acompanhamento dos parametros de qualidade da agua",
                        "resultados validados (AnalysisResult) e avaliacoes por parametro"),
                new ReportDefinition("2.28.123", "SG-1013",
                        "Relatorio de acompanhamento de recoleta de material para analises laboratoriais",
                        "amostras RECOLLECTION e suas originais"),
                new ReportDefinition("2.28.124", "SG-1014",
                        "Relatorio de acompanhamento das observacoes associadas as analises laboratoriais",
                        "eventos de custodia e justificativas de correcao dos resultados"),
                new ReportDefinition("2.28.125", "SG-1015",
                        "Painel de controle detalhado das analises fora de conformidade",
                        "avaliacoes NON_CONFORMING do periodo (ConformityEvaluator)"),
                new ReportDefinition("2.28.126", "SG-1016",
                        "Painel de controle detalhado das analises fora de conformidade, por ponto de coleta",
                        "avaliacoes NON_CONFORMING agrupadas pelo ponto da amostra"));
    }
}
