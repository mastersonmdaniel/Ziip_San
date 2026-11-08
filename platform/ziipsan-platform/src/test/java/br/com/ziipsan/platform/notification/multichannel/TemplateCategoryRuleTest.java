package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.NotificationCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Classificação, estimativa e triagem de marketing, calculados à mão. */
class TemplateCategoryRuleTest {

    private static final Map<String, WhatsAppCategory> MAP =
            Map.of("bill.whatsapp", WhatsAppCategory.UTILITY);

    @Test
    @DisplayName("modelo no mapa devolve a categoria declarada")
    void classifyPresent() {
        assertThat(TemplateCategoryRule.classify(MAP, "bill.whatsapp"))
                .contains(WhatsAppCategory.UTILITY);
    }

    @Test
    @DisplayName("modelo fora do mapa: vazio (nenhuma categoria presumida)")
    void classifyAbsent() {
        assertThat(TemplateCategoryRule.classify(MAP, "promo.whatsapp")).isEmpty();
    }

    @Test
    @DisplayName("estimativa = unitário x destinatários (0,35 x 3 = 1,05)")
    void estimateMultiplies() {
        assertThat(TemplateCategoryRule.estimate(new BigDecimal("0.35"), 3))
                .isEqualByComparingTo("1.05");
        assertThatThrownBy(() -> TemplateCategoryRule.estimate(new BigDecimal("0.35"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("modelo de cobrança utilidade com termo promocional: risco de marketing")
    void marketingRiskFlagsBillingUtilityWithPromoTerm() {
        boolean risk = TemplateCategoryRule.marketingRisk(WhatsAppCategory.UTILITY,
                NotificationCategory.BILLING, "Sua fatura chegou com DESCONTO especial",
                TemplateCategoryRule.PROPOSED_MARKETING_TERMS);
        assertThat(risk).isTrue();
    }

    @Test
    @DisplayName("modelo já declarado marketing não tem risco de reclassificação")
    void marketingRiskSkipsDeclaredMarketing() {
        boolean risk = TemplateCategoryRule.marketingRisk(WhatsAppCategory.MARKETING,
                NotificationCategory.BILLING, "Aproveite esta oferta com desconto",
                TemplateCategoryRule.PROPOSED_MARKETING_TERMS);
        assertThat(risk).isFalse();
    }

    @Test
    @DisplayName("modelo que não é de cobrança está fora da triagem do aceite")
    void marketingRiskSkipsNonBilling() {
        boolean risk = TemplateCategoryRule.marketingRisk(WhatsAppCategory.UTILITY,
                NotificationCategory.SERVICE_ORDER, "Equipe a caminho, aproveite para acompanhar",
                TemplateCategoryRule.PROPOSED_MARKETING_TERMS);
        assertThat(risk).isFalse();
    }

    @Test
    @DisplayName("a triagem de termos ignora caixa")
    void containsTermCaseInsensitive() {
        assertThat(TemplateCategoryRule.containsMarketingTerm("PROMOÇÃO da semana",
                TemplateCategoryRule.PROPOSED_MARKETING_TERMS)).isTrue();
        assertThat(TemplateCategoryRule.containsMarketingTerm("Sua fatura venceu ontem",
                TemplateCategoryRule.PROPOSED_MARKETING_TERMS)).isFalse();
    }
}
