package br.com.ziipsan.commercial.collection.ampliada;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Depositos identificados e nao identificados (ED-26): deposito com referencia a
 * uma conta aberta e valor igual ao devido e IDENTIFICADO; sem referencia, com
 * referencia desconhecida ou com valor divergente, e NAO IDENTIFICADO e vai a
 * fila - a identificacao manual e a devolucao sao da P3.29 (fila e OS de
 * identificacao, V6_5); aqui so a triagem, sem baixa inventada.
 */
public final class DepositIdentification {

    public enum DepositStatus { IDENTIFIED, UNIDENTIFIED }

    /** Deposito bancario avulso (extrato/VAN): referencia pode faltar. */
    public record Deposit(BigDecimal amount, LocalDate depositedOn, UUID referencedBillId) {
        public Deposit {
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("valor do deposito deve ser positivo: " + amount);
            }
            Objects.requireNonNull(depositedOn, "data do deposito e obrigatoria");
        }
    }

    /** Conta aberta candidata, como a fiacao a le do billing. */
    public record OpenBill(UUID billId, BigDecimal amountDue) {
        public OpenBill {
            // B2 (revisao cruzada): amountDue nulo era NPE na comparacao; agora erro explicito.
            Objects.requireNonNull(billId, "conta candidata e obrigatoria");
            if (amountDue == null || amountDue.signum() < 0) {
                throw new IllegalArgumentException("valor devido da conta candidata deve ser informado e >= 0: "
                        + amountDue);
            }
        }
    }

    public record Identification(DepositStatus status, UUID billId, String reason) {
    }

    private DepositIdentification() {
    }

    public static Identification identify(Deposit deposit, OpenBill candidate) {
        Objects.requireNonNull(deposit, "deposito e obrigatorio");
        if (deposit.referencedBillId() == null) {
            return new Identification(DepositStatus.UNIDENTIFIED, null,
                    "deposito sem referencia a conta: fila de nao identificados (P3.29)");
        }
        if (candidate == null || !candidate.billId().equals(deposit.referencedBillId())) {
            return new Identification(DepositStatus.UNIDENTIFIED, deposit.referencedBillId(),
                    "referencia nao corresponde a conta aberta: fila de nao identificados (P3.29)");
        }
        if (deposit.amount().compareTo(candidate.amountDue()) != 0) {
            return new Identification(DepositStatus.UNIDENTIFIED, deposit.referencedBillId(),
                    "valor do deposito diverge do devido: identificacao manual (P3.29)");
        }
        return new Identification(DepositStatus.IDENTIFIED, candidate.billId(),
                "referencia e valor conferem com a conta aberta");
    }
}
