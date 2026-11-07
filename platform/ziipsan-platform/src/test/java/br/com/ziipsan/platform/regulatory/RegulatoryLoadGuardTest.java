package br.com.ziipsan.platform.regulatory;

import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.Confirmation;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.DeadlineBasis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A guarda de carga (P2.31) aplica os limites da P0.20 (RAS-16) e recusa a
 * regra local que contraria lei federal — um teste nomeado por chave ou
 * por regra, como o acceptance da P0.20 pede para os limites travados.
 */
class RegulatoryLoadGuardTest {

    private static RegulatoryProfileDef profileWith(String key, String valueJson) {
        return RegulatoryProfileDef.builder("SYN_TEST", "SYN", "Sintético", "Ato sintético",
                        LocalDate.of(2026, 1, 1), null)
                .value(key, valueJson, "Ato sintético", "art. 1", DeadlineBasis.NOT_APPLICABLE,
                        Confirmation.CONFIRMED, null)
                .build();
    }

    @Test
    @DisplayName("cut.forbidden_window: janela local até 12 h de sexta é recusada como valor (Lei 14.015/2020)")
    void cutWindowFridayNoonRefusedAsValue() {
        assertThatThrownBy(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("cut.forbidden_window", "{\"rule\":\"FRIDAY_UNTIL_12H\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("14.015");
        assertThatThrownBy(() -> RegulatoryLoadGuard.assertValueLoadable(
                "cut.forbidden_window", "{\"rule\":\"FRIDAY_UNTIL_12H\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> RegulatoryLoadGuard.assertValueLoadable(
                "cut.forbidden_window", "{\"rule\":\"FEDERAL_FLOOR_14_015\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Tarifa social: regra de perda do benefício por cortes é recusada em qualquer chave (Lei 14.898/2024)")
    void socialBenefitLossRuleRefused() {
        assertThatThrownBy(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("social.local_rule", "\"SOCIAL_LOSS_AFTER_TWO_CUTS\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("14.898");
        assertThatThrownBy(() -> RegulatoryLoadGuard.assertValueLoadable(
                "social.local_rule", "\"BENEFIT_LOSS_BY_CUTS\""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fee.fine_pct: 2,00 de fábrica passa; acima de 2,00 não é carga de fábrica (P0.20, classe b)")
    void feeFineAboveFactoryCeilingRefused() {
        assertThatCode(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("fee.fine_pct", "2.00"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("fee.fine_pct", "2.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2%");
    }

    @Test
    @DisplayName("fee.interest_daily_pct: teto de SC de 0,033% ao dia; acima é recusado (mapa de ERIs §4)")
    void feeInterestAboveScCapRefused() {
        assertThatCode(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("fee.interest_daily_pct", "0.033"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("fee.interest_daily_pct", "0.034")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0,033");
    }

    @Test
    @DisplayName("fee.interest_mode: CALENDAR_MONTH fora do catálogo da P0.20 é recusado (RAS-16)")
    void feeInterestModeCalendarMonthRefused() {
        assertThatThrownBy(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("fee.interest_mode", "\"CALENDAR_MONTH\"")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("fee.interest_mode", "\"DAILY_RATE_WITH_CAP\"")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cut.min_notice_days: 29 é recusado pelo piso federal de 30; 30 e 45 passam (classe c, só amplia)")
    void cutNoticeBelowFederalFloorRefused() {
        assertThatThrownBy(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("cut.min_notice_days", "29")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30");
        assertThatCode(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("cut.min_notice_days", "45"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ret.billing_history_months: 119 é recusado pelo piso de 120 (NR 11, art. 22; RAS-16)")
    void retBillingHistoryBelowFloorRefused() {
        assertThatThrownBy(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("ret.billing_history_months", "119")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("120");
    }

    @Test
    @DisplayName("meter.average_months e meter.max_estimated_cycles: zero é recusado; 6 e 3 passam (RAS-16)")
    void meterAverageAndCyclesValidated() {
        assertThatThrownBy(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("meter.average_months", "0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("meter.max_estimated_cycles", "0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("meter.average_months", "6"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("meter.after_cap_rule: fora do catálogo da P2.5 é recusado")
    void afterCapRuleOutsideCatalogRefused() {
        assertThatThrownBy(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("meter.after_cap_rule", "\"MINIMUM_CONSUMPTION\"")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P2.5");
        assertThatCode(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("meter.after_cap_rule", "\"SETTLE_BALANCE_IN_3_INSTALLMENTS\"")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bill.due_date_options: mínimo de 4 do CISAM-SUL não reduz o piso de 6 para concessionária (Lei 8.987, art. 7º-A)")
    void cisamSulMinimumDoesNotLowerConcessionaireFloor() {
        RegulatoryProfileDef sul = ScRegulatoryProfiles.byKey("CISAM_SUL_RN03_2017");
        assertThatCode(() -> RegulatoryLoadGuard.validateDueDateOptionsFor(sul, false))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> RegulatoryLoadGuard.validateDueDateOptionsFor(sul, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("7º-A");
        RegulatoryProfileDef aris = ScRegulatoryProfiles.byKey("ARIS_RN19_2019");
        assertThatCode(() -> RegulatoryLoadGuard.validateDueDateOptionsFor(aris, true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("meter.reading_cycle_days: faixa invertida é recusada; 27 a 33 passa")
    void readingCycleBandValidated() {
        assertThatThrownBy(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("meter.reading_cycle_days", "{\"minDays\":33,\"maxDays\":27}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> RegulatoryLoadGuard.validateProfile(
                profileWith("meter.reading_cycle_days", "{\"minDays\":27,\"maxDays\":33}")))
                .doesNotThrowAnyException();
    }
}
