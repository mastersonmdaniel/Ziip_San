package br.com.ziipsan.commercial.billing.leakmeter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.28: o limite de faturas em 12 meses, nos modos simples e sequencial, e a duplicidade. */
class LeakEligibilityRuleTest {

    private static LeakMeterParameters params(String max, String mode) {
        Map<String, String> map = new HashMap<>();
        if (max != null) {
            map.put(LeakMeterParameters.LEAK_MAX_BILLS_PER_12_MONTHS, max);
        }
        if (mode != null) {
            map.put(LeakMeterParameters.LEAK_BILLS_LIMIT_MODE, "\"" + mode + "\"");
        }
        return LeakMeterParameters.fromValues(map);
    }

    @Test
    @DisplayName("sem teto declarado com nulo explicito (caso ARIS): cinco faturas na janela nao barram a sexta")
    void noCap() {
        LeakEligibilityRule.Eligibility e = LeakEligibilityRule.evaluate(params("null", null),
                List.of(YearMonth.of(2025, 9), YearMonth.of(2025, 11), YearMonth.of(2026, 1),
                        YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
                List.of(YearMonth.of(2026, 4)));
        assertThat(e.eligible()).isTrue();
        assertThat(e.maxBills()).isNull();
    }

    @Test
    @DisplayName("teto 2 (caso ARESC): duas na janela + uma pedida estoura; uma na janela + uma pedida passa")
    void capOfTwo() {
        LeakMeterParameters p = params("2", "ANY_IN_12_MONTHS");
        LeakEligibilityRule.Eligibility over = LeakEligibilityRule.evaluate(p,
                List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 2)), List.of(YearMonth.of(2026, 3)));
        assertThat(over.eligible()).isFalse();
        assertThat(over.priorsInWindow()).isEqualTo(2);
        assertThat(over.reason()).contains("2 faturas em 12 meses");
        LeakEligibilityRule.Eligibility ok = LeakEligibilityRule.evaluate(p,
                List.of(YearMonth.of(2026, 1)), List.of(YearMonth.of(2026, 3)));
        assertThat(ok.eligible()).isTrue();
    }

    @Test
    @DisplayName("janela de 12 meses pelas referencias: 2025-03 esta fora para pedido de 2026-03; 2025-04 esta dentro")
    void windowBoundary() {
        LeakMeterParameters p = params("2", "ANY_IN_12_MONTHS");
        // A referencia exatamente 12 meses antes da primeira pedida esta no limite e NAO conta.
        LeakEligibilityRule.Eligibility outside = LeakEligibilityRule.evaluate(p,
                List.of(YearMonth.of(2025, 3)), List.of(YearMonth.of(2026, 3)));
        assertThat(outside.priorsInWindow()).isEqualTo(0);
        LeakEligibilityRule.Eligibility inside = LeakEligibilityRule.evaluate(p,
                List.of(YearMonth.of(2025, 4)), List.of(YearMonth.of(2026, 3)));
        assertThat(inside.priorsInWindow()).isEqualTo(1);
    }

    @Test
    @DisplayName("modo sequencial (caso AGIR): 2026-01 + 2026-03 tem buraco e e inelegivel; 2026-01 + 2026-02 passa")
    void sequentialMode() {
        LeakMeterParameters p = params("2", "SEQUENTIAL_IN_12_MONTHS");
        LeakEligibilityRule.Eligibility gap = LeakEligibilityRule.evaluate(p, List.of(),
                List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 3)));
        assertThat(gap.eligible()).isFalse();
        assertThat(gap.sequentialSatisfied()).isFalse();
        LeakEligibilityRule.Eligibility ok = LeakEligibilityRule.evaluate(p, List.of(),
                List.of(YearMonth.of(2026, 2), YearMonth.of(2026, 1)));
        assertThat(ok.eligible()).isTrue();
        assertThat(ok.sequentialSatisfied()).isTrue();
    }

    @Test
    @DisplayName("modo simples nao exige sequencia: 2026-01 + 2026-03 dentro do teto e elegivel")
    void anyModeDoesNotRequireSequence() {
        LeakEligibilityRule.Eligibility e = LeakEligibilityRule.evaluate(params("2", "ANY_IN_12_MONTHS"),
                List.of(), List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 3)));
        assertThat(e.eligible()).isTrue();
    }

    @Test
    @DisplayName("fatura ja beneficiada pedida de novo: inelegivel por duplicidade, mesmo sem teto")
    void duplicateReference() {
        LeakEligibilityRule.Eligibility e = LeakEligibilityRule.evaluate(params("null", null),
                List.of(YearMonth.of(2026, 1)), List.of(YearMonth.of(2026, 1)));
        assertThat(e.eligible()).isFalse();
        assertThat(e.reason()).contains("duplicidade");
        assertThatThrownBy(() -> LeakEligibilityRule.requireEligible(params("null", null),
                List.of(YearMonth.of(2026, 1)), List.of(YearMonth.of(2026, 1))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("inelegivel");
    }
}
