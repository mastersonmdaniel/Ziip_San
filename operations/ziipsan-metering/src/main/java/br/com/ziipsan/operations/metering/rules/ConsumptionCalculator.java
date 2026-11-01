package br.com.ziipsan.operations.metering.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Cascata do consumo de água faturado de uma unidade em um mês: o coração do módulo
 * (relatório 02, seção 3, escrito do zero, sem I/O).
 *
 * <h2>Ordem dos passos (ligação com hidrômetro)</h2>
 * <ol>
 *   <li>Média e consumo mínimo da ligação ({@link AverageConsumption}, {@link ConnectionMinimum}).</li>
 *   <li>Leitura: hidrômetro substituído no período; leitura atual maior, igual (hidrômetro parado) ou
 *       menor (virada) que a anterior; ou ausência de leitura.</li>
 *   <li>Anormalidade de leitura (informada ou gerada), com e sem leitura.</li>
 *   <li>Ajuste mensal por dias, se a rota o faz.</li>
 *   <li>Estouro, alto e baixo consumo, com a ação escalonada por reincidência.</li>
 *   <li>Situação especial de faturamento.</li>
 *   <li>Consumo mínimo fixado na ligação, ou consumo informado.</li>
 *   <li>Ajuste de múltiplas economias (o resto não é faturado).</li>
 *   <li>Permissão de faturar pelo tipo de consumo.</li>
 * </ol>
 * O relatório 02 enumera, no resumo (0.2), estouro/alto/baixo <i>antes</i> do ajuste por dias, e, no
 * detalhe (3.3, passos 5 e 6), depois. Vale o detalhe: comparar o consumo com patamares mensais só
 * faz sentido depois de normalizá-lo ao ciclo (35 dias em ciclo de 30 não é estouro).
 *
 * <h2>Rateio e esgoto</h2>
 * Fora desta classe, em {@link CondominiumApportionment} e {@link SewageVolumeCalculator}: o rateio
 * depende dos consumos de todos os micros e do macro, e o esgoto depende do resultado da água.
 *
 * <h2>Arredondamento (um por operação)</h2>
 * <ul>
 *   <li>Média: truncamento ({@link AverageConsumption}).</li>
 *   <li>Fator de anormalidade de leitura: truncamento (DOWN).</li>
 *   <li>Fator da ação de estouro/alto/baixo: HALF_UP para inteiro.</li>
 *   <li>Leitura ausente fora da tolerância: média diária com 3 casas HALF_DOWN, vezes os dias, HALF_UP
 *       para inteiro.</li>
 *   <li>Ajuste por dias: média diária com 4 casas HALF_UP, vezes dias, HALF_UP para inteiro (consumo
 *       e leitura).</li>
 *   <li>Patamares "vezes a média": HALF_UP para inteiro; baixo consumo compara sem arredondar.</li>
 *   <li>Múltiplas economias: divisão inteira (DOWN).</li>
 * </ul>
 *
 * <h2>O que lança e o que avisa</h2>
 * Dado obrigatório ausente lança {@link MeteringInputException}. Aproximação, regra não aplicável e
 * anormalidade substituída por outra viram {@link CalculationWarning}: nada é descartado em silêncio.
 *
 * <p>Instâncias são imutáveis e seguras entre threads; o estado de cada cálculo é local.
 */
public final class ConsumptionCalculator {

    private final ConsumptionPolicy policy;

    public ConsumptionCalculator(ConsumptionPolicy policy) {
        this.policy = MeteringInputException.notNull(policy, "policy");
    }

    public ConsumptionResult calculate(ConsumptionInput input) {
        MeteringInputException.notNull(input, "input");
        return new Run(policy, input).execute();
    }

    /** Estado de um cálculo. */
    private static final class Run {

        private final ConsumptionPolicy policy;
        private final ConsumptionInput in;
        private final List<CalculationWarning> warnings = new ArrayList<>();

        private int connectionMinimum;
        private AverageResult avg;

        private Integer consumption;
        private ConsumptionType type;
        private ConsumptionAnomalyKind anomaly;
        private ReadingAnomalyRule billedRule;
        private Integer billedReading;
        private LocalDate billedDate;
        private Integer actionMonth;
        private boolean billingPaused;
        private int discarded;

        /** Leitura anterior que serve de base à diferença. */
        private int previousReading;
        /** Leitura atual já limitada à capacidade do mostrador; nula sem leitura. */
        private Integer currentReading;

        Run(ConsumptionPolicy policy, ConsumptionInput in) {
            this.policy = policy;
            this.in = in;
        }

        ConsumptionResult execute() {
            connectionMinimum = ConnectionMinimum.of(in.economyGroups());
            avg = AverageConsumption.compute(in.reference(), in.history(), policy.average(), connectionMinimum);
            // Sem hidrômetro a média não entra no cálculo: o aviso de "sem histórico" só vale se ela for usada.
            if (in.meter() != null) {
                ensureAverageWarnings();
            }

            if (in.meter() == null) {
                notMetered();
            } else {
                metered(in.meter());
            }
            return finish();
        }

        // ------------------------------------------------------------------------------------
        // Ligação sem hidrômetro (3.9)
        // ------------------------------------------------------------------------------------

        private void notMetered() {
            if (in.fixedMinimumConsumption() != null) {
                consumption = in.fixedMinimumConsumption();
                type = ConsumptionType.MINIMUM_FIXED;
            } else {
                consumption = notMeteredValue();
                type = ConsumptionType.NOT_METERED;
            }
            applySpecialSituation();
        }

        private int notMeteredValue() {
            NonMeteredConsumption.Result r = NonMeteredConsumption.compute(policy.nonMeteredMode(),
                    in.economyGroups(), in.nonMeteredFacts(), in.reference());
            warnings.addAll(r.warnings());
            return r.consumption();
        }

        // ------------------------------------------------------------------------------------
        // Ligação com hidrômetro (3.3)
        // ------------------------------------------------------------------------------------

        private void metered(MeterReadingData m) {
            Integer informed = m.currentReadingInformed();
            Integer previousInformed = m.previousReadingInformed();
            // O legado usa a leitura anterior informada quando ela é igual à atual informada
            // (hidrômetro parado); senão, a anterior faturada.
            previousReading = (informed != null && previousInformed != null && previousInformed.equals(informed))
                    ? previousInformed : m.previousReadingBilled();

            if (informed != null) {
                int limit = (int) ReadingValidator.dialMaximum(m.digits());
                if (informed > limit) {
                    warn(WarningCode.READING_CLAMPED_TO_DIAL_CAPACITY, "Leitura " + informed
                            + " excede o mostrador de " + m.digits() + " dígitos; usado " + limit);
                    currentReading = limit;
                } else {
                    currentReading = informed;
                }
            }
            billedRule = m.informedAnomaly();

            if (currentReading != null) {
                if (m.replacedInPeriod()) {
                    meterReplaced(m);
                } else {
                    withReading(m);
                }
            } else if (billedRule == null) {
                withoutReadingOrAnomaly(m);
            }

            if (billedRule != null) {
                applyReadingAnomaly(m);
            }
            applyDayAdjustment(m);
            detectConsumptionAnomalies(m);
            applySpecialSituation();
            applyFixedMinimumOrInformed(m);
            if (in.connection().cut()) {
                warn(WarningCode.CUT_CONNECTION_DAYS_RULE_NOT_IMPLEMENTED,
                        "Ligação cortada: a regra de dias de corte (relatório 02, 3.3 passo 9) não é aplicada por este módulo");
            }
            applyMultiEconomyAdjustment(m);
        }

        private ConsumptionType realOrEstimated(MeterReadingData m) {
            return m.previousStatus().isReal() ? ConsumptionType.REAL : ConsumptionType.ESTIMATED;
        }

        /** Passo 1: hidrômetro substituído dentro do período. */
        private void meterReplaced(MeterReadingData m) {
            MeteringInputException.require(m.installationReading() != null,
                    MeteringErrorCode.MISSING_INSTALLATION_READING,
                    "hidrômetro instalado em " + m.installationDate() + " (dentro do período) sem leitura de instalação");
            if (currentReading > m.installationReading()) {
                consumption = currentReading - m.installationReading();
                type = realOrEstimated(m);
            } else {
                consumption = avg.average();
                type = ConsumptionType.METER_AVERAGE;
            }
            anomaly = ConsumptionAnomalyKind.METER_REPLACED_INFORMED;
            billedReading = currentReading;
            billedDate = m.currentReadingDate();
            warn(WarningCode.REPLACED_METER_PRIOR_CONSUMPTION_EXCLUDED,
                    "Hidrômetro substituído em " + m.installationDate()
                            + ": o consumo do hidrômetro anterior até a retirada não é somado (relatório 02, 3.3 passo 1)");
        }

        /** Passo 2: leitura atual informada. */
        private void withReading(MeterReadingData m) {
            billedReading = currentReading;
            billedDate = m.currentReadingDate();
            if (currentReading > previousReading) {
                consumption = currentReading - previousReading;
                type = realOrEstimated(m);
                ExpectedReadingRange range = ReadingValidator.expectedRange(avg.average(), previousReading,
                        m.digits(), policy.expectedRange());
                if (range.isOutOfRange(currentReading)) {
                    setAnomaly(ConsumptionAnomalyKind.OUT_OF_RANGE);
                }
            } else if (currentReading == previousReading) {
                consumption = 0;
                type = realOrEstimated(m);
                detectStoppedMeter(m);
            } else {
                rollover(m);
            }
        }

        /** Leitura igual à anterior: gera a anormalidade de hidrômetro parado, se as condições valem. */
        private void detectStoppedMeter(MeterReadingData m) {
            ConnectionCondition c = in.connection();
            if (m.informedAnomaly() == null && currentReading > policy.stoppedMeterReadingFloor()
                    && c.suppliedWithWater() && !c.seasonal()) {
                boolean otherSource = c.hasWell() || c.sewerMinimumFixed();
                ReadingAnomalyRule rule = otherSource ? policy.stoppedMeterNoConsumptionRule()
                        : policy.stoppedMeterRule();
                if (rule == null) {
                    warn(WarningCode.STOPPED_METER_RULE_NOT_CONFIGURED, "Hidrômetro parado detectado ("
                            + (otherSource ? "com poço ou esgoto com mínimo fixado" : "sem poço nem esgoto mínimo")
                            + "), mas o prestador não configurou a anormalidade de leitura correspondente");
                } else {
                    billedRule = rule;
                }
            }
        }

        /** Leitura atual menor que a anterior: virada do mostrador ou leitura inválida. */
        private void rollover(MeterReadingData m) {
            long modulus = ReadingValidator.dialModulus(m.digits());
            long calculated = currentReading + modulus - previousReading;
            BigDecimal limitByAverage = policy.rollover().averageMultiple().multiply(BigDecimal.valueOf(avg.average()));
            boolean plausible = calculated > 0
                    && (calculated <= policy.rollover().maxPlausibleConsumption()
                    || BigDecimal.valueOf(calculated).compareTo(limitByAverage) <= 0);
            if (plausible) {
                consumption = Math.toIntExact(calculated);
                type = realOrEstimated(m);
                setAnomaly(ConsumptionAnomalyKind.METER_ROLLOVER);
            } else {
                consumption = avg.average();
                type = ConsumptionType.METER_AVERAGE;
                setAnomaly(m.previousStatus().isReal() ? ConsumptionAnomalyKind.READING_BELOW_PREVIOUS
                        : ConsumptionAnomalyKind.READING_BELOW_PROJECTED);
                billedReading = wrap(previousReading + (long) avg.average(), m.digits());
            }
        }

        /** Passo 3: sem leitura e sem anormalidade. */
        private void withoutReadingOrAnomaly(MeterReadingData m) {
            long days = ChronoUnit.DAYS.between(m.previousReadingDate(), m.cycleDate());
            MeteringInputException.require(days >= 0, MeteringErrorCode.INVALID_DATES,
                    "data do cronograma anterior à leitura anterior");
            if (Math.abs(days - m.cycleDays()) <= policy.noReadingToleranceDays()) {
                consumption = avg.average();
                type = ConsumptionType.METER_AVERAGE;
            } else {
                BigDecimal daily = BigDecimal.valueOf(avg.average())
                        .divide(BigDecimal.valueOf(m.cycleDays()), 3, RoundingMode.HALF_DOWN);
                consumption = Rounding.halfUp(daily.multiply(BigDecimal.valueOf(days)));
                type = ConsumptionType.ADJUSTED_AVERAGE;
            }
            setAnomaly(ConsumptionAnomalyKind.READING_NOT_INFORMED);
            billedReading = wrap(previousReading + (long) consumption, m.digits());
            billedDate = m.cycleDate();
        }

        /** Passo 4: efeito da anormalidade de leitura (informada ou gerada). */
        private void applyReadingAnomaly(MeterReadingData m) {
            ReadingAnomalyRule rule = billedRule;
            if (currentReading == null) {
                setAnomaly(ConsumptionAnomalyKind.READING_ANOMALY);
                applyEffectWithoutReading(rule.consumptionEffectWithoutReading());
                billedReading = switch (rule.readingEffectWithoutReading()) {
                    case PREVIOUS_PLUS_AVERAGE -> wrap(previousReading + (long) avg.average(), m.digits());
                    case PREVIOUS -> previousReading;
                    case PREVIOUS_PLUS_CONSUMPTION -> wrap(previousReading + (long) consumption, m.digits());
                    case INFORMED -> throw new IllegalStateException("validado no construtor da regra");
                };
                billedDate = m.cycleDate();
                if (rule.factorWithoutReading() != null) {
                    consumption = Rounding.truncate(BigDecimal.valueOf(consumption).multiply(rule.factorWithoutReading()));
                }
            } else {
                applyEffectWithReading(rule.consumptionEffectWithReading());
                billedReading = switch (rule.readingEffectWithReading()) {
                    case PREVIOUS_PLUS_AVERAGE -> wrap(previousReading + (long) avg.average(), m.digits());
                    case PREVIOUS -> previousReading;
                    case PREVIOUS_PLUS_CONSUMPTION -> wrap(previousReading + (long) consumption, m.digits());
                    case INFORMED -> billedReading;
                };
                if (rule.factorWithReading() != null) {
                    consumption = Rounding.truncate(BigDecimal.valueOf(consumption).multiply(rule.factorWithReading()));
                }
            }
        }

        /** Efeito sobre o consumo quando <b>não</b> há leitura atual (3.4, "sem leitura"). */
        private void applyEffectWithoutReading(ConsumptionEffect effect) {
            switch (effect) {
                case AVERAGE -> {
                    ensureAverageWarnings();
                    consumption = avg.average();
                    type = ConsumptionType.METER_AVERAGE;
                }
                case MINIMUM -> {
                    consumption = connectionMinimum;
                    type = ConsumptionType.NO_CONSUMPTION;
                }
                case NOT_METERED -> {
                    consumption = notMeteredValue();
                    type = ConsumptionType.ESTIMATED;
                }
                default -> throw new MeteringInputException(MeteringErrorCode.EFFECT_NOT_APPLICABLE,
                        "efeito " + effect + " não se aplica sem leitura");
            }
        }

        /** Efeito sobre o consumo quando há leitura atual (3.4, "com leitura"; também a ação de 3.6). */
        private void applyEffectWithReading(ConsumptionEffect effect) {
            switch (effect) {
                case AVERAGE -> {
                    consumption = avg.average();
                    type = ConsumptionType.METER_AVERAGE;
                }
                case MINIMUM -> {
                    consumption = 0;
                    type = ConsumptionType.NO_CONSUMPTION;
                }
                case NORMAL -> {
                    // mantém o consumo calculado
                }
                case MAX_OF_AVERAGE_AND_MEASURED -> {
                    if (avg.average() > consumption) {
                        consumption = avg.average();
                        type = ConsumptionType.METER_AVERAGE;
                    }
                }
                case MIN_OF_AVERAGE_AND_MEASURED -> {
                    if (avg.average() < consumption) {
                        consumption = avg.average();
                        type = ConsumptionType.METER_AVERAGE;
                    }
                }
                case NOT_METERED -> {
                    consumption = notMeteredValue();
                    type = ConsumptionType.ESTIMATED;
                }
            }
        }

        /** Passo 5: ajuste mensal por dias. */
        private void applyDayAdjustment(MeterReadingData m) {
            if (!m.routeAdjustsByDays() || currentReading == null) {
                return;
            }
            if (m.replacedInPeriod()) {
                warn(WarningCode.DAY_ADJUSTMENT_SKIPPED_METER_REPLACED,
                        "Hidrômetro substituído no período: o ajuste por dias (consumo diário desde a instalação) não é aplicado por este módulo");
                return;
            }
            // O relatório não restringe o tipo; só o consumo obtido de diferença de leituras é
            // proporcionalizado, porque normalizar uma média já substituída por regra não faz sentido.
            if (type != ConsumptionType.REAL && type != ConsumptionType.ESTIMATED) {
                return;
            }
            long actualDays = ChronoUnit.DAYS.between(m.previousReadingDate(), m.currentReadingDate());
            if (actualDays <= 0) {
                warn(WarningCode.DAY_ADJUSTMENT_SKIPPED_ZERO_DAYS,
                        "Leitura atual na mesma data da anterior: ajuste por dias não aplicado");
                return;
            }
            long adjustDays = m.cycleDays() - actualDays;
            if (Math.abs(adjustDays) <= policy.dayAdjustmentToleranceDays()) {
                return;
            }
            BigDecimal daily = BigDecimal.valueOf(consumption)
                    .divide(BigDecimal.valueOf(actualDays), 4, RoundingMode.HALF_UP);
            consumption = Rounding.halfUp(daily.multiply(BigDecimal.valueOf(m.cycleDays())));
            type = ConsumptionType.ADJUSTED_AVERAGE;
            billedReading = wrap(currentReading + (long) Rounding.halfUp(daily.multiply(BigDecimal.valueOf(adjustDays))),
                    m.digits());
            billedDate = m.currentReadingDate().plusDays(adjustDays);
        }

        /** Passo 6: estouro, alto e baixo consumo, com a ação escalonada. */
        private void detectConsumptionAnomalies(MeterReadingData m) {
            EconomyGroup principal = principalGroup();
            CategoryThresholds principalThresholds = principal.thresholds();

            ConsumptionAnomalyKind detected = null;
            Boolean burst = exceedsPatterns(ConsumptionAnomalyKind.BURST, CategoryThresholds::burstConsumption,
                    principalThresholds == null ? null : principalThresholds.burstTimesAverage());
            if (Boolean.TRUE.equals(burst)) {
                detected = ConsumptionAnomalyKind.BURST;
            } else {
                Boolean high = exceedsPatterns(ConsumptionAnomalyKind.HIGH_CONSUMPTION,
                        CategoryThresholds::highConsumption,
                        principalThresholds == null ? null : principalThresholds.highTimesAverage());
                if (Boolean.TRUE.equals(high)) {
                    detected = ConsumptionAnomalyKind.HIGH_CONSUMPTION;
                } else if (isLowConsumption(m, principalThresholds)) {
                    detected = ConsumptionAnomalyKind.LOW_CONSUMPTION;
                }
            }
            if (detected == null) {
                return;
            }

            int month = escalationMonth(detected);
            ConsumptionAnomalyAction action = policy.actionFor(detected, principal.categoryCode());
            if (action != null) {
                ConsumptionAnomalyAction.Step step = action.stepFor(month);
                applyEffectWithReading(step.effect());
                if (step.factor() != null) {
                    consumption = Rounding.halfUp(BigDecimal.valueOf(consumption).multiply(step.factor()));
                }
                setAnomaly(detected);
                actionMonth = month;
            } else if (detected == ConsumptionAnomalyKind.BURST) {
                if (month == 1) {
                    consumption = avg.average();
                    type = ConsumptionType.METER_AVERAGE;
                    setAnomaly(ConsumptionAnomalyKind.BURST_CHARGED_AT_AVERAGE);
                } else {
                    setAnomaly(ConsumptionAnomalyKind.BURST);
                    Long maximum = sumPerEconomy(CategoryThresholds::burstMaximum);
                    if (maximum == null) {
                        warn(WarningCode.BURST_MAXIMUM_NOT_CONFIGURED,
                                "Estouro reincidente e consumo máximo por economia não configurado: consumo mantido");
                    } else if (consumption > maximum) {
                        warn(WarningCode.BURST_RECURRENCE_ABOVE_MAXIMUM, "Estouro reincidente de " + consumption
                                + " m³ acima do máximo de " + maximum
                                + " m³: o relatório 02 não diz o que fazer; consumo mantido, requer revisão");
                    }
                }
            } else {
                setAnomaly(detected);
            }
        }

        /**
         * Estouro ou alto consumo: consumo acima da soma dos patamares por economia E acima de
         * "vezes a média" (HALF_UP). Nulo quando os patamares não estão configurados (com aviso).
         */
        private Boolean exceedsPatterns(ConsumptionAnomalyKind kind,
                                        Function<CategoryThresholds, Integer> perEconomy, BigDecimal timesAverage) {
            Long sum = sumPerEconomy(perEconomy);
            if (sum == null || timesAverage == null) {
                warn(WarningCode.THRESHOLDS_NOT_CONFIGURED,
                        "Patamares de " + kind + " não configurados para a categoria: detecção não executada");
                return null;
            }
            int byAverage = Rounding.halfUp(BigDecimal.valueOf(avg.average()).multiply(timesAverage));
            return consumption > sum && consumption > byAverage;
        }

        private boolean isLowConsumption(MeterReadingData m, CategoryThresholds principalThresholds) {
            // Pré-condições do relatório: ligação não cortada, sem poço, leitura realizada ou
            // confirmada e sem anormalidade de leitura.
            boolean applicable = !in.connection().cut() && !in.connection().hasWell()
                    && currentReading != null && m.currentStatus() != null && m.currentStatus().isReal()
                    && billedRule == null;
            if (!applicable) {
                return false;
            }
            Long floor = sumPerEconomy(CategoryThresholds::lowAverageFloor);
            BigDecimal percent = principalThresholds == null ? null : principalThresholds.lowPercentOfAverage();
            if (floor == null || percent == null) {
                warn(WarningCode.THRESHOLDS_NOT_CONFIGURED,
                        "Patamares de LOW_CONSUMPTION não configurados para a categoria: detecção não executada");
                return false;
            }
            // consumo < média * percentual / 100, sem arredondar: consumo * 100 < média * percentual.
            return avg.average() > floor
                    && BigDecimal.valueOf(consumption).multiply(BigDecimal.valueOf(100))
                    .compareTo(BigDecimal.valueOf(avg.average()).multiply(percent)) < 0;
        }

        /** Soma, por categoria, do patamar vezes as economias; nulo se alguma categoria não o configurou. */
        private Long sumPerEconomy(Function<CategoryThresholds, Integer> field) {
            long total = 0;
            for (EconomyGroup g : in.economyGroups()) {
                Integer value = g.thresholds() == null ? null : field.apply(g.thresholds());
                if (value == null) {
                    return null;
                }
                total += (long) value * g.economies();
            }
            return total;
        }

        /** Categoria principal: a de mais economias; em empate, a primeira da lista. */
        private EconomyGroup principalGroup() {
            EconomyGroup principal = in.economyGroups().get(0);
            for (EconomyGroup g : in.economyGroups()) {
                if (g.economies() > principal.economies()) {
                    principal = g;
                }
            }
            return principal;
        }

        /**
         * 1 se a anormalidade (ou a mesma família) não ocorreu no mês anterior; 2 se ocorreu só no
         * anterior; 3 se ocorreu nos dois meses anteriores.
         */
        private int escalationMonth(ConsumptionAnomalyKind detected) {
            boolean previous1 = occurredIn(detected, in.reference().minusMonths(1));
            boolean previous2 = occurredIn(detected, in.reference().minusMonths(2));
            if (!previous1) {
                return 1;
            }
            return previous2 ? 3 : 2;
        }

        private boolean occurredIn(ConsumptionAnomalyKind detected, YearMonth month) {
            return in.history().stream()
                    .anyMatch(h -> h.reference().equals(month) && detected.sameFamilyAs(h.anomaly()));
        }

        /** Passo 7: situação especial de faturamento. */
        private void applySpecialSituation() {
            SpecialSituation s = in.specialSituation();
            if (s == null || !s.appliesTo(in.reference())) {
                return;
            }
            Integer fixed = in.meter() != null ? s.fixedMeteredConsumption() : s.fixedNotMeteredConsumption();
            if (fixed != null) {
                consumption = fixed;
                type = ConsumptionType.SPECIAL_SITUATION_FIXED;
            } else if (s.effect() != null) {
                applyEffectWithoutReading(s.effect());
            } else {
                warn(WarningCode.SPECIAL_SITUATION_WITHOUT_CONSUMPTION_RULE,
                        "Situação especial vigente em " + in.reference()
                                + " sem consumo fixo nem efeito: consumo calculado mantido");
            }
            if (s.pausesBilling()) {
                billingPaused = true;
                warn(WarningCode.BILLING_PAUSED, "Faturamento paralisado por situação especial em " + in.reference());
            }
        }

        /** Passo 8: consumo mínimo fixado na ligação, senão consumo informado. */
        private void applyFixedMinimumOrInformed(MeterReadingData m) {
            Integer fixedMinimum = in.fixedMinimumConsumption();
            if (fixedMinimum != null && consumption < fixedMinimum) {
                consumption = fixedMinimum;
                type = ConsumptionType.MINIMUM_FIXED;
                setAnomaly(ConsumptionAnomalyKind.MINIMUM_FIXED);
            } else if (m.informedConsumption() != null) {
                consumption = m.informedConsumption();
                type = ConsumptionType.INFORMED;
                setAnomaly(ConsumptionAnomalyKind.CONSUMPTION_INFORMED);
            }
        }

        /** Passo 10: com mais de uma economia, o consumo faturado desconta o resto que não fecha por economia. */
        private void applyMultiEconomyAdjustment(MeterReadingData m) {
            int economies = ConnectionMinimum.totalEconomies(in.economyGroups());
            if (economies <= 1) {
                return;
            }
            int measured = consumption;
            int excess = measured - connectionMinimum;
            long remainder;
            if (excess > 0) {
                int perEconomy = excess / economies;
                long adjustedTotal = 0;
                for (EconomyGroup g : in.economyGroups()) {
                    adjustedTotal += (long) perEconomy * g.economies()
                            + (long) g.minimumConsumptionPerEconomy() * g.economies();
                }
                remainder = measured - adjustedTotal;
            } else {
                remainder = measured % economies;
            }
            // Com economias equivalentes o mínimo da ligação deixa de ser a soma dos mínimos por
            // economia e a conta do relatório pode devolver resto negativo (consumo inflado).
            MeteringInputException.require(remainder >= 0, MeteringErrorCode.INCONSISTENT_ECONOMY_ADJUSTMENT,
                    "ajuste de múltiplas economias daria resto negativo (" + remainder
                            + "): economias equivalentes tornam o mínimo da ligação incoerente com o relatório 02, 3.8");
            int rest = (int) remainder;
            if (rest > 0) {
                long dial = ReadingValidator.dialModulus(m.digits());
                if (billedReading != null && billedReading == Math.floorMod(previousReading + (long) measured, dial)) {
                    billedReading = wrap((long) billedReading - rest, m.digits());
                }
                discarded = rest;
                warn(WarningCode.DISCARDED_CONSUMPTION_REMAINDER, rest + " m³ não faturados: o consumo de "
                        + measured + " m³ não se divide em economias inteiras (" + economies + " economias)");
            }
            consumption = measured - rest;
        }

        // ------------------------------------------------------------------------------------

        private ConsumptionResult finish() {
            ConnectionCondition c = in.connection();
            boolean typeBillable = c.billableTypes().contains(type);
            if (!typeBillable) {
                warn(WarningCode.CONSUMPTION_TYPE_NOT_BILLABLE, "Tipo " + type
                        + " não permite faturar nesta situação de ligação: faturado zerado, calculado mantido");
            }
            boolean billable = typeBillable && !billingPaused;
            return new ConsumptionResult(
                    billable ? consumption : 0, consumption, billable, billingPaused, type, anomaly,
                    billedRule == null ? null : billedRule.code(), billedReading, billedDate,
                    avg.average(), avg.monthsConsidered(), avg.source(), connectionMinimum, discarded,
                    actionMonth, warnings);
        }

        /**
         * Registra a anormalidade. Se havia outra, ela é <b>substituída com aviso</b>: a última
         * detectada prevalece (como no legado), mas a anterior fica no resultado.
         */
        private void setAnomaly(ConsumptionAnomalyKind next) {
            if (anomaly != null && anomaly != next) {
                warn(WarningCode.ANOMALY_SUPERSEDED, anomaly + " substituída por " + next);
            }
            anomaly = next;
        }

        private boolean averageWarningsAdded;

        private void ensureAverageWarnings() {
            if (!averageWarningsAdded) {
                warnings.addAll(avg.warnings());
                averageWarningsAdded = true;
            }
        }

        private void warn(WarningCode code, String message) {
            warnings.add(new CalculationWarning(code, message));
        }

        /** Leva uma leitura para dentro do mostrador (módulo {@code 10^dígitos}). */
        private static int wrap(long value, int digits) {
            return (int) Math.floorMod(value, ReadingValidator.dialModulus(digits));
        }
    }
}
