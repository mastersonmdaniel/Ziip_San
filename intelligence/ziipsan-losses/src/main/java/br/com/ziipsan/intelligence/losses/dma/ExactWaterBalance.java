package br.com.ziipsan.intelligence.losses.dma;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.com.ziipsan.intelligence.losses.dma.DmaModel.DmaDefinition;
import br.com.ziipsan.intelligence.losses.dma.DmaModel.DmaPeriodBalance;
import br.com.ziipsan.intelligence.losses.dma.DmaModel.DmcPeriodBalance;
import br.com.ziipsan.intelligence.losses.dma.DmaModel.DmcPeriodInputs;

/**
 * Balanco hidrico EXACTO por periodo (II-6, E2) — camada aditiva sobre
 * o balanco mensal em {@code double} da ED-85
 * ({@code WaterBalanceCalculator}): mesmas identidades do balanco
 * hidrico padrao, mas em {@link BigDecimal} sem nenhum
 * arredondamento, para qualquer periodo [inicio, fim], e com
 * agregacao de DMA (soma dos DMCs membros).
 *
 * <p>Componentes que a base nao expuser (hoje: perdas aparentes, do
 * territorio da ED-95) NAO sao estimadas: a perda real sai AUSENTE
 * (Optional vazio) e o nome da componente consta da lista de
 * ausentes do resultado. Nenhum valor de perda "aceitavel" e
 * calculado nem comparado — nao ha meta neste pacote (a meta da
 * ED-85, {@code loss.dmc_target_l_per_conn_day}, continua a ser
 * parametro do tenant sem valor presumido).
 */
public final class ExactWaterBalance {

    /** Nome declarado da componente opcional hoje nao exposta pela base. */
    public static final String ABSENT_APPARENT_LOSSES = "apparentLossesM3";

    private ExactWaterBalance() {}

    public static DmcPeriodBalance calculate(DmcPeriodInputs in) {
        BigDecimal netInput = in.systemInputM3().subtract(in.exportedM3());
        BigDecimal billedTotal = in.billedMeteredM3().add(in.billedUnmeteredM3());
        BigDecimal authorized = billedTotal.add(in.authorizedUnbilledM3());
        BigDecimal totalLosses = netInput.subtract(authorized);
        BigDecimal nrw = netInput.subtract(billedTotal);
        Optional<BigDecimal> real = in.apparentLossesM3().map(totalLosses::subtract);
        List<String> absent = real.isEmpty() ? List.of(ABSENT_APPARENT_LOSSES) : List.of();
        return new DmcPeriodBalance(in.dmcId(), in.dmcCode(), in.periodStart(), in.periodEnd(),
                netInput, billedTotal, authorized, totalLosses,
                in.apparentLossesM3(), real, nrw, absent);
    }

    /**
     * Agregacao do DMA: exige a definicao (membros exactos), o MESMO
     * periodo em todos os balancos e exactamente um balanco por DMC
     * membro — membro sem balanco ou balanco fora da definicao
     * RECUSA (agregar um subconjunto em silencio seria inventar o
     * DMA). Perda real do agregado so existe se TODOS os membros a
     * conhecerem; senao sai ausente e declarada.
     */
    public static DmaPeriodBalance aggregate(DmaDefinition definition, List<DmcPeriodBalance> memberBalances) {
        java.util.Objects.requireNonNull(definition, "definicao do DMA obrigatoria");
        java.util.Objects.requireNonNull(memberBalances, "balancos dos DMCs membros obrigatorios");
        List<String> codes = memberBalances.stream().map(DmcPeriodBalance::dmcCode).sorted().toList();
        List<String> expected = definition.memberDmcCodes().stream().sorted().toList();
        if (!codes.equals(expected))
            throw new IllegalArgumentException("agregacao exige exactamente os DMCs da definicao "
                    + expected + "; recebidos " + codes);
        DmcPeriodBalance first = memberBalances.get(0);
        if (memberBalances.stream().anyMatch(b -> !b.periodStart().equals(first.periodStart())
                || !b.periodEnd().equals(first.periodEnd())))
            throw new IllegalArgumentException("agregacao de DMA exige o mesmo periodo em todos os DMCs");
        BigDecimal net = sum(memberBalances.stream().map(DmcPeriodBalance::netInputM3).toList());
        BigDecimal billed = sum(memberBalances.stream().map(DmcPeriodBalance::billedTotalM3).toList());
        BigDecimal authorized = sum(memberBalances.stream().map(DmcPeriodBalance::authorizedConsumptionM3).toList());
        BigDecimal losses = sum(memberBalances.stream().map(DmcPeriodBalance::totalLossesM3).toList());
        BigDecimal nrw = sum(memberBalances.stream().map(DmcPeriodBalance::nonRevenueWaterM3).toList());
        boolean allApparent = memberBalances.stream().allMatch(b -> b.apparentLossesM3().isPresent());
        boolean allReal = memberBalances.stream().allMatch(b -> b.realLossesM3().isPresent());
        Optional<BigDecimal> apparent = allApparent
                ? Optional.of(sum(memberBalances.stream().map(b -> b.apparentLossesM3().orElseThrow()).toList()))
                : Optional.empty();
        Optional<BigDecimal> real = allReal
                ? Optional.of(sum(memberBalances.stream().map(b -> b.realLossesM3().orElseThrow()).toList()))
                : Optional.empty();
        List<String> absent = new ArrayList<>();
        if (real.isEmpty()) absent.add(ABSENT_APPARENT_LOSSES);
        return new DmaPeriodBalance(definition.dmaCode(), definition.sectorCode(),
                first.periodStart(), first.periodEnd(), memberBalances.size(),
                net, billed, authorized, losses, apparent, real, nrw, absent);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : values) total = total.add(v);
        return total;
    }
}
