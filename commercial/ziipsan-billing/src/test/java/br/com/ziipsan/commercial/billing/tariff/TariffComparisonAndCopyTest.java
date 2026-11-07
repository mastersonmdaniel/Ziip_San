package br.com.ziipsan.commercial.billing.tariff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cópia de versão e comparação vigente × proposta (P2.29), valores à mão
 * sobre a tarifa fictícia T1 com reajuste de 10%: RES 30,00→33,00 (+10,00%),
 * faixa 4,00→4,40 (+10,00%); COM 50,00→55,00.
 */
class TariffComparisonAndCopyTest {

    @Test
    @DisplayName("cópia reproduz preços, regras e regra social com id e vigência novos, sem tocar a origem")
    void copyReproducesTheStructure() {
        TariffVersion source = t1WithSocial(social("50", band(0, 20, "50")));

        TariffVersion copy = TariffVersionCopier.copy(source, id(300),
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));

        assertThat(copy.id()).isEqualTo(id(300));
        assertThat(copy.validFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(copy.validTo()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(copy.ruleFor(RES, null).minCharge()).isEqualByComparingTo("30.00");
        assertThat(copy.ruleFor(RES, null).blocks()).extracting(b -> b.pricePerM3().toPlainString())
                .containsExactly("4.00", "6.00", "9.00");
        assertThat(copy.socialRule().minimumChargeDiscountPct()).isEqualByComparingTo("50");
        // objetos novos, não os mesmos da origem
        assertThat(copy.ruleFor(RES, null)).isNotSameAs(source.ruleFor(RES, null));
        assertThat(copy.rules().get(0).blocks().get(0)).isNotSameAs(source.rules().get(0).blocks().get(0));
    }

    @Test
    @DisplayName("comparação do reajuste de 10%: parcela fixa e faixas com variação absoluta e percentual")
    void comparisonOfTheTenPercentReadjustment() {
        TariffVersion proposed = ReadjustmentCalculator.apply(t1(), id(301), LocalDate.of(2027, 1, 1), null,
                ReadjustFormula.INDEX_ONLY, d("10"), null);

        TariffComparison.Result result = TariffComparison.compare(t1(), proposed);

        TariffComparison.RuleDelta res = result.rules().stream()
                .filter(r -> r.categoryId().equals(RES)).findFirst().orElseThrow();
        assertThat(res.currentMinCharge()).isEqualByComparingTo("30.00");
        assertThat(res.proposedMinCharge()).isEqualByComparingTo("33.0000");
        assertThat(res.minChargeDelta()).isEqualByComparingTo("3.00");
        assertThat(res.minChargeDeltaPct()).isEqualByComparingTo("10.00");
        assertThat(res.blocks().get(0).currentPrice()).isEqualByComparingTo("4.00");
        assertThat(res.blocks().get(0).proposedPrice()).isEqualByComparingTo("4.4000");
        assertThat(res.blocks().get(0).deltaPct()).isEqualByComparingTo("10.00");

        TariffComparison.RuleDelta com = result.rules().stream()
                .filter(r -> r.categoryId().equals(COM)).findFirst().orElseThrow();
        assertThat(com.minChargeDeltaPct()).isEqualByComparingTo("10.00");
        assertThat(result.social().presentInCurrent()).isFalse();
        assertThat(result.social().presentInProposed()).isFalse();
    }

    @Test
    @DisplayName("comparação mostra categoria que só existe na proposta (nunca é ignorada)")
    void comparisonShowsRulesOnlyInTheProposal() {
        CategoryRule extra = CategoryRule.forCategory(id(9), 5, d("20.00"), null,
                List.of(block(null, "3.00")));
        TariffVersion proposed = new TariffVersion(id(302), LocalDate.of(2027, 1, 1), null,
                List.of(t1Res(), t1Com(), extra), null);

        TariffComparison.Result result = TariffComparison.compare(t1(), proposed);

        TariffComparison.RuleDelta added = result.rules().stream()
                .filter(r -> r.categoryId().equals(id(9))).findFirst().orElseThrow();
        assertThat(added.inCurrent()).isFalse();
        assertThat(added.inProposed()).isTrue();
        assertThat(added.proposedMinCharge()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("comparação emparelha faixas por toM3: revisão que insere faixa intermediária não desalinha os deltas")
    void comparisonPairsBlocksByToM3NotByPosition() {
        // vigente RES: até 20 → 4,00; até 50 → 6,00; acima → 9,00.
        // proposta (revisão): insere a faixa até 35 → 5,00 e sobe a de 50 para 6,60 (+10%).
        // Por posição, a faixa de 50 seria comparada com a de 35 (5,00) — o delta certo é com 6,60.
        CategoryRule revisedRes = res(10, "30.00",
                block(20, "4.00"), block(35, "5.00"), block(50, "6.60"), block(null, "9.00"));
        TariffVersion proposed = new TariffVersion(id(303), LocalDate.of(2027, 1, 1), null,
                List.of(revisedRes, t1Com()), null);

        TariffComparison.Result result = TariffComparison.compare(t1(), proposed);

        TariffComparison.RuleDelta res = result.rules().stream()
                .filter(r -> r.categoryId().equals(RES)).findFirst().orElseThrow();
        TariffComparison.BlockDelta b50 = res.blocks().stream()
                .filter(b -> Integer.valueOf(50).equals(b.toM3())).findFirst().orElseThrow();
        assertThat(b50.currentPrice()).isEqualByComparingTo("6.00");
        assertThat(b50.proposedPrice()).isEqualByComparingTo("6.60");
        assertThat(b50.deltaPct()).isEqualByComparingTo("10.00");

        TariffComparison.BlockDelta b35 = res.blocks().stream()
                .filter(b -> Integer.valueOf(35).equals(b.toM3())).findFirst().orElseThrow();
        assertThat(b35.currentPrice()).isNull();
        assertThat(b35.proposedPrice()).isEqualByComparingTo("5.00");
        assertThat(b35.delta()).isNull();

        TariffComparison.BlockDelta open = res.blocks().stream()
                .filter(b -> b.toM3() == null).findFirst().orElseThrow();
        assertThat(open.currentPrice()).isEqualByComparingTo("9.00");
        assertThat(open.proposedPrice()).isEqualByComparingTo("9.00");
        assertThat(open.deltaPct()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("metadado de reajuste confere o percentual aplicado contra a fórmula (5% declarado × 6% da fórmula é recusado)")
    void metadataCrossChecksTheAppliedPercent() {
        assertThatThrownBy(() -> new VersionChangeMetadata(
                TariffVersionChangeType.READJUSTMENT, id(100), LocalDate.of(2025, 6, 1), "IPCA",
                d("6"), null, d("5"), ReadjustFormula.INDEX_ONLY,
                "Res. 001/2026", LocalDate.of(2026, 1, 1), null, null, "metodo"))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("diverge da fórmula");
    }

    @Test
    @DisplayName("metadado: revisão sem ato é recusada; reajuste sem data-base é recusado; cópia sem origem é recusada")
    void metadataRequiresTheFactsOfEachChangeType() {
        assertThatThrownBy(() -> new VersionChangeMetadata(
                TariffVersionChangeType.REVIEW, id(100), null, null, null, null, null, null,
                null, LocalDate.of(2026, 1, 1), null, null, "metodo"))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("ato");
        assertThatThrownBy(() -> new VersionChangeMetadata(
                TariffVersionChangeType.READJUSTMENT, id(100), null, "IPCA",
                d("5"), null, d("5"), ReadjustFormula.INDEX_ONLY,
                null, LocalDate.of(2026, 1, 1), null, null, "metodo"))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("data-base");
        assertThatThrownBy(() -> new VersionChangeMetadata(
                TariffVersionChangeType.COPY, null, null, null, null, null, null, null,
                null, null, null, null, null))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("origem");
    }
}
