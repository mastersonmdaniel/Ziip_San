package br.com.ziipsan.commercial.billing.tariff;

import br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RAS-13 (P2.5/F5): cobrança por economia com franquia e excedente, recusa do
 * modelo híbrido (STJ, Tema 414) e medição individualizada (Lei 13.312/2016).
 * Tarifa fictícia T1 (RES: franquia 10 m³, parcela fixa 30,00; faixa até 20 a
 * 4,00/m³) — valores conferidos à mão. Condomínio completo é da ED-15.
 */
class EconomyChargeRulesTest {

    @Test
    @DisplayName("modelo híbrido vedado pelo STJ (Tema 414) é RECUSADO na configuração")
    void hybridModelRefused() {
        assertThatThrownBy(() -> EconomyChargeRules.validateConfiguration(
                EconomyChargeRules.Model.HYBRID_MINIMUM_TIMES_ECONOMIES_SINGLE_METER))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("Tema 414");
    }

    @Test
    @DisplayName("franquia e excedente por economia: 2 economias RES, 27 m³ → franquia 20, excedente 7 (3 por economia): 2×30,00 + 6 m³ × 4,00 = 84,00")
    void franchiseAndExcessPerEconomy() {
        EconomyChargeRules.validateConfiguration(
                EconomyChargeRules.Model.PER_ECONOMY_FRANCHISE_AND_EXCESS);
        List<CategoryCharge> charges = TariffEngine.categoriesFor(
                FictitiousTariffs.t1(), List.of(FictitiousTariffs.res(2)), 27);
        BigDecimal total = charges.stream().map(CategoryCharge::net)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        assertThat(total).isEqualByComparingTo("84.00");
    }

    @Test
    @DisplayName("medição individualizada (Lei 13.312/2016): hidrômetros de 12 m³ (30,00 + 2×4,00 = 38,00) e 8 m³ (só a parcela fixa, 30,00) somam 68,00")
    void individualizedMeteringSumsEachMeter() {
        BigDecimal total = EconomyChargeRules.individualizedTotal(
                FictitiousTariffs.t1Schedule(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30),
                List.of(new EconomyChargeRules.MeteredEconomy(FictitiousTariffs.res(1), 12),
                        new EconomyChargeRules.MeteredEconomy(FictitiousTariffs.res(1), 8)));
        assertThat(total).isEqualByComparingTo("68.00");
    }

    @Test
    @DisplayName("medição individualizada com grupo de 2 economias num hidrômetro só é recusada")
    void individualizedMeteringRejectsGroupedMeter() {
        assertThatThrownBy(() -> new EconomyChargeRules.MeteredEconomy(FictitiousTariffs.res(2), 12))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
