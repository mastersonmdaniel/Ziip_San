package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffEngine;
import br.com.ziipsan.commercial.billing.tariff.TariffSchedule;
import br.com.ziipsan.commercial.billing.tariff.TariffVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.block;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.id;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.res;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Correccao R-01 (REVISAO-PA-02, GRAVE) — periodo MULTI-VERSAO: os
 * contra-exemplos CE1/CE2 do revisor re-executados como testes.
 *
 * <p>Cenario: periodo 15/12/2026–14/01/2027; V0 vigora ate 19/12/2026 e V1
 * (reajuste) desde 20/12/2026; programa com corte (effective_from) em
 * 01/01/2027 e aliquota ficticia de 10%. Emissao em 02/01/2027.
 */
class EmissionDateMultiVersionTest {

    private static final LocalDate FROM = LocalDate.of(2026, 12, 15);
    private static final LocalDate TO = LocalDate.of(2027, 1, 14);
    private static final LocalDate ISSUED = LocalDate.of(2027, 1, 2);

    private static TariffVersion v0() {
        return new TariffVersion(id(200), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 19),
                List.of(res(10, "30.0000", block(20, "4.0000"), block(50, "6.0000"),
                        block(null, "9.0000"))), null);
    }

    private static TariffVersion v1() {
        // Reajuste anual: +25% sobre V0, com valid_from NO MEIO do periodo.
        return new TariffVersion(id(201), LocalDate.of(2026, 12, 20), null,
                List.of(res(10, "37.5000", block(20, "5.0000"), block(50, "7.5000"),
                        block(null, "11.2500"))), null);
    }

    private record Gen(ReliefProgram program, TariffSchedule origin,
                       Map<UUID, TariffVersion> relievedById, List<ReliefLink> links,
                       TariffVersion relieved0, TariffVersion relieved1) {
    }

    private Gen generateBoth() {
        var generator = new RelievedVersionGenerator(new RelievedVersionGenerator.GenerationLedger());
        ReliefProgram program = ReliefFixtures.program(false);
        var results = generator.generateForAllSources(program, List.of(v0(), v1()),
                v -> ReliefFixtures.tenantRate("10"), UUID.randomUUID(), 4);
        TariffVersion r0 = results.get(0).relievedVersion();
        TariffVersion r1 = results.get(1).relievedVersion();
        return new Gen(program, new TariffSchedule(List.of(v0(), v1())),
                Map.of(r0.id(), r0, r1.id(), r1),
                List.of(results.get(0).link(), results.get(1).link()), r0, r1);
    }

    @Test
    @DisplayName("R-01 completo: reajuste x desoneracao compoem fatia a fatia, com os precos certos de cada versao")
    void multiVersionCompleteAppliesSliceBySlice() {
        Gen g = generateBoth();
        var resolution = EmissionDateResolver.resolveForEmission(g.origin(), FROM, TO, ISSUED,
                g.program(), g.links(), g.relievedById());

        assertThat(resolution.relievedApplied()).isTrue();
        assertThat(resolution.missingSourceVersionIds()).isEmpty();
        assertThat(resolution.sourceVersionIds()).containsExactly(id(200), id(201));
        var slices = resolution.schedule().slices(FROM, TO);
        assertThat(slices).hasSize(2);
        assertThat(slices.get(0).version().id()).isEqualTo(g.relieved0().id());
        assertThat(slices.get(0).from()).isEqualTo(FROM);
        assertThat(slices.get(0).to()).isEqualTo(LocalDate.of(2026, 12, 19));
        assertThat(slices.get(1).version().id()).isEqualTo(g.relieved1().id());
        assertThat(slices.get(1).from()).isEqualTo(LocalDate.of(2026, 12, 20));
        // Precos certos por fatia: V0 desonerada 3,6000; V1 (reajustada) desonerada 4,5000.
        assertThat(slices.get(0).version().rules().get(0).blocks().get(0).pricePerM3())
                .isEqualByComparingTo("3.6000");
        assertThat(slices.get(1).version().rules().get(0).blocks().get(0).pricePerM3())
                .isEqualByComparingTo("4.5000");

        // Oraculo estrutural: o motor sobre o schedule resolvido cobra o
        // mesmo que sobre o schedule esperado construido a mao — e difere
        // tanto da origem sem desoneracao como do erro CE2 (desonerada de
        // V0 aplicada a totalidade: 100,80).
        var expected = new TariffSchedule(List.of(
                new TariffVersion(g.relieved0().id(), FROM, LocalDate.of(2026, 12, 19),
                        g.relieved0().rules(), null),
                new TariffVersion(g.relieved1().id(), LocalDate.of(2026, 12, 20), TO,
                        g.relieved1().rules(), null)));
        var net = TariffEngine.calculate(resolution.schedule(), FROM, TO, List.of(res(1)), 27).net();
        assertThat(net).isEqualByComparingTo(
                TariffEngine.calculate(expected, FROM, TO, List.of(res(1)), 27).net());
        assertThat(net).isNotEqualByComparingTo("100.80");
        assertThat(net).isNotEqualByComparingTo(
                TariffEngine.calculate(g.origin(), FROM, TO, List.of(res(1)), 27).net());
    }

    @Test
    @DisplayName("R-01 CE1: ligacao so da versao em vigor a publicacao (V1) NAO bloqueia em silencio nem aplica parcial — conjunto nao desonerado, fatia V0 sinalizada")
    void ce1OnlyV1LinkedLeavesSetUnrelieved() {
        Gen g = generateBoth();
        var resolution = EmissionDateResolver.resolveForEmission(g.origin(), FROM, TO, ISSUED,
                g.program(), List.of(g.links().get(1)), g.relievedById());
        assertThat(resolution.relievedApplied()).isFalse();
        assertThat(resolution.missingSourceVersionIds()).containsExactly(id(200));
        assertThat(resolution.schedule()).isSameAs(g.origin());
    }

    @Test
    @DisplayName("R-01 CE2: ligacao so de V0 NUNCA aplica precos de V0 a totalidade — conjunto nao desonerado, fatia V1 sinalizada")
    void ce2OnlyV0LinkedNeverAppliesV0PricesToWholePeriod() {
        Gen g = generateBoth();
        var resolution = EmissionDateResolver.resolveForEmission(g.origin(), FROM, TO, ISSUED,
                g.program(), List.of(g.links().get(0)), g.relievedById());
        assertThat(resolution.relievedApplied()).isFalse();
        assertThat(resolution.missingSourceVersionIds()).containsExactly(id(201));
        assertThat(resolution.schedule()).isSameAs(g.origin());
    }

    @Test
    @DisplayName("R-01 fronteira: versao que comeca exactamente em effective_from compoe como qualquer fronteira de versao")
    void versionStartingExactlyAtEffectiveFromComposes() {
        var generator = new RelievedVersionGenerator(new RelievedVersionGenerator.GenerationLedger());
        ReliefProgram program = ReliefFixtures.program(false); // corte 01/01/2027
        TariffVersion before = new TariffVersion(id(210), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                List.of(res(10, "30.0000", block(20, "4.0000"), block(50, "6.0000"),
                        block(null, "9.0000"))), null);
        TariffVersion atCut = new TariffVersion(id(211), LocalDate.of(2027, 1, 1), null,
                List.of(res(10, "33.0000", block(20, "4.4000"), block(50, "6.6000"),
                        block(null, "9.9000"))), null);
        var results = generator.generateForAllSources(program, List.of(before, atCut),
                v -> ReliefFixtures.tenantRate("10"), UUID.randomUUID(), 4);
        var origin = new TariffSchedule(List.of(before, atCut));
        var resolution = EmissionDateResolver.resolveForEmission(origin, FROM, TO, ISSUED, program,
                List.of(results.get(0).link(), results.get(1).link()),
                Map.of(results.get(0).relievedVersion().id(), results.get(0).relievedVersion(),
                        results.get(1).relievedVersion().id(), results.get(1).relievedVersion()));
        assertThat(resolution.relievedApplied()).isTrue();
        var slices = resolution.schedule().slices(FROM, TO);
        assertThat(slices).hasSize(2);
        assertThat(slices.get(1).from()).isEqualTo(LocalDate.of(2027, 1, 1));
        // Fatia do corte: desonerada da versao do corte (4,4000 x 0,9 = 3,9600).
        assertThat(slices.get(1).version().rules().get(0).blocks().get(0).pricePerM3())
                .isEqualByComparingTo("3.9600");
    }
}
