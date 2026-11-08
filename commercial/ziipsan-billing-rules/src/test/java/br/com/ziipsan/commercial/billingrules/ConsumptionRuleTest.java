package br.com.ziipsan.commercial.billingrules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsumptionRuleTest {

    @Test
    void plainDifference() {
        assertThat(ConsumptionRule.consumption(1000, 1012, 5)).isEqualTo(12);
        assertThat(ConsumptionRule.consumption(1000, 1000, 5)).isZero();
    }

    @Test
    void rolloverCompletesTheDial() {
        assertThat(ConsumptionRule.consumption(99990, 15, 5)).isEqualTo(25);
        assertThat(ConsumptionRule.consumption(99999, 0, 5)).isEqualTo(1);
    }

    @Test
    void readingsOutsideTheDialAreRejected() {
        assertThatThrownBy(() -> ConsumptionRule.consumption(100000, 0, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsumptionRule.consumption(0, -1, 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsumptionRule.consumption(0, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
