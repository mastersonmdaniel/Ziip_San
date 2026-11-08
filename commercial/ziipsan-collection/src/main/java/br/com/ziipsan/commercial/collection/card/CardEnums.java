package br.com.ziipsan.commercial.collection.card;

/**
 * Vocabularios do pagamento com cartao pela credenciadora do tenant
 * (ED-28; 18e-E14, decisao D3 opcao A do levantamento de editais;
 * ADR-0028 (h)). Os CHECKs do SQL pendente espelham estes nomes.
 *
 * <p>Nenhum vocabulario aqui nomeia dado de cartao: o cartao e digitado
 * SOMENTE na pagina da credenciadora (link ou checkout hospedado); o
 * produto conhece apenas a sessao, a transacao da credenciadora e os
 * valores — ver {@link CardDataGuard}.
 */
public final class CardEnums {

    private CardEnums() {
    }

    /** Como o pagador chega a pagina da credenciadora ({@code card.checkout_mode}). */
    public enum CheckoutMode {
        /** Link de pagamento gerado pela credenciadora e entregue ao pagador. */
        PAYMENT_LINK,
        /** Checkout hospedado pela credenciadora (redirect a partir do portal). */
        HOSTED_CHECKOUT
    }

    /** Situacao da sessao de checkout/link aberta para uma parcela ({@code status}). */
    public enum SessionStatus {
        CREATED,
        PAID,
        REFUSED,
        CANCELED,
        EXPIRED,
        CHARGED_BACK
    }

    /** Situacao da transacao como a credenciadora a devolve (retorno/consulta). */
    public enum AcquirerTxStatus {
        APPROVED,
        PENDING,
        REFUSED,
        CANCELED,
        CHARGED_BACK,
        REFUNDED
    }
}
