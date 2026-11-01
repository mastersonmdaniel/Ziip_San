package br.com.ziipsan.operations.metering.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static br.com.ziipsan.operations.metering.rules.Fixtures.bd;
import static br.com.ziipsan.operations.metering.rules.Fixtures.calc;
import static br.com.ziipsan.operations.metering.rules.Fixtures.calcRes;
import static br.com.ziipsan.operations.metering.rules.Fixtures.history;
import static br.com.ziipsan.operations.metering.rules.Fixtures.input;
import static br.com.ziipsan.operations.metering.rules.Fixtures.policy;
import static br.com.ziipsan.operations.metering.rules.Fixtures.reading;
import static br.com.ziipsan.operations.metering.rules.Fixtures.res;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cascata do consumo de ligação com hidrômetro (relatório 02, seção 3 e casos L-*, A-*, X-*, M-*, N-05).
 *
 * <p><b>Todos os valores são FICTÍCIOS</b> (ver {@link Fixtures}). Cada resultado esperado foi
 * recalculado à mão, de forma independente, a partir da regra e dos parâmetros do caso, e depois
 * conferido com o relatório. O comentário de cada teste traz a conta. Divergências com o relatório
 * estão marcadas com "DIVERGÊNCIA".
 */
class ConsumptionCalculatorMeasuredTest {

    private static final LocalDate SEP_01 = LocalDate.of(2026, 9, 1);
    private static final LocalDate OCT_01 = LocalDate.of(2026, 10, 1);

    private static List<WarningCode> codes(ConsumptionResult r) {
        return r.warnings().stream().map(CalculationWarning::code).toList();
    }

    // =========================================================================================
    // Leitura (3.3 passo 2)
    // =========================================================================================

    @Nested
    class Reading {

        @ParameterizedTest(name = "L-01/L-02: leitura anterior {0} -> tipo {1}")
        @DisplayName("L-01/L-02: 1027 - 1000 = 27; REAL se a leitura anterior é realizada ou confirmada, senão ESTIMADO")
        @EnumSource(ReadingStatus.class)
        void l01_l02_typeDependsOnPreviousReadingStatus(ReadingStatus previousStatus) {
            // média 25 (6 x 25): faixa esperada [1013, 1038]; 1027 está dentro, sem anormalidade.
            ConsumptionResult r = calcRes(25, reading(1000, 1027).previousStatus(previousStatus));

            assertThat(r.billedConsumption()).isEqualTo(27);
            assertThat(r.type()).isEqualTo(previousStatus.isReal() ? ConsumptionType.REAL : ConsumptionType.ESTIMATED);
            assertThat(r.anomaly()).isNull();
            assertThat(r.billedReading()).isEqualTo(1027);
            assertThat(r.billable()).isTrue();
            assertThat(r.warnings()).isEmpty();
        }

        @ParameterizedTest(name = "{0}: anterior {1}, atual {2}, média {3} -> consumo {5} {6} / {7}, leitura faturada {8}")
        @DisplayName("L-03/L-04: virada do mostrador e leitura menor que a anterior")
        @CsvSource({
                // L-03: calc = 30 + 100000 - 99950 = 80; 80 <= 200 -> virada plausível
                "L-03,  99950, 30,    60, REALIZED,     80,  REAL,          METER_ROLLOVER,           30",
                // L-03b (extra): calc = 400 + 100000 - 99950 = 450 > 200, mas <= 2 x 250 = 500 -> virada
                "L-03b, 99950, 400,  250, REALIZED,     450, REAL,          METER_ROLLOVER,           400",
                // L-03c (extra): leitura anterior não real -> o tipo vira ESTIMADO, a virada continua
                "L-03c, 99950, 30,    60, NOT_REALIZED, 80,  ESTIMATED,     METER_ROLLOVER,           30",
                // L-04: calc = 99000 + 100000 - 99950 = 99050; > 200 e > 2 x 25 = 50 -> não é virada;
                // consumo = média 25; leitura faturada = 99950 + 25 = 99975
                "L-04,  99950, 99000,  25, REALIZED,    25,  METER_AVERAGE, READING_BELOW_PREVIOUS,   99975",
                // L-04b (extra): leitura anterior não realizada -> anormalidade 'projetada'
                "L-04b, 99950, 99000,  25, NOT_REALIZED, 25, METER_AVERAGE, READING_BELOW_PROJECTED,  99975",
                // L-04c (extra): a leitura faturada dá a volta no mostrador: (99990 + 25) mod 100000 = 15
                "L-04c, 99990, 99000,  25, REALIZED,    25,  METER_AVERAGE, READING_BELOW_PREVIOUS,   15",
        })
        void l03_l04_rolloverAndLowerReading(String id, int previous, int current, int average,
                                             ReadingStatus previousStatus, int consumption, ConsumptionType type,
                                             ConsumptionAnomalyKind anomaly, int billedReading) {
            ConsumptionResult r = calcRes(average, reading(previous, current).previousStatus(previousStatus));

            assertThat(r.billedConsumption()).as(id).isEqualTo(consumption);
            assertThat(r.type()).as(id).isEqualTo(type);
            assertThat(r.anomaly()).as(id).isEqualTo(anomaly);
            assertThat(r.billedReading()).as(id).isEqualTo(billedReading);
        }

        @ParameterizedTest(name = "virada: atual {0} -> calc {1}: plausível? {2}")
        @DisplayName("fronteira da plausibilidade da virada: calc <= 200 ou calc <= 2 x média (sem patamares configurados)")
        @CsvSource({
                // média 25 -> 2 x 25 = 50. calc = atual + 100000 - 99950 = atual + 50.
                "150, 200, true",   // calc 200 <= 200
                "151, 201, false",  // 201 > 200 e > 50
                "0,    50, true",   // calc 50 <= 200
        })
        void rolloverPlausibilityBoundary(int current, int calculated, boolean plausible) {
            ConsumptionResult r = calc(policy(), input(reading(99950, current).build(),
                    List.of(Fixtures.resWithoutThresholds(1)), history(25)));
            if (plausible) {
                assertThat(r.billedConsumption()).isEqualTo(calculated);
                assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.METER_ROLLOVER);
            } else {
                assertThat(r.billedConsumption()).isEqualTo(25);
                assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.READING_BELOW_PREVIOUS);
            }
        }

        @Test
        @DisplayName("leitura acima da capacidade do mostrador é limitada ao maior valor, com aviso")
        void readingAboveDialCapacityIsClampedWithWarning() {
            // 5 dígitos: limite 99999. Atual 100500 -> 99999; consumo = 99999 - 99950 = 49.
            // Faixa (média 60: 0,6 x 60 = 36 ; 1,4 x 60 = 84): [99986, 100034 -> 34] atravessa a virada;
            // 99999 >= 99986, dentro.
            ConsumptionResult r = calcRes(60, reading(99950, 100500));

            assertThat(r.billedConsumption()).isEqualTo(49);
            assertThat(r.billedReading()).isEqualTo(99999);
            assertThat(r.anomaly()).isNull();
            assertThat(codes(r)).containsExactly(WarningCode.READING_CLAMPED_TO_DIAL_CAPACITY);
        }

        @Test
        @DisplayName("fora da faixa esperada gera a anormalidade, mas não muda o consumo")
        void outOfRangeOnlyFlags() {
            // média 20: faixa [1008, 1032]; 1040 é maior; consumo 40, que também não é alto nem estouro.
            // 40 > 40 é falso (alto exige > 40); então só FORA_FAIXA.
            ConsumptionResult r = calcRes(20, reading(1000, 1040));
            assertThat(r.billedConsumption()).isEqualTo(40);
            assertThat(r.type()).isEqualTo(ConsumptionType.REAL);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.OUT_OF_RANGE);
        }

        @Test
        @DisplayName("sem leitura anterior faturada o cálculo é recusado, não presume zero")
        void missingPreviousReadingIsRejected() {
            assertThatThrownBy(() -> new MeterReadingData(5, null, null, SEP_01, 1027, OCT_01, OCT_01, 30, false,
                    ReadingStatus.REALIZED, ReadingStatus.REALIZED, null, null, null, null))
                    .isInstanceOfSatisfying(MeteringInputException.class,
                            e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.MISSING_PREVIOUS_READING));
        }
    }

    // =========================================================================================
    // Hidrômetro parado (3.3 passo 2, leitura igual)
    // =========================================================================================

    @Nested
    class StoppedMeter {

        @Test
        @DisplayName("L-05: atual = anterior = 1500 (> 1), abastecida, não sazonal, sem poço: consumo 0 e anormalidade de hidrômetro parado")
        void l05_stoppedMeter() {
            // a leitura anterior informada (1500) é igual à atual, então é ela a base: diferença 0.
            ConsumptionResult r = calcRes(25, reading(1500, 1500).previousInformed(1500));

            assertThat(r.billedConsumption()).isZero();
            assertThat(r.type()).isEqualTo(ConsumptionType.REAL);
            assertThat(r.billedReadingAnomalyCode()).isEqualTo("PARADO");
            assertThat(r.anomaly()).isNull();
            assertThat(r.billedReading()).isEqualTo(1500);
            assertThat(r.warnings()).isEmpty();
        }

        @Test
        @DisplayName("com poço (ou esgoto com mínimo fixado) a anormalidade é a de parado sem consumo")
        void stoppedMeterWithWell() {
            ConsumptionResult r = calc(policy(), input(reading(1500, 1500).previousInformed(1500).build(),
                    List.of(res(1)), history(25)).withWell());
            assertThat(r.billedReadingAnomalyCode()).isEqualTo("PARADO-SC");
        }

        @Test
        @DisplayName("imóvel sazonal, sem abastecimento ou leitura <= piso não geram hidrômetro parado")
        void stoppedMeterConditionsNotMet() {
            assertThat(calc(policy(), input(reading(1500, 1500).build(), List.of(res(1)), history(25)).seasonal())
                    .billedReadingAnomalyCode()).as("sazonal").isNull();
            assertThat(calc(policy(), input(reading(1500, 1500).build(), List.of(res(1)), history(25)).notSupplied())
                    .billedReadingAnomalyCode()).as("sem abastecimento").isNull();
            // piso fictício = 1: leitura 1 não é maior que 1
            assertThat(calcRes(25, reading(1, 1)).billedReadingAnomalyCode()).as("leitura 1").isNull();
            assertThat(calcRes(25, reading(0, 0)).billedReadingAnomalyCode()).as("leitura 0").isNull();
            assertThat(calcRes(25, reading(2, 2)).billedReadingAnomalyCode()).as("leitura 2").isEqualTo("PARADO");
        }

        @Test
        @DisplayName("anormalidade já informada pelo leiturista impede a geração automática")
        void informedAnomalyBlocksGeneration() {
            ReadingAnomalyRule informed = Fixtures.anomaly("OUTRA", ReadingEffect.PREVIOUS, ReadingEffect.INFORMED,
                    ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL, null, null);
            ConsumptionResult r = calcRes(25, reading(1500, 1500).anomaly(informed));
            assertThat(r.billedReadingAnomalyCode()).isEqualTo("OUTRA");
        }

        @Test
        @DisplayName("se o prestador não configurou a anormalidade, o hidrômetro parado vira aviso, não silêncio")
        void notConfiguredIsAWarning() {
            ConsumptionPolicy unconfigured = Fixtures.policy(NonMeteredMode.BY_TARIFF, List.of(), null, null);
            ConsumptionResult r = calc(unconfigured, input(reading(1500, 1500).build(), List.of(res(1)), history(25)));
            assertThat(r.billedConsumption()).isZero();
            assertThat(r.billedReadingAnomalyCode()).isNull();
            assertThat(codes(r)).containsExactly(WarningCode.STOPPED_METER_RULE_NOT_CONFIGURED);
        }
    }

    // =========================================================================================
    // Leitura ausente (3.3 passo 3)
    // =========================================================================================

    @Nested
    class MissingReading {

        @Test
        @DisplayName("L-06: sem leitura, 33 dias: |33 - 30| = 3 <= 5, fatura a média 22")
        void l06_withinToleranceChargesTheAverage() {
            ConsumptionResult r = calcRes(22, reading(1000, null).cycleDate(LocalDate.of(2026, 10, 4)));

            assertThat(r.billedConsumption()).isEqualTo(22);
            assertThat(r.type()).isEqualTo(ConsumptionType.METER_AVERAGE);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.READING_NOT_INFORMED);
            assertThat(r.billedReading()).isEqualTo(1022);
            assertThat(r.billedReadingDate()).isEqualTo(LocalDate.of(2026, 10, 4));
            assertThat(r.warnings()).isEmpty();
        }

        @Test
        @DisplayName("L-07: sem leitura, 38 dias: |38 - 30| = 8 > 5. 22 / 30 = 0,733 (3 casas, HALF_DOWN) x 38 = 27,854 -> 28")
        void l07_outsideToleranceProportionalizesByDays() {
            ConsumptionResult r = calcRes(22, reading(1000, null).cycleDate(LocalDate.of(2026, 10, 9)));

            assertThat(r.billedConsumption()).isEqualTo(28);
            assertThat(r.type()).isEqualTo(ConsumptionType.ADJUSTED_AVERAGE);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.READING_NOT_INFORMED);
            assertThat(r.billedReading()).isEqualTo(1028);
        }

        @ParameterizedTest(name = "{0} dias -> consumo {1} ({2})")
        @DisplayName("fronteira da tolerância (5 dias, ciclo 30): 35 ainda é média; 36 proporcionaliza")
        @CsvSource({
                // 25 dias (|25-30| = 5): média
                "25, 22, METER_AVERAGE",
                // 35 dias: média
                "35, 22, METER_AVERAGE",
                // 36 dias: 22 / 30 = 0,733 x 36 = 26,388 -> 26
                "36, 26, ADJUSTED_AVERAGE",
                // 24 dias: 0,733 x 24 = 17,592 -> 18
                "24, 18, ADJUSTED_AVERAGE",
        })
        void toleranceBoundary(int days, int consumption, ConsumptionType type) {
            ConsumptionResult r = calcRes(22, reading(1000, null).cycleDate(SEP_01.plusDays(days)));
            assertThat(r.billedConsumption()).isEqualTo(consumption);
            assertThat(r.type()).isEqualTo(type);
        }

        @Test
        @DisplayName("data do cronograma anterior à leitura anterior é recusada")
        void negativeDaysRejected() {
            assertThatThrownBy(() -> calcRes(22, reading(1000, null).cycleDate(LocalDate.of(2026, 8, 30))))
                    .isInstanceOfSatisfying(MeteringInputException.class,
                            e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.INVALID_DATES));
        }
    }

    // =========================================================================================
    // Ajuste mensal por dias (3.5)
    // =========================================================================================

    @Nested
    class DayAdjustment {

        @Test
        @DisplayName("A-03: 35 dias em ciclo de 30: 40 / 35 = 1,1429 (4 casas) x 30 = 34,287 -> 34; leitura 1040 + ARRED(-5,7145) = 1034; data 06/10 - 5 = 01/10")
        void a03_longerThanCycle() {
            ConsumptionResult r = calcRes(40, reading(1000, 1040).routeAdjusts(true)
                    .currentDate(LocalDate.of(2026, 10, 6)));

            assertThat(r.billedConsumption()).isEqualTo(34);
            assertThat(r.type()).isEqualTo(ConsumptionType.ADJUSTED_AVERAGE);
            assertThat(r.billedReading()).isEqualTo(1034);
            assertThat(r.billedReadingDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(r.warnings()).isEmpty();
        }

        @Test
        @DisplayName("A-04: 33 dias (diferença -3, dentro da tolerância de 3): sem ajuste, consumo 40")
        void a04_withinToleranceKeepsReading() {
            ConsumptionResult r = calcRes(40, reading(1000, 1040).routeAdjusts(true)
                    .currentDate(LocalDate.of(2026, 10, 4)));

            assertThat(r.billedConsumption()).isEqualTo(40);
            assertThat(r.type()).isEqualTo(ConsumptionType.REAL);
            assertThat(r.billedReading()).isEqualTo(1040);
            assertThat(r.billedReadingDate()).isEqualTo(LocalDate.of(2026, 10, 4));
        }

        @Test
        @DisplayName("extra: 25 dias em ciclo de 30: 20 / 25 = 0,8000 x 30 = 24; leitura 1020 + 4 = 1024; data 26/09 + 5 = 01/10")
        void shorterThanCycle() {
            ConsumptionResult r = calcRes(20, reading(1000, 1020).routeAdjusts(true)
                    .currentDate(LocalDate.of(2026, 9, 26)));

            assertThat(r.billedConsumption()).isEqualTo(24);
            assertThat(r.type()).isEqualTo(ConsumptionType.ADJUSTED_AVERAGE);
            assertThat(r.billedReading()).isEqualTo(1024);
            assertThat(r.billedReadingDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        }

        @Test
        @DisplayName("o consumo diário é arredondado a 4 casas antes de multiplicar: 22 / 21 = 1,0476 x 30 = 31,428 -> 31 (com 2 casas daria 1,05 x 30 = 31,5 -> 32); leitura 1022 + ARRED(1,0476 x 9 = 9,4284) = 1031")
        void dailyConsumptionKeepsFourDecimals() {
            ConsumptionResult r = calcRes(22, reading(1000, 1022).routeAdjusts(true)
                    .currentDate(LocalDate.of(2026, 9, 22)));

            assertThat(r.billedConsumption()).isEqualTo(31);
            assertThat(r.type()).isEqualTo(ConsumptionType.ADJUSTED_AVERAGE);
            assertThat(r.billedReading()).isEqualTo(1031);
            assertThat(r.billedReadingDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        }

        @Test
        @DisplayName("rota que não ajusta mantém a leitura mesmo com 35 dias")
        void routeWithoutAdjustmentKeepsReading() {
            ConsumptionResult r = calcRes(40, reading(1000, 1040).routeAdjusts(false)
                    .currentDate(LocalDate.of(2026, 10, 6)));
            assertThat(r.billedConsumption()).isEqualTo(40);
            assertThat(r.billedReading()).isEqualTo(1040);
        }

        @Test
        @DisplayName("o ajuste só proporcionaliza consumo de diferença de leituras, não uma média já substituída")
        void adjustmentSkipsNonMeasuredTypes() {
            // leitura 99000 < 99950 não é virada: consumo = média 25 (METER_AVERAGE), 35 dias.
            ConsumptionResult r = calcRes(25, reading(99950, 99000).routeAdjusts(true)
                    .currentDate(LocalDate.of(2026, 10, 6)));
            assertThat(r.billedConsumption()).isEqualTo(25);
            assertThat(r.type()).isEqualTo(ConsumptionType.METER_AVERAGE);
        }

        @Test
        @DisplayName("mesma data da leitura anterior: o ajuste não divide por zero, avisa")
        void sameDayDoesNotDivideByZero() {
            ConsumptionResult r = calcRes(25, reading(1000, 1020).routeAdjusts(true).currentDate(SEP_01));
            assertThat(r.billedConsumption()).isEqualTo(20);
            assertThat(codes(r)).contains(WarningCode.DAY_ADJUSTMENT_SKIPPED_ZERO_DAYS);
        }
    }

    // =========================================================================================
    // Hidrômetro substituído (3.3 passo 1)
    // =========================================================================================

    @Nested
    class MeterReplaced {

        private final LocalDate installedMidPeriod = LocalDate.of(2026, 9, 15);

        @Test
        @DisplayName("substituído no período: consumo = atual - leitura de instalação (80 - 50 = 30)")
        void consumptionFromInstallationReading() {
            ConsumptionResult r = calcRes(30, reading(1000, 80).installation(installedMidPeriod, 50));

            assertThat(r.billedConsumption()).isEqualTo(30);
            assertThat(r.type()).isEqualTo(ConsumptionType.REAL);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.METER_REPLACED_INFORMED);
            assertThat(r.billedReading()).isEqualTo(80);
            assertThat(codes(r)).containsExactly(WarningCode.REPLACED_METER_PRIOR_CONSUMPTION_EXCLUDED);
        }

        @Test
        @DisplayName("leitura atual não maior que a de instalação: fatura a média")
        void currentNotAboveInstallationUsesAverage() {
            ConsumptionResult r = calcRes(30, reading(1000, 40).installation(installedMidPeriod, 50));
            assertThat(r.billedConsumption()).isEqualTo(30);
            assertThat(r.type()).isEqualTo(ConsumptionType.METER_AVERAGE);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.METER_REPLACED_INFORMED);
        }

        @Test
        @DisplayName("sem a leitura de instalação o cálculo é recusado")
        void missingInstallationReading() {
            assertThatThrownBy(() -> calcRes(30, reading(1000, 80).installation(installedMidPeriod, null)))
                    .isInstanceOfSatisfying(MeteringInputException.class,
                            e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.MISSING_INSTALLATION_READING));
        }

        @Test
        @DisplayName("instalação na data da leitura anterior ou depois da atual não é substituição no período")
        void installationOutsideThePeriod() {
            ConsumptionResult sameDay = calcRes(25, reading(1000, 1027).installation(SEP_01, 0));
            assertThat(sameDay.anomaly()).isNull();
            assertThat(sameDay.billedConsumption()).isEqualTo(27);

            ConsumptionResult after = calcRes(25, reading(1000, 1027).installation(LocalDate.of(2026, 10, 2), 0));
            assertThat(after.anomaly()).isNull();
        }

        @Test
        @DisplayName("substituição em rota com ajuste por dias: o ajuste não é aplicado e avisa")
        void dayAdjustmentSkippedWithWarning() {
            ConsumptionResult r = calcRes(30, reading(1000, 80).installation(installedMidPeriod, 50)
                    .routeAdjusts(true));
            assertThat(r.billedConsumption()).isEqualTo(30);
            assertThat(codes(r)).contains(WarningCode.DAY_ADJUSTMENT_SKIPPED_METER_REPLACED);
        }
    }

    // =========================================================================================
    // Anormalidade de leitura (3.4)
    // =========================================================================================

    @Nested
    class ReadingAnomalyEffects {

        private ReadingAnomalyRule rule(ReadingEffect readingWithout, ReadingEffect readingWith,
                                        ConsumptionEffect consWithout, ConsumptionEffect consWith,
                                        String factorWithout, String factorWith) {
            return Fixtures.anomaly("A1", readingWithout, readingWith, consWithout, consWith,
                    factorWithout == null ? null : bd(factorWithout), factorWith == null ? null : bd(factorWith));
        }

        @Test
        @DisplayName("sem leitura, efeito MÍNIMO: consumo mínimo da ligação (10), tipo SEM CONSUMO, leitura faturada = anterior")
        void withoutReadingMinimum() {
            ConsumptionResult r = calcRes(25, reading(1000, null).anomaly(rule(ReadingEffect.PREVIOUS,
                    ReadingEffect.INFORMED, ConsumptionEffect.MINIMUM, ConsumptionEffect.NORMAL, null, null)));

            assertThat(r.billedConsumption()).isEqualTo(10);
            assertThat(r.type()).isEqualTo(ConsumptionType.NO_CONSUMPTION);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.READING_ANOMALY);
            assertThat(r.billedReading()).isEqualTo(1000);
            assertThat(r.billedReadingAnomalyCode()).isEqualTo("A1");
            assertThat(r.billedReadingDate()).isEqualTo(OCT_01);
            assertThat(r.warnings()).isEmpty();
        }

        @Test
        @DisplayName("sem leitura, efeito MÉDIA e leitura anterior + média: consumo 22, leitura faturada 1022")
        void withoutReadingAverage() {
            ConsumptionResult r = calcRes(22, reading(1000, null).anomaly(rule(ReadingEffect.PREVIOUS_PLUS_AVERAGE,
                    ReadingEffect.INFORMED, ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL, null, null)));
            assertThat(r.billedConsumption()).isEqualTo(22);
            assertThat(r.type()).isEqualTo(ConsumptionType.METER_AVERAGE);
            assertThat(r.billedReading()).isEqualTo(1022);
        }

        @Test
        @DisplayName("sem leitura, fator 1,5: 25 x 1,5 = 37,5 TRUNCA para 37 (HALF_UP daria 38); a leitura usa o consumo antes do fator (1025)")
        void withoutReadingFactorTruncates() {
            ConsumptionResult r = calcRes(25, reading(1000, null).anomaly(rule(ReadingEffect.PREVIOUS_PLUS_CONSUMPTION,
                    ReadingEffect.INFORMED, ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL, "1.5", null)));
            assertThat(r.billedConsumption()).isEqualTo(37);
            assertThat(r.billedReading()).isEqualTo(1025);
        }

        @Test
        @DisplayName("sem leitura, efeito NÃO MEDIDO: consumo de ligação não medida (10), tipo ESTIMADO")
        void withoutReadingNotMetered() {
            ConsumptionResult r = calcRes(25, reading(1000, null).anomaly(rule(ReadingEffect.PREVIOUS,
                    ReadingEffect.INFORMED, ConsumptionEffect.NOT_METERED, ConsumptionEffect.NORMAL, null, null)));
            assertThat(r.billedConsumption()).isEqualTo(10);
            assertThat(r.type()).isEqualTo(ConsumptionType.ESTIMATED);
        }

        @Test
        @DisplayName("DIVERGÊNCIA PRESERVADA: com leitura, efeito MÍNIMO dá 0 (sem leitura dá o mínimo da ligação)")
        void withReadingMinimumIsZero() {
            ConsumptionResult r = calcRes(25, reading(1000, 1027).anomaly(rule(ReadingEffect.PREVIOUS,
                    ReadingEffect.INFORMED, ConsumptionEffect.AVERAGE, ConsumptionEffect.MINIMUM, null, null)));
            assertThat(r.billedConsumption()).isZero();
            assertThat(r.type()).isEqualTo(ConsumptionType.NO_CONSUMPTION);
            assertThat(r.billedReading()).isEqualTo(1027);
        }

        @ParameterizedTest(name = "{0}: leitura {1}, média 40 -> {2} ({3})")
        @DisplayName("com leitura: maior/menor entre a média (40) e o medido")
        @CsvSource({
                // medido 27 (1027): MAIOR entre 40 e 27 = 40 (média); MENOR = 27
                "MAX_OF_AVERAGE_AND_MEASURED, 1027, 40, METER_AVERAGE",
                "MIN_OF_AVERAGE_AND_MEASURED, 1027, 27, REAL",
                // medido 55 (1055): MAIOR = 55; MENOR = 40 (média)
                "MAX_OF_AVERAGE_AND_MEASURED, 1055, 55, REAL",
                "MIN_OF_AVERAGE_AND_MEASURED, 1055, 40, METER_AVERAGE",
        })
        void withReadingMaxMin(ConsumptionEffect effect, int current, int consumption, ConsumptionType type) {
            ConsumptionResult r = calcRes(40, reading(1000, current).anomaly(rule(ReadingEffect.PREVIOUS,
                    ReadingEffect.INFORMED, ConsumptionEffect.AVERAGE, effect, null, null)));
            assertThat(r.billedConsumption()).isEqualTo(consumption);
            assertThat(r.type()).isEqualTo(type);
        }

        @Test
        @DisplayName("com leitura, NORMAL com fator 0,5: 27 x 0,5 = 13,5 TRUNCA para 13; leitura faturada = anterior")
        void withReadingFactorTruncates() {
            ConsumptionResult r = calcRes(25, reading(1000, 1027).anomaly(rule(ReadingEffect.PREVIOUS,
                    ReadingEffect.PREVIOUS, ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL, null, "0.5")));
            assertThat(r.billedConsumption()).isEqualTo(13);
            assertThat(r.type()).isEqualTo(ConsumptionType.REAL);
            assertThat(r.billedReading()).isEqualTo(1000);
        }

        @Test
        @DisplayName("com leitura, efeito NÃO MEDIDO: 10, tipo ESTIMADO")
        void withReadingNotMetered() {
            ConsumptionResult r = calcRes(25, reading(1000, 1027).anomaly(rule(ReadingEffect.PREVIOUS,
                    ReadingEffect.PREVIOUS_PLUS_AVERAGE, ConsumptionEffect.AVERAGE, ConsumptionEffect.NOT_METERED,
                    null, null)));
            assertThat(r.billedConsumption()).isEqualTo(10);
            assertThat(r.type()).isEqualTo(ConsumptionType.ESTIMATED);
            assertThat(r.billedReading()).isEqualTo(1025);
        }

        @Test
        @DisplayName("efeito que não existe sem leitura é recusado na construção da regra")
        void effectsWithoutReadingAreValidated() {
            assertThatThrownBy(() -> rule(ReadingEffect.INFORMED, ReadingEffect.INFORMED, ConsumptionEffect.AVERAGE,
                    ConsumptionEffect.NORMAL, null, null)).isInstanceOf(MeteringInputException.class);
            assertThatThrownBy(() -> rule(ReadingEffect.PREVIOUS, ReadingEffect.INFORMED,
                    ConsumptionEffect.MAX_OF_AVERAGE_AND_MEASURED, ConsumptionEffect.NORMAL, null, null))
                    .isInstanceOf(MeteringInputException.class);
            assertThatThrownBy(() -> rule(ReadingEffect.PREVIOUS, ReadingEffect.INFORMED, ConsumptionEffect.AVERAGE,
                    ConsumptionEffect.NORMAL, "-1", null)).isInstanceOf(MeteringInputException.class);
        }
    }

    // =========================================================================================
    // Estouro, alto e baixo consumo (3.6)
    // =========================================================================================

    @Nested
    class BurstHighLow {

        // Patamares fictícios (relatório 02, 10.8), 1 economia RES: estouro 50 e 5x a média;
        // alto 40 e 3x a média; baixo: média > 10 e consumo < 40% da média.

        @Test
        @DisplayName("X-01 sem ação e 1ª ocorrência: 120 > 50 e > ARRED(20 x 5) = 100: estouro; cobra a média 20 (anormalidade 'cobrança pela média')")
        void x01_firstBurstWithoutActionChargesTheAverage() {
            ConsumptionResult r = calcRes(20, reading(1000, 1120));

            assertThat(r.billedConsumption()).isEqualTo(20);
            assertThat(r.type()).isEqualTo(ConsumptionType.METER_AVERAGE);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.BURST_CHARGED_AT_AVERAGE);
            assertThat(r.appliedActionMonth()).isNull();
            // a fora-da-faixa detectada antes foi substituída: fica registrado, não some
            assertThat(codes(r)).containsExactly(WarningCode.ANOMALY_SUPERSEDED);
            assertThat(r.warnings().get(0).message()).contains("OUT_OF_RANGE").contains("BURST_CHARGED_AT_AVERAGE");
        }

        @ParameterizedTest(name = "{0}: média {1}, consumo {2} -> anormalidade {3}, tipo {4}")
        @DisplayName("X-02 a X-05 e fronteiras: estouro, alto e baixo sem ação cadastrada (consumo mantido)")
        @CsvSource(nullValues = "NONE", value = {
                // X-02: 100 > 50 mas 100 > 100 é falso: sem estouro. Alto: 100 > 40 e 100 > ARRED(20 x 3) = 60: ALTO
                "X-02,      20, 100, HIGH_CONSUMPTION, REAL",
                // fronteira do alto: 61 > 60 -> alto
                "X-02b,     20, 61,  HIGH_CONSUMPTION, REAL",
                // X-03: 55 > 40, mas 55 > 60 é falso: sem alto; sem estouro (55 > 100 falso); só fora da faixa
                "X-03,      20, 55,  OUT_OF_RANGE,     REAL",
                // fronteira: 60 > 60 é falso
                "X-03b,     20, 60,  OUT_OF_RANGE,     REAL",
                // X-04: média 30 > 10 e 11 < 30 x 40 / 100 = 12,0: BAIXO
                "X-04,      30, 11,  LOW_CONSUMPTION,  REAL",
                // X-05: 12 < 12,0 é falso: sem baixo (a leitura 1012 fica abaixo da faixa [1015, 1045])
                "X-05,      30, 12,  OUT_OF_RANGE,     REAL",
                // piso do baixo: média 10 > 10 é falso: sem baixo, mesmo com 3 < 40% de 10
                "X-04b,     10, 3,   NONE,             REAL",
        })
        void x02_toX05(String id, int average, int consumption, ConsumptionAnomalyKind anomaly, ConsumptionType type) {
            ConsumptionResult r = calcRes(average, reading(1000, 1000 + consumption));

            assertThat(r.billedConsumption()).as(id).isEqualTo(consumption);
            assertThat(r.type()).as(id).isEqualTo(type);
            assertThat(r.anomaly()).as(id).isEqualTo(anomaly);
        }

        @Test
        @DisplayName("baixo consumo não é avaliado com ligação cortada, poço, leitura não realizada ou anormalidade de leitura")
        void lowConsumptionPreconditions() {
            // consumo 11, média 30: baixo se as pré-condições valessem. Sobra só a fora-da-faixa.
            assertThat(calc(policy(), input(reading(1000, 1011).build(), List.of(res(1)), history(30)).cut()).anomaly())
                    .as("cortada").isEqualTo(ConsumptionAnomalyKind.OUT_OF_RANGE);
            assertThat(calc(policy(), input(reading(1000, 1011).build(), List.of(res(1)), history(30)).withWell())
                    .anomaly()).as("poço").isEqualTo(ConsumptionAnomalyKind.OUT_OF_RANGE);
            assertThat(calcRes(30, reading(1000, 1011).currentStatus(ReadingStatus.NOT_REALIZED)).anomaly())
                    .as("leitura não realizada").isEqualTo(ConsumptionAnomalyKind.OUT_OF_RANGE);
            ReadingAnomalyRule normal = Fixtures.anomaly("A1", ReadingEffect.PREVIOUS, ReadingEffect.INFORMED,
                    ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL, null, null);
            assertThat(calcRes(30, reading(1000, 1011).anomaly(normal)).anomaly())
                    .as("anormalidade de leitura").isEqualTo(ConsumptionAnomalyKind.OUT_OF_RANGE);
            assertThat(calcRes(30, reading(1000, 1011).currentStatus(ReadingStatus.CONFIRMED)).anomaly())
                    .as("leitura confirmada vale").isEqualTo(ConsumptionAnomalyKind.LOW_CONSUMPTION);
        }

        private ConsumptionAnomalyAction burstAction() {
            return new ConsumptionAnomalyAction(ConsumptionAnomalyKind.BURST, "RES",
                    new ConsumptionAnomalyAction.Step(ConsumptionEffect.NORMAL, null),
                    new ConsumptionAnomalyAction.Step(ConsumptionEffect.AVERAGE, bd("1.5")),
                    new ConsumptionAnomalyAction.Step(ConsumptionEffect.AVERAGE, bd("2")));
        }

        @Test
        @DisplayName("X-01 com ação: anormalidade ESTOURO e efeito do mês 1 (NORMAL: mantém 120)")
        void x01_withActionMonthOne() {
            ConsumptionResult r = calc(policy(List.of(burstAction())),
                    input(reading(1000, 1120).build(), List.of(res(1)), history(20)));
            assertThat(r.billedConsumption()).isEqualTo(120);
            assertThat(r.type()).isEqualTo(ConsumptionType.REAL);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.BURST);
            assertThat(r.appliedActionMonth()).isEqualTo(1);
        }

        @Test
        @DisplayName("mês 2 (estouro só no mês anterior): efeito MÉDIA com fator 1,5: ARRED(20 x 1,5) = 30")
        void actionMonthTwo() {
            ConsumptionResult r = calc(policy(List.of(burstAction())), input(reading(1000, 1120).build(),
                    List.of(res(1)), Fixtures.historyWithAnomalies(20, ConsumptionAnomalyKind.BURST, null)));
            assertThat(r.billedConsumption()).isEqualTo(30);
            assertThat(r.type()).isEqualTo(ConsumptionType.METER_AVERAGE);
            assertThat(r.appliedActionMonth()).isEqualTo(2);
        }

        @Test
        @DisplayName("X-06: estouro em ref-1 e ref-2 (a cobrança pela média conta como a mesma família): ação do mês 3, média x 2 = 40")
        void x06_actionMonthThree() {
            ConsumptionResult r = calc(policy(List.of(burstAction())), input(reading(1000, 1120).build(),
                    List.of(res(1)), Fixtures.historyWithAnomalies(20, ConsumptionAnomalyKind.BURST,
                            ConsumptionAnomalyKind.BURST_CHARGED_AT_AVERAGE)));
            assertThat(r.billedConsumption()).isEqualTo(40);
            assertThat(r.type()).isEqualTo(ConsumptionType.METER_AVERAGE);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.BURST);
            assertThat(r.appliedActionMonth()).isEqualTo(3);
        }

        @Test
        @DisplayName("estouro só em ref-2 (e não em ref-1) conta como 1ª ocorrência")
        void burstOnlyTwoMonthsAgoIsFirstOccurrence() {
            ConsumptionResult r = calc(policy(List.of(burstAction())), input(reading(1000, 1120).build(),
                    List.of(res(1)), Fixtures.historyWithAnomalies(20, null, ConsumptionAnomalyKind.BURST)));
            assertThat(r.appliedActionMonth()).isEqualTo(1);
            assertThat(r.billedConsumption()).isEqualTo(120);
        }

        @Test
        @DisplayName("reincidente sem ação, dentro do máximo (120 <= 150): mantém o estouro e o consumo")
        void recurrentBurstWithinMaximumKeepsConsumption() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1120).build(), List.of(res(1, 150)),
                    Fixtures.historyWithAnomalies(20, ConsumptionAnomalyKind.BURST_CHARGED_AT_AVERAGE, null)));
            assertThat(r.billedConsumption()).isEqualTo(120);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.BURST);
            assertThat(codes(r)).doesNotContain(WarningCode.BURST_RECURRENCE_ABOVE_MAXIMUM);
        }

        @Test
        @DisplayName("LACUNA DO RELATÓRIO: reincidente acima do máximo (120 > 100) não tem regra; mantém e avisa para revisão")
        void recurrentBurstAboveMaximumWarns() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1120).build(), List.of(res(1, 100)),
                    Fixtures.historyWithAnomalies(20, ConsumptionAnomalyKind.BURST, null)));
            assertThat(r.billedConsumption()).isEqualTo(120);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.BURST);
            assertThat(codes(r)).contains(WarningCode.BURST_RECURRENCE_ABOVE_MAXIMUM);
        }

        @Test
        @DisplayName("reincidente sem consumo máximo configurado: mantém e avisa")
        void recurrentBurstWithoutMaximum() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1120).build(), List.of(res(1, null)),
                    Fixtures.historyWithAnomalies(20, ConsumptionAnomalyKind.BURST, null)));
            assertThat(codes(r)).contains(WarningCode.BURST_MAXIMUM_NOT_CONFIGURED);
        }

        @Test
        @DisplayName("patamares não configurados: nada é detectado e o aviso diz isso (não presume valor)")
        void thresholdsNotConfigured() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1120).build(),
                    List.of(Fixtures.resWithoutThresholds(1)), history(20)));
            assertThat(r.billedConsumption()).isEqualTo(120);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.OUT_OF_RANGE);
            assertThat(codes(r)).containsExactly(WarningCode.THRESHOLDS_NOT_CONFIGURED,
                    WarningCode.THRESHOLDS_NOT_CONFIGURED, WarningCode.THRESHOLDS_NOT_CONFIGURED);
        }

        @Test
        @DisplayName("ação de alto e baixo consumo: usa o efeito e o fator do mês")
        void highAndLowActions() {
            ConsumptionAnomalyAction high = new ConsumptionAnomalyAction(ConsumptionAnomalyKind.HIGH_CONSUMPTION, "RES",
                    new ConsumptionAnomalyAction.Step(ConsumptionEffect.MIN_OF_AVERAGE_AND_MEASURED, null),
                    new ConsumptionAnomalyAction.Step(ConsumptionEffect.AVERAGE, null),
                    new ConsumptionAnomalyAction.Step(ConsumptionEffect.AVERAGE, null));
            // alto: 100 (> 40 e > 60); MENOR entre média 20 e medido 100 = 20
            ConsumptionResult r = calc(policy(List.of(high)),
                    input(reading(1000, 1100).build(), List.of(res(1)), history(20)));
            assertThat(r.billedConsumption()).isEqualTo(20);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.HIGH_CONSUMPTION);

            ConsumptionAnomalyAction low = new ConsumptionAnomalyAction(ConsumptionAnomalyKind.LOW_CONSUMPTION, "RES",
                    new ConsumptionAnomalyAction.Step(ConsumptionEffect.AVERAGE, null),
                    new ConsumptionAnomalyAction.Step(ConsumptionEffect.AVERAGE, null),
                    new ConsumptionAnomalyAction.Step(ConsumptionEffect.AVERAGE, null));
            // baixo (X-04): consumo 11, média 30 -> média 30
            ConsumptionResult r2 = calc(policy(List.of(low)),
                    input(reading(1000, 1011).build(), List.of(res(1)), history(30)));
            assertThat(r2.billedConsumption()).isEqualTo(30);
            assertThat(r2.anomaly()).isEqualTo(ConsumptionAnomalyKind.LOW_CONSUMPTION);
        }

        @Test
        @DisplayName("com várias categorias, os patamares somam por economia e a principal (mais economias) dá o 'vezes a média'")
        void patternsSumPerEconomyAndUsePrincipalCategory() {
            // RES 1 + COM 3: soma do estouro = 50 x 1 + 50 x 3 = 200; principal = COM. Média 60: 5 x 60 = 300.
            // Consumo 250: > 200 mas não > 300: sem estouro. Alto: soma 160, 3 x 60 = 180: 250 > 180: ALTO.
            // (Múltiplas economias: min = 10 x 4 = 40; excesso 210 DIV 4 = 52; ajustado 52 x 4 + 40 = 248; resto 2.)
            ConsumptionResult r = calc(policy(), input(reading(1000, 1250).build(),
                    List.of(res(1), Fixtures.com(3, 10)), history(60)));
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.HIGH_CONSUMPTION);
            assertThat(r.calculatedConsumption()).isEqualTo(248);
            assertThat(r.billedConsumption()).isEqualTo(248);
            assertThat(r.discardedRemainder()).isEqualTo(2);
        }
    }

    // =========================================================================================
    // Situação especial (3.7), consumo mínimo fixado e informado (3.3 passo 8)
    // =========================================================================================

    @Nested
    class SpecialAndFixed {

        private final YearMonth aug = YearMonth.of(2026, 8);
        private final YearMonth dec = YearMonth.of(2026, 12);

        @Test
        @DisplayName("situação especial vigente fixa o consumo (12) e não paralisa: fatura 12, tipo FIXO")
        void specialSituationFixesConsumption() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1027).build(), List.of(res(1)), history(25))
                    .special(new SpecialSituation(aug, dec, false, 12, null, null)));
            assertThat(r.billedConsumption()).isEqualTo(12);
            assertThat(r.type()).isEqualTo(ConsumptionType.SPECIAL_SITUATION_FIXED);
            assertThat(r.billable()).isTrue();
        }

        @Test
        @DisplayName("situação especial que paralisa o faturamento: consumo calculado, faturado zero, com aviso")
        void pausedBilling() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1027).build(), List.of(res(1)), history(25))
                    .special(new SpecialSituation(aug, null, true, 12, null, null)));
            assertThat(r.billingPaused()).isTrue();
            assertThat(r.billable()).isFalse();
            assertThat(r.billedConsumption()).isZero();
            assertThat(r.calculatedConsumption()).isEqualTo(12);
            assertThat(codes(r)).containsExactly(WarningCode.BILLING_PAUSED);
        }

        @Test
        @DisplayName("fora da vigência a situação especial não age (aberta, termina em dez/2026; ref = out/2026 está dentro; set/2026 está fora)")
        void outsideValidity() {
            SpecialSituation endedInSeptember = new SpecialSituation(aug, YearMonth.of(2026, 9), false, 12, null, null);
            ConsumptionResult r = calc(policy(), input(reading(1000, 1027).build(), List.of(res(1)), history(25))
                    .special(endedInSeptember));
            assertThat(r.billedConsumption()).isEqualTo(27);
            assertThat(r.type()).isEqualTo(ConsumptionType.REAL);
        }

        @Test
        @DisplayName("situação especial com efeito MÉDIA em vez de valor fixo")
        void specialSituationWithEffect() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1027).build(), List.of(res(1)), history(25))
                    .special(new SpecialSituation(aug, dec, false, null, null, ConsumptionEffect.AVERAGE)));
            assertThat(r.billedConsumption()).isEqualTo(25);
            assertThat(r.type()).isEqualTo(ConsumptionType.METER_AVERAGE);
        }

        @Test
        @DisplayName("situação especial sem valor nem efeito mantém o calculado e avisa")
        void specialSituationWithoutRule() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1027).build(), List.of(res(1)), history(25))
                    .special(new SpecialSituation(aug, dec, false, null, null, null)));
            assertThat(r.billedConsumption()).isEqualTo(27);
            assertThat(codes(r)).containsExactly(WarningCode.SPECIAL_SITUATION_WITHOUT_CONSUMPTION_RULE);
        }

        @Test
        @DisplayName("situação especial só aceita efeitos que existem sem leitura")
        void specialSituationEffectValidated() {
            assertThatThrownBy(() -> new SpecialSituation(aug, dec, false, null, null, ConsumptionEffect.NORMAL))
                    .isInstanceOf(MeteringInputException.class);
            assertThatThrownBy(() -> new SpecialSituation(dec, aug, false, null, null, null))
                    .isInstanceOf(MeteringInputException.class);
        }

        @Test
        @DisplayName("N-05: consumo mínimo fixado 15, medido 9: fatura 15, tipo MÍNIMO FIXADO, anormalidade de mínimo fixado")
        void n05_fixedMinimumRaisesConsumption() {
            // média 12: faixa esperada [1005, 1019]; 1009 está dentro.
            ConsumptionResult r = calc(policy(), input(reading(1000, 1009).build(), List.of(res(1)), history(12))
                    .fixedMinimum(15));
            assertThat(r.billedConsumption()).isEqualTo(15);
            assertThat(r.type()).isEqualTo(ConsumptionType.MINIMUM_FIXED);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.MINIMUM_FIXED);
        }

        @Test
        @DisplayName("consumo medido acima do mínimo fixado não muda")
        void measuredAboveFixedMinimumIsUntouched() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1020).build(), List.of(res(1)), history(20))
                    .fixedMinimum(15));
            assertThat(r.billedConsumption()).isEqualTo(20);
            assertThat(r.type()).isEqualTo(ConsumptionType.REAL);
        }

        @Test
        @DisplayName("consumo informado no lugar do medido, sem mínimo fixado")
        void informedConsumption() {
            ConsumptionResult r = calcRes(25, reading(1000, 1027).informedConsumption(18));
            assertThat(r.billedConsumption()).isEqualTo(18);
            assertThat(r.type()).isEqualTo(ConsumptionType.INFORMED);
            assertThat(r.anomaly()).isEqualTo(ConsumptionAnomalyKind.CONSUMPTION_INFORMED);
        }

        @Test
        @DisplayName("LEITURA LITERAL do relatório: 'senão se' — mínimo fixado que não dispara (9 >= 5) deixa o informado valer")
        void informedAppliesWhenFixedMinimumDoesNotTrigger() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1009).informedConsumption(18).build(),
                    List.of(res(1)), history(12)).fixedMinimum(5));
            assertThat(r.billedConsumption()).isEqualTo(18);
            assertThat(r.type()).isEqualTo(ConsumptionType.INFORMED);
        }

        @Test
        @DisplayName("mínimo fixado que dispara tem precedência sobre o informado")
        void fixedMinimumWinsOverInformed() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1009).informedConsumption(18).build(),
                    List.of(res(1)), history(12)).fixedMinimum(15));
            assertThat(r.billedConsumption()).isEqualTo(15);
            assertThat(r.type()).isEqualTo(ConsumptionType.MINIMUM_FIXED);
        }
    }

    // =========================================================================================
    // Múltiplas economias (3.8)
    // =========================================================================================

    @Nested
    class MultiEconomy {

        @Test
        @DisplayName("M-01 (= T-05): RES 2 economias, medido 35, mínimo 10 por economia: mín 20, excesso 15, 15 DIV 2 = 7, ajustado 7x2 + 10x2 = 34, resto 1: fatura 34")
        void m01_twoEconomies() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1035).build(), List.of(res(2)), history(35)));

            assertThat(r.calculatedConsumption()).isEqualTo(34);
            assertThat(r.billedConsumption()).isEqualTo(34);
            assertThat(r.discardedRemainder()).isEqualTo(1);
            assertThat(r.billedReading()).as("a leitura faturada recua o resto").isEqualTo(1034);
            assertThat(codes(r)).containsExactly(WarningCode.DISCARDED_CONSUMPTION_REMAINDER);
        }

        @Test
        @DisplayName("M-02 (= T-06): RES 1 + COM 1, medido 25: mín 20, excesso 5, 5 DIV 2 = 2, ajustado (2+10) + (2+10) = 24, resto 1: fatura 24")
        void m02_twoCategories() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1025).build(),
                    List.of(res(1), Fixtures.com(1, 10)), history(25)));
            assertThat(r.billedConsumption()).isEqualTo(24);
            assertThat(r.discardedRemainder()).isEqualTo(1);
        }

        @Test
        @DisplayName("M-03 (= T-07): RES 3 economias, medido 28 (< mínimo 30): resto = 28 MOD 3 = 1, fatura 27; a leitura faturada recua 1")
        void m03_belowMinimum() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1028).build(), List.of(res(3)), history(28)));
            assertThat(r.billedConsumption()).isEqualTo(27);
            assertThat(r.discardedRemainder()).isEqualTo(1);
            assertThat(r.billedReading()).isEqualTo(1027);
        }

        @Test
        @DisplayName("consumo que já fecha por economia não perde nada nem avisa (RES 2, medido 36: mín 20, excesso 16, 8 por economia, ajustado 16 + 20 = 36)")
        void exactConsumptionKeepsEverything() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1036).build(), List.of(res(2)), history(36)));
            assertThat(r.billedConsumption()).isEqualTo(36);
            assertThat(r.discardedRemainder()).isZero();
            assertThat(r.billedReading()).isEqualTo(1036);
            assertThat(r.warnings()).isEmpty();
        }

        @Test
        @DisplayName("DIVERGÊNCIA do relatório (10.16): o invariante certo é (faturado - mínimo) múltiplo de economias, não o faturado. RES 1 (mín 10) + COM 1 (mín 7), medido 25: fatura 25, que não é múltiplo de 2")
        void invariantIsRelativeToTheMinimum() {
            // mín = 10 + 7 = 17; excesso 8; 8 DIV 2 = 4; ajustado (4+10) + (4+7) = 25; resto 0.
            ConsumptionResult r = calc(policy(), input(reading(1000, 1025).build(),
                    List.of(res(1), Fixtures.com(1, 7)), history(25)));
            assertThat(r.billedConsumption()).isEqualTo(25);
            assertThat(r.billedConsumption() % 2).isEqualTo(1);
            assertThat((r.billedConsumption() - r.connectionMinimum()) % 2).isZero();
        }

        @Test
        @DisplayName("DIVERGÊNCIA do relatório (3.8): com economias equivalentes o resto fica negativo e o consumo seria inflado; é recusado")
        void equivalentEconomiesMakeTheFormulaIncoherent() {
            // RES 3 economias reais, 1 equivalente: mínimo da ligação = 10 x 1 = 10. Medido 12: excesso 2,
            // 2 DIV 3 = 0; ajustado = 0 x 3 + 10 x 3 = 30; resto = 12 - 30 = -18 -> faturaria 30.
            EconomyGroup equivalent = new EconomyGroup("RES", 3, 10, 1, Fixtures.thresholds(150), List.of(), List.of());
            assertThatThrownBy(() -> calc(policy(), input(reading(1000, 1012).build(), List.of(equivalent), history(12))))
                    .isInstanceOfSatisfying(MeteringInputException.class,
                            e -> assertThat(e.getCode()).isEqualTo(MeteringErrorCode.INCONSISTENT_ECONOMY_ADJUSTMENT));
        }

        @Test
        @DisplayName("uma economia só: nenhum ajuste")
        void singleEconomyUntouched() {
            ConsumptionResult r = calcRes(25, reading(1000, 1027));
            assertThat(r.discardedRemainder()).isZero();
        }
    }

    // =========================================================================================
    // Permissão de faturar (3.3 passo 11) e ligação cortada (passo 9)
    // =========================================================================================

    @Nested
    class BillingPermission {

        @Test
        @DisplayName("tipo fora dos que permitem faturar nesta situação de ligação: faturado 0, calculado 27 (base da média) e aviso")
        void typeNotBillable() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1027).previousStatus(ReadingStatus.NOT_REALIZED).build(),
                    List.of(res(1)), history(25)).billable(ConsumptionType.REAL));

            assertThat(r.type()).isEqualTo(ConsumptionType.ESTIMATED);
            assertThat(r.billedConsumption()).isZero();
            assertThat(r.calculatedConsumption()).isEqualTo(27);
            assertThat(r.billable()).isFalse();
            assertThat(codes(r)).containsExactly(WarningCode.CONSUMPTION_TYPE_NOT_BILLABLE);
        }

        @Test
        @DisplayName("ligação cortada: a regra de dias de corte não é aplicada por este módulo e isso é avisado, não calado")
        void cutConnectionWarnsThatTheDaysRuleIsNotApplied() {
            ConsumptionResult r = calc(policy(), input(reading(1000, 1027).build(), List.of(res(1)), history(25)).cut());
            assertThat(r.billedConsumption()).isEqualTo(27);
            assertThat(codes(r)).containsExactly(WarningCode.CUT_CONNECTION_DAYS_RULE_NOT_IMPLEMENTED);
        }
    }

    // =========================================================================================
    // Média e histórico dentro da cascata
    // =========================================================================================

    @Test
    @DisplayName("sem histórico a média usada é o mínimo da ligação e o resultado carrega essa origem")
    void noHistoryUsesConnectionMinimum() {
        ConsumptionResult r = calc(policy(), input(reading(1000, null).build(), List.of(res(1)), List.of()));
        assertThat(r.average()).isEqualTo(10);
        assertThat(r.averageMonthsConsidered()).isEqualTo(1);
        assertThat(r.averageSource()).isEqualTo(AverageResult.Source.CONNECTION_MINIMUM);
        assertThat(r.hasWarning(WarningCode.AVERAGE_FROM_CONNECTION_MINIMUM)).isTrue();
    }

    @Test
    @DisplayName("determinismo: o mesmo insumo dá exatamente o mesmo resultado")
    void sameInputSameResult() {
        ConsumptionInput input = input(reading(1000, 1120).build(), List.of(res(1)), history(20)).build();
        ConsumptionCalculator calculator = new ConsumptionCalculator(policy());
        assertThat(calculator.calculate(input)).isEqualTo(calculator.calculate(input));
    }
}
