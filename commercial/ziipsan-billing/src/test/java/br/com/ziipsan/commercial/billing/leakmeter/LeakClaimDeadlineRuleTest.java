package br.com.ziipsan.commercial.billing.leakmeter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.28: prazo do vazamento pelas duas ancoras atestadas (leitura e vencimento). */
class LeakClaimDeadlineRuleTest {

    private static LeakMeterParameters params(String days, String anchor) {
        return LeakMeterParameters.fromValues(Map.of(
                LeakMeterParameters.LEAK_CLAIM_DEADLINE_DAYS, days,
                LeakMeterParameters.LEAK_CLAIM_DEADLINE_ANCHOR, "\"" + anchor + "\""));
    }

    @Test
    @DisplayName("ancora na leitura: 45 dias de 10/03 vao ate 24/04 inclusive; 25/04 e intempestivo")
    void readingAnchor() {
        LeakMeterParameters p = params("45", "READING_DATE");
        LocalDate reading = LocalDate.of(2026, 3, 10);
        assertThat(LeakClaimDeadlineRule.deadlineOn(p, reading, null)).isEqualTo(LocalDate.of(2026, 4, 24));
        assertThat(LeakClaimDeadlineRule.evaluate(p, LocalDate.of(2026, 4, 24), reading, null).timely()).isTrue();
        assertThat(LeakClaimDeadlineRule.evaluate(p, LocalDate.of(2026, 4, 25), reading, null).timely()).isFalse();
    }

    @Test
    @DisplayName("ancora no vencimento: 30 dias de 20/03 vao ate 19/04 inclusive")
    void dueDateAnchor() {
        LeakMeterParameters p = params("30", "DUE_DATE");
        LocalDate due = LocalDate.of(2026, 3, 20);
        assertThat(LeakClaimDeadlineRule.deadlineOn(p, null, due)).isEqualTo(LocalDate.of(2026, 4, 19));
        assertThat(LeakClaimDeadlineRule.evaluate(p, LocalDate.of(2026, 4, 19), null, due).timely()).isTrue();
        assertThat(LeakClaimDeadlineRule.evaluate(p, LocalDate.of(2026, 4, 20), null, due).timely()).isFalse();
    }

    @Test
    @DisplayName("pedido antes da propria ancora e intempestivo (a fatura ainda nao existia na data ancorada)")
    void claimBeforeAnchor() {
        LeakMeterParameters p = params("30", "DUE_DATE");
        assertThat(LeakClaimDeadlineRule.evaluate(p, LocalDate.of(2026, 3, 19), null,
                LocalDate.of(2026, 3, 20)).timely()).isFalse();
    }

    @Test
    @DisplayName("ancora sem a data correspondente: erro explicito nomeando a data que falta")
    void anchorWithoutDate() {
        LeakMeterParameters p = params("45", "READING_DATE");
        assertThatThrownBy(() -> LeakClaimDeadlineRule.deadlineOn(p, null, LocalDate.of(2026, 3, 20)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("leitura");
    }

    @Test
    @DisplayName("requireTimely lanca com as datas quando intempestivo e devolve a conferencia quando tempestivo")
    void requireTimelyBehavior() {
        LeakMeterParameters p = params("30", "DUE_DATE");
        LocalDate due = LocalDate.of(2026, 3, 20);
        assertThat(LeakClaimDeadlineRule.requireTimely(p, LocalDate.of(2026, 4, 1), null, due).deadlineOn())
                .isEqualTo(LocalDate.of(2026, 4, 19));
        assertThatThrownBy(() -> LeakClaimDeadlineRule.requireTimely(p, LocalDate.of(2026, 5, 1), null, due))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2026-04-19");
    }
}
