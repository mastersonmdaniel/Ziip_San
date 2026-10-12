package br.com.sigsan.commercial.collection.payment;

/**
 * Situação de um pagamento arrecadado. Os 15 valores correspondem às situações do relatório 03
 * §2.5 (o GSAN tem 15 códigos, em 14 linhas da tabela: "15, 16" é uma linha com dois códigos),
 * com nomes próprios e <b>sem reutilizar a numeração do GSAN</b>.
 *
 * <p>Nem todos são decididos pelo {@link PaymentClassifier}: {@link #assignedByClassifier()}
 * separa os que a classificação produz dos que nascem de outros fluxos (entrada do movimento,
 * devolução, recuperação de crédito, baixa de excedente).
 */
public enum PaymentStatus {

    /** Casado com o documento, valor dentro da tolerância de classificação. */
    CLASSIFIED(true),
    /** Segundo (ou posterior) pagamento do mesmo documento; o valor todo é excedente a devolver. */
    DUPLICATE(true),
    /** Documento não encontrado ou pagamento não identificado. */
    DOC_NOT_FOUND(true),
    /** Pagamento único maior que o documento, além das tolerâncias; a diferença é excedente. */
    EXCESS(true),
    /** Excedente que aguarda baixa (definido pelo fluxo de baixa; entra no total como abatimento). */
    TO_WRITE_DOWN(false),
    /** Valor não confere com o documento (a menor, ou diferença que não se atribui a um pagamento). */
    AMOUNT_MISMATCH(true),
    /** Movimento aberto: recém-entrado, ainda não classificado. */
    OPEN(false),
    /** Duplicidade já devolvida ao pagador. */
    DUPLICATE_REFUNDED(false),
    /** Documento ainda no ciclo contábil aberto: classificar só depois do fechamento. */
    TO_ACCOUNT(true),
    DOC_PRESCRIBED(true),
    DOC_PARCELED(true),
    DOC_CANCELED(true),
    DOC_PROCESSING_ERROR(true),
    /** Pagamento reaproveitado como recuperação de crédito de documento cancelado. */
    CREDIT_RECOVERY_CANCELED(false),
    /** Pagamento duplicado reaproveitado como recuperação de crédito. */
    CREDIT_RECOVERY_DUPLICATE(false);

    private final boolean assignedByClassifier;

    PaymentStatus(boolean assignedByClassifier) {
        this.assignedByClassifier = assignedByClassifier;
    }

    /** {@code true} se {@link PaymentClassifier} pode produzir esta situação. */
    public boolean assignedByClassifier() {
        return assignedByClassifier;
    }

    /**
     * {@code true} se o pagamento já saiu do circuito de classificação (devolvido ou reaproveitado)
     * e não entra de novo no total do documento.
     */
    public boolean isSettledElsewhere() {
        return this == DUPLICATE_REFUNDED || this == CREDIT_RECOVERY_CANCELED
                || this == CREDIT_RECOVERY_DUPLICATE;
    }
}
