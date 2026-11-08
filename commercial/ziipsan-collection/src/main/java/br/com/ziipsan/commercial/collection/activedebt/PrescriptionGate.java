package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Portao de prescricao da divida ativa (ED-31; RAS-15 da P4.1): TODA
 * inscricao, protesto e ajuizamento passa pela regra de prescricao da P4.1
 * ({@link Prescription}). O desfecho PRESCRIBED bloqueia; o desfecho
 * UNDETERMINED (linha sem prazo confirmado ou dependente de analise
 * juridica) tambem bloqueia, por cautela — e o mesmo comportamento que a
 * P4.1 declara para os seus portoes. Nunca se presume "nao prescrita".
 *
 * <p>O relogio e injetado ({@link Clock}) para o teste com relogio exigido
 * pelo aceite: a data de avaliacao e {@code LocalDate.now(clock)}.
 */
public final class PrescriptionGate {

    public enum Operation { ENROLLMENT, PROTEST, FILING, INSTALLMENT, CDA_ISSUANCE, PROSECUTION_HANDOFF }

    public record Decision(boolean allowed, Prescription.Evaluation evaluation, Operation operation) {}

    private final Prescription.Table table;
    private final Clock clock;

    public PrescriptionGate(Prescription.Table table, Clock clock) {
        this.table = Objects.requireNonNull(table, "tabela de prescricao (P4.1) e obrigatoria");
        this.clock = Objects.requireNonNull(clock, "relogio e obrigatorio");
    }

    public LocalDate today() { return LocalDate.now(clock); }

    public Prescription.Evaluation evaluate(Prescription.ProviderNature nature, Prescription.ClaimType claim,
                                            LocalDate dueDate, LocalDate lastAcknowledgment,
                                            boolean legalAnalysisRecorded) {
        return Prescription.evaluate(table, nature, claim, dueDate, lastAcknowledgment,
                legalAnalysisRecorded, today());
    }

    public Decision check(Operation op, Prescription.ProviderNature nature, Prescription.ClaimType claim,
                          LocalDate dueDate, LocalDate lastAcknowledgment, boolean legalAnalysisRecorded) {
        var eval = evaluate(nature, claim, dueDate, lastAcknowledgment, legalAnalysisRecorded);
        boolean allowed = eval.outcome() == Prescription.Outcome.NOT_PRESCRIBED;
        return new Decision(allowed, eval, op);
    }

    /**
     * Avaliacao ITEM A ITEM de uma inscricao (correcao D1 da revisao da
     * ED-31): cada debito e avaliado com o SEU vencimento e o SEU
     * reconhecimento — o reconhecimento de um debito NUNCA reinicia o
     * relogio de outro. Se QUALQUER item estiver PRESCRIBED ou
     * UNDETERMINED, a operacao inteira e bloqueada, com o item nomeado
     * na mensagem. Agregar a inscricao (menor vencimento + maior
     * reconhecimento de itens diferentes) esta PROIBIDO: mascarava
     * debito prescrito em inscricao mista.
     */
    public void assertAllowedForAll(Operation op, Prescription.ProviderNature nature,
                                    Prescription.ClaimType claim,
                                    List<ActiveDebtEnrollment.DebtItem> items,
                                    boolean legalAnalysisRecorded) {
        Objects.requireNonNull(items, "itens da inscricao sao obrigatorios");
        if (items.isEmpty()) throw new IllegalArgumentException("inscricao sem itens nao pode ser avaliada");
        for (ActiveDebtEnrollment.DebtItem item : items) {
            Decision d = check(op, nature, claim, item.dueDate(), item.lastAcknowledgment(), legalAnalysisRecorded);
            if (!d.allowed()) {
                throw new IllegalStateException("divida ativa: " + op + " bloqueado pela prescricao (P4.1/RAS-15) no item "
                        + item.billId() + " (vencimento " + item.dueDate() + "): "
                        + d.evaluation().outcome() + " — " + d.evaluation().reason());
            }
        }
    }

    /** Como check, mas recusando com excecao explicita (uso nos servicos). */
    public Prescription.Evaluation assertAllowed(Operation op, Prescription.ProviderNature nature,
                                                 Prescription.ClaimType claim, LocalDate dueDate,
                                                 LocalDate lastAcknowledgment, boolean legalAnalysisRecorded) {
        Decision d = check(op, nature, claim, dueDate, lastAcknowledgment, legalAnalysisRecorded);
        if (!d.allowed()) {
            throw new IllegalStateException("divida ativa: " + op + " bloqueado pela prescricao (P4.1/RAS-15): "
                    + d.evaluation().outcome() + " — " + d.evaluation().reason());
        }
        return d.evaluation();
    }

    /**
     * Alerta de prescricao: verdadeiro quando o termo final esta a
     * {@code alertDays} dias ou menos (prazo parametrizado
     * {@code da.prescription_alert_days}) — ou ja alcancado. Sem termo final
     * (UNDETERMINED) nao ha data para alertar: devolve false e o motivo fica
     * no Evaluation; o bloqueio por cautela segue valendo nos portoes.
     */
    public boolean prescriptionAlertDue(Prescription.Evaluation eval, int alertDays) {
        Objects.requireNonNull(eval, "avaliacao e obrigatoria");
        if (alertDays < 0) throw new IllegalArgumentException("antecedencia do alerta nao pode ser negativa");
        if (eval.deadline() == null) return false;
        return !today().isBefore(eval.deadline().minusDays(alertDays));
    }
}
