package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.bill.BillInput;
import br.com.ziipsan.commercial.billing.bill.TaxRate;
import br.com.ziipsan.commercial.billing.bill.TaxWithholdingPolicy;
import br.com.ziipsan.commercial.billing.tariff.SewerFactorPolicy;
import br.com.ziipsan.commercial.billing.tariff.SewerParameters;
import br.com.ziipsan.commercial.billingrules.bill.CreditLine;
import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeDefinition;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeVersion;
import br.com.ziipsan.commercial.billingrules.servicecatalog.RevenueNature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.ziipsan.commercial.billingrules.composition.BillChargeLine;
import br.com.ziipsan.commercial.billingrules.composition.BillComposition;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.COMPETENCE;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.FROM;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.TO;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.credit;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.definition;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.sewerResolution;
import static br.com.ziipsan.commercial.billing.composition.BillComposerEquivalenceTest.waterResolution;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.res;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1Schedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * INV-C2-03 e INV-C2-04: a linha de terceiro entra DEPOIS do
 * {@code CreditApplier} e esta FORA da {@code taxBase} — so aparece em
 * {@code thirdPartyTotal} e no {@code total}. Codigos e valores sao
 * fixtures ficticias de teste.
 */
class BillComposerThirdPartyTest {

    static ChargeResolution thirdPartyResolution() {
        ChargeDefinition def = new ChargeDefinition(UUID.randomUUID(), "CONVENIO-FIXTURE",
                "Fixture convenio", ChargeOwner.THIRD_PARTY, RevenueNature.ANCILLARY,
                CalculationModel.EXTERNAL_VALUE, UUID.randomUUID(), false, true, false, null, null);
        ChargeVersion version = new ChargeVersion(UUID.randomUUID(), def.id(), 1,
                LocalDate.of(2026, 1, 1), null, CalculationModel.EXTERNAL_VALUE, null, null,
                UUID.randomUUID(), ChargeVersion.ChargeParameters.empty());
        return new ChargeResolution(def, version);
    }

    static ChargeCalculationContext context(boolean withThirdParty, String creditTotal,
                                            boolean withTax) {
        List<ChargeResolution> resolutions = withThirdParty
                ? List.of(waterResolution(), sewerResolution(), thirdPartyResolution())
                : List.of(waterResolution(), sewerResolution());
        ChargeCalculationContext.Builder builder = ChargeCalculationContext.builder()
                .unitRef("UC-FIXTURE").competence(COMPETENCE)
                .schedule(t1Schedule()).periodFrom(FROM).periodTo(TO).groups(List.of(res(1)))
                .waterConsumption(27)
                .sewer(new BillInput.SewerBilling(27,
                        new SewerParameters(d("80"), d("100"), null, SewerFactorPolicy.EXACT, false)))
                .credits(List.of(credit(creditTotal, 1, 0, "0.00")))
                .taxPolicy(withTax
                        ? new TaxWithholdingPolicy(List.of(new TaxRate("FIXTURE-T", d("10"))))
                        : null)
                .resolvedCharges(resolutions)
                .tariffVersionScopes(BillComposerEquivalenceTest.companyScopes());
        if (withThirdParty) {
            builder.externalValueProvider((resolution, ctx) -> d("30.00"));
        }
        return builder.build();
    }

    /**
     * Comparacao das linhas de credito da INV-C2-03 POR VALOR
     * (correccao D-PROVA-C2-01): mesmo tamanho, mesma ordem e, indice
     * a indice, TODOS os campos de valor/comportamento do record
     * {@link CreditLine} — originCode, installmentNo, applied,
     * residualAfter e installmentsDoneAfter — cada um com igualdade
     * exacta (isEqualTo, escala de BigDecimal incluida, como no resto
     * da suite). Nenhum campo de valor fica de fora: a forca da
     * assercao sobre valores e a mesma da igualdade integral.
     *
     * <p>Os dois campos de identidade — receivableId e
     * creditOriginId — NAO sao comparaveis entre estas duas
     * composicoes, e a exclusao fica registada aqui (nao e omissao
     * nem afrouxamento silencioso): sao identidade TRANSPORTADA do
     * {@code CreditReceivable} de entrada. O {@code CreditApplier}
     * vigente copia credit.id()/creditOriginId() para a linha sem
     * gerar nem alterar UUID algum, e o maestro limita-se a invocar
     * o CreditApplier e a transportar as linhas resultantes — a
     * composicao nao cria nem regenera identidade de credito. Quem
     * gera os UUID e a fixture: cada context(...) desta classe
     * constroi um credito NOVO por credit(...) de
     * BillComposerEquivalenceTest, que usa UUID.randomUUID() nos
     * dois campos a cada chamada. As duas composicoes comparadas
     * nascem, por isso, de instancias distintas do mesmo
     * credito-fixture, e as identidades diferem por construcao da
     * ENTRADA — compara-las mede a aleatoriedade da fixture, nao o
     * comportamento da composicao. A INV-C2-03 e sobre o que o
     * CreditApplier APLICA (teto e valores), nao sobre identidade
     * efemera gerada em tempo de execucao.
     *
     * <p>Quando a MESMA instancia de credito alimenta os dois lados
     * da comparacao, a igualdade integral continua a ser exigida:
     * e o caso da INV-C2-01 em BillComposerEquivalenceTest, que
     * partilha o mesmo BillInput entre o vigente e o maestro e
     * mantem creditLines() em isEqualTo integral — la a identidade
     * transportada TEM de coincidir, e coincide.
     */
    static void assertCreditLinesEqualByValue(List<CreditLine> actual, List<CreditLine> expected) {
        assertThat(actual).hasSameSizeAs(expected);
        for (int i = 0; i < expected.size(); i++) {
            CreditLine a = actual.get(i);
            CreditLine e = expected.get(i);
            assertThat(a.originCode()).as("creditLines[%d].originCode", i)
                    .isEqualTo(e.originCode());
            assertThat(a.installmentNo()).as("creditLines[%d].installmentNo", i)
                    .isEqualTo(e.installmentNo());
            assertThat(a.applied()).as("creditLines[%d].applied", i)
                    .isEqualTo(e.applied());
            assertThat(a.residualAfter()).as("creditLines[%d].residualAfter", i)
                    .isEqualTo(e.residualAfter());
            assertThat(a.installmentsDoneAfter()).as("creditLines[%d].installmentsDoneAfter", i)
                    .isEqualTo(e.installmentsDoneAfter());
        }
    }

    @Test
    @DisplayName("INV-C2-03: creditos identicos com e sem terceiro (teto = proprias + debitos, sem terceiro)")
    void creditsAreIdenticalWithAndWithoutThirdParty() {
        // Credito de 130,00 > proprias (201,60? nao: agua 112,00 + esgoto 89,60 = 201,60;
        // usa-se credito ACIMA das proprias para o teto ser o factor decisivo)
        BillComposition without = BillComposer.compose(context(false, "500.00", false));
        BillComposition with = BillComposer.compose(context(true, "500.00", false));
        // D-PROVA-C2-01: comparacao POR VALOR (ver assertCreditLinesEqualByValue):
        // a igualdade integral do record comparava tambem receivableId/
        // creditOriginId, identidades efemeras geradas pela fixture a cada
        // context(...) — divergiam com valores identicos e a assercao falhava
        // por construcao, sem defeito da composicao.
        assertCreditLinesEqualByValue(with.creditLines(), without.creditLines());
        assertThat(with.creditsValue()).isEqualTo(without.creditsValue());
        // O teto aplicado e proprias + debitos = 201,60 — NAO inclui os 30,00 do terceiro.
        assertThat(with.creditsValue()).isEqualTo(d("201.60"));
    }

    @Test
    @DisplayName("INV-C2-04: taxBase e taxLines identicos com e sem terceiro; terceiro so no total")
    void taxBaseAndLinesAreIdenticalWithAndWithoutThirdParty() {
        BillComposition without = BillComposer.compose(context(false, "50.00", true));
        BillComposition with = BillComposer.compose(context(true, "50.00", true));
        assertThat(with.taxBase()).isEqualTo(without.taxBase());
        assertThat(with.taxLines()).isEqualTo(without.taxLines());
        assertThat(with.taxesValue()).isEqualTo(without.taxesValue());
        assertThat(with.ownSubtotal()).isEqualTo(without.ownSubtotal());
        assertThat(with.thirdPartyTotal()).isEqualTo(d("30.00"));
        assertThat(without.thirdPartyTotal()).isEqualTo(d("0.00"));
        assertThat(with.total()).isEqualTo(without.total().add(d("30.00")));
    }

    @Test
    @DisplayName("ordem normativa: linha de terceiro e a ULTIMA da composicao e nasce THIRD_PARTY")
    void thirdPartyLineIsLast() {
        BillComposition with = BillComposer.compose(context(true, "50.00", true));
        assertThat(with.lines()).isNotEmpty();
        BillChargeLine last = with.lines().get(with.lines().size() - 1);
        assertThat(last.chargeCode()).isEqualTo("CONVENIO-FIXTURE");
        assertThat(last.owner()).isEqualTo(ChargeOwner.THIRD_PARTY);
        assertThat(last.amount()).isEqualTo(d("30.00"));
        assertThat(with.thirdPartyLines()).containsExactly(last);
    }

    @Test
    @DisplayName("adapter: composicao com terceiro NAO projecta em BillResult classico")
    void adapterRefusesThirdPartyComposition() {
        BillComposition with = BillComposer.compose(context(true, "50.00", false));
        assertThatThrownBy(() -> BillCalculatorAdapter.toBillResult(with))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terceiro");
    }
}
