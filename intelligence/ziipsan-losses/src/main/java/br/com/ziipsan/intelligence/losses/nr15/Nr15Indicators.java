package br.com.ziipsan.intelligence.losses.nr15;

import java.util.List;
import java.util.Objects;

/**
 * Indicadores de perdas de um periodo para a saida da NR 15 (ED-78),
 * calculados por AGREGACAO das fotos da ED-85 do periodo (mes ou
 * ano): somam-se volumes e ligacao-dias e derivam-se as razoes.
 * Nenhum balanco e recomposto aqui.
 *
 * <p>Conteudo:
 * <ul>
 *   <li><b>Indice de perdas por ligacao segregado</b> (NR 15, art.
 *       5o, I, a - NR 9 Nivel I-01, conforme SINISA): perdas totais,
 *       reais e aparentes em litros por ligacao por dia. Real e
 *       aparente sao ANULAVEIS em bloco: se qualquer foto do periodo
 *       desconhece o componente, o indice do periodo e ausente.</li>
 *   <li><b>IVI e ILI por periodo</b> (item literal do aceite): IVI
 *       (Indice de Vazamentos da Infraestrutura) e o nome em
 *       portugues do ILI (Infrastructure Leakage Index) da IWA -
 *       mesma razao CARL/UARL (a literatura nacional o registra:
 *       "IVI - Indice de Vazamentos na Infraestrutura (ILI no
 *       formato ingles)"; a Nota Informativa 1/2025 da ANA usa IVI
 *       para o mesmo indicador). Por isso ha UM valor, exposto nos
 *       dois nomes - inventar uma segunda formula seria fabricar
 *       metodo. O ILI do periodo agrega os ILIs da ED-85 sem
 *       recalcular a UARL: UARL do DMC = CARL/ILI (inversao do valor
 *       que a ED-85 gravou), e ILI do periodo = soma(CARL)/soma(UARL)
 *       sobre os DMCs do periodo; se algum DMC desconhece a perda
 *       real ou tem perda real sem ILI, o ILI do periodo e AUSENTE
 *       (null) - media de ILIs ou subconjunto silencioso seriam
 *       numeros inventados.</li>
 *   <li><b>Agua nao faturada %</b> sobre os totais somados.
 *       <b>Convencao herdada (correcao ED-78-CORR-1, ressalva R1 da
 *       revisao):</b> com entrada liquida somada &lt;= 0, o
 *       percentual devolvido e 0,0 - e NAO null. A convencao e
 *       herdada VERBATIM da ED-85 ({@code nrwPct} identico no
 *       balanco por DMC): mudar so aqui faria a saida da NR 15
 *       divergir do produtor do numero. Registra-se que 0,0% com
 *       entrada zero e indistinguivel de uma medicao real de 0%;
 *       a decisao de virar null (ou nao) e CONJUNTA com a ED-85 e
 *       esta na pendencia P-ED78-08 - esta entrega nao a altera
 *       por conta propria.</li>
 *   <li><b>Hidrometacao (IAG1003) e macromedicao (NR 9 II-02)</b>
 *       quando as contagens da {@link MeteringCoveragePort} sao
 *       anexadas por {@link #withCoverage}.</li>
 * </ul>
 *
 * <p>O indice de intermitencia (NR 9 I-04, citado no art. 5o) NAO e
 * calculado aqui: ele e da ED-92/ED-77 (fichas da NR 9) - a
 * declaracao o referencia na fiacao, sem duplicar o calculo.
 */
public record Nr15Indicators(
        String periodLabel,
        int snapshotCount,
        Double totalLossesLPerConnDay,
        Double realLossesLPerConnDayOrNull,
        Double apparentLossesLPerConnDayOrNull,
        Double iliOrNull,
        double nonRevenuePercent,
        Double hydroMeteringPercentOrNull,
        Double macroMeteringPercentOrNull) {

    public Nr15Indicators {
        if (periodLabel == null || periodLabel.isBlank()) {
            throw new IllegalArgumentException("rotulo do periodo obrigatorio nos indicadores");
        }
        if (snapshotCount <= 0) throw new IllegalArgumentException("indicadores sem foto da ED-85");
        if (totalLossesLPerConnDay == null || Double.isNaN(totalLossesLPerConnDay)) {
            throw new IllegalArgumentException("indice de perdas totais por ligacao obrigatorio");
        }
    }

    /** IVI: o mesmo valor do ILI (nome em portugues do indicador IWA). */
    public Double iviOrNull() {
        return iliOrNull;
    }

    /** Anexa os indices de cobertura de medicao calculados da porta da ED-17. */
    public Nr15Indicators withCoverage(MeteringCoveragePort.CoverageCounts counts) {
        Objects.requireNonNull(counts, "contagens de cobertura obrigatorias");
        return new Nr15Indicators(periodLabel, snapshotCount, totalLossesLPerConnDay,
                realLossesLPerConnDayOrNull, apparentLossesLPerConnDayOrNull, iliOrNull,
                nonRevenuePercent, counts.hydroMeteringPercentOrNull(),
                counts.macroMeteringPercentOrNull());
    }

    /** Indicadores do periodo a partir das fotos da ED-85 que o compoem. */
    public static Nr15Indicators forPeriod(String periodLabel, List<DmcBalanceSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "fotos da ED-85 do periodo obrigatorias");
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("indicadores de periodo sem nenhuma foto da ED-85");
        }
        double connectionDays = snapshots.stream()
                .mapToDouble(s -> (double) s.connections() * s.periodDays()).sum();
        if (connectionDays <= 0) throw new IllegalArgumentException("ligacao-dias do periodo invalidas");
        double totalLosses = snapshots.stream().mapToDouble(DmcBalanceSnapshot::totalLossesM3).sum();
        double netInput = snapshots.stream().mapToDouble(DmcBalanceSnapshot::netInputM3).sum();
        double nonRevenue = snapshots.stream().mapToDouble(DmcBalanceSnapshot::nonRevenueWaterM3).sum();
        Double apparent = sumNullable(snapshots, true);
        Double real = sumNullable(snapshots, false);
        return new Nr15Indicators(
                periodLabel,
                snapshots.size(),
                totalLosses * 1000.0 / connectionDays,
                real == null ? null : real * 1000.0 / connectionDays,
                apparent == null ? null : apparent * 1000.0 / connectionDays,
                aggregateIli(snapshots),
                netInput <= 0 ? 0.0 : nonRevenue / netInput * 100.0,
                null, null);
    }

    /**
     * ILI do periodo = soma(CARL) / soma(UARL), com UARL recuperada de
     * cada foto como CARL/ILI gravado pela ED-85. Ausente se QUALQUER
     * foto desconhece a perda real (o CARL do sistema e desconhecido:
     * calcular so com os DMCs conhecidos apresentaria um subconjunto
     * como se fosse o sistema) ou se alguma foto com perda real nao
     * traz ILI.
     *
     * <p><b>Caso-limite documentado (correcao ED-78-CORR-1, ressalva
     * R2 da revisao): perda real zero ou negativa.</b> Quando um DMC
     * tem perda real EXATAMENTE zero, a ED-85 grava ILI = 0 para ele
     * e a inversao UARL = CARL/ILI vira 0/0 - indefinida: nao existe
     * UARL recuperavel dessa foto. Quando a perda real e NEGATIVA, a
     * ED-85 grava ILI negativo (ou nulo), igualmente sem inversao
     * valida. Nos dois casos a guarda {@code ili <= 0} devolve null
     * para o PERIODO INTEIRO: o ILI de periodo some, mesmo que os
     * demais DMCs do periodo tivessem ILI computavel. O
     * comportamento e deliberado e honesto - descartar o DMC
     * problematico e agregar so os restantes apresentaria um
     * subconjunto como se fosse o sistema, o mesmo defeito ja
     * corrigido para a perda real desconhecida. A guarda NAO
     * distingue "ILI ausente" de "ILI zero/negativo": os dois
     * caminhos convergem para o mesmo null. Se um dia o
     * comportamento precisar mudar (ex.: tratar perda real zero como
     * UARL conhecida por outra via), a decisao e CONJUNTA com a
     * ED-85 - dona do ILI gravado - e esta registrada na pendencia
     * P-ED78-09; esta entrega nao a antecipa.
     */
    private static Double aggregateIli(List<DmcBalanceSnapshot> snapshots) {
        double carlTotal = 0.0;
        double uarlTotal = 0.0;
        for (DmcBalanceSnapshot snapshot : snapshots) {
            if (snapshot.realLossesM3OrNull() == null) return null;
            if (snapshot.iliOrNull() == null || snapshot.iliOrNull() <= 0) return null;
            carlTotal += snapshot.realLossesM3OrNull();
            uarlTotal += snapshot.realLossesM3OrNull() / snapshot.iliOrNull();
        }
        if (uarlTotal <= 0) return null;
        return carlTotal / uarlTotal;
    }

    private static Double sumNullable(List<DmcBalanceSnapshot> snapshots, boolean apparent) {
        double total = 0.0;
        for (DmcBalanceSnapshot snapshot : snapshots) {
            Double value = apparent ? snapshot.apparentLossesM3OrNull() : snapshot.realLossesM3OrNull();
            if (value == null) return null;
            total += value;
        }
        return total;
    }
}
