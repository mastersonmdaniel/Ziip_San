package br.com.ziipsan.commercial.collection.ampliada;

import br.com.ziipsan.commercial.collection.parameters.ArrecadacaoParameters.PartialSettlementOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Quitacao em cascata de documento agrupador (ED-26): um unico pagamento quita,
 * em ordem, as contas agrupadas - inclusive a fatura agrupadora do orgao
 * centralizador, cujas contas sao as das unidades vinculadas.
 *
 * <p>Quando o valor pago e MENOR que o total agrupado, a ordem de aplicacao e a do
 * parametro {@code pay.partial_settlement_order} (lido por
 * {@code ArrecadacaoParameters}); a conta alcancada parcialmente fica com saldo
 * aberto explicito no resultado, e as seguintes nao sao tocadas. Valor pago MAIOR
 * que o total quita tudo e devolve o excedente como {@code excessAmount} (a
 * destinacao do excedente - credito a realizar - e da classificacao da P3.1,
 * nao desta regra).
 *
 * <p>Regra pura: decide a distribuicao; quem grava as baixas e a fiacao
 * transacional da integracao, conta a conta, na ordem devolvida.
 */
public final class CascadeSettlement {

    /** Fatia agrupada: uma conta (de uma unidade) dentro do documento agrupador. */
    public record GroupedBill(UUID billId, UUID unitId, LocalDate dueDate, LocalDate issueDate,
                              BigDecimal amountDue) {
        public GroupedBill {
            Objects.requireNonNull(billId, "conta e obrigatoria");
            // B1 (revisao cruzada): conta agrupada e, por definicao, "de uma unidade".
            Objects.requireNonNull(unitId, "unidade da conta agrupada e obrigatoria");
            Objects.requireNonNull(dueDate, "vencimento e obrigatorio");
            Objects.requireNonNull(issueDate, "emissao e obrigatoria");
            if (amountDue == null || amountDue.signum() <= 0) {
                throw new IllegalArgumentException("valor da conta agrupada deve ser positivo: " + amountDue);
            }
        }
    }

    /** Aplicacao sobre uma conta: quanto baixou e quanto resta aberto nela. */
    public record Allocation(UUID billId, UUID unitId, BigDecimal settledAmount,
                             BigDecimal remainingOpen, boolean fullySettled) {
    }

    /**
     * @param allocations  aplicacoes na ordem em que devem ser gravadas
     * @param totalDue     soma das contas agrupadas
     * @param excessAmount valor pago alem do total (zero quando pago a menor ou exato)
     */
    public record CascadeResult(List<Allocation> allocations, BigDecimal totalDue,
                                BigDecimal totalSettled, BigDecimal excessAmount) {
    }

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private CascadeSettlement() {
    }

    public static CascadeResult settle(List<GroupedBill> bills, BigDecimal paidAmount,
                                       PartialSettlementOrder order) {
        Objects.requireNonNull(bills, "contas agrupadas sao obrigatorias");
        Objects.requireNonNull(order, "ordem de quitacao (pay.partial_settlement_order) e obrigatoria");
        if (bills.isEmpty()) {
            throw new IllegalArgumentException("documento agrupador sem nenhuma conta nao quita nada");
        }
        if (paidAmount == null || paidAmount.signum() <= 0) {
            throw new IllegalArgumentException("valor pago deve ser positivo: " + paidAmount);
        }
        List<GroupedBill> sorted = new ArrayList<>(bills);
        sorted.sort(comparator(order));
        BigDecimal totalDue = bills.stream().map(GroupedBill::amountDue).reduce(ZERO, BigDecimal::add);
        BigDecimal remaining = paidAmount;
        BigDecimal settled = ZERO;
        List<Allocation> allocations = new ArrayList<>();
        for (GroupedBill bill : sorted) {
            BigDecimal applied = remaining.min(bill.amountDue());
            if (applied.signum() < 0) {
                applied = ZERO;
            }
            remaining = remaining.subtract(applied);
            settled = settled.add(applied);
            allocations.add(new Allocation(bill.billId(), bill.unitId(), applied,
                    bill.amountDue().subtract(applied), applied.compareTo(bill.amountDue()) == 0));
        }
        BigDecimal excess = paidAmount.subtract(settled).max(ZERO);
        return new CascadeResult(List.copyOf(allocations), totalDue, settled, excess);
    }

    private static Comparator<GroupedBill> comparator(PartialSettlementOrder order) {
        return switch (order) {
            case DUE_DATE_ASC -> Comparator.comparing(GroupedBill::dueDate)
                    .thenComparing(GroupedBill::issueDate).thenComparing(g -> g.billId().toString());
            case ISSUE_DATE_ASC -> Comparator.comparing(GroupedBill::issueDate)
                    .thenComparing(GroupedBill::dueDate).thenComparing(g -> g.billId().toString());
            case AMOUNT_ASC -> Comparator.comparing(GroupedBill::amountDue)
                    .thenComparing(GroupedBill::dueDate).thenComparing(g -> g.billId().toString());
            case AMOUNT_DESC -> Comparator.comparing(GroupedBill::amountDue).reversed()
                    .thenComparing(GroupedBill::dueDate).thenComparing(g -> g.billId().toString());
        };
    }
}
