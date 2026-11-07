package br.com.ziipsan.commercial.billing.social;

import br.com.ziipsan.commercial.billing.parameters.BillingParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAD-88 (P2.5/F5): benefício vencido deixa de ser aplicado na competência
 * seguinte ao vencimento; o marco do aviso usa
 * {@code benefit.expiry_notice_days} (a mensagem é da P2.32). Datas fictícias,
 * conferidas à mão.
 */
class BenefitExpiryRuleTest {

    private static final LocalDate VALID_UNTIL = LocalDate.of(2026, 3, 15);

    @Test
    @DisplayName("benefício válido até 15/03/2026 vale na competência 2026-03 e NÃO vale na seguinte (2026-04)")
    void expiredBenefitNotAppliedInNextCompetence() {
        assertThat(BenefitExpiryRule.appliesIn(YearMonth.of(2026, 2), VALID_UNTIL)).isTrue();
        assertThat(BenefitExpiryRule.appliesIn(YearMonth.of(2026, 3), VALID_UNTIL)).isTrue();
        assertThat(BenefitExpiryRule.appliesIn(YearMonth.of(2026, 4), VALID_UNTIL)).isFalse();
        assertThat(BenefitExpiryRule.lastApplicableCompetence(VALID_UNTIL))
                .isEqualTo(YearMonth.of(2026, 3));
    }

    @Test
    @DisplayName("marco do aviso: vencimento 15/04/2026 com benefit.expiry_notice_days = 30 → aviso a partir de 16/03/2026")
    void noticeStartUsesParameterDays() {
        BillingParameters parameters = BillingParameters.fromValues(
                Map.of(BillingParameters.EXPIRY_NOTICE_DAYS, "30"));
        LocalDate validUntil = LocalDate.of(2026, 4, 15);
        assertThat(parameters.expiryNoticeDays()).isEqualTo(30);
        assertThat(BenefitExpiryRule.noticeStartDate(validUntil, parameters.expiryNoticeDays()))
                .isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(BenefitExpiryRule.noticeWindowOpen(LocalDate.of(2026, 3, 15), validUntil, 30)).isFalse();
        assertThat(BenefitExpiryRule.noticeWindowOpen(LocalDate.of(2026, 3, 16), validUntil, 30)).isTrue();
        assertThat(BenefitExpiryRule.noticeWindowOpen(LocalDate.of(2026, 4, 15), validUntil, 30)).isTrue();
        assertThat(BenefitExpiryRule.noticeWindowOpen(LocalDate.of(2026, 4, 16), validUntil, 30)).isFalse();
    }
}
