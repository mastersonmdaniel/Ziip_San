package br.com.ziipsan.commercial.collection.card;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Conciliacao pelo EXTRATO da credenciadora (ED-28, segunda perna do
 * aceite: "baixa e conciliacao automaticas pelo retorno da
 * credenciadora e pelo extrato"). Puro e deterministico, no padrao do
 * {@code SplitPaymentReconciler} da P3.1: casa as baixas por cartao ja
 * gravadas com as linhas do extrato pela transacao da credenciadora.
 *
 * <p>Resultados por item:
 * <ul>
 *   <li>{@code CONCILIADO}: bruto igual e liquido dentro da tolerancia
 *       {@code card.statement_tolerance};</li>
 *   <li>{@code DIVERGENTE}: bruto diferente, ou liquido fora da
 *       tolerancia (a diferenca vai no item, para a fila manual);</li>
 *   <li>{@code AUSENTE_NO_EXTRATO}: houve baixa e o extrato nao a traz
 *       (liquidacao ainda nao ocorreu ou a credenciadora nao pagou);</li>
 *   <li>{@code EXTRATO_SEM_BAIXA}: o extrato traz transacao sem baixa
 *       correspondente (dinheiro sem parcela: fila da arrecadacao);</li>
 *   <li>{@code DUPLICADO_NO_EXTRATO}: a mesma transacao aparece 2+
 *       vezes no extrato — nunca conta duas vezes nos totais.</li>
 * </ul>
 */
public final class CardStatementReconciler {

    public enum ItemStatus { CONCILIADO, DIVERGENTE, AUSENTE_NO_EXTRATO, EXTRATO_SEM_BAIXA, DUPLICADO_NO_EXTRATO }

    /** Baixa esperada no extrato (das baixas gravadas pelo retorno). */
    public record ExpectedSettlement(String acquirerTransactionId, String sessionReference,
                                     BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal netAmount) {
        public ExpectedSettlement {
            if (acquirerTransactionId == null || acquirerTransactionId.isBlank()
                    || grossAmount == null || feeAmount == null || netAmount == null) {
                throw new IllegalArgumentException("baixa esperada exige transacao e valores");
            }
        }
    }

    /** @param netDifference liquido do extrato − liquido esperado (zero quando nao ha par) */
    public record Item(String acquirerTransactionId, ItemStatus status, BigDecimal netDifference) {
    }

    public record Result(List<Item> items, BigDecimal expectedNetTotal, BigDecimal statementNetTotal,
                         int reconciledCount) {
        public Result {
            items = List.copyOf(items);
        }
    }

    public Result reconcile(List<ExpectedSettlement> expected,
                            List<CardPorts.AcquirerPort.StatementLine> statement,
                            BigDecimal tolerance) {
        Objects.requireNonNull(expected, "baixas esperadas sao obrigatorias");
        Objects.requireNonNull(statement, "linhas do extrato sao obrigatorias");
        Objects.requireNonNull(tolerance, "tolerancia (card.statement_tolerance) e obrigatoria");
        if (tolerance.signum() < 0) {
            throw new IllegalArgumentException("tolerancia nao pode ser negativa: " + tolerance);
        }

        Map<String, CardPorts.AcquirerPort.StatementLine> firstLineByTx = new HashMap<>();
        Map<String, Integer> lineCountByTx = new HashMap<>();
        for (var line : statement) {
            firstLineByTx.putIfAbsent(line.acquirerTransactionId(), line);
            lineCountByTx.merge(line.acquirerTransactionId(), 1, Integer::sum);
        }

        List<Item> items = new ArrayList<>();
        BigDecimal expectedNetTotal = BigDecimal.ZERO;
        int reconciled = 0;
        for (var exp : expected) {
            expectedNetTotal = expectedNetTotal.add(exp.netAmount());
            int count = lineCountByTx.getOrDefault(exp.acquirerTransactionId(), 0);
            if (count == 0) {
                items.add(new Item(exp.acquirerTransactionId(), ItemStatus.AUSENTE_NO_EXTRATO, BigDecimal.ZERO));
            } else if (count > 1) {
                items.add(new Item(exp.acquirerTransactionId(), ItemStatus.DUPLICADO_NO_EXTRATO, BigDecimal.ZERO));
            } else {
                var line = firstLineByTx.get(exp.acquirerTransactionId());
                BigDecimal netDiff = line.netAmount().subtract(exp.netAmount());
                boolean ok = line.grossAmount().compareTo(exp.grossAmount()) == 0
                        && netDiff.abs().compareTo(tolerance) <= 0;
                if (ok) {
                    reconciled++;
                }
                items.add(new Item(exp.acquirerTransactionId(),
                        ok ? ItemStatus.CONCILIADO : ItemStatus.DIVERGENTE, netDiff));
            }
        }

        BigDecimal statementNetTotal = BigDecimal.ZERO;
        var expectedTx = new java.util.HashSet<String>();
        for (var exp : expected) {
            expectedTx.add(exp.acquirerTransactionId());
        }
        for (var line : statement) {
            // Duplicada no extrato nao entra de novo no total: conta a primeira ocorrencia.
            if (firstLineByTx.get(line.acquirerTransactionId()) == line) {
                statementNetTotal = statementNetTotal.add(line.netAmount());
            }
            if (!expectedTx.contains(line.acquirerTransactionId())) {
                items.add(new Item(line.acquirerTransactionId(), ItemStatus.EXTRATO_SEM_BAIXA, BigDecimal.ZERO));
            }
        }
        return new Result(items, expectedNetTotal, statementNetTotal, reconciled);
    }
}
