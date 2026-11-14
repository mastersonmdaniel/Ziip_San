package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.CategoryRule;
import br.com.ziipsan.commercial.billing.tariff.TariffBlock;
import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;
import br.com.ziipsan.commercial.billing.tariff.TariffVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1WithSocial;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.social;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.band;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Grupo 1 — Transformacao (secao 4.1) + classificacao de multas (NT 6.6). */
class ReliefTransformationTest {

    private final RelievedVersionGenerator generator =
            new RelievedVersionGenerator(new RelievedVersionGenerator.GenerationLedger());

    @Test
    @DisplayName("preco x factor com politica gravada; franquia, toM3 e regra social copiados sem alteracao")
    void transformsPricesAndCopiesStructure() {
        TariffVersion source = t1WithSocial(social("50", band(0, 15, "50")));
        var result = generator.generateTariffVersion(ReliefFixtures.program(false), source,
                ReliefFixtures.tenantRate("10"), UUID.randomUUID(), UUID.randomUUID(), 4);

        assertThat(result.reused()).isFalse();
        TariffVersion relieved = result.relievedVersion();
        CategoryRule res = relieved.rules().get(0);
        assertThat(res.minCharge()).isEqualByComparingTo("27.0000");
        assertThat(res.minConsumption()).isEqualTo(10);
        assertThat(res.blocks()).extracting(TariffBlock::pricePerM3)
                .containsExactly(d("3.6000"), d("5.4000"), d("8.1000"));
        assertThat(res.blocks()).extracting(TariffBlock::toM3).containsExactly(20, 50, null);
        assertThat(relieved.socialRule()).isEqualTo(source.socialRule());
        assertThat(result.link().factor()).isEqualByComparingTo("0.900000");
        assertThat(result.link().roundingPolicy()).isEqualTo("HALF_UP@4");
        assertThat(result.link().appliedRatePct()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("idempotencia: segunda geracao do mesmo programa+origem devolve a ligacao existente")
    void generationIsIdempotent() {
        TariffVersion source = t1();
        var first = generator.generateTariffVersion(ReliefFixtures.program(false), source,
                ReliefFixtures.tenantRate("10"), UUID.randomUUID(), UUID.randomUUID(), 4);
        var second = generator.generateTariffVersion(ReliefFixtures.program(false), source,
                ReliefFixtures.tenantRate("10"), UUID.randomUUID(), UUID.randomUUID(), 4);
        assertThat(second.reused()).isTrue();
        assertThat(second.link()).isEqualTo(first.link());
    }

    @Test
    @DisplayName("preco que arredonda para zero: geracao recusada")
    void zeroingPriceIsRefused() {
        TariffVersion tiny = new TariffVersion(UUID.randomUUID(),
                java.time.LocalDate.of(2026, 1, 1), null,
                List.of(CategoryRule.forCategory(UUID.randomUUID(), 0, d("0.00004"), null,
                        List.of(new TariffBlock(null, d("1.0000"))))), null);
        assertThatThrownBy(() -> generator.generateTariffVersion(ReliefFixtures.program(false), tiny,
                ReliefFixtures.tenantRate("10"), UUID.randomUUID(), UUID.randomUUID(), 4))
                .isInstanceOf(TariffConfigurationException.class);
    }

    @Test
    @DisplayName("NT 6.3.2: politica com casas diferentes da tabela vigente e recusada na geracao")
    void roundingDivergingFromSourceTableIsRefused() {
        ReliefProgram program2 = new ReliefProgram(ReliefFixtures.PROGRAM_ID, "X", "x", null,
                "ato", ReliefFixtures.CUT, ReliefProgramStatus.ACTIVE, null,
                new ReliefRoundingPolicy(2), null, null, null, null, null, null);
        assertThatThrownBy(() -> generator.generateTariffVersion(program2, t1(),
                ReliefFixtures.tenantRate("10"), UUID.randomUUID(), UUID.randomUUID(), 4))
                .isInstanceOf(TariffConfigurationException.class)
                .hasMessageContaining("6.3.2");
    }

    @Test
    @DisplayName("multas (NT 6.6): recuperacao aplica factor; indenizatoria, percentual e nao classificada recusam")
    void fineClassificationGatesGeneration() {
        var policy = ReliefRoundingPolicy.tenantDefault();
        var factor = RelievedVersionGenerator.factorOf(d("10"));
        assertThat(generator.relieveFixedFee(new RelievedVersionGenerator.ClassifiedFixedFee(
                "M1", d("100.00"), FineClassification.NOMINAL_RECOVERY_REVENUE), factor, policy))
                .isEqualByComparingTo("90.0000");
        assertThatThrownBy(() -> generator.relieveFixedFee(new RelievedVersionGenerator.ClassifiedFixedFee(
                "M2", d("100.00"), FineClassification.NOMINAL_INDEMNITY), factor, policy))
                .isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> generator.relieveFixedFee(new RelievedVersionGenerator.ClassifiedFixedFee(
                "M3", d("100.00"), FineClassification.NOMINAL_UNCLASSIFIED), factor, policy))
                .isInstanceOf(TariffConfigurationException.class);
        assertThatThrownBy(() -> generator.relieveFixedFee(new RelievedVersionGenerator.ClassifiedFixedFee(
                "M4", d("100.00"), FineClassification.PERCENTUAL), factor, policy))
                .isInstanceOf(TariffConfigurationException.class);
    }

    @Test
    @DisplayName("ISSQN complementar (NT 6.4.4/6.2.3.7): factores somam-se, nunca sucessivos; destaque por agravamento")
    void issqnCombinedFactorAndHighlight() {
        // (1 - 10% - 5%) = 0,85; sucessivo seria 0,90 x 0,95 = 0,855 — diferente.
        assertThat(ComplementaryServiceRelief.combinedFactor(d("10"), d("5")))
                .isEqualByComparingTo("0.850000");
        assertThat(ComplementaryServiceRelief.relieveWithIssqn(d("100.00"), d("10"), d("5"),
                ReliefRoundingPolicy.tenantDefault())).isEqualByComparingTo("85.0000");
        // destaque: 85,00 x [5 / (100 - 5)] = 85 x 0,052631... = 4,47
        assertThat(ComplementaryServiceRelief.issqnHighlight(d("85.00"), d("5")))
                .isEqualByComparingTo("4.47");
    }
}
