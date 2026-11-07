package br.com.ziipsan.commercial.billing.leakmeter;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Prazo de reclamacao do vazamento oculto (P3.28): o pedido so e tempestivo
 * se chegar ate {@code ancora + leak.claim_deadline_days}, onde a ancora e a
 * data escolhida em {@code leak.claim_deadline_anchor} -- a data da leitura
 * (ARIS, RN 19/2019 art. 89: "reclamacao em ate 45 dias da leitura") ou a
 * data de vencimento da fatura (ARESC, Res. 046/2016 art. 97, e AGIR,
 * RN 001/2013 art. 107: "pedido em 30 dias do vencimento"), conforme as
 * notas dos perfis da P2.31.
 *
 * <p>Contagem: o dia da ancora NAO conta; o ultimo dia e
 * {@code ancora.plusDays(prazo)} e um pedido NESSE dia e tempestivo
 * (fronteira inclusiva, a leitura mais favoravel ao usuario entre as
 * contagens possiveis -- ADR-0038). Pedido anterior a propria ancora e
 * recusado como incoerente (a fatura ainda nao existia na data ancorada).
 *
 * <p>Regra pura: quem abre a revisao (P3.28) consulta esta decisao ANTES de
 * criar qualquer OS -- pedido intempestivo nao deixa rastro.
 */
public final class LeakClaimDeadlineRule {

    /** Resultado da afericao do prazo, com as datas que o justificam. */
    public record DeadlineCheck(boolean timely, LocalDate anchorDate, LocalDate deadlineOn) {
    }

    private LeakClaimDeadlineRule() {
    }

    /** A data da qual o prazo corre, conforme a ancora configurada. */
    public static LocalDate anchorDate(LeakMeterParameters parameters, LocalDate readingDate, LocalDate dueDate) {
        Objects.requireNonNull(parameters, "parameters");
        return switch (parameters.claimDeadlineAnchor()) {
            case READING_DATE -> {
                if (readingDate == null) {
                    throw new IllegalArgumentException("Ancora READING_DATE exige a data da leitura da fatura");
                }
                yield readingDate;
            }
            case DUE_DATE -> {
                if (dueDate == null) {
                    throw new IllegalArgumentException("Ancora DUE_DATE exige a data de vencimento da fatura");
                }
                yield dueDate;
            }
        };
    }

    /** O ultimo dia tempestivo: ancora + prazo, com o dia da ancora fora da contagem. */
    public static LocalDate deadlineOn(LeakMeterParameters parameters, LocalDate readingDate, LocalDate dueDate) {
        return anchorDate(parameters, readingDate, dueDate).plusDays(parameters.claimDeadlineDays());
    }

    /** A reclamacao feita em {@code claimDate} e tempestivo? */
    public static DeadlineCheck evaluate(LeakMeterParameters parameters, LocalDate claimDate,
                                         LocalDate readingDate, LocalDate dueDate) {
        Objects.requireNonNull(claimDate, "claimDate");
        LocalDate anchor = anchorDate(parameters, readingDate, dueDate);
        LocalDate deadline = anchor.plusDays(parameters.claimDeadlineDays());
        boolean timely = !claimDate.isBefore(anchor) && !claimDate.isAfter(deadline);
        return new DeadlineCheck(timely, anchor, deadline);
    }

    /** Como {@link #evaluate}, mas lanca excecao nomeando as datas quando intempestivo. */
    public static DeadlineCheck requireTimely(LeakMeterParameters parameters, LocalDate claimDate,
                                              LocalDate readingDate, LocalDate dueDate) {
        DeadlineCheck check = evaluate(parameters, claimDate, readingDate, dueDate);
        if (!check.timely()) {
            throw new IllegalArgumentException("Reclamacao de vazamento intempestiva: pedido em " + claimDate
                    + ", ancora " + parameters.claimDeadlineAnchor() + " em " + check.anchorDate()
                    + ", prazo de " + parameters.claimDeadlineDays() + " dias ate " + check.deadlineOn());
        }
        return check;
    }
}
