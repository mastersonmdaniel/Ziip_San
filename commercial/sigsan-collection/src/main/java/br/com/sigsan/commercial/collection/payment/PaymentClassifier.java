package br.com.sigsan.commercial.collection.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Classificação de pagamentos contra o documento que eles quitam. Serviço puro: sem I/O e sem
 * valor padrão. As tolerâncias que o GSAN fixa no código (R$ 0,03 e R$ 2,00) são parâmetros do
 * tenant em {@link Policy}.
 *
 * <h3>Regras (na ordem)</h3>
 * <ol>
 *   <li>Pagamento já devolvido ou reaproveitado ({@link PaymentStatus#isSettledElsewhere()}) fica
 *       como está e não entra no total.</li>
 *   <li>Sem documento: {@link PaymentStatus#DOC_NOT_FOUND} (pagamento não identificado).</li>
 *   <li>Documento prescrito, parcelado, cancelado ou com erro: a situação correspondente.</li>
 *   <li>Documento normal com ciclo contábil aberto: {@link PaymentStatus#TO_ACCOUNT}.</li>
 *   <li>Documento normal, ciclo fechado. {@code total} = soma dos pagamentos menos o excedente
 *       já registrado nos que estão em {@link PaymentStatus#TO_WRITE_DOWN}; {@code dif = total -
 *       valor do documento}:
 *     <ul>
 *       <li>{@code |dif| <= tolerância de classificação}: todos {@code CLASSIFIED};</li>
 *       <li>{@code dif < 0} (a menor): se {@code |dif| <= tolerância de ajuste}, todos
 *           {@code CLASSIFIED} e o ajuste é um {@code DEBIT_TO_CHARGE} de {@code |dif|};
 *           senão todos {@code AMOUNT_MISMATCH};</li>
 *       <li>{@code dif > 0} (a maior): se {@code dif <= tolerância de ajuste}, todos
 *           {@code CLASSIFIED} e o ajuste é um {@code CREDIT_TO_REALIZE} de {@code dif}. Senão,
 *           entre os pagamentos cujo valor iguala o documento (dentro da tolerância de
 *           classificação) um vence ({@link Policy#duplicateWinner()}) e vira {@code CLASSIFIED};
 *           os demais iguais viram {@code DUPLICATE} com excedente igual ao seu valor; os de
 *           outro valor viram {@code AMOUNT_MISMATCH}. Se nenhum iguala o documento: um único
 *           pagamento vira {@code EXCESS} (excedente = {@code dif}); vários viram
 *           {@code AMOUNT_MISMATCH} (não se sabe a qual atribuir o excesso).</li>
 *     </ul></li>
 * </ol>
 *
 * <p>Divergência registrada com o GSAN (relatório 03 §2.5, pseudocódigo "não conferido linha a
 * linha"): lá o pagamento a maior sem par exato cai em {@code VALOR_NAO_CONFERE}; aqui vira
 * {@code EXCESS}, porque a maior (devolver/creditar) e a menor (cobrar a diferença) têm
 * tratamentos opostos e o relatório lista as duas situações separadas.
 */
public final class PaymentClassifier {

    public enum DocumentCondition { NORMAL, PRESCRIBED, PARCELED, CANCELED, PROCESSING_ERROR }

    public enum DuplicateWinner { OLDEST_PAYMENT, NEWEST_PAYMENT }

    public enum AdjustmentKind { NONE, CREDIT_TO_REALIZE, DEBIT_TO_CHARGE }

    /**
     * Parâmetros do tenant.
     *
     * @param classifyTolerance tolerância de classificação (o GSAN fixa 0,03)
     * @param adjustTolerance   tolerância de ajuste, maior ou igual à de classificação (o GSAN fixa 2,00)
     * @param duplicateWinner   qual pagamento fica como o válido quando há duplicidade
     */
    public record Policy(BigDecimal classifyTolerance, BigDecimal adjustTolerance, DuplicateWinner duplicateWinner) {
        public Policy {
            if (classifyTolerance == null || classifyTolerance.signum() < 0) {
                throw new IllegalArgumentException("Tolerância de classificação: obrigatória e não negativa");
            }
            if (adjustTolerance == null || adjustTolerance.compareTo(classifyTolerance) < 0) {
                throw new IllegalArgumentException(
                        "Tolerância de ajuste: obrigatória e não menor que a de classificação");
            }
            if (duplicateWinner == null) {
                throw new IllegalArgumentException("A política de duplicidade é obrigatória");
            }
        }
    }

    /**
     * @param id             identificador do pagamento (devolvido nos resultados)
     * @param amount         valor pago, maior que zero
     * @param paidOn         data do pagamento
     * @param previousStatus situação atual ({@link PaymentStatus#OPEN} para movimento novo)
     * @param pendingExcess  excedente já registrado, relevante só em {@code TO_WRITE_DOWN}
     * @param unitIdentified se o pagamento foi vinculado a uma unidade consumidora
     */
    public record Payment(String id, BigDecimal amount, LocalDate paidOn, PaymentStatus previousStatus,
                          BigDecimal pendingExcess, boolean unitIdentified) {
        public Payment {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Pagamento sem identificador");
            }
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("O valor do pagamento deve ser maior que zero");
            }
            if (paidOn == null || previousStatus == null || pendingExcess == null || pendingExcess.signum() < 0) {
                throw new IllegalArgumentException("Data, situação anterior e excedente (>= 0) são obrigatórios");
            }
        }

        BigDecimal effectiveAmount() {
            return previousStatus == PaymentStatus.TO_WRITE_DOWN ? amount.subtract(pendingExcess) : amount;
        }
    }

    /**
     * @param amount               valor do documento
     * @param condition            situação do documento
     * @param accountingCycleOpen  {@code true} se o documento ainda está no ciclo contábil aberto
     */
    public record Document(BigDecimal amount, DocumentCondition condition, boolean accountingCycleOpen) {
        public Document {
            if (amount == null || amount.signum() < 0 || condition == null) {
                throw new IllegalArgumentException("Valor (>= 0) e situação do documento são obrigatórios");
            }
        }
    }

    /**
     * @param paymentId    pagamento
     * @param status       nova situação
     * @param excessAmount excedente a devolver/creditar atribuído a este pagamento (zero se nenhum)
     * @param identified   se o pagamento está vinculado a unidade ou documento
     * @param reason       motivo, para a trilha de auditoria
     */
    public record Outcome(String paymentId, PaymentStatus status, BigDecimal excessAmount, boolean identified,
                          String reason) {
    }

    /**
     * @param outcomes         um por pagamento, na ordem de entrada
     * @param adjustment       ajuste gerado pela diferença dentro da tolerância de ajuste
     * @param adjustmentAmount valor do ajuste (zero se {@code NONE})
     * @param documentSettled  se os pagamentos classificados quitam o documento
     */
    public record Result(List<Outcome> outcomes, AdjustmentKind adjustment, BigDecimal adjustmentAmount,
                         boolean documentSettled) {
    }

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final Policy policy;

    public PaymentClassifier(Policy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("A política é obrigatória");
        }
        this.policy = policy;
    }

    public Result classify(Optional<Document> document, List<Payment> payments) {
        List<Outcome> outcomes = new ArrayList<>(payments.size());
        List<Payment> active = new ArrayList<>();
        // Mantém a ordem de entrada: reserva a posição e preenche depois.
        Outcome[] slots = new Outcome[payments.size()];
        for (int i = 0; i < payments.size(); i++) {
            Payment p = payments.get(i);
            if (p.previousStatus().isSettledElsewhere()) {
                slots[i] = new Outcome(p.id(), p.previousStatus(), ZERO, true, "mantido: já devolvido ou reaproveitado");
            } else {
                active.add(p);
            }
        }

        Result inner = classifyActive(document, active);
        int next = 0;
        for (int i = 0; i < slots.length; i++) {
            outcomes.add(slots[i] != null ? slots[i] : inner.outcomes().get(next++));
        }
        return new Result(outcomes, inner.adjustment(), inner.adjustmentAmount(), inner.documentSettled());
    }

    private Result classifyActive(Optional<Document> document, List<Payment> active) {
        if (active.isEmpty()) {
            return new Result(List.of(), AdjustmentKind.NONE, ZERO, false);
        }
        if (document.isEmpty()) {
            return uniform(active, PaymentStatus.DOC_NOT_FOUND, "documento não encontrado", false, false);
        }
        Document doc = document.get();
        switch (doc.condition()) {
            case PRESCRIBED:
                return uniform(active, PaymentStatus.DOC_PRESCRIBED, "documento prescrito", true, false);
            case PARCELED:
                return uniform(active, PaymentStatus.DOC_PARCELED, "documento parcelado", true, false);
            case CANCELED:
                return uniform(active, PaymentStatus.DOC_CANCELED, "documento cancelado", true, false);
            case PROCESSING_ERROR:
                return uniform(active, PaymentStatus.DOC_PROCESSING_ERROR, "erro no processamento do documento", true,
                        false);
            case NORMAL:
            default:
                break;
        }
        if (doc.accountingCycleOpen()) {
            return uniform(active, PaymentStatus.TO_ACCOUNT, "ciclo contábil do documento aberto", true, false);
        }
        return classifyAmounts(doc, active);
    }

    private Result classifyAmounts(Document doc, List<Payment> active) {
        BigDecimal total = active.stream().map(Payment::effectiveAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = total.subtract(doc.amount());
        BigDecimal abs = diff.abs();

        if (abs.compareTo(policy.classifyTolerance()) <= 0) {
            return uniform(active, PaymentStatus.CLASSIFIED, "total confere com o documento", true, true);
        }
        if (diff.signum() < 0) {
            if (abs.compareTo(policy.adjustTolerance()) <= 0) {
                return withAdjustment(active, AdjustmentKind.DEBIT_TO_CHARGE, abs,
                        "a menor dentro da tolerância de ajuste");
            }
            return uniform(active, PaymentStatus.AMOUNT_MISMATCH, "a menor além da tolerância de ajuste", true, false);
        }
        // a maior
        if (diff.compareTo(policy.adjustTolerance()) <= 0) {
            return withAdjustment(active, AdjustmentKind.CREDIT_TO_REALIZE, diff,
                    "a maior dentro da tolerância de ajuste");
        }
        return overpaid(doc, active, diff);
    }

    private Result overpaid(Document doc, List<Payment> active, BigDecimal diff) {
        List<Payment> exact = new ArrayList<>();
        for (Payment p : active) {
            if (p.effectiveAmount().subtract(doc.amount()).abs().compareTo(policy.classifyTolerance()) <= 0) {
                exact.add(p);
            }
        }
        if (exact.isEmpty()) {
            if (active.size() == 1) {
                Payment p = active.get(0);
                return new Result(List.of(new Outcome(p.id(), PaymentStatus.EXCESS, diff, true,
                        "pagamento único maior que o documento")), AdjustmentKind.NONE, ZERO, false);
            }
            return uniform(active, PaymentStatus.AMOUNT_MISMATCH,
                    "total maior que o documento sem pagamento de valor igual", true, false);
        }
        Payment winner = exact.get(0);
        for (Payment p : exact) {
            boolean better = policy.duplicateWinner() == DuplicateWinner.OLDEST_PAYMENT
                    ? p.paidOn().isBefore(winner.paidOn())
                    : p.paidOn().isAfter(winner.paidOn());
            if (better) {
                winner = p;
            }
        }
        List<Outcome> out = new ArrayList<>(active.size());
        for (Payment p : active) {
            if (p == winner) {
                out.add(new Outcome(p.id(), PaymentStatus.CLASSIFIED, ZERO, true, "pagamento válido do documento"));
            } else if (exact.contains(p)) {
                out.add(new Outcome(p.id(), PaymentStatus.DUPLICATE, p.effectiveAmount(), true,
                        "duplicidade do documento"));
            } else {
                out.add(new Outcome(p.id(), PaymentStatus.AMOUNT_MISMATCH, ZERO, true,
                        "valor diferente do documento"));
            }
        }
        return new Result(out, AdjustmentKind.NONE, ZERO, true);
    }

    private Result uniform(List<Payment> active, PaymentStatus status, String reason, boolean identifiedDoc,
                           boolean settled) {
        List<Outcome> out = new ArrayList<>(active.size());
        for (Payment p : active) {
            out.add(new Outcome(p.id(), status, ZERO, identifiedDoc || p.unitIdentified(), reason));
        }
        return new Result(out, AdjustmentKind.NONE, ZERO, settled);
    }

    private Result withAdjustment(List<Payment> active, AdjustmentKind kind, BigDecimal amount, String reason) {
        List<Outcome> out = new ArrayList<>(active.size());
        for (Payment p : active) {
            out.add(new Outcome(p.id(), PaymentStatus.CLASSIFIED, ZERO, true, reason));
        }
        return new Result(out, kind, amount, true);
    }
}
