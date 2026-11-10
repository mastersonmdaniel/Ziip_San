package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Acerto do cancelamento de conta (aceite da P2.20; CDC, art. 42):
 * <ul>
 *   <li>os <b>creditos ja realizados</b> na conta cancelada voltam ao saldo
 *       do usuario (soma das linhas de {@code bill_credits} da conta) --
 *       regra travada, nao e parametro;</li>
 *   <li>dos debitos cobrados na conta, so <b>voltam a ser cobrados</b> os
 *       que o motivo do cancelamento nao abrange: o motivo (catalogo
 *       {@code bill_cancel_reasons}) declara os tipos de debito abrangidos
 *       ({@code covered_debit_type_codes}); tipo fora da lista volta, tipo
 *       dentro nao volta;</li>
 *   <li>pagamento, item de fatura agrupadora e debito automatico ligados a
 *       conta cancelada migram para a conta nova ({@link MigrationPlan}).</li>
 * </ul>
 * Calculo puro sobre as linhas fotografadas da conta; quem persiste o
 * credito de volta e a recobranca e o servico, na mesma transacao.
 */
public final class CancellationSettlement {

    /** Linha de debito cobrada na conta cancelada (de bill_debits). */
    public record ChargedDebit(UUID receivableId, String debitTypeCode, BigDecimal amount) {
        public ChargedDebit {
            Objects.requireNonNull(receivableId, "receivableId");
            Objects.requireNonNull(debitTypeCode, "debitTypeCode");
            Objects.requireNonNull(amount, "amount");
        }
    }

    /**
     * Para onde cada vinculo da conta cancelada vai.
     *
     * <p>{@code newBillId} e a <b>nova versao da conta</b> quando o
     * cancelamento tem substituicao (a conta que passa a cobrar); sem
     * substituicao, {@code newBillId == null} e o destino e explicito:
     * {@link #toNextEmission()} -- os vinculos migram para a proxima
     * emissao da unidade. Apontar o plano para a propria conta cancelada
     * seria migrar para uma conta morta (foi o defeito A1 da revisao
     * cruzada da P2.20; o servico so monta plano com o id da conta nova
     * de verdade, ou com o destino "proxima emissao").
     */
    public record MigrationPlan(UUID newBillId, List<UUID> paymentIds, UUID groupedInvoiceItemId,
                                UUID autoDebitMandateId) {
        public MigrationPlan {
            paymentIds = paymentIds == null ? List.of() : List.copyOf(paymentIds);
        }

        public boolean hasAnythingToMigrate() {
            return !paymentIds.isEmpty() || groupedInvoiceItemId != null || autoDebitMandateId != null;
        }

        /** Sem conta nova: os vinculos migram para a proxima emissao da unidade. */
        public boolean toNextEmission() {
            return newBillId == null;
        }
    }

    /** Resultado do acerto: credito de volta ao saldo + debitos que voltam a cobrar. */
    public record Settlement(BigDecimal creditsReturnedToBalance, List<ChargedDebit> debitsToRebill,
                             BigDecimal rebillTotal) {
    }

    private CancellationSettlement() {
    }

    /**
     * @param realizedCreditAmounts valores das linhas de credito realizadas na conta (bill_credits)
     * @param chargedDebits         debitos cobrados na conta (bill_debits)
     * @param coveredDebitTypeCodes tipos de debito que o motivo do cancelamento abrange
     *                              (do catalogo; vazio = o motivo nao abrange nenhum)
     */
    public static Settlement settle(List<BigDecimal> realizedCreditAmounts, List<ChargedDebit> chargedDebits,
                                    Set<String> coveredDebitTypeCodes) {
        Objects.requireNonNull(realizedCreditAmounts, "realizedCreditAmounts");
        Objects.requireNonNull(chargedDebits, "chargedDebits");
        Set<String> covered = coveredDebitTypeCodes == null ? Set.of() : coveredDebitTypeCodes;
        BigDecimal returned = Money.ZERO;
        for (BigDecimal amount : realizedCreditAmounts) {
            if (amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Credito realizado invalido: " + amount);
            }
            returned = returned.add(amount);
        }
        List<ChargedDebit> rebill = chargedDebits.stream()
                .filter(d -> !covered.contains(d.debitTypeCode()))
                .toList();
        BigDecimal rebillTotal = Money.ZERO;
        for (ChargedDebit d : rebill) {
            rebillTotal = rebillTotal.add(d.amount());
        }
        return new Settlement(returned, rebill, rebillTotal);
    }
}
