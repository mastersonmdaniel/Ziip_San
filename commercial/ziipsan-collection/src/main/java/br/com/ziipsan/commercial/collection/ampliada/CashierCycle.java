package br.com.ziipsan.commercial.collection.ampliada;

import br.com.ziipsan.commercial.collection.retorno.PaymentClassifier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ciclo do caixa proprio do prestador (ED-26, condicional por
 * {@code pay.own_cashier_enabled}): abertura por operador com fundo de troco,
 * recebimento em especie e em cartao COM comprovante, sangria, fechamento com
 * conferencia - e cada recebimento baixado pela MESMA classificacao da P3.1
 * ({@link PaymentClassifier}), sem regra paralela de baixa no caixa.
 *
 * <p>Conferencia do fechamento: esperado em especie = fundo + recebimentos em
 * especie QUE BAIXARAM algo - sangrias (cartao nao entra no gaveta); diferenca =
 * declarado - esperado; zero = CONFERIDO, diferente = DIVERGENCIA (registrada,
 * nunca ajustada em silencio). Maquina de estados explicita: operacao fora da
 * ordem (receber sem abrir, sangria maior que o gaveta, fechar duas vezes) e
 * erro.
 *
 * <p><b>Classificacao completa (correcao M3 da revisao cruzada):</b> o
 * recebimento recebe a entrada INTEIRA da P3.1
 * ({@link PaymentClassifier.ClassificationInput}) - estado real da conta
 * (aberta/ja paga/prescrita), encargos reais calculados ate o pagamento e
 * flag de revisao aberta - montada pela fiacao a partir da conta; o caixa nao
 * fixa estado, encargos nem revisao. Comportamento da gaveta declarado:
 * especie so entra no esperado quando a classificacao BAIXA algo
 * ({@code settledAmount > 0}); recebimento sem baixa
 * ({@code NONE_PENDING_MANUAL}, {@code DUPLICATE_*}, nao identificado,
 * transferencia) NAO soma a gaveta - o valor fica registrado no comprovante e
 * segue fora da conferencia, para classificacao manual ou devolucao pela
 * P3.29, nunca como especie conferivel em silencio.
 *
 * <p><b>Reabertura (correcao M2 da revisao cruzada):</b> {@link #close()} encerra
 * o ciclo, e um novo {@link #open} na mesma instancia REINICIA o estado -
 * comprovantes, especie recebido e sangrias do ciclo anterior nao vazam para o
 * novo ciclo (a fotografia do ciclo fechado ja foi devolvida no
 * {@link Closing}).
 */
public final class CashierCycle {

    public enum Tender { CASH, CARD }
    public enum ClosingStatus { CONFERIDO, DIVERGENCIA }

    /** Comprovante de um recebimento: numero sequencial do caixa, meio e a
     *  classificacao da P3.1 que baixou (ou nao) a conta. */
    public record Receipt(int number, Tender tender, BigDecimal amount,
                          PaymentClassifier.ClassificationDecision classification) {
    }

    /** Resultado do fechamento com a conferencia. */
    public record Closing(BigDecimal expectedCash, BigDecimal declaredCash, BigDecimal difference,
                          ClosingStatus status, List<Receipt> receipts) {
    }

    private final PaymentClassifier classifier = new PaymentClassifier();
    private final List<Receipt> receipts = new ArrayList<>();
    private boolean open;
    private boolean closed;
    private UUID operatorId;
    private BigDecimal openingFloat = new BigDecimal("0.00");
    private BigDecimal cashReceived = new BigDecimal("0.00");
    private BigDecimal bleeds = new BigDecimal("0.00");

    public void open(UUID operatorId, boolean cashierEnabled, BigDecimal openingFloat) {
        CashierAccess.requireEnabled(cashierEnabled);
        if (open) {
            throw new IllegalStateException("caixa ja esta aberto");
        }
        this.operatorId = Objects.requireNonNull(operatorId, "operador do caixa e obrigatorio");
        if (openingFloat == null || openingFloat.signum() < 0) {
            throw new IllegalArgumentException("fundo de troco deve ser >= 0: " + openingFloat);
        }
        // Reabertura apos close(): reinicia o ciclo (M2) - sem estado zumbi.
        this.receipts.clear();
        this.cashReceived = new BigDecimal("0.00");
        this.bleeds = new BigDecimal("0.00");
        this.closed = false;
        this.openingFloat = openingFloat;
        this.open = true;
    }

    /**
     * Recebe e classifica pela P3.1 com a entrada real da conta; devolve o
     * comprovante do recebimento. Especie so soma a gaveta quando a
     * classificacao baixa algo ({@code settledAmount > 0}) - ver javadoc da
     * classe (M3).
     */
    public Receipt receive(Tender tender, PaymentClassifier.ClassificationInput input) {
        requireOpen();
        Objects.requireNonNull(tender, "meio do recebimento e obrigatorio");
        Objects.requireNonNull(input, "entrada da classificacao da P3.1 e obrigatoria");
        var decision = classifier.classify(input);
        if (tender == Tender.CASH && decision.settledAmount().signum() > 0) {
            cashReceived = cashReceived.add(input.paidAmount());
        }
        Receipt receipt = new Receipt(receipts.size() + 1, tender, input.paidAmount(), decision);
        receipts.add(receipt);
        return receipt;
    }

    /** Sangria: retira especie do gaveta; nao pode passar do que ha em especie. */
    public void bleed(BigDecimal amount) {
        requireOpen();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("sangria deve ser positiva: " + amount);
        }
        BigDecimal available = openingFloat.add(cashReceived).subtract(bleeds);
        if (amount.compareTo(available) > 0) {
            throw new IllegalStateException("sangria de " + amount
                    + " maior que o especie em caixa (" + available + ")");
        }
        bleeds = bleeds.add(amount);
    }

    public Closing close(BigDecimal declaredCash) {
        requireOpen();
        if (declaredCash == null || declaredCash.signum() < 0) {
            throw new IllegalArgumentException("valor declarado no fechamento deve ser >= 0: " + declaredCash);
        }
        BigDecimal expected = openingFloat.add(cashReceived).subtract(bleeds);
        BigDecimal difference = declaredCash.subtract(expected);
        closed = true;
        open = false;
        return new Closing(expected, declaredCash, difference,
                difference.signum() == 0 ? ClosingStatus.CONFERIDO : ClosingStatus.DIVERGENCIA,
                List.copyOf(receipts));
    }

    public UUID operatorId() {
        return operatorId;
    }

    private void requireOpen() {
        if (!open || closed) {
            throw new IllegalStateException("caixa nao esta aberto para esta operacao");
        }
    }
}
