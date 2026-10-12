package br.com.sigsan.operations.metering.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Dados de teste. <b>TODOS OS VALORES DESTE ARQUIVO SÃO FICTÍCIOS.</b> Eles reproduzem os
 * parâmetros ilustrativos da seção 10 do relatório 02 (docs/levantamento) para que os casos
 * R-*, L-*, A-*, X-*, M-*, N-* possam ser conferidos contra o relatório. Nenhum deles é política
 * de prestador, norma ou valor de produção; o código de produção não tem nenhum desses números.
 */
final class Fixtures {

    private Fixtures() {
    }

    static final YearMonth REF = YearMonth.of(2026, 10);

    // ---- Política fictícia -------------------------------------------------------------------

    /** Faixas ilustrativas do relatório 02 (2.5): cortes 10/20/45/100 e fatores 0,4-1,6 / 0,5-1,5 / 0,6-1,4 / 0,7-1,3. */
    static ExpectedRangePolicy rangePolicy() {
        return new ExpectedRangePolicy(List.of(
                new ExpectedRangePolicy.Band(10, bd("0"), 0, bd("1"), 10),
                new ExpectedRangePolicy.Band(20, bd("0.4"), 0, bd("1.6"), 0),
                new ExpectedRangePolicy.Band(45, bd("0.5"), 0, bd("1.5"), 0),
                new ExpectedRangePolicy.Band(100, bd("0.6"), 0, bd("1.4"), 0),
                new ExpectedRangePolicy.Band(null, bd("0.7"), 0, bd("1.3"), 0)));
    }

    static AveragePolicy averagePolicy() {
        return new AveragePolicy(6, 12, EnumSet.allOf(ConsumptionType.class),
                EnumSet.allOf(ConsumptionAnomalyKind.class));
    }

    static ReadingAnomalyRule stoppedMeterRule() {
        return anomaly("PARADO", ReadingEffect.PREVIOUS, ReadingEffect.INFORMED, ConsumptionEffect.AVERAGE,
                ConsumptionEffect.NORMAL, null, null);
    }

    static ReadingAnomalyRule stoppedMeterNoConsumptionRule() {
        return anomaly("PARADO-SC", ReadingEffect.PREVIOUS, ReadingEffect.INFORMED, ConsumptionEffect.AVERAGE,
                ConsumptionEffect.NORMAL, null, null);
    }

    static ConsumptionPolicy policy() {
        return policy(NonMeteredMode.BY_TARIFF, List.of(), stoppedMeterRule(), stoppedMeterNoConsumptionRule());
    }

    static ConsumptionPolicy policy(NonMeteredMode mode) {
        return policy(mode, List.of(), stoppedMeterRule(), stoppedMeterNoConsumptionRule());
    }

    static ConsumptionPolicy policy(List<ConsumptionAnomalyAction> actions) {
        return policy(NonMeteredMode.BY_TARIFF, actions, stoppedMeterRule(), stoppedMeterNoConsumptionRule());
    }

    static ConsumptionPolicy policy(NonMeteredMode mode, List<ConsumptionAnomalyAction> actions,
                                    ReadingAnomalyRule stopped, ReadingAnomalyRule stoppedNoConsumption) {
        return new ConsumptionPolicy(averagePolicy(), rangePolicy(),
                new RolloverPolicy(200, bd("2")), 5, 3, 1, stopped, stoppedNoConsumption, mode, actions);
    }

    // ---- Regras de anormalidade ----------------------------------------------------------------

    static ReadingAnomalyRule anomaly(String code, ReadingEffect readingWithout, ReadingEffect readingWith,
                                      ConsumptionEffect consumptionWithout, ConsumptionEffect consumptionWith,
                                      BigDecimal factorWithout, BigDecimal factorWith) {
        return new ReadingAnomalyRule(code, readingWithout, readingWith, consumptionWithout, consumptionWith,
                factorWithout, factorWith, false);
    }

    static ReadingAnomalyRule noMeterAnomaly(String code) {
        return new ReadingAnomalyRule(code, ReadingEffect.PREVIOUS, ReadingEffect.INFORMED,
                ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL, null, null, true);
    }

    // ---- Economias -----------------------------------------------------------------------------

    /** Patamares ilustrativos do relatório 02 (10.8): estouro 50 / 5x, alto 40 / 3x, baixo média>10 e <40%. */
    static CategoryThresholds thresholds(Integer burstMaximum) {
        return new CategoryThresholds(50, bd("5"), burstMaximum, 40, bd("3"), 10, bd("40"));
    }

    static EconomyGroup res(int economies) {
        return new EconomyGroup("RES", economies, 10, null, thresholds(150), List.of(), List.of());
    }

    static EconomyGroup res(int economies, Integer burstMaximum) {
        return new EconomyGroup("RES", economies, 10, null, thresholds(burstMaximum), List.of(), List.of());
    }

    static EconomyGroup com(int economies, int minimumPerEconomy) {
        return new EconomyGroup("COM", economies, minimumPerEconomy, null, thresholds(150), List.of(), List.of());
    }

    /** Grupo sem patamares: a detecção de estouro/alto/baixo fica desligada (com aviso). */
    static EconomyGroup resWithoutThresholds(int economies) {
        return new EconomyGroup("RES", economies, 10, null, null, List.of(), List.of());
    }

    // ---- Histórico -----------------------------------------------------------------------------

    /** Seis meses (set a abr de 2026) com o mesmo consumo, REAL e sem anormalidade: média = valor. */
    static List<HistoryEntry> history(int each) {
        List<HistoryEntry> list = new ArrayList<>();
        for (int back = 1; back <= 6; back++) {
            list.add(new HistoryEntry(REF.minusMonths(back), each, ConsumptionType.REAL, null));
        }
        return list;
    }

    static List<HistoryEntry> historyWithAnomalies(int each, ConsumptionAnomalyKind last, ConsumptionAnomalyKind beforeLast) {
        List<HistoryEntry> list = new ArrayList<>();
        for (int back = 1; back <= 6; back++) {
            ConsumptionAnomalyKind kind = back == 1 ? last : back == 2 ? beforeLast : null;
            list.add(new HistoryEntry(REF.minusMonths(back), each, ConsumptionType.REAL, kind));
        }
        return list;
    }

    // ---- Leitura -------------------------------------------------------------------------------

    static ReadingBuilder reading(int previous, Integer current) {
        return new ReadingBuilder(previous, current);
    }

    /** Monta {@link MeterReadingData}. Padrão: 5 dígitos, 30 dias (01/09 a 01/10), ciclo de 30, leitura anterior realizada. */
    static final class ReadingBuilder {
        int digits = 5;
        Integer previousBilled;
        Integer previousInformed;
        LocalDate previousDate = LocalDate.of(2026, 9, 1);
        Integer current;
        LocalDate currentDate = LocalDate.of(2026, 10, 1);
        LocalDate cycleDate = LocalDate.of(2026, 10, 1);
        int cycleDays = 30;
        boolean routeAdjusts = false;
        ReadingStatus previousStatus = ReadingStatus.REALIZED;
        ReadingStatus currentStatus = ReadingStatus.REALIZED;
        ReadingAnomalyRule anomaly;
        Integer informedConsumption;
        LocalDate installationDate;
        Integer installationReading;

        ReadingBuilder(int previous, Integer current) {
            this.previousBilled = previous;
            this.current = current;
            if (current == null) {
                this.currentDate = null;
                this.currentStatus = null;
            }
        }

        ReadingBuilder digits(int v) { digits = v; return this; }
        ReadingBuilder previousInformed(Integer v) { previousInformed = v; return this; }
        ReadingBuilder previousDate(LocalDate v) { previousDate = v; return this; }
        ReadingBuilder currentDate(LocalDate v) { currentDate = v; return this; }
        ReadingBuilder cycleDate(LocalDate v) { cycleDate = v; return this; }
        ReadingBuilder cycleDays(int v) { cycleDays = v; return this; }
        ReadingBuilder routeAdjusts(boolean v) { routeAdjusts = v; return this; }
        ReadingBuilder previousStatus(ReadingStatus v) { previousStatus = v; return this; }
        ReadingBuilder currentStatus(ReadingStatus v) { currentStatus = v; return this; }
        ReadingBuilder anomaly(ReadingAnomalyRule v) { anomaly = v; return this; }
        ReadingBuilder informedConsumption(Integer v) { informedConsumption = v; return this; }
        ReadingBuilder installation(LocalDate date, Integer reading) {
            installationDate = date;
            installationReading = reading;
            return this;
        }

        MeterReadingData build() {
            return new MeterReadingData(digits, previousBilled, previousInformed, previousDate, current,
                    currentDate, cycleDate, cycleDays, routeAdjusts, previousStatus, currentStatus, anomaly,
                    informedConsumption, installationDate, installationReading);
        }
    }

    // ---- Entrada -------------------------------------------------------------------------------

    static ConnectionCondition defaultConnection() {
        return new ConnectionCondition(true, false, false, false, false, EnumSet.allOf(ConsumptionType.class));
    }

    static InputBuilder input(MeterReadingData meter, List<EconomyGroup> groups, List<HistoryEntry> history) {
        return new InputBuilder(meter, groups, history);
    }

    static final class InputBuilder {
        YearMonth reference = REF;
        final List<EconomyGroup> groups;
        final MeterReadingData meter;
        ConnectionCondition connection = defaultConnection();
        final List<HistoryEntry> history;
        Integer fixedMinimum;
        SpecialSituation special;
        NonMeteredFacts facts = NonMeteredFacts.NONE;

        InputBuilder(MeterReadingData meter, List<EconomyGroup> groups, List<HistoryEntry> history) {
            this.meter = meter;
            this.groups = groups;
            this.history = history;
        }

        InputBuilder connection(ConnectionCondition v) { connection = v; return this; }
        InputBuilder fixedMinimum(Integer v) { fixedMinimum = v; return this; }
        InputBuilder special(SpecialSituation v) { special = v; return this; }
        InputBuilder facts(NonMeteredFacts v) { facts = v; return this; }

        InputBuilder cut() {
            ConnectionCondition c = connection;
            connection = new ConnectionCondition(c.suppliedWithWater(), true, c.hasWell(), c.seasonal(),
                    c.sewerMinimumFixed(), c.billableTypes());
            return this;
        }

        InputBuilder withWell() {
            ConnectionCondition c = connection;
            connection = new ConnectionCondition(c.suppliedWithWater(), c.cut(), true, c.seasonal(),
                    c.sewerMinimumFixed(), c.billableTypes());
            return this;
        }

        InputBuilder seasonal() {
            ConnectionCondition c = connection;
            connection = new ConnectionCondition(c.suppliedWithWater(), c.cut(), c.hasWell(), true,
                    c.sewerMinimumFixed(), c.billableTypes());
            return this;
        }

        InputBuilder notSupplied() {
            ConnectionCondition c = connection;
            connection = new ConnectionCondition(false, c.cut(), c.hasWell(), c.seasonal(),
                    c.sewerMinimumFixed(), c.billableTypes());
            return this;
        }

        InputBuilder billable(ConsumptionType... types) {
            ConnectionCondition c = connection;
            Set<ConsumptionType> set = EnumSet.noneOf(ConsumptionType.class);
            set.addAll(Arrays.asList(types));
            connection = new ConnectionCondition(c.suppliedWithWater(), c.cut(), c.hasWell(), c.seasonal(),
                    c.sewerMinimumFixed(), set);
            return this;
        }

        ConsumptionInput build() {
            return new ConsumptionInput(reference, groups, meter, connection, history, fixedMinimum, special, facts);
        }
    }

    static ConsumptionResult calc(ConsumptionPolicy policy, InputBuilder input) {
        return new ConsumptionCalculator(policy).calculate(input.build());
    }

    /** Caso padrão com hidrômetro, 1 economia RES, histórico de {@code avg} m³. */
    static ConsumptionResult calcRes(int avg, ReadingBuilder reading) {
        return calc(policy(), input(reading.build(), List.of(res(1)), history(avg)));
    }

    static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
