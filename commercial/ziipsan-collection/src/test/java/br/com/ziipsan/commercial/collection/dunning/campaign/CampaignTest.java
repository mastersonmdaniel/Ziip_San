package br.com.ziipsan.commercial.collection.dunning.campaign;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Programa de regularizacao por ato do ente (CAD-73). */
class CampaignTest {

    private static RegularizationCampaign campaign(BigDecimal principalPct) {
        return new RegularizationCampaign(UUID.randomUUID(), "Regulariza 2026",
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 31),
                "Decreto do ente 123/2026", UUID.randomUUID(), UUID.randomUUID(),
                30, 180, Set.of("RESIDENTIAL"),
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("50"),
                principalPct, 12, "CAMPAIGN_ADHESION_TERM", true);
    }

    @Test
    @DisplayName("aprovacao dupla: proponente igual ao aprovador e recusado; ato nao citado tambem")
    void doubleApprovalAndAct() {
        UUID same = UUID.randomUUID();
        assertThatThrownBy(() -> new RegularizationCampaign(UUID.randomUUID(), "X",
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 31), "Ato 1/2026",
                same, same, 30, 180, Set.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 12, "TERM", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RegularizationCampaign(UUID.randomUUID(), "X",
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 31), " ",
                UUID.randomUUID(), UUID.randomUUID(), 30, 180, Set.of(), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 12, "TERM", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("abatimentos: multa 100%, juros 50%, correcao 50% sobre 1000+100+200+100 = 1150")
    void discountsMath() {
        var result = campaign(BigDecimal.ZERO).discountsFor(
                new RegularizationCampaign.DebtComponents(
                        new BigDecimal("1000"), new BigDecimal("100"),
                        new BigDecimal("200"), new BigDecimal("100")),
                BigDecimal.ZERO);
        assertThat(result.fineDiscount()).isEqualByComparingTo("100.00");
        assertThat(result.interestDiscount()).isEqualByComparingTo("100.00");
        assertThat(result.correctionDiscount()).isEqualByComparingTo("50.00");
        assertThat(result.principalDiscount()).isEqualByComparingTo("0.00");
        assertThat(result.discountedTotal()).isEqualByComparingTo("1150.00");
    }

    @Test
    @DisplayName("abatimento do principal FORA DO LIMITE: recusado na campanha e na adesao (teste do aceite)")
    void principalOutsideLimitRefused() {
        var over = campaign(new BigDecimal("20"));
        assertThatThrownBy(() -> over.requireWithinPrincipalLimit(new BigDecimal("10")))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> over.discountsFor(
                new RegularizationCampaign.DebtComponents(new BigDecimal("1000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new BigDecimal("10")))
                .isInstanceOf(ApiException.Validation.class);
        // dentro do limite: 20% com limite 20 passa
        over.requireWithinPrincipalLimit(new BigDecimal("20"));
    }

    @Test
    @DisplayName("publico-alvo por faixa de atraso e categoria; vigencia fechada")
    void targeting() {
        var c = campaign(BigDecimal.ZERO);
        assertThat(c.targets(30, "RESIDENTIAL")).isTrue();
        assertThat(c.targets(180, "RESIDENTIAL")).isTrue();
        assertThat(c.targets(29, "RESIDENTIAL")).isFalse();
        assertThat(c.targets(181, "RESIDENTIAL")).isFalse();
        assertThat(c.targets(60, "COMMERCIAL")).isFalse();
        assertThat(c.activeAt(LocalDate.of(2026, 11, 1))).isTrue();
        assertThat(c.activeAt(LocalDate.of(2027, 2, 1))).isFalse();
    }

    @Test
    @DisplayName("relatorio de adesao e recuperacao; perda do beneficio so no descumprimento previsto")
    void reportAndBenefitLoss() {
        var c = campaign(BigDecimal.ZERO);
        var adherences = List.of(
                new RegularizationCampaign.Adherence(UUID.randomUUID(), UUID.randomUUID(),
                        LocalDate.of(2026, 11, 10), UUID.randomUUID(), new BigDecimal("500"), false),
                new RegularizationCampaign.Adherence(UUID.randomUUID(), UUID.randomUUID(),
                        LocalDate.of(2026, 11, 12), UUID.randomUUID(), new BigDecimal("300"), true));
        var report = RegularizationCampaign.report(c, adherences);
        assertThat(report.adherents()).isEqualTo(2);
        assertThat(report.totalRecovered()).isEqualByComparingTo("800");
        assertThat(report.defaults()).isEqualTo(1);
        assertThat(report.benefitsLost()).isEqualTo(1);
        assertThat(adherences.get(0).benefitLost(c)).isFalse();
        assertThat(adherences.get(1).benefitLost(c)).isTrue();
    }

    @Test
    @DisplayName("D6: teto do principal validado JA NA CRIACAO da campanha (nao so no calculo de desconto)")
    void principalLimitValidatedAtCreation() {
        assertThatThrownBy(() -> RegularizationCampaign.createWithinPrincipalLimit(
                UUID.randomUUID(), "Regulariza 2026",
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 31),
                "Decreto do ente 123/2026", UUID.randomUUID(), UUID.randomUUID(),
                30, 180, Set.of("RESIDENTIAL"),
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("50"),
                new BigDecimal("20"), 12, "CAMPAIGN_ADHESION_TERM", true,
                new BigDecimal("10")))
                .isInstanceOf(ApiException.Validation.class);
        var within = RegularizationCampaign.createWithinPrincipalLimit(
                UUID.randomUUID(), "Regulariza 2026",
                LocalDate.of(2026, 11, 1), LocalDate.of(2027, 1, 31),
                "Decreto do ente 123/2026", UUID.randomUUID(), UUID.randomUUID(),
                30, 180, Set.of("RESIDENTIAL"),
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("50"),
                new BigDecimal("10"), 12, "CAMPAIGN_ADHESION_TERM", true,
                new BigDecimal("10"));
        assertThat(within.principalDiscountPct()).isEqualByComparingTo("10");
    }
}
