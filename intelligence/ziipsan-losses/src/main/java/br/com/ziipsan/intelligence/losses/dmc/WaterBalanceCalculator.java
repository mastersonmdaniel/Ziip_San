package br.com.ziipsan.intelligence.losses.dmc;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Balanco hidrico mensal por DMC (ED-85; OPE-01, ficha de perdas da
 * NR 9/ANA) — o {@code WaterBalanceCalculator} do modulo
 * {@code intelligence/ziipsan-losses} (ex-`sigsan-losses`) deixa de
 * calcular com MARCADORES no lugar das entradas: TODA componente de
 * volume e um {@link Double} obrigatorio no construtor de
 * {@link Inputs} — componente nao declarada (null) RECUSA o calculo;
 * zero so existe quando o chamador declara zero. As unicas entradas
 * anulaveis sao as que o dominio admite desconhecer: perdas aparentes
 * (territorio da ED-95; sem elas, perda real fica AUSENTE e so a
 * perda total e conhecida) e as grandezas do ILI (sem pressao media,
 * extensao ou perda real, ILI fica ausente — nunca estimado).
 *
 * <pre>
 *   entrada liquida   = volume de entrada do sistema (macromedido)
 *                       - exportado em bloco (medido, OPE-05)
 *   consumo autorizado = faturado micromedido AJUSTADO (JOR-34)
 *                       + faturado nao micromedido + autorizado nao faturado
 *   perdas totais     = entrada liquida - consumo autorizado
 *   perdas reais      = perdas totais - perdas aparentes (se declaradas)
 *   agua nao faturada = entrada liquida - volume faturado total
 * </pre>
 *
 * ILI (com dados reais): ILI = CARL / UARL, com
 * UARL (L/dia) = (18 x Lm + 0,8 x Nc + 25 x Lp) x P — Lm extensao de
 * rede (km), Nc ligacoes, Lp extensao de ramais (km), P pressao media
 * (mca) — formula padrao IWA declarada como METODO; os quatro dados
 * sao entradas reais do chamador.
 *
 * Grau de confianca (metodo interno declarado, nao e norma):
 * HIGH = fracao medida da entrada >= 0,9 E perdas aparentes
 * declaradas; MEDIUM = fracao medida >= 0,6 (ou >= 0,9 sem aparentes);
 * LOW = demais casos.
 */
public final class WaterBalanceCalculator {

    public enum ConfidenceGrade { HIGH, MEDIUM, LOW }
    public enum TargetStatus { AT_OR_BELOW_TARGET, ABOVE_TARGET, NO_TARGET }

    public record Inputs(UUID dmcId, YearMonth period, int periodDays, int connections,
                         Double systemInputM3, Double exportedM3,
                         Double billedMeteredAdjustedM3, Double billedUnmeteredM3,
                         Double authorizedUnbilledM3, Double apparentLossesM3OrNull,
                         MicroPeriodAdjustment.Method adjustmentMethod,
                         Double measuredShareOfInput, Double targetLPerConnDayOrNull,
                         Double mainsLengthKmOrNull, Double avgPressureMOrNull,
                         Double servicePipeLengthKmOrNull, IncidentLossInputs incidentAnnex) {
        public Inputs {
            Objects.requireNonNull(dmcId, "DMC do balanco obrigatorio");
            Objects.requireNonNull(period, "periodo mensal do balanco obrigatorio");
            Objects.requireNonNull(adjustmentMethod, "metodo de ajuste JOR-34 obrigatorio (declarado e gravado no balanco)");
            requireDeclared(systemInputM3, "volume de entrada macromedido");
            requireDeclared(exportedM3, "volume exportado em bloco medido");
            requireDeclared(billedMeteredAdjustedM3, "volume faturado micromedido ajustado");
            requireDeclared(billedUnmeteredM3, "volume faturado nao micromedido");
            requireDeclared(authorizedUnbilledM3, "volume autorizado nao faturado");
            requireDeclared(measuredShareOfInput, "fracao medida da entrada (grau de confianca)");
            if (periodDays <= 0) throw new IllegalArgumentException("dias do periodo invalidos: " + periodDays);
            if (connections <= 0) throw new IllegalArgumentException("balanco por ligacao exige ligacoes > 0");
            requireNonNegative(systemInputM3, "volume de entrada");
            requireNonNegative(exportedM3, "volume exportado");
            requireNonNegative(billedMeteredAdjustedM3, "faturado micromedido");
            requireNonNegative(billedUnmeteredM3, "faturado nao micromedido");
            requireNonNegative(authorizedUnbilledM3, "autorizado nao faturado");
            if (apparentLossesM3OrNull != null) requireNonNegative(apparentLossesM3OrNull, "perdas aparentes");
            if (exportedM3 > systemInputM3) throw new IllegalArgumentException(
                    "exportado em bloco maior que a entrada do sistema");
            if (measuredShareOfInput < 0 || measuredShareOfInput > 1) throw new IllegalArgumentException(
                    "fracao medida da entrada fora de [0,1]: " + measuredShareOfInput);
            if (targetLPerConnDayOrNull != null && targetLPerConnDayOrNull <= 0)
                throw new IllegalArgumentException("meta de perdas deve ser positiva quando declarada");
            if (incidentAnnex == null) incidentAnnex = IncidentLossInputs.none();
        }
        private static void requireDeclared(Double value, String what) {
            if (value == null) throw new IllegalArgumentException(
                    what + " nao declarado: o balanco nao calcula com marcador no lugar da entrada (OPE-01)");
            if (Double.isNaN(value)) throw new IllegalArgumentException(what + " NaN");
        }
        private static void requireNonNegative(double value, String what) {
            if (value < 0) throw new IllegalArgumentException(what + " negativo: " + value);
        }
    }

    public record DmcMonthlyBalance(UUID dmcId, YearMonth period,
                                    double netInputM3, double authorizedConsumptionM3,
                                    double billedTotalM3, double totalLossesM3,
                                    Double apparentLossesM3OrNull, Double realLossesM3OrNull,
                                    double nonRevenueWaterM3, double nonRevenuePercent,
                                    Double realLossesLPerConnDayOrNull, Double iliOrNull,
                                    ConfidenceGrade confidence, TargetStatus targetStatus,
                                    MicroPeriodAdjustment.Method adjustmentMethod,
                                    IncidentLossInputs incidentAnnex,
                                    boolean incidentVolumeExceedsRealLosses) {}

    /** Balanco de setor: soma dos balancos dos seus DMCs (perda real so se TODOS a conhecem). */
    public record SectorMonthlyBalance(String sectorCode, YearMonth period, int dmcCount,
                                       double netInputM3, double authorizedConsumptionM3,
                                       double billedTotalM3, double totalLossesM3,
                                       Double realLossesM3OrNull, double nonRevenueWaterM3,
                                       double nonRevenuePercent) {}

    private WaterBalanceCalculator() {}

    public static DmcMonthlyBalance calculate(Inputs in) {
        double netInput = in.systemInputM3() - in.exportedM3();
        double billedTotal = in.billedMeteredAdjustedM3() + in.billedUnmeteredM3();
        double authorized = billedTotal + in.authorizedUnbilledM3();
        double totalLosses = netInput - authorized;
        Double realLosses = in.apparentLossesM3OrNull() == null ? null
                : totalLosses - in.apparentLossesM3OrNull();
        double nrw = netInput - billedTotal;
        double nrwPct = netInput <= 0 ? 0.0 : nrw / netInput * 100.0;
        Double realPerConnDay = realLosses == null ? null
                : realLosses * 1000.0 / in.connections() / in.periodDays();
        Double ili = ili(in, realLosses);
        ConfidenceGrade confidence = confidence(in);
        TargetStatus targetStatus = realPerConnDay == null || in.targetLPerConnDayOrNull() == null
                ? TargetStatus.NO_TARGET
                : (realPerConnDay <= in.targetLPerConnDayOrNull()
                        ? TargetStatus.AT_OR_BELOW_TARGET : TargetStatus.ABOVE_TARGET);
        boolean annexExceeds = in.incidentAnnex().totalLostVolumeM3OrNull() != null
                && realLosses != null && in.incidentAnnex().totalLostVolumeM3OrNull() > realLosses;
        return new DmcMonthlyBalance(in.dmcId(), in.period(), netInput, authorized, billedTotal,
                totalLosses, in.apparentLossesM3OrNull(), realLosses, nrw, nrwPct,
                realPerConnDay, ili, confidence, targetStatus, in.adjustmentMethod(),
                in.incidentAnnex(), annexExceeds);
    }

    private static Double ili(Inputs in, Double realLossesM3) {
        if (realLossesM3 == null || in.mainsLengthKmOrNull() == null
                || in.avgPressureMOrNull() == null || in.servicePipeLengthKmOrNull() == null)
            return null;
        double uarlLPerDay = (18.0 * in.mainsLengthKmOrNull()
                + 0.8 * in.connections()
                + 25.0 * in.servicePipeLengthKmOrNull()) * in.avgPressureMOrNull();
        if (uarlLPerDay <= 0) return null;
        double carlLPerDay = realLossesM3 * 1000.0 / in.periodDays();
        return carlLPerDay / uarlLPerDay;
    }

    private static ConfidenceGrade confidence(Inputs in) {
        double share = in.measuredShareOfInput();
        if (share >= 0.9 && in.apparentLossesM3OrNull() != null) return ConfidenceGrade.HIGH;
        if (share >= 0.6) return ConfidenceGrade.MEDIUM;
        return ConfidenceGrade.LOW;
    }

    public static SectorMonthlyBalance aggregateSector(String sectorCode, List<DmcMonthlyBalance> balances) {
        if (sectorCode == null || sectorCode.isBlank())
            throw new IllegalArgumentException("setor do agregado obrigatorio");
        Objects.requireNonNull(balances, "balancos dos DMCs obrigatorios");
        if (balances.isEmpty()) throw new IllegalArgumentException("agregado de setor sem nenhum DMC");
        YearMonth period = balances.get(0).period();
        if (balances.stream().anyMatch(b -> !b.period().equals(period)))
            throw new IllegalArgumentException("agregado de setor exige o mesmo periodo em todos os DMCs");
        double net = balances.stream().mapToDouble(DmcMonthlyBalance::netInputM3).sum();
        double authorized = balances.stream().mapToDouble(DmcMonthlyBalance::authorizedConsumptionM3).sum();
        double billed = balances.stream().mapToDouble(DmcMonthlyBalance::billedTotalM3).sum();
        double totalLosses = balances.stream().mapToDouble(DmcMonthlyBalance::totalLossesM3).sum();
        Double real = balances.stream().anyMatch(b -> b.realLossesM3OrNull() == null) ? null
                : balances.stream().mapToDouble(b -> b.realLossesM3OrNull()).sum();
        double nrw = net - billed;
        double nrwPct = net <= 0 ? 0.0 : nrw / net * 100.0;
        return new SectorMonthlyBalance(sectorCode, period, balances.size(), net, authorized,
                billed, totalLosses, real, nrw, nrwPct);
    }

    /** Entrada real do balanco para a ED-78 (NR 15) e para o SINISA da ED-48: leitura sem recalculo. */
    public static Optional<DmcMonthlyBalance> realBalanceEntry(List<DmcMonthlyBalance> balances,
                                                               UUID dmcId, YearMonth period) {
        return balances.stream()
                .filter(b -> b.dmcId().equals(dmcId) && b.period().equals(period))
                .findFirst();
    }
}
