package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.bill.BillCalculator;
import br.com.ziipsan.commercial.billing.bill.BillInput;
import br.com.ziipsan.commercial.billing.tariff.SewerFactorPolicy;
import br.com.ziipsan.commercial.billing.tariff.SewerParameters;
import br.com.ziipsan.commercial.billing.tariff.TariffVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.res;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1Schedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Grupos 4, 6 e 7 — prova diferencial ADR-0048 + ancora P2.5; esgoto por
 * arrastamento; guarda de emissao (NT 5.3) e Q-D1 = C.
 */
class ReliefGuardAndDifferentialTest {

    @Test
    @DisplayName("grupo 4: caso-ancora P2.5 intacto (R$ 201,60) e massa pre-corte identica")
    void anchorAndPreCutDifferential() {
        SewerParameters sewer = new SewerParameters(d("80"), d("100"), null,
                SewerFactorPolicy.EXACT, false);
        BillInput input = new BillInput(t1Schedule(), LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 10, 4), List.of(res(1)), 27,
                new BillInput.SewerBilling(27, sewer), List.of(), List.of(), null);
        var result = BillCalculator.calculate(input);
        assertThat(result.waterValue()).isEqualByComparingTo("112.00");
        assertThat(result.sewerValue()).isEqualByComparingTo("89.60");
        assertThat(result.total()).isEqualByComparingTo("201.60");
    }

    @Test
    @DisplayName("grupo 6: esgoto deriva da estrutura desonerada; pccoleta/pcesgoto intocados")
    void sewerFollowsRelievedStructure() {
        var generator = new RelievedVersionGenerator(new RelievedVersionGenerator.GenerationLedger());
        var generated = generator.generateTariffVersion(ReliefFixtures.program(false), t1(),
                ReliefFixtures.tenantRate("10"), UUID.randomUUID(), UUID.randomUUID(), 4);
        TariffVersion relieved = new TariffVersion(generated.relievedVersion().id(),
                LocalDate.of(2026, 1, 1), null, generated.relievedVersion().rules(), null);
        SewerParameters sewer = new SewerParameters(d("80"), d("100"), null,
                SewerFactorPolicy.EXACT, false);
        BillInput input = new BillInput(new br.com.ziipsan.commercial.billing.tariff.TariffSchedule(
                List.of(relieved)), LocalDate.of(2027, 1, 5), LocalDate.of(2027, 2, 3),
                List.of(res(1)), 27, new BillInput.SewerBilling(27, sewer), List.of(), List.of(), null);
        var result = BillCalculator.calculate(input);
        assertThat(result.waterValue()).isEqualByComparingTo("100.80");
        assertThat(result.sewerValue()).isEqualByComparingTo("80.64");
        assertThat(result.total()).isEqualByComparingTo("181.44");
        assertThat(sewer.collectionPct()).isEqualByComparingTo("100");
        assertThat(sewer.sewerPct()).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("grupo 7: guarda — sem desonerada e sem via provavel, emissao recusada (falha fechada)")
    void guardBlocksWithoutRelievedOrProvisional() {
        ReliefProgram program = ReliefFixtures.program(false);
        assertThatThrownBy(() -> ReliefEmissionGuard.check(program, LocalDate.of(2027, 1, 10),
                UUID.randomUUID(), List.of(), false, false))
                .isInstanceOf(ReliefEmissionGuard.ReliefEmissionBlockedException.class);
    }

    @Test
    @DisplayName("grupo 7: via provisoria (NT 5.3) — gatilho e a falta de homologacao; exige declaracao e comunicacao formal")
    void provisionalPathNeedsDeclarationTriggerAndNotice() {
        ReliefProgram declared = ReliefFixtures.program(true);
        // falta de homologacao + declaracao + comunicacao formal -> provisorio permitido
        assertThat(ReliefEmissionGuard.check(declared, LocalDate.of(2027, 1, 10),
                UUID.randomUUID(), List.of(), false, true))
                .isEqualTo(ReliefEmissionGuard.Decision.PROVISIONAL_ALLOWED);
        // sem comunicacao formal registada -> recusado (NT 5.3.3-a)
        assertThatThrownBy(() -> ReliefEmissionGuard.check(declared, LocalDate.of(2027, 1, 10),
                UUID.randomUUID(), List.of(), false, false))
                .isInstanceOf(ReliefEmissionGuard.ReliefEmissionBlockedException.class);
        // homologacao publicada -> a via provisoria nao se aplica; sem desonerada, recusa
        assertThatThrownBy(() -> ReliefEmissionGuard.check(declared, LocalDate.of(2027, 1, 10),
                UUID.randomUUID(), List.of(), true, true))
                .isInstanceOf(ReliefEmissionGuard.ReliefEmissionBlockedException.class);
        // marcador do snapshot: provisorio com CBS+IBS incluidos e forma de regularizacao
        var keys = ReliefSnapshotKeys.provisional(declared.id());
        assertThat(keys.reliefProvisionalFallback()).isTrue();
        assertThat(keys.provisionalIncludesCbsIbs()).isTrue();
        assertThat(keys.toJsonFragment()).contains("COMPENSACAO_DAS_DIFERENCAS");
        var report = ProvisionalBillReport.ofProgram(List.of(
                new ProvisionalBillReport.ProvisionalBill("F-1", declared.id(),
                        LocalDate.of(2027, 1, 10), keys)), declared.id());
        assertThat(report).hasSize(1);
    }

    @Test
    @DisplayName("grupo 7: emissao com a versao desonerada registada passa; pre-corte nao se aplica")
    void guardPassesWithRelievedVersion() {
        var generator = new RelievedVersionGenerator(new RelievedVersionGenerator.GenerationLedger());
        ReliefProgram program = ReliefFixtures.program(false);
        var generated = generator.generateTariffVersion(program, t1(),
                ReliefFixtures.tenantRate("10"), UUID.randomUUID(), UUID.randomUUID(), 4);
        assertThat(ReliefEmissionGuard.check(program, LocalDate.of(2027, 1, 10),
                generated.relievedVersion().id(), List.of(generated.link()), true, false))
                .isEqualTo(ReliefEmissionGuard.Decision.RELIEVED_OK);
        assertThat(ReliefEmissionGuard.check(program, LocalDate.of(2026, 12, 31),
                UUID.randomUUID(), List.of(), false, false))
                .isEqualTo(ReliefEmissionGuard.Decision.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("Q-D1 = C: retificacao mantem SEMPRE a tabela original — fatura de 2026 retificada em 2027, incl. motivo tipo 02")
    void rectificationAlwaysKeepsOriginalTable() {
        TariffVersion original2026 = t1();
        for (String motive : List.of("01", "02", "03", "09")) {
            TariffVersion resolved = EmissionDateResolver.resolveForRectification(original2026, motive);
            assertThat(resolved.id()).isEqualTo(original2026.id());
            assertThat(resolved).isSameAs(original2026);
        }
    }

    @Test
    @DisplayName("maquina de estados (secao 7.2): DRAFT nao salta para ACTIVE; publicar exige reserva de Fase II")
    void programStateMachine() {
        ReliefProgram draft = new ReliefProgram(UUID.randomUUID(), "P", "p", null, "ato",
                ReliefFixtures.CUT, ReliefProgramStatus.DRAFT, null, null, null, null, null, null,
                null, null);
        assertThatThrownBy(() -> draft.transitionTo(ReliefProgramStatus.ACTIVE))
                .isInstanceOf(br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException.class);
        ReliefProgram approvedNoReserve = new ReliefProgram(UUID.randomUUID(), "P", "p", null, "ato",
                ReliefFixtures.CUT, ReliefProgramStatus.APPROVED, null, null, null, null,
                "Reguladora ficticia", null, null, null);
        assertThatThrownBy(() -> approvedNoReserve.publish(LocalDate.of(2026, 11, 15), "Ato 1"))
                .isInstanceOf(br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException.class)
                .hasMessageContaining("Fase II");
    }
}
