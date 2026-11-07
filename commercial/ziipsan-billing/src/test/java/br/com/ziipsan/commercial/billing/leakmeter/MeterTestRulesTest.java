package br.com.ziipsan.commercial.billing.leakmeter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.28: aviso, cobranca e meses de revisao da afericao (meter.test_*). */
class MeterTestRulesTest {

    private static LeakMeterParameters params(String noticeDays, String reviewMonths, Boolean onlyIfNormal) {
        Map<String, String> map = new HashMap<>();
        map.put(LeakMeterParameters.METER_TEST_NOTICE_DAYS, noticeDays);
        map.put(LeakMeterParameters.METER_TEST_REVIEW_MONTHS, reviewMonths);
        if (onlyIfNormal != null) {
            map.put(LeakMeterParameters.METER_TEST_CHARGE_ONLY_IF_NORMAL, onlyIfNormal.toString());
        }
        return LeakMeterParameters.fromValues(map);
    }

    @Test
    @DisplayName("aviso de 5 dias: aviso em 01/08 so permite ensaio de 06/08 em diante")
    void noticeWindow() {
        LeakMeterParameters p = params("5", "6", null);
        LocalDate notice = LocalDate.of(2026, 8, 1);
        assertThat(MeterTestRules.earliestTestDate(p, notice)).isEqualTo(LocalDate.of(2026, 8, 6));
        assertThat(MeterTestRules.isNoticeTimely(p, notice, LocalDate.of(2026, 8, 5))).isFalse();
        assertThat(MeterTestRules.isNoticeTimely(p, notice, LocalDate.of(2026, 8, 6))).isTrue();
        assertThatThrownBy(() -> MeterTestRules.requireTimelyNotice(p, notice, LocalDate.of(2026, 8, 5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2026-08-06");
    }

    @Test
    @DisplayName("cobranca so se normal (padrao): NORMAL cobra; IRREGULAR e INCONCLUSIVO nao")
    void chargeOnlyIfNormal() {
        LeakMeterParameters p = params("5", "6", null);
        assertThat(MeterTestRules.chargeDecision(p, MeterTestRules.MeterVerdict.NORMAL).chargeable()).isTrue();
        assertThat(MeterTestRules.chargeDecision(p, MeterTestRules.MeterVerdict.IRREGULAR).chargeable()).isFalse();
        assertThat(MeterTestRules.chargeDecision(p, MeterTestRules.MeterVerdict.INCONCLUSIVE).chargeable()).isFalse();
    }

    @Test
    @DisplayName("tenant desligou a condicao: qualquer veredito e cobravel")
    void chargeRegardlessWhenDisabled() {
        LeakMeterParameters p = params("5", "6", false);
        assertThat(MeterTestRules.chargeDecision(p, MeterTestRules.MeterVerdict.IRREGULAR).chargeable()).isTrue();
        assertThat(MeterTestRules.chargeDecision(p, MeterTestRules.MeterVerdict.INCONCLUSIVE).chargeable()).isTrue();
    }

    @Test
    @DisplayName("irregular com 6 meses de revisao e ensaio em 15/08/2026: referencias 2026-03 a 2026-08")
    void reviewReferencesForIrregular() {
        LeakMeterParameters p = params("5", "6", null);
        assertThat(MeterTestRules.reviewReferences(p, MeterTestRules.MeterVerdict.IRREGULAR,
                LocalDate.of(2026, 8, 15)))
                .containsExactly(YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2026, 5),
                        YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8));
    }

    @Test
    @DisplayName("normal ou inconclusivo nao abre revisao: lista vazia")
    void noReviewForOtherVerdicts() {
        LeakMeterParameters p = params("5", "6", null);
        assertThat(MeterTestRules.reviewReferences(p, MeterTestRules.MeterVerdict.NORMAL,
                LocalDate.of(2026, 8, 15))).isEmpty();
        assertThat(MeterTestRules.reviewReferences(p, MeterTestRules.MeterVerdict.INCONCLUSIVE,
                LocalDate.of(2026, 8, 15))).isEmpty();
    }
}
