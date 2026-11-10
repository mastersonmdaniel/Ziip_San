package br.com.ziipsan.commercial.billing.tariff;

import br.com.ziipsan.commercial.billing.bill.BillCalculator;
import br.com.ziipsan.commercial.billing.bill.BillInput;
import br.com.ziipsan.commercial.billing.bill.BillResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import br.com.ziipsan.commercial.billingrules.tariff.ChargeLine;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simulação de impacto (P2.29) sobre massa calculada à mão.
 *
 * <p>Estrutura vigente: T1 com tarifa social (fixa 50%, banda (0,20] a 50%).
 * Proposta: reajuste de 10% sobre todos os preços (percentuais intactos).
 * Período de referência: 2026-09-05 a 2026-10-04 (uma só vigência, fator 1).
 *
 * <p>Massa (contas de água pela T1; ver TariffEngineTest T-03):
 * <ul>
 *   <li>A: RES 1 eco, 27 m³ → 30 + 10×4 + 7×6 = 112,00 → proposta 123,20;</li>
 *   <li>B: COM 1 eco, 27 m³ → 50 + 10×7 + 7×10 = 190,00 → proposta 209,00;</li>
 *   <li>C: RES 1 eco social, 27 m³ → bruto 112,00; desconto: fixa 30×50% = 15,00
 *       + faixa 11..20: 4,00 × 10 m³ na banda × 50% = 20,00 → desconto 35,00,
 *       líquido 77,00 → proposta: bruto 123,20, desconto 16,50 + 22,00 = 38,50,
 *       líquido 84,70;</li>
 *   <li>D: RES 1 eco, 27 m³ + esgoto (base 27, coleta 100%, esgoto 80% exato,
 *       sem desconto social no esgoto): água 112,00 + esgoto 112,00×0,80 = 89,60
 *       → 201,60 → proposta 123,20 + 98,56 = 221,76.</li>
 * </ul>
 * Totais: vigente 112 + 190 + 77 + 201,60 = 580,60; proposta 638,66 (+10%).
 * Receita RES (água de A, C, D + esgoto de D): bruto vigente
 * 112 + 112 + 112 + 89,60 = 425,60, desconto 35,00, líquido 390,60.
 */
class ImpactSimulatorTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 5);
    private static final LocalDate TO = LocalDate.of(2026, 10, 4);

    private static TariffVersion current() {
        return t1WithSocial(social("50", band(0, 20, "50")));
    }

    private static TariffVersion proposed() {
        return ReadjustmentCalculator.apply(current(), id(200), LocalDate.of(2027, 1, 1), null,
                ReadjustFormula.INDEX_ONLY, d("10"), null);
    }

    private static SewerParameters sewerParams() {
        return new SewerParameters(d("80"), d("100"), null, SewerFactorPolicy.EXACT, false);
    }

    private static List<ImpactSimulator.SimulationUnit> mass() {
        return List.of(
                new ImpactSimulator.SimulationUnit(id(1001), List.of(res(1)), 27, null),
                new ImpactSimulator.SimulationUnit(id(1002), List.of(com(1)), 27, null),
                new ImpactSimulator.SimulationUnit(id(1003), List.of(socialRes(1, 1)), 27, null),
                new ImpactSimulator.SimulationUnit(id(1004), List.of(res(1)), 27,
                        new ImpactSimulator.SewerInput(27, sewerParams())));
    }

    private static List<ImpactSimulator.TypicalProfile> profiles() {
        return List.of(
                new ImpactSimulator.TypicalProfile("RES 10 m3", RES, null, 10, false),
                new ImpactSimulator.TypicalProfile("RES 27 m3", RES, null, 27, false),
                new ImpactSimulator.TypicalProfile("COM 10 m3", COM, null, 10, false),
                new ImpactSimulator.TypicalProfile("RES social 27 m3", RES, null, 27, true));
    }

    @Test
    @DisplayName("receita total e por unidade: vigente 580,60 → proposta 638,66 (+58,06)")
    void revenueTotalsAndPerUnit() {
        ImpactSimulator.ImpactReport report = ImpactSimulator.simulate(current(), proposed(), mass(), FROM, TO, profiles());

        assertThat(report.currentRevenue()).isEqualByComparingTo("580.60");
        assertThat(report.proposedRevenue()).isEqualByComparingTo("638.66");
        assertThat(report.revenueDelta()).isEqualByComparingTo("58.06");
        assertThat(report.units()).extracting(u -> u.currentNet().toPlainString())
                .containsExactly("112.00", "190.00", "77.00", "201.60");
        assertThat(report.units()).extracting(u -> u.proposedNet().toPlainString())
                .containsExactly("123.20", "209.00", "84.70", "221.76");
    }

    @Test
    @DisplayName("receita por categoria: RES bruto 425,60 / desconto 35,00 / líquido 390,60 → 429,66; COM 190,00 → 209,00")
    void revenueByCategory() {
        ImpactSimulator.ImpactReport report = ImpactSimulator.simulate(current(), proposed(), mass(), FROM, TO, profiles());

        ImpactSimulator.CategoryRevenue res = report.revenueByCategory().stream()
                .filter(c -> c.categoryId().equals(RES)).findFirst().orElseThrow();
        assertThat(res.units()).isEqualTo(3);
        assertThat(res.economies()).isEqualTo(3);
        assertThat(res.currentGross()).isEqualByComparingTo("425.60");
        assertThat(res.currentDiscount()).isEqualByComparingTo("35.00");
        assertThat(res.currentNet()).isEqualByComparingTo("390.60");
        assertThat(res.proposedNet()).isEqualByComparingTo("429.66");
        assertThat(res.deltaPct()).isEqualByComparingTo("10.00");

        ImpactSimulator.CategoryRevenue com = report.revenueByCategory().stream()
                .filter(c -> c.categoryId().equals(COM)).findFirst().orElseThrow();
        assertThat(com.currentNet()).isEqualByComparingTo("190.00");
        assertThat(com.proposedNet()).isEqualByComparingTo("209.00");
    }

    @Test
    @DisplayName("receita por faixa (RES): fixa líquida 99,00 → 108,90; faixa 11..20 líquida 132,00 → 145,20; faixa 21..50: 159,60 → 175,56")
    void revenueByBlock() {
        ImpactSimulator.ImpactReport report = ImpactSimulator.simulate(current(), proposed(), mass(), FROM, TO, profiles());

        ImpactSimulator.BlockRevenue minimum = report.revenueByBlock().stream()
                .filter(b -> b.categoryId().equals(RES) && b.lineType() == ChargeLine.Type.MINIMUM)
                .findFirst().orElseThrow();
        // fixa: A 30 + C (30 − 15) + D agua 30 + D esgoto (30 × 0,80) 24 = liquido 99,00
        assertThat(minimum.currentGross()).isEqualByComparingTo("114.00");
        assertThat(minimum.currentDiscount()).isEqualByComparingTo("15.00");
        assertThat(minimum.currentNet()).isEqualByComparingTo("99.00");
        assertThat(minimum.proposedNet()).isEqualByComparingTo("108.90");

        ImpactSimulator.BlockRevenue block1120 = report.revenueByBlock().stream()
                .filter(b -> b.categoryId().equals(RES) && b.lineType() == ChargeLine.Type.BLOCK
                        && b.fromM3() == 10 && b.toM3() == 20)
                .findFirst().orElseThrow();
        // 11..20: A 40 + C (40 − 20) + D agua 40 + D esgoto 32 = liquido 132,00
        assertThat(block1120.currentNet()).isEqualByComparingTo("132.00");
        assertThat(block1120.proposedNet()).isEqualByComparingTo("145.20");

        ImpactSimulator.BlockRevenue block2150 = report.revenueByBlock().stream()
                .filter(b -> b.categoryId().equals(RES) && b.lineType() == ChargeLine.Type.BLOCK
                        && b.fromM3() == 20 && b.toM3() == 50)
                .findFirst().orElseThrow();
        // 21..50: A 42 + C 42 + D agua 42 + D esgoto 33,60 = 159,60
        assertThat(block2150.currentNet()).isEqualByComparingTo("159.60");
        assertThat(block2150.proposedNet()).isEqualByComparingTo("175.56");
    }

    @Test
    @DisplayName("conta-tipo por categoria e perfil: RES 10 → 30,00→33,00; RES 27 → 112,00→123,20; COM 10 → 50,00→55,00; RES social 27 → 77,00→84,70")
    void typicalBills() {
        ImpactSimulator.ImpactReport report = ImpactSimulator.simulate(current(), proposed(), mass(), FROM, TO, profiles());

        assertThat(report.typicalBills()).extracting(t -> t.currentNet().toPlainString())
                .containsExactly("30.00", "112.00", "50.00", "77.00");
        assertThat(report.typicalBills()).extracting(t -> t.proposedNet().toPlainString())
                .containsExactly("33.00", "123.20", "55.00", "84.70");
        assertThat(report.typicalBills().get(1).deltaPct()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("efeito na tarifa social e no subsídio: desconto (subsídio) 35,00 → 38,50 (+3,50); líquido social 77,00 → 84,70")
    void socialImpactAndSubsidy() {
        ImpactSimulator.ImpactReport report = ImpactSimulator.simulate(current(), proposed(), mass(), FROM, TO, profiles());

        assertThat(report.socialImpact().socialEconomies()).isEqualTo(1);
        assertThat(report.socialImpact().currentDiscount()).isEqualByComparingTo("35.00");
        assertThat(report.socialImpact().proposedDiscount()).isEqualByComparingTo("38.50");
        assertThat(report.socialImpact().discountDelta()).isEqualByComparingTo("3.50");
        assertThat(report.socialImpact().currentSocialNet()).isEqualByComparingTo("77.00");
        assertThat(report.socialImpact().proposedSocialNet()).isEqualByComparingTo("84.70");
    }

    @Test
    @DisplayName("a simulação NÃO altera a base: estruturas e massa intactas, e repetir a simulação dá o mesmo relatório")
    void simulationDoesNotMutateAnything() {
        TariffVersion current = current();
        TariffVersion proposed = proposed();
        List<ImpactSimulator.SimulationUnit> mass = mass();
        String currentBefore = current.rules().toString() + current.socialRule();
        String proposedBefore = proposed.rules().toString() + proposed.socialRule();
        int massSize = mass.size();

        ImpactSimulator.ImpactReport first = ImpactSimulator.simulate(current, proposed, mass, FROM, TO, profiles());
        ImpactSimulator.ImpactReport second = ImpactSimulator.simulate(current, proposed, mass, FROM, TO, profiles());

        assertThat(current.rules().toString() + current.socialRule()).isEqualTo(currentBefore);
        assertThat(proposed.rules().toString() + proposed.socialRule()).isEqualTo(proposedBefore);
        assertThat(mass).hasSize(massSize);
        assertThat(second).isEqualTo(first);
        // os precos da vigente continuam os da T1: nada foi gravado nem reajustado no lugar
        assertThat(current.ruleFor(RES, null).minCharge()).isEqualByComparingTo("30.00");
        assertThat(current.validFrom()).isEqualTo(T1_FROM);
    }

    @Test
    @DisplayName("proposta igual à vigente REPRODUZ a conta da P2.5 (BillCalculator) para a mesma massa, com e sem esgoto")
    void simulationReproducesTheP25BillForTheSameMass() {
        TariffVersion t1 = t1();
        TariffSchedule schedule = t1Schedule();
        List<ImpactSimulator.SimulationUnit> units = List.of(
                new ImpactSimulator.SimulationUnit(id(1001), List.of(res(1)), 27, null),
                new ImpactSimulator.SimulationUnit(id(1004), List.of(res(1)), 27,
                        new ImpactSimulator.SewerInput(27, sewerParams())));

        ImpactSimulator.ImpactReport report = ImpactSimulator.simulate(t1, t1, units, FROM, TO, List.of());

        // a mesma massa pelo caminho da conta da P2.5
        BillResult billA = BillCalculator.calculate(new BillInput(schedule, FROM, TO,
                List.of(res(1)), 27, null, List.of(), List.of(), null));
        BillResult billD = BillCalculator.calculate(new BillInput(schedule, FROM, TO,
                List.of(res(1)), 27, new BillInput.SewerBilling(27, sewerParams()),
                List.of(), List.of(), null));

        assertThat(billA.total()).isEqualByComparingTo("112.00");
        assertThat(billD.total()).isEqualByComparingTo("201.60");
        assertThat(report.units().get(0).currentNet()).isEqualByComparingTo(billA.total());
        assertThat(report.units().get(1).currentNet()).isEqualByComparingTo(billD.total());
        assertThat(report.currentRevenue()).isEqualByComparingTo(billA.total().add(billD.total()));
        assertThat(report.revenueDelta()).isEqualByComparingTo("0.00");
        assertThat(report.units()).allSatisfy(u -> assertThat(u.delta()).isEqualByComparingTo("0.00"));
    }

    @Test
    @DisplayName("simulação sem massa é recusada (não existe impacto sem base)")
    void emptyMassIsRefused() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        ImpactSimulator.simulate(current(), proposed(), List.of(), FROM, TO, profiles()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("massa");
    }
}
