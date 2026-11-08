package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/** Teste COM RELOGIO exigido pelo aceite: o relogio e fixo e avancado entre avaliacoes. */
class PrescriptionGateClockTest {

    @Test @DisplayName("relogio: vespera do termo final permite; no dia do termo, inscricao/protesto/ajuizamento bloqueiam")
    void clockBoundaryBlocksAllOperations() {
        var due = LocalDate.of(2016, 3, 10); // termo final 10/03/2026 (art. 205, 10 anos)
        var before = ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 3, 9));
        for (var op : PrescriptionGate.Operation.values())
            assertThat(before.check(op, Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL, due, null, false).allowed()).isTrue();
        var onDay = ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 3, 10));
        for (var op : PrescriptionGate.Operation.values()) {
            assertThat(onDay.check(op, Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL, due, null, false).allowed()).isFalse();
            assertThatThrownBy(() -> onDay.assertAllowed(op, Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL, due, null, false))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("PRESCRIBED");
        }
    }

    @Test @DisplayName("relogio: reconhecimento da divida reinicia o prazo e desbloqueia")
    void acknowledgmentRestartsClock() {
        var gate = ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 3, 10));
        var d = gate.check(PrescriptionGate.Operation.ENROLLMENT, Prescription.ProviderNature.PRIVATE_LAW,
                Prescription.ClaimType.GENERAL, LocalDate.of(2016, 3, 10), LocalDate.of(2020, 1, 15), false);
        assertThat(d.allowed()).isTrue();
        assertThat(d.evaluation().deadline()).isEqualTo(LocalDate.of(2030, 1, 15));
    }

    @Test @DisplayName("prescricao indeterminada (Temas 251-254 sem prazo) tambem bloqueia, por cautela")
    void undeterminedBlocks() {
        var gate = ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8));
        var d = gate.check(PrescriptionGate.Operation.FILING, Prescription.ProviderNature.PRIVATE_LAW,
                Prescription.ClaimType.TARIFF_EXECUTION, LocalDate.of(2020, 1, 1), null, false);
        assertThat(d.evaluation().outcome()).isEqualTo(Prescription.Outcome.UNDETERMINED);
        assertThat(d.allowed()).isFalse();
    }

    @Test @DisplayName("alerta de prescricao dispara dentro da janela parametrizada (180 dias) e nao antes")
    void alertWindow() {
        var due = LocalDate.of(2016, 9, 10); // termo 10/09/2026; janela de 180 dias abre 14/03/2026 (apos a vigencia da tabela, 01/01/2026)
        var early = ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 3, 13));
        var evalEarly = early.evaluate(Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL, due, null, false);
        assertThat(early.prescriptionAlertDue(evalEarly, 180)).isFalse();
        var inWindow = ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 3, 14));
        var evalIn = inWindow.evaluate(Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL, due, null, false);
        assertThat(inWindow.prescriptionAlertDue(evalIn, 180)).isTrue();
    }
}
