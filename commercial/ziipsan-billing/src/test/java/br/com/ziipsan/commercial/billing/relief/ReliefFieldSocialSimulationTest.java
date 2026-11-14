package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.ImpactSimulator;
import br.com.ziipsan.commercial.billing.tariff.SewerFactorPolicy;
import br.com.ziipsan.commercial.billing.tariff.SewerParameters;
import br.com.ziipsan.commercial.billing.tariff.TariffEngine;
import br.com.ziipsan.commercial.billing.tariff.TariffVersion;
import br.com.ziipsan.commercial.billingrules.FieldBillCalculator;
import br.com.ziipsan.commercial.billingrules.FieldBillInput;
import br.com.ziipsan.commercial.billingrules.FieldTariffSnapshot;
import br.com.ziipsan.commercial.billingrules.TariffBracket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.band;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.res;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.social;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.socialRes;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1WithSocial;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Grupos 5, 8 e 9 — tarifa social sobre a desonerada; projeccao de campo por
 * data prevista de emissao (sem tocar no ziipsan-billing-rules: o retrato ja
 * carrega precos resolvidos); simulacao simulado = publicado + contrato (V-46).
 */
class ReliefFieldSocialSimulationTest {

    private TariffVersion relievedOf(TariffVersion source) {
        var generator = new RelievedVersionGenerator(new RelievedVersionGenerator.GenerationLedger());
        return generator.generateTariffVersion(ReliefFixtures.program(false), source,
                ReliefFixtures.tenantRate("10"), UUID.randomUUID(), UUID.randomUUID(), 4)
                .relievedVersion();
    }

    @Test
    @DisplayName("grupo 5: social desconta sobre valores ja desonerados; bruto/desconto/liquido reconciliam")
    void socialDiscountAppliesOnRelievedValues() {
        // Regra S-2 ficticia: banda (0,15] a 50% e 50% na fixa.
        // Original: bruto 112,00, desconto 25,00, liquido 87,00.
        // Desonerada (x0,9): bruto 100,80; desconto = 5m3x3,60x50% + 27,00x50% = 9,00 + 13,50 = 22,50; liquido 78,30.
        TariffVersion relieved = relievedOf(t1WithSocial(social("50", band(0, 15, "50"))));
        var charge = TariffEngine.categoriesFor(relieved, List.of(socialRes(1, 1)), 27).get(0);
        assertThat(charge.gross()).isEqualByComparingTo("100.80");
        assertThat(charge.discount()).isEqualByComparingTo("22.50");
        assertThat(charge.net()).isEqualByComparingTo("78.30");
        assertThat(charge.gross().subtract(charge.discount())).isEqualByComparingTo(charge.net());
    }

    @Test
    @DisplayName("grupo 8: pacote de rota pos-corte carrega o retrato desonerado; campo e servidor concordam na parcela fixa")
    void fieldSnapshotCarriesRelievedPrices() {
        TariffVersion relieved = relievedOf(t1());
        var resRule = relieved.rules().get(0);
        FieldTariffSnapshot snapshot = new FieldTariffSnapshot(relieved.id(),
                resRule.categoryId(), resRule.minConsumption(), resRule.minCharge(),
                List.of(new TariffBracket(10, d("3.60")), new TariffBracket(30, d("5.40")),
                        new TariffBracket(null, d("8.10"))),
                false, null, Map.of());
        assertThat(snapshot.tariffVersionId()).isEqualTo(relieved.id());
        assertThat(snapshot.fixedChargePerEconomy()).isEqualByComparingTo("27.0000");

        // Consumo abaixo da franquia (5 m3): campo cobra so a parcela fixa desonerada,
        // e o servidor, sobre a mesma versao, cobra o mesmo liquido.
        var fieldResult = FieldBillCalculator.calculate(new FieldBillInput(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1000, 1005, 6, 1, snapshot,
                List.of(), List.of(), LocalDate.of(2027, 1, 31)));
        var serverCharge = TariffEngine.categoriesFor(relieved, List.of(res(1)), 5).get(0);
        assertThat(fieldResult.total()).isEqualByComparingTo("27.00");
        assertThat(serverCharge.net()).isEqualByComparingTo("27.00");
    }

    @Test
    @DisplayName("grupo 9: simulacao — o ensaio desonerado reproduz o publicado; agregacao por contrato (V-46)")
    void simulationMatchesPublicationAndAggregatesByContract() {
        TariffVersion current = t1();
        TariffVersion trial = relievedOf(current);
        // "Publicado": nova geracao independente da mesma transformacao (ledger novo).
        TariffVersion published = relievedOf(current);
        SewerParameters sewer = new SewerParameters(d("80"), d("100"), null,
                SewerFactorPolicy.EXACT, false);
        UUID unitA = UUID.randomUUID();
        UUID unitB = UUID.randomUUID();
        List<ImpactSimulator.SimulationUnit> units = List.of(
                new ImpactSimulator.SimulationUnit(unitA, List.of(res(1)), 27,
                        new ImpactSimulator.SewerInput(27, sewer)),
                new ImpactSimulator.SimulationUnit(unitB, List.of(res(1)), 27,
                        new ImpactSimulator.SewerInput(27, sewer)));
        LocalDate from = LocalDate.of(2026, 12, 1);
        LocalDate to = LocalDate.of(2026, 12, 30);

        var trialReport = ReliefSimulationService.simulate(current, trial, units, from, to, List.of());
        var publishedReport = ReliefSimulationService.simulate(current, published, units, from, to, List.of());
        assertThat(trialReport.proposedRevenue()).isEqualByComparingTo(publishedReport.proposedRevenue());
        // Cada unidade: vigente 201,60; desonerada 181,44.
        assertThat(trialReport.currentRevenue()).isEqualByComparingTo("403.20");
        assertThat(trialReport.proposedRevenue()).isEqualByComparingTo("362.88");

        var byContract = ReliefSimulationService.aggregateByContract(trialReport,
                Map.of(unitA, "CONTRATO-GC-1", unitB, "CONTRATO-GC-1"));
        assertThat(byContract).hasSize(1);
        assertThat(byContract.get(0).contractRef()).isEqualTo("CONTRATO-GC-1");
        assertThat(byContract.get(0).units()).isEqualTo(2);
        assertThat(byContract.get(0).delta()).isEqualByComparingTo("-40.32");
    }
}
