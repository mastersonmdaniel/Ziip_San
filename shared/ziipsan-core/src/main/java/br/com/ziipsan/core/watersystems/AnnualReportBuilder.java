package br.com.ziipsan.core.watersystems;

import br.com.ziipsan.core.quality.MonthlyClosing;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Montador do relatorio anual (ED-79): agrega os resumos mensais da
 * ED-50 por parametro, transporta os blocos mensais da P2.24 e as
 * limpezas da ED-89 do ano. Regra de completude HONESTA: mes sem
 * fechamento apresentado entra em {@code monthsMissing} e o relatorio
 * nasce DRAFT — READY so com os 12 meses presentes. Nenhum mes e
 * preenchido por aproximacao, media ou repeticao de outro mes.
 * Limpeza ausente no ano NAO trava o relatorio (pode nao ter havido
 * execucao): a lista vai vazia, como fato. (Decisao nº 4 do produtor,
 * mantida.)
 *
 * <p><b>Correcoes D2 (revisao da ED-79, 08/11/2026):</b>
 * <ul>
 *   <li><b>pertencimento das limpezas (sondas A6/A7):</b> cada
 *       {@code CleaningLine} carrega o {@code supplySystem} da ED-89
 *       e o montador RECUSA limpeza de sistema divergente do sistema
 *       do relatorio, limpeza sem sistema de origem identificado e
 *       limpeza DUPLICADA (mesmo reservatorio + data + laudo) — antes
 *       o sistema era descartado na conversao e limpezas de outro
 *       sistema, inventadas ou duplicadas entravam sem conferencia.
 *       A proveniencia esperada da lista e a fonte real da ED-89
 *       ({@code ChemReports.annualReportSource}); a conferencia de
 *       pertencimento aqui e a trava do nucleo sobre qualquer lista
 *       apresentada;</li>
 *   <li><b>fechamento encerrado (sonda A4):</b>
 *       {@link #buildFromClosings} recebe os {@link MonthlyClosing}
 *       REAIS da ED-50 e deriva os resumos via
 *       {@code MonthlyQualitySummary.fromClosing}, que RECUSA
 *       fechamento nao-CLOSED — o {@code isClosed()} nao e mais
 *       descartado na conversao. Fechamento de outro sistema tambem
 *       recusa. O {@link #build} por resumos crus permanece como via
 *       de transporte (os resumos ja derivados de fechamentos), sem
 *       prometer o portao que so a via por fechamentos tem.</li>
 * </ul>
 */
public final class AnnualReportBuilder {
    private AnnualReportBuilder() {}

    public static AnnualQualityReport build(UUID id, UUID tenantId, UUID consumerUnitId, String supplySystemCode,
                                            int year,
                                            List<AnnualQualityReport.MonthlyQualitySummary> summaries,
                                            List<AnnualQualityReport.MonthlyBlockLine> blocks,
                                            List<AnnualQualityReport.CleaningLine> cleanings) {
        if (summaries == null || blocks == null || cleanings == null)
            throw new IllegalArgumentException("resumos, blocos e limpezas sao obrigatorios (podem ser vazios)");
        for (AnnualQualityReport.MonthlyQualitySummary s : summaries) {
            if (s.month().getYear() != year)
                throw new IllegalArgumentException("resumo de " + s.month() + " fora do ano do relatorio " + year);
        }
        for (AnnualQualityReport.MonthlyBlockLine b : blocks) {
            if (b.referenceMonth().getYear() != year)
                throw new IllegalArgumentException("bloco de " + b.referenceMonth() + " fora do ano do relatorio " + year);
        }
        Set<String> cleaningKeys = new LinkedHashSet<>();
        for (AnnualQualityReport.CleaningLine c : cleanings) {
            if (c.executedOn().getYear() != year)
                throw new IllegalArgumentException("limpeza de " + c.executedOn() + " fora do ano do relatorio " + year);
            // Correcao D2 (A6): pertencimento — a limpeza tem de ser DO
            // sistema do relatorio; sistema divergente ou nao
            // identificado (registro da ED-89 sem reservatorio/sistema)
            // nao pode ser atestado neste relatorio.
            if (c.supplySystem() == null || c.supplySystem().isBlank())
                throw new IllegalArgumentException("limpeza do reservatorio " + c.reservoirCode()
                        + " sem sistema de origem identificado (ED-89): pertencimento nao conferivel (correcao D2)");
            if (!c.supplySystem().equals(supplySystemCode))
                throw new IllegalArgumentException("limpeza do reservatorio " + c.reservoirCode()
                        + " e do sistema " + c.supplySystem() + ", nao do sistema do relatorio "
                        + supplySystemCode + " (correcao D2)");
            // Correcao D2 (A7): duplicada — mesma execucao (reservatorio
            // + data + laudo) apresentada duas vezes nao entra em dobro.
            String key = c.reservoirCode() + "|" + c.executedOn() + "|" + c.reportRef();
            if (!cleaningKeys.add(key))
                throw new IllegalArgumentException("limpeza duplicada na lista apresentada: " + key
                        + " (correcao D2)");
        }

        TreeSet<YearMonth> present = new TreeSet<>();
        summaries.forEach(s -> present.add(s.month()));
        List<YearMonth> missing = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            if (!present.contains(ym)) missing.add(ym);
        }

        Map<String, List<AnnualQualityReport.MonthlyQualitySummary>> byParameter = new LinkedHashMap<>();
        summaries.forEach(s -> byParameter.computeIfAbsent(s.parameterKey(), k -> new ArrayList<>()).add(s));
        List<AnnualQualityReport.AnnualLine> lines = byParameter.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    List<AnnualQualityReport.MonthlyQualitySummary> rows = e.getValue();
                    int months = (int) rows.stream().map(AnnualQualityReport.MonthlyQualitySummary::month).distinct().count();
                    return new AnnualQualityReport.AnnualLine(e.getKey(), months,
                            rows.stream().mapToInt(AnnualQualityReport.MonthlyQualitySummary::planned).sum(),
                            rows.stream().mapToInt(AnnualQualityReport.MonthlyQualitySummary::collected).sum(),
                            rows.stream().mapToInt(AnnualQualityReport.MonthlyQualitySummary::analyzed).sum(),
                            rows.stream().mapToInt(AnnualQualityReport.MonthlyQualitySummary::conforming).sum(),
                            rows.stream().mapToInt(AnnualQualityReport.MonthlyQualitySummary::nonConforming).sum(),
                            rows.stream().mapToInt(AnnualQualityReport.MonthlyQualitySummary::indeterminate).sum());
                }).toList();

        AnnualQualityReport.Status status = missing.isEmpty()
                ? AnnualQualityReport.Status.READY : AnnualQualityReport.Status.DRAFT;
        return AnnualQualityReport.born(id, tenantId, consumerUnitId, supplySystemCode, year, status,
                lines, List.copyOf(present), List.copyOf(missing),
                List.copyOf(blocks), List.copyOf(cleanings), AnnualQualityReport.LEGAL_BASIS);
    }

    /**
     * Via pelas FONTES REAIS da ED-50 (correcao D2/A4): recebe os
     * fechamentos mensais {@link MonthlyClosing} do sistema e deriva
     * os resumos deles. Fechamento nao-ENCERRADO recusa (via
     * {@code MonthlyQualitySummary.fromClosing}); fechamento de outro
     * sistema ou de outro ano recusa; mes com dois fechamentos
     * recusa (duplicidade de fonte). O restante (blocos da P2.24,
     * limpezas da ED-89 e a regra dos 12 meses) e o do {@link #build}.
     */
    public static AnnualQualityReport buildFromClosings(UUID id, UUID tenantId, UUID consumerUnitId,
                                                        String supplySystemCode, int year,
                                                        List<MonthlyClosing> closings,
                                                        List<AnnualQualityReport.MonthlyBlockLine> blocks,
                                                        List<AnnualQualityReport.CleaningLine> cleanings) {
        if (closings == null)
            throw new IllegalArgumentException("fechamentos da ED-50 sao obrigatorios (podem ser vazios)");
        List<AnnualQualityReport.MonthlyQualitySummary> summaries = new ArrayList<>();
        Set<YearMonth> seenMonths = new LinkedHashSet<>();
        for (MonthlyClosing closing : closings) {
            if (!closing.supplySystem().equals(supplySystemCode))
                throw new IllegalArgumentException("fechamento de " + closing.month() + " e do sistema "
                        + closing.supplySystem() + ", nao do sistema do relatorio " + supplySystemCode
                        + " (correcao D2)");
            if (closing.month().getYear() != year)
                throw new IllegalArgumentException("fechamento de " + closing.month()
                        + " fora do ano do relatorio " + year);
            if (!seenMonths.add(closing.month()))
                throw new IllegalArgumentException("dois fechamentos apresentados para o mes "
                        + closing.month() + " (correcao D2)");
            summaries.addAll(AnnualQualityReport.MonthlyQualitySummary.fromClosing(closing));
        }
        return build(id, tenantId, consumerUnitId, supplySystemCode, year, summaries, blocks, cleanings);
    }
}
