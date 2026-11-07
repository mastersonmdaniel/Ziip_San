package br.com.ziipsan.commercial.billing.tariff;

import br.com.ziipsan.commercial.billing.parameters.BillingParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RAS-13 (P2.5/F5): categorias opcionais da NR 13 (art. 6º) em
 * {@code tariff.optional_categories} (classe d) — critério, prova e validade
 * são do tenant (fictícios nestes testes, só para exercitar a regra).
 */
class OptionalCategoriesTest {

    private static BillingParameters parameters(String json) {
        return BillingParameters.fromValues(Map.of(BillingParameters.OPTIONAL_CATEGORIES, json));
    }

    @Test
    @DisplayName("as 4 categorias do art. 6º, cada uma com critério, prova e validade")
    void fourOptionalCategoriesWithCriterionProofValidity() {
        List<OptionalCategoryRule> rules = parameters("""
                [
                  {"category": "VULNERABLE_RESIDENTIAL", "criterion": "criterio-ficticio-vulneravel",
                   "proof": "prova-ficticia-vulneravel", "validityMonths": 12},
                  {"category": "MICRO_SMALL_BUSINESS", "criterion": "criterio-ficticio-me-epp",
                   "proof": "prova-ficticia-me-epp", "validityMonths": 24},
                  {"category": "COLLECTIVE_SINGLE_METER", "criterion": "criterio-ficticio-coletiva",
                   "proof": "prova-ficticia-coletiva", "validityMonths": 12},
                  {"category": "ASSISTANCE_ENTITY", "criterion": "criterio-ficticio-assistencial",
                   "proof": "prova-ficticia-assistencial", "validityMonths": 36}
                ]
                """).optionalCategories();
        assertThat(rules).hasSize(4);
        assertThat(rules).extracting(r -> r.category().name()).containsExactly(
                "VULNERABLE_RESIDENTIAL", "MICRO_SMALL_BUSINESS",
                "COLLECTIVE_SINGLE_METER", "ASSISTANCE_ENTITY");
        assertThat(rules.get(3).criterion()).isEqualTo("criterio-ficticio-assistencial");
        assertThat(rules.get(3).proof()).isEqualTo("prova-ficticia-assistencial");
        assertThat(rules.get(3).validityMonths()).isEqualTo(36);
    }

    @Test
    @DisplayName("categoria sem prova declarada é recusada")
    void missingProofRefused() {
        assertThatThrownBy(() -> parameters("""
                [{"category": "ASSISTANCE_ENTITY", "criterion": "criterio-ficticio",
                  "proof": "", "validityMonths": 12}]
                """).optionalCategories())
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("prova");
    }

    @Test
    @DisplayName("categoria com validade zero é recusada")
    void zeroValidityRefused() {
        assertThatThrownBy(() -> parameters("""
                [{"category": "VULNERABLE_RESIDENTIAL", "criterion": "criterio-ficticio",
                  "proof": "prova-ficticia", "validityMonths": 0}]
                """).optionalCategories())
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("validade");
    }
}
