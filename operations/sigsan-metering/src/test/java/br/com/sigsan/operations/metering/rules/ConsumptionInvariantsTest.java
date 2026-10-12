package br.com.sigsan.operations.metering.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static br.com.sigsan.operations.metering.rules.Fixtures.calc;
import static br.com.sigsan.operations.metering.rules.Fixtures.history;
import static br.com.sigsan.operations.metering.rules.Fixtures.input;
import static br.com.sigsan.operations.metering.rules.Fixtures.policy;
import static br.com.sigsan.operations.metering.rules.Fixtures.reading;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invariantes da cascata (relatório 02, 10.16), varridas sobre muitas combinações. Valores
 * FICTÍCIOS e determinísticos (varredura exaustiva ou semente fixa).
 */
class ConsumptionInvariantsTest {

    @Test
    @DisplayName("múltiplas economias: o resto fica em [0, economias); (faturado - mínimo) é múltiplo de economias com excesso, e o faturado é múltiplo sem excesso")
    void multiEconomyAdjustmentInvariants() {
        int cases = 0;
        for (int economies = 2; economies <= 5; economies++) {
            for (int minimumPerEconomy : new int[]{0, 1, 5, 10, 12}) {
                for (int measured = 0; measured <= 120; measured++) {
                    EconomyGroup group = new EconomyGroup("RES", economies, minimumPerEconomy, null, null,
                            List.of(), List.of());
                    ConsumptionResult r = calc(policy(), input(reading(0, measured).build(), List.of(group),
                            history(10)));
                    int minimum = minimumPerEconomy * economies;
                    String label = "economias " + economies + ", mínimo/economia " + minimumPerEconomy
                            + ", medido " + measured;

                    assertThat(r.calculatedConsumption()).as(label).isLessThanOrEqualTo(measured);
                    assertThat(r.discardedRemainder()).as(label).isBetween(0, economies - 1);
                    assertThat(measured - r.calculatedConsumption()).as(label).isEqualTo(r.discardedRemainder());
                    if (measured - minimum > 0) {
                        assertThat((r.calculatedConsumption() - minimum) % economies).as(label).isZero();
                    } else {
                        assertThat(r.calculatedConsumption() % economies).as(label).isZero();
                    }
                    cases++;
                }
            }
        }
        assertThat(cases).isEqualTo(4 * 5 * 121);
    }

    @Test
    @DisplayName("o faturado é sempre 0 ou o calculado; a base da média (calculado) nunca é perdida")
    void billedIsZeroOrCalculated() {
        Random random = new Random(20261011L);
        ConsumptionType[] all = ConsumptionType.values();
        for (int i = 0; i < 500; i++) {
            Set<ConsumptionType> billable = EnumSet.noneOf(ConsumptionType.class);
            for (ConsumptionType type : all) {
                if (random.nextBoolean()) {
                    billable.add(type);
                }
            }
            int measured = random.nextInt(80);
            ReadingStatus previous = ReadingStatus.values()[random.nextInt(ReadingStatus.values().length)];
            ConsumptionResult r = calc(policy(), input(reading(1000, 1000 + measured).previousStatus(previous).build(),
                    List.of(Fixtures.res(1)), history(20)).billable(billable.toArray(new ConsumptionType[0])));

            assertThat(r.billedConsumption()).isIn(0, r.calculatedConsumption());
            assertThat(r.calculatedConsumption()).isGreaterThanOrEqualTo(0);
            assertThat(r.billable()).isEqualTo(billable.contains(r.type()));
            if (!r.billable()) {
                assertThat(r.billedConsumption()).isZero();
                assertThat(r.hasWarning(WarningCode.CONSUMPTION_TYPE_NOT_BILLABLE)).isTrue();
            }
        }
    }

    @Test
    @DisplayName("toda leitura faturada cabe no mostrador, qualquer que seja o caminho da cascata")
    void billedReadingAlwaysFitsTheDial() {
        Random random = new Random(7L);
        for (int i = 0; i < 1000; i++) {
            int previous = random.nextInt(100_000);
            int current = random.nextInt(100_000);
            ConsumptionResult r = calc(policy(), input(reading(previous, current).routeAdjusts(random.nextBoolean())
                    .currentDate(java.time.LocalDate.of(2026, 9, 1).plusDays(15 + random.nextInt(40))).build(),
                    List.of(Fixtures.res(1 + random.nextInt(3))), history(random.nextInt(60))));
            assertThat(r.billedReading()).as("anterior %d, atual %d", previous, current).isBetween(0, 99_999);
            assertThat(r.billedConsumption()).isGreaterThanOrEqualTo(0);
        }
    }
}
