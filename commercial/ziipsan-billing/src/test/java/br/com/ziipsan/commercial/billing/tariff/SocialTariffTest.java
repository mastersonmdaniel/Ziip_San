package br.com.ziipsan.commercial.billing.tariff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarifa social como regra de cálculo parametrizável.
 *
 * <p>TODOS os percentuais e limites de volume destes testes são <b>fictícios e escolhidos só para
 * exercitar o mecanismo</b>; o produto não embute nenhum (o desconto e o limite de volume são
 * parâmetros da tabela tarifária do tenant). Os arranjos testados imitam, de forma genérica, os
 * desenhos que o relatório 08 descreve entre as agências: uma banda de 0 a N m³; duas bandas
 * escalonadas; uma banda sem limite de volume; desconto também sobre a parcela fixa.
 *
 * <p>Base: T1, RES, consumo 27 m³, 1 economia social. Sem desconto: 30,00 (fixa) + 40,00 (11..20 m³ a
 * 4,00) + 42,00 (21..27 m³ a 6,00) = 112,00.
 */
class SocialTariffTest {

    record Case(String id, SocialTariffRule rule, String expectedDiscount, String expectedNet) {
        @Override
        public String toString() {
            return id;
        }
    }

    static Stream<Case> cases() {
        return Stream.of(
                // banda (0,15] a 50%: só a faixa 11..20 tem m³ dentro da banda: 11..15 = 5 m³ × 4,00 × 50% = 10,00
                new Case("S-1 uma banda 0..15 a 50%, fixa sem desconto",
                        social("0", band(0, 15, "50")), "10.00", "102.00"),
                // idem + 50% sobre a fixa: 30,00 × 50% = 15,00; total do desconto 25,00
                new Case("S-2 uma banda 0..15 a 50% e 50% na fixa",
                        social("50", band(0, 15, "50")), "25.00", "87.00"),
                // (0,15] a 50% e (15,20] a 25%: 11..15 → 5×4×50% = 10,00; 16..20 → 5×4×25% = 5,00; faixa 21..27 fora
                new Case("S-3 duas bandas escalonadas 50% / 25%",
                        social("0", band(0, 15, "50"), band(15, 20, "25")), "15.00", "97.00"),
                // banda sem limite a 50%: faixa 11..20: 10×4×50% = 20,00; faixa 21..27: 7×6×50% = 21,00 → 41,00
                new Case("S-4 uma banda sem limite de volume a 50%",
                        social("0", band(0, null, "50")), "41.00", "71.00"),
                // 100% nos m³: todas as faixas de preço zeradas (40,00 + 42,00 = 82,00); a fixa (30,00) fica
                new Case("S-5 banda sem limite a 100%",
                        social("0", band(0, null, "100")), "82.00", "30.00")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("desconto por volume (bandas) e sobre a parcela fixa, tudo parametrizado")
    void discountFollowsParameters(Case c) {
        TariffVersion v = t1WithSocial(c.rule());

        CategoryCharge charge = TariffEngine.categoriesFor(v, List.of(socialRes(1, 1)), 27).get(0);

        assertThat(charge.gross()).isEqualByComparingTo("112.00");
        assertThat(charge.discount()).isEqualByComparingTo(c.expectedDiscount());
        assertThat(charge.net()).isEqualByComparingTo(c.expectedNet());
    }

    @Test
    @DisplayName("só as economias sociais recebem o desconto: 2 economias, 1 social, 35 m³ (7 por economia na faixa 11..17)")
    void onlySocialEconomiesGetTheDiscount() {
        // por economia: 30,00 + 7 × 4,00 = 58,00; bruto = 116,00.
        // banda (0,15]: a faixa 11..17 tem 11..15 = 5 m³ na banda → 5 × 4,00 × 50% = 10,00 por economia social. 1 social → 10,00.
        TariffVersion v = t1WithSocial(social("0", band(0, 15, "50")));

        CategoryCharge c = TariffEngine.categoriesFor(v, List.of(socialRes(2, 1)), 35).get(0);

        assertThat(c.gross()).isEqualByComparingTo("116.00");
        assertThat(c.discount()).isEqualByComparingTo("10.00");
        assertThat(c.net()).isEqualByComparingTo("106.00");
        assertThat(c.socialEconomies()).isEqualTo(1);

        // duas economias sociais: 20,00
        CategoryCharge both = TariffEngine.categoriesFor(v, List.of(socialRes(2, 2)), 35).get(0);
        assertThat(both.discount()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("consumo dentro da franquia: só a parcela fixa; desconto na fixa 50% → 15,00")
    void discountOnMinimumChargeOnly() {
        TariffVersion v = t1WithSocial(social("50", band(0, 15, "50")));

        CategoryCharge c = TariffEngine.categoriesFor(v, List.of(socialRes(1, 1)), 8).get(0);

        assertThat(c.gross()).isEqualByComparingTo("30.00");
        assertThat(c.discount()).isEqualByComparingTo("15.00");
        assertThat(c.net()).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("sem economia social, a regra social da versão não muda nada")
    void noSocialEconomyNoDiscount() {
        TariffVersion v = t1WithSocial(social("50", band(0, null, "100")));

        assertThat(TariffEngine.categoriesFor(v, List.of(res(1)), 27).get(0).net()).isEqualByComparingTo("112.00");
    }

    @Test
    @DisplayName("economia social numa versão sem regra de tarifa social é erro (nunca desconto suposto)")
    void socialEconomyWithoutRuleIsAnError() {
        assertThatThrownBy(() -> TariffEngine.categoriesFor(t1(), List.of(socialRes(1, 1)), 27))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("tarifa social");
    }

    @Test
    @DisplayName("desconto com tarifa em duas vigências: cada versão aplica a própria regra, depois rateia por dias")
    void socialWithTwoVersions() {
        // Versão A (T1): bruto 112,00, desconto 10,00 (banda 0..15 a 50%). Versão B: 33,00 + 44,00 + 46,20 = 123,20;
        // faixa 11..20 a 4,40: 5 m³ na banda × 4,40 × 50% = 11,00. Rateio 0,5000 cada:
        // A: bruto 56,00, desconto 5,00 → 51,00. B: bruto 61,60, desconto 5,50 → 56,10. Total 107,10.
        TariffVersion a = new TariffVersion(id(100), T1_FROM, LocalDate.of(2026, 9, 19), List.of(t1Res(), t1Com()),
                social("0", band(0, 15, "50")));
        TariffVersion b = new TariffVersion(id(200), LocalDate.of(2026, 9, 20), null,
                List.of(res(10, "33.00", block(20, "4.40"), block(50, "6.60"), block(null, "9.90"))),
                social("0", band(0, 15, "50")));

        TariffCharge charge = TariffEngine.calculate(scheduleOf(a, b), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 4),
                List.of(socialRes(1, 1)), 27);

        assertThat(charge.gross()).isEqualByComparingTo("117.60");
        assertThat(charge.discount()).isEqualByComparingTo("10.50");
        assertThat(charge.net()).isEqualByComparingTo("107.10");
    }

    @Test
    @DisplayName("esgoto: o desconto social só vale se o tenant mandar (decisão não confirmada em norma)")
    void sewerFollowsTheSocialFlag() {
        TariffSchedule schedule = scheduleOf(t1WithSocial(social("0", band(0, 15, "50"))));
        List<TariffGroup> groups = List.of(socialRes(1, 1));
        LocalDate from = LocalDate.of(2026, 9, 5);
        LocalDate to = LocalDate.of(2026, 10, 4);

        // volume 27, pcesgoto 100%: estrutura com desconto = 102,00; sem desconto = 112,00
        SewerCharge with = TariffEngine.calculateSewer(schedule, from, to, groups, 27,
                new SewerParameters(d("100"), d("100"), null, SewerFactorPolicy.EXACT, true));
        SewerCharge without = TariffEngine.calculateSewer(schedule, from, to, groups, 27,
                new SewerParameters(d("100"), d("100"), null, SewerFactorPolicy.EXACT, false));

        assertThat(with.net()).isEqualByComparingTo("102.00");
        assertThat(without.net()).isEqualByComparingTo("112.00");
    }

    @Test
    @DisplayName("o desconto nunca excede o bruto, e a regra social não depende de nenhum dado de adimplência")
    void discountNeverExceedsGrossAndHasNoPaymentInput() {
        TariffVersion v = t1WithSocial(social("100", band(0, null, "100")));

        for (int consumption = 0; consumption <= 120; consumption++) {
            for (int economies = 1; economies <= 4; economies++) {
                for (int social = 0; social <= economies; social++) {
                    CategoryCharge c = TariffEngine.categoriesFor(v, List.of(socialRes(economies, social)), consumption).get(0);
                    assertThat(c.discount()).isLessThanOrEqualTo(c.gross());
                    assertThat(c.net().signum()).isGreaterThanOrEqualTo(0);
                }
            }
        }
        // a assinatura de TariffEngine.categoriesFor não recebe situação de pagamento: a concessão
        // não pode ser condicionada à adimplência (Lei 14.898/2024, relatório 08, R-14)
    }

    @Test
    @DisplayName("regra social malformada é recusada: sem bandas, sobreposição, percentual fora de (0,100], fixa ausente")
    void malformedSocialRules() {
        assertThatThrownBy(() -> new SocialTariffRule(d("0"), List.of()))
                .isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> new SocialTariffRule(null, List.of(band(0, 15, "50"))))
                .as("desconto da parcela fixa tem de ser informado, ainda que seja 0")
                .isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> social("0", band(0, 15, "50"), band(10, 20, "25")))
                .as("sobreposição").isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> social("0", band(0, null, "50"), band(20, 30, "25")))
                .as("banda sem limite não admite outra depois").isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> band(0, 15, "0")).isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> band(0, 15, "100.01")).isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> band(15, 15, "50")).isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> social("101", band(0, 15, "50"))).isInstanceOf(TariffConfigurationException.class);
        // bandas adjacentes (0,15] e (15,20] são válidas
        assertThat(social("0", band(15, 20, "25"), band(0, 15, "50")).bands().get(0).fromM3()).isZero();
    }

    @Test
    @DisplayName("economias sociais não podem exceder as economias do grupo")
    void socialEconomiesBoundedByEconomies() {
        assertThatThrownBy(() -> new TariffGroup(RES, null, 2, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TariffGroup(RES, null, 2, -1)).isInstanceOf(IllegalArgumentException.class);
    }

}
