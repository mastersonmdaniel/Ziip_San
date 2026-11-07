package br.com.ziipsan.commercial.accounting.receivable;

import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator.Sphere;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Contas a receber contabil — FIN-05 (20c): saldo mensal por prazo (curto/longo)
 * e por esfera (particular/publico), com a movimentacao do mes.
 * Formula (declarada e testada a mao):
 *   atual = anterior + faturado - pago - parcelado - cancelado - devolvido - imposto
 * "parcelado" sai deste saldo porque vira parcelamento (saldo proprio); "imposto"
 * e o tributo segregado/retido que nao e receita a receber do usuario.
 *
 * Decisao pos-revisao (L3): saldo atual NEGATIVO nao e licito no FIN-05 —
 * pago+parcelado+cancelado+devolvido+imposto maiores que anterior+faturado
 * indicam inconsistencia da movimentacao (pagamento a maior vira credito do
 * usuario, nao saldo negativo de recebiveis); current() recusa com
 * IllegalStateException em vez de devolver o negativo em silencio.
 */
public record ReceivablesMonthlyBalance(Sphere sphere, Term term,
        BigDecimal previous, BigDecimal billed, BigDecimal paid, BigDecimal parcelled,
        BigDecimal cancelled, BigDecimal refunded, BigDecimal tax) {

    public enum Term { SHORT, LONG }

    public ReceivablesMonthlyBalance {
        Objects.requireNonNull(sphere, "esfera e obrigatoria");
        Objects.requireNonNull(term, "prazo e obrigatorio");
        for (BigDecimal v : new BigDecimal[]{previous, billed, paid, parcelled, cancelled, refunded, tax}) {
            Objects.requireNonNull(v, "componente do saldo FIN-05 e obrigatorio");
            if (v.signum() < 0) throw new IllegalArgumentException("componente do saldo FIN-05 nao pode ser negativo: " + v);
        }
    }

    public BigDecimal current() {
        BigDecimal v = previous.add(billed).subtract(paid).subtract(parcelled)
                .subtract(cancelled).subtract(refunded).subtract(tax);
        if (v.signum() < 0)
            throw new IllegalStateException("saldo FIN-05 negativo (" + v + ") para " + sphere + "/" + term
                    + ": movimentacao do mes inconsistente (baixado maior que o devido)");
        return v;
    }

    /**
     * Agregacao de registros da MESMA esfera e prazo (correcao pos-revisao M6):
     * o relatorio de um bucket esfera x prazo e a soma componente a componente
     * dos registros dele; misturar esfera ou prazo e recusado.
     */
    public static ReceivablesMonthlyBalance aggregate(List<ReceivablesMonthlyBalance> parts) {
        Objects.requireNonNull(parts, "registros FIN-05 sao obrigatorios");
        if (parts.isEmpty()) throw new IllegalArgumentException("agregacao FIN-05 exige ao menos um registro");
        Sphere sphere = parts.get(0).sphere();
        Term term = parts.get(0).term();
        BigDecimal previous = BigDecimal.ZERO, billed = BigDecimal.ZERO, paid = BigDecimal.ZERO,
                parcelled = BigDecimal.ZERO, cancelled = BigDecimal.ZERO, refunded = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        for (ReceivablesMonthlyBalance p : parts) {
            if (p.sphere() != sphere || p.term() != term)
                throw new IllegalArgumentException("agregacao FIN-05 nao mistura esfera/prazo: " + p.sphere() + "/" + p.term() + " em " + sphere + "/" + term);
            previous = previous.add(p.previous());
            billed = billed.add(p.billed());
            paid = paid.add(p.paid());
            parcelled = parcelled.add(p.parcelled());
            cancelled = cancelled.add(p.cancelled());
            refunded = refunded.add(p.refunded());
            tax = tax.add(p.tax());
        }
        return new ReceivablesMonthlyBalance(sphere, term, previous, billed, paid, parcelled, cancelled, refunded, tax);
    }
}
