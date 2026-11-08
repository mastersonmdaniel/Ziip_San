package br.com.ziipsan.intelligence.losses.nr15;

import br.com.ziipsan.intelligence.losses.dmc.WaterBalanceCalculator;

import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Foto de LEITURA de um balanco mensal por DMC da ED-85 (ED-78):
 * espelha, campo a campo, a tabela {@code loss_monthly_balances} da
 * ED-85 - a superficie que a propria ED-85 declara legivel pela
 * ED-78 ("tabela loss_monthly_balances como snapshot legivel" e
 * {@code WaterBalanceCalculator.realBalanceEntry}).
 *
 * <p><b>Sem calculo proprio de balanco (item literal do aceite):</b>
 * TODOS os valores derivados (entrada liquida, consumo autorizado,
 * faturado total, perdas totais/reais, agua nao faturada, ILI) sao
 * LIDOS desta foto - foram calculados pela ED-85. A ED-78 apenas os
 * SOMA para agregar o periodo (a mesma operacao que a ED-85 faz no
 * agregado por setor) e deriva razoes de indicador (divisoes), nunca
 * recompoe o balanco a partir de insumos.
 *
 * <p>{@link #fromBalance} e a ponte em memoria: constroi a foto a
 * partir das {@code Inputs} e do {@code DmcMonthlyBalance} que a
 * ED-85 produziu - exatamente o que a ED-85 persiste na tabela. Na
 * fiacao, a foto vem da tabela via {@link DmcBalancePort}.
 */
public record DmcBalanceSnapshot(
        UUID dmcId,
        YearMonth period,
        int periodDays,
        int connections,
        double systemInputM3,
        double exportedM3,
        double netInputM3,
        double billedMeteredAdjustedM3,
        double billedUnmeteredM3,
        double authorizedUnbilledM3,
        double authorizedConsumptionM3,
        double billedTotalM3,
        double totalLossesM3,
        Double apparentLossesM3OrNull,
        Double realLossesM3OrNull,
        double nonRevenueWaterM3,
        double nonRevenuePercent,
        Double realLossesLPerConnDayOrNull,
        Double iliOrNull,
        WaterBalanceCalculator.ConfidenceGrade confidence) {

    public DmcBalanceSnapshot {
        Objects.requireNonNull(dmcId, "DMC da foto obrigatorio");
        Objects.requireNonNull(period, "periodo da foto obrigatorio");
        Objects.requireNonNull(confidence, "grau de confianca da ED-85 obrigatorio na foto");
        if (periodDays <= 0) throw new IllegalArgumentException("dias do periodo invalidos: " + periodDays);
        if (connections <= 0) throw new IllegalArgumentException("foto por ligacao exige ligacoes > 0");
        requireNonNegative(systemInputM3, "volume de entrada");
        requireNonNegative(exportedM3, "volume exportado");
        requireNonNegative(netInputM3, "entrada liquida");
        requireNonNegative(billedMeteredAdjustedM3, "faturado micromedido ajustado");
        requireNonNegative(billedUnmeteredM3, "faturado nao micromedido");
        requireNonNegative(authorizedUnbilledM3, "autorizado nao faturado");
        requireNonNegative(authorizedConsumptionM3, "consumo autorizado");
        requireNonNegative(billedTotalM3, "faturado total");
        requireNonNegative(nonRevenueWaterM3, "agua nao faturada");
        if (exportedM3 > systemInputM3) throw new IllegalArgumentException(
                "exportado em bloco maior que a entrada do sistema na foto da ED-85");
        if (apparentLossesM3OrNull != null) requireNonNegative(apparentLossesM3OrNull, "perdas aparentes");
        // Perda real e RESIDUO na ED-85 (total - aparentes): pode ser
        // negativa quando as aparentes declaradas excedem as perdas
        // totais - sinal de inconsistencia que a ED-85 expoe, nao
        // recusa (a coluna real_losses_m3 dela nao tem CHECK >= 0).
        // A foto le o que a ED-85 gravou; so NaN e recusado.
        if (realLossesM3OrNull != null && Double.isNaN(realLossesM3OrNull))
            throw new IllegalArgumentException("perdas reais NaN na foto");
        if (Double.isNaN(totalLossesM3)) throw new IllegalArgumentException("perdas totais NaN na foto");
    }

    private static void requireNonNegative(double value, String what) {
        if (value < 0 || Double.isNaN(value)) throw new IllegalArgumentException(what + " invalido na foto: " + value);
    }

    /**
     * Ponte ED-85 -> ED-78: a foto do que a ED-85 calculou e persiste.
     * Nenhum numero e derivado aqui alem da copia dos campos.
     */
    public static DmcBalanceSnapshot fromBalance(WaterBalanceCalculator.Inputs in,
                                                 WaterBalanceCalculator.DmcMonthlyBalance balance) {
        Objects.requireNonNull(in, "entradas da ED-85 obrigatorias na ponte");
        Objects.requireNonNull(balance, "balanco da ED-85 obrigatorio na ponte");
        if (!in.dmcId().equals(balance.dmcId()) || !in.period().equals(balance.period())) {
            throw new IllegalArgumentException("ponte ED-85/ED-78 com DMC/periodo divergentes das entradas");
        }
        return new DmcBalanceSnapshot(
                balance.dmcId(), balance.period(), in.periodDays(), in.connections(),
                in.systemInputM3(), in.exportedM3(), balance.netInputM3(),
                in.billedMeteredAdjustedM3(), in.billedUnmeteredM3(), in.authorizedUnbilledM3(),
                balance.authorizedConsumptionM3(), balance.billedTotalM3(), balance.totalLossesM3(),
                balance.apparentLossesM3OrNull(), balance.realLossesM3OrNull(),
                balance.nonRevenueWaterM3(), balance.nonRevenuePercent(),
                balance.realLossesLPerConnDayOrNull(), balance.iliOrNull(), balance.confidence());
    }
}
