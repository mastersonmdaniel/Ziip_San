package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro do AJUIZAMENTO da execucao fiscal (ED-31; ADR-0028, d): o
 * produto REGISTRA numero do processo, vara e fase — e PARA ai. O produto
 * NAO conduz o processo judicial: nao ha peticionamento, prazo processual
 * nem movimentacao nesta classe; a fase so muda quando a procuradoria
 * INFORMA a nova fase e alguem a registra (registro de fato externo).
 *
 * <p>Portoes do ajuizamento: (1) valor da inscricao >=
 * {@code da.min_filing_amount} (lei do ente, parametro obrigatorio);
 * (2) prescricao da P4.1 nao consumada nem indeterminada, avaliada
 * ITEM A ITEM (correcao D1 da revisao: qualquer item prescrito ou
 * indeterminado bloqueia a inscricao inteira; agregar vencimento e
 * reconhecimento de itens diferentes mascarava debito prescrito).
 *
 * <p>Correcao D6(b) da revisao: quando o LIVRO e fornecido no
 * construtor, {@code register} marca a inscricao como {@code FILED}
 * no livro; sem livro, o registro existe mas a marcacao fica a cargo
 * da integracao (declarado, nao silencioso).
 */
public final class FilingRecord {

    /** Fases REGISTRADAS (informadas de fora), nao conduzidas pelo produto. */
    public enum Phase { FILED, SUMMONS, DEFENSE, JUDGMENT, APPEAL, CLOSED }

    public record Record(UUID enrollmentId, String processNumber, String court, Phase phase, LocalDate registeredOn) {
        public Record {
            if (enrollmentId == null || processNumber == null || processNumber.isBlank()
                    || court == null || court.isBlank() || phase == null || registeredOn == null)
                throw new IllegalArgumentException("registro de ajuizamento exige inscricao, numero do processo, vara, fase e data");
        }
        public Record withPhase(Phase newPhase, LocalDate on) {
            return new Record(enrollmentId, processNumber, court, Objects.requireNonNull(newPhase), Objects.requireNonNull(on));
        }
    }

    private final PrescriptionGate gate;
    private final ActiveDebtParameters params;
    private final Prescription.ProviderNature nature;
    private final Prescription.ClaimType claim;
    private final ActiveDebtEnrollment.Book book;

    public FilingRecord(PrescriptionGate gate, ActiveDebtParameters params,
                        Prescription.ProviderNature nature, Prescription.ClaimType claim) {
        this(gate, params, nature, claim, null);
    }

    /** Com livro: {@code register} marca a inscricao como FILED no livro (correcao D6b). */
    public FilingRecord(PrescriptionGate gate, ActiveDebtParameters params,
                        Prescription.ProviderNature nature, Prescription.ClaimType claim,
                        ActiveDebtEnrollment.Book book) {
        this.gate = Objects.requireNonNull(gate); this.params = Objects.requireNonNull(params);
        this.nature = Objects.requireNonNull(nature); this.claim = Objects.requireNonNull(claim);
        this.book = book;
    }

    /** Elegibilidade para ajuizar: minimo da lei do ente + prescricao ITEM A ITEM (sem registrar nada). */
    public void assertEligible(ActiveDebtEnrollment.Enrollment enrollment, boolean legalAnalysisRecorded) {
        Objects.requireNonNull(enrollment, "inscricao e obrigatoria");
        var min = params.minFilingAmount();
        if (enrollment.total().compareTo(min) < 0)
            throw new IllegalStateException("valor " + enrollment.total() + " abaixo do minimo para ajuizar (da.min_filing_amount=" + min + ", lei do ente)");
        gate.assertAllowedForAll(PrescriptionGate.Operation.FILING, nature, claim,
                enrollment.items(), legalAnalysisRecorded);
    }

    public Record register(ActiveDebtEnrollment.Enrollment enrollment, String processNumber, String court,
                           boolean legalAnalysisRecorded) {
        assertEligible(enrollment, legalAnalysisRecorded);
        var record = new Record(enrollment.id(), processNumber, court, Phase.FILED, gate.today());
        if (book != null && book.entries().stream().anyMatch(e -> e.id().equals(enrollment.id())))
            book.replace(enrollment.withStatus(ActiveDebtEnrollment.Status.FILED));
        return record;
    }
}
