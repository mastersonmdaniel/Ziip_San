package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.bill.BillCalculator;
import br.com.ziipsan.commercial.billing.bill.BillInput;
import br.com.ziipsan.commercial.billing.composition.BillCalculatorAdapter;
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
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1Schedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correccao R-03 (REVISAO-PA-02) — a montagem VIVA invoca seleccao +
 * resolucao + guarda: directamente pela {@link ReliefBillingAssembly} e
 * atraves da camada de montagem do contexto
 * ({@link BillCalculatorAdapter}). Sem programa, o comportamento e
 * identico ao vigente (mesma instancia de schedule; diferencial verde).
 */
class ReliefBillingAssemblyTest {

    private static final LocalDate FROM = LocalDate.of(2026, 12, 15);
    private static final LocalDate TO = LocalDate.of(2027, 1, 14);
    private static final LocalDate ISSUED = LocalDate.of(2027, 1, 2);

    private final ReliefBillingAssembly assembly = new ReliefBillingAssembly();

    private record Multi(ReliefProgram program, TariffSchedule origin,
                         ReliefBillingAssembly.ReliefContext context, TariffVersion relieved1) {
    }

    private Multi multi(boolean provisional) {
        var generator = new RelievedVersionGenerator(new RelievedVersionGenerator.GenerationLedger());
        ReliefProgram program = provisional
                ? new ReliefProgram(ReliefFixtures.PROGRAM_ID, "DESON-2027", "fic", null, "ato",
                        ReliefFixtures.CUT, ReliefProgramStatus.ACTIVE, true,
                        ReliefRoundingPolicy.tenantDefault(), null, null, "Reguladora ficticia",
                        "Reserva ficticia", null, null)
                : ReliefFixtures.program(false);
        TariffVersion v0 = new TariffVersion(id(200), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 19),
                List.of(res(10, "30.0000", block(20, "4.0000"), block(50, "6.0000"),
                        block(null, "9.0000"))), null);
        TariffVersion v1 = new TariffVersion(id(201), LocalDate.of(2026, 12, 20), null,
                List.of(res(10, "37.5000", block(20, "5.0000"), block(50, "7.5000"),
                        block(null, "11.2500"))), null);
        var results = generator.generateForAllSources(program, List.of(v0, v1),
                v -> ReliefFixtures.tenantRate("10"), UUID.randomUUID(), 4);
        var ctx = new ReliefBillingAssembly.ReliefContext(List.of(program),
                List.of(results.get(0).link(), results.get(1).link()),
                Map.of(results.get(0).relievedVersion().id(), results.get(0).relievedVersion(),
                        results.get(1).relievedVersion().id(), results.get(1).relievedVersion()),
                false, false);
        return new Multi(program, new TariffSchedule(List.of(v0, v1)), ctx,
                results.get(1).relievedVersion());
    }

    @Test
    @DisplayName("montagem completa multi-versao: RELIEVED_OK e schedule desonerado fatia a fatia")
    void assemblyAppliesReliefSliceBySlice() {
        Multi m = multi(false);
        var assembled = assembly.assemble(m.origin(), FROM, TO, ISSUED, null, m.context());
        assertThat(assembled.decision()).isEqualTo(ReliefEmissionGuard.Decision.RELIEVED_OK);
        assertThat(assembled.schedule()).isNotSameAs(m.origin());
        assertThat(assembled.schedule().slices(FROM, TO)).hasSize(2);
        assertThat(assembled.schedule().slices(FROM, TO).get(1).version().id())
                .isEqualTo(m.relieved1().id());
    }

    @Test
    @DisplayName("montagem incompleta sem via provisoria: a guarda bloqueia a emissao (falha fechada)")
    void assemblyBlocksWhenSliceUnlinked() {
        Multi m = multi(false);
        var partial = new ReliefBillingAssembly.ReliefContext(List.of(m.program()),
                List.of(m.context().links().get(0)), m.context().relievedById(), false, false);
        assertThatThrownBy(() -> assembly.assemble(m.origin(), FROM, TO, ISSUED, null, partial))
                .isInstanceOf(ReliefEmissionGuard.ReliefEmissionBlockedException.class);
    }

    @Test
    @DisplayName("montagem incompleta com via provisoria declarada: PROVISIONAL_ALLOWED com o schedule vigente (marcado no resultado)")
    void assemblyProvisionalPathKeepsCurrentSchedule() {
        Multi m = multi(true);
        var partial = new ReliefBillingAssembly.ReliefContext(List.of(m.program()),
                List.of(m.context().links().get(0)), m.context().relievedById(), false, true);
        var assembled = assembly.assemble(m.origin(), FROM, TO, ISSUED, null, partial);
        assertThat(assembled.decision()).isEqualTo(ReliefEmissionGuard.Decision.PROVISIONAL_ALLOWED);
        assertThat(assembled.schedule()).isSameAs(m.origin());
        assertThat(assembled.resolution().missingSourceVersionIds()).containsExactly(id(201));
    }

    @Test
    @DisplayName("sem programa: NOT_APPLICABLE e a MESMA instancia de schedule (diferencial intacto)")
    void assemblyWithoutProgramIsIdentity() {
        TariffSchedule origin = t1Schedule();
        var assembled = assembly.assemble(origin, FROM, TO, ISSUED, null,
                ReliefBillingAssembly.ReliefContext.none());
        assertThat(assembled.decision()).isEqualTo(ReliefEmissionGuard.Decision.NOT_APPLICABLE);
        assertThat(assembled.schedule()).isSameAs(origin);
    }

    @Test
    @DisplayName("camada viva (BillCalculatorAdapter): sem programa o contexto leva o schedule do input; com programa, o desonerado — e a conta sai a 100,80")
    void adapterWiresAssemblyIntoLiveContext() {
        BillInput input = new BillInput(t1Schedule(), FROM, TO, List.of(res(1)), 27, null,
                List.of(), List.of(), null);
        // Sem programa: identidade.
        var plain = BillCalculatorAdapter.toContext(input, "UC-1", LocalDate.of(2027, 1, 1),
                ISSUED, null, assembly, ReliefBillingAssembly.ReliefContext.none());
        assertThat(plain.assembled().decision())
                .isEqualTo(ReliefEmissionGuard.Decision.NOT_APPLICABLE);
        assertThat(plain.builder().build().schedule()).isSameAs(input.schedule());

        // Com programa (versao unica): contexto desonerado, conta a 100,80.
        Multi m = multi(false);
        var singleGen = new RelievedVersionGenerator(new RelievedVersionGenerator.GenerationLedger());
        var gen = singleGen.generateTariffVersion(m.program(),
                t1Schedule().versions().get(0), ReliefFixtures.tenantRate("10"),
                UUID.randomUUID(), UUID.randomUUID(), 4);
        var ctx = new ReliefBillingAssembly.ReliefContext(List.of(m.program()), List.of(gen.link()),
                Map.of(gen.relievedVersion().id(), gen.relievedVersion()), true, false);
        var relieved = BillCalculatorAdapter.toContext(input, "UC-1", LocalDate.of(2027, 1, 1),
                ISSUED, null, assembly, ctx);
        assertThat(relieved.assembled().decision())
                .isEqualTo(ReliefEmissionGuard.Decision.RELIEVED_OK);
        var result = BillCalculator.calculate(relieved.builder().build().toBillInput());
        assertThat(result.waterValue()).isEqualByComparingTo("100.80");
        assertThat(result.total()).isEqualByComparingTo("100.80");
    }
}
