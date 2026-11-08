package br.com.ziipsan.core.quality;

import java.util.List;

/**
 * Arquivo SISAGUA ASSISTIDO: monta o arquivo de remessa a partir do
 * fechamento mensal fechado e das entradas validadas, em ordem
 * deterministica, acompanhado da identificacao do leiaute configurado
 * no tenant ({@code quality.sisagua_layout}).
 *
 * <p>Disciplina [SEM FONTE]: o leiaute oficial do SISAGUA nao consta
 * das fontes do projeto — por isso (1) sem o parametro configurado a
 * geracao RECUSA (nada de leiaute presumido); (2) o arquivo se declara
 * "assistido": linhas {@code mes;sistema;ponto;etiqueta;parametro;
 * resultado;unidade;veredito} prontas para a transcricao/conferencia
 * no sistema oficial, com o cabecalho nomeando o leiaute-alvo
 * configurado; (3) a conformidade final do arquivo com o leiaute
 * oficial vigente e pendencia nomeada de integracao (P-ED50-04), a
 * conferir com o responsavel tecnico e a documentacao oficial antes
 * da primeira remessa real.
 *
 * <p>So entram resultados VALIDADOS de amostras recebidas pelo
 * laboratorio — inclusive COMPLAINT e RECOLLECTION (o SISAGUA recebe o
 * fato analitico; dado pessoal nao vai: ponto e etiqueta sao
 * operacionais, sem nome/documento do reclamante).
 */
public final class SisaguaExport {

    public record SisaguaFile(String layout, String fileName, String content, int rowCount) {}

    private SisaguaExport() {}

    public static SisaguaFile generate(MonthlyClosing closing, List<MonthlyClosing.Entry> entries,
                                       String layoutRaw) {
        String layout = QualityParameters.sisaguaLayout(layoutRaw);
        if (closing == null) throw new IllegalArgumentException("fechamento e obrigatorio");
        if (!closing.isClosed())
            throw new IllegalStateException("arquivo SISAGUA exige fechamento mensal FECHADO");
        if (entries == null) throw new IllegalArgumentException("entradas sao obrigatorias (podem ser vazias)");

        List<MonthlyClosing.Entry> rows = entries.stream()
                .filter(e -> e.result().validated())
                .filter(e -> e.sample().receivedByLab())
                .sorted(java.util.Comparator
                        .comparing((MonthlyClosing.Entry e) -> e.sample().pointId().toString())
                        .thenComparing(e -> e.result().parameterKey())
                        .thenComparing(e -> e.sample().labelCode()))
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("# SISAGUA-ASSISTIDO;leiaute=").append(layout)
                .append(";mes=").append(closing.month())
                .append(";sistema=").append(closing.supplySystem())
                .append(";fechamento=").append(closing.id())
                .append('\n');
        for (MonthlyClosing.Entry e : rows) {
            sb.append(closing.month()).append(';')
                    .append(closing.supplySystem()).append(';')
                    .append(e.sample().pointId()).append(';')
                    .append(e.sample().labelCode()).append(';')
                    .append(e.result().parameterKey()).append(';')
                    .append(e.result().resultText()).append(';')
                    .append(e.result().unit() == null ? "" : e.result().unit()).append(';')
                    .append(e.assessment().verdict())
                    .append('\n');
        }
        String fileName = "sisagua-" + closing.supplySystem() + "-" + closing.month() + ".txt";
        return new SisaguaFile(layout, fileName, sb.toString(), rows.size());
    }
}
