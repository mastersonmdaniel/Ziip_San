package br.com.ziipsan.commercial.accounting.event;

/** Chaves de evento contabil (ED-32). Recuperacao de credito e evento proprio (vem da P3.1: pagamento de conta prescrita, CC art. 882). */
public enum AccountingEventKey {
    BILL_ISSUED, PAYMENT_RECEIVED, CREDIT_RECOVERY, BILL_CANCELLED, BILL_PARCELLED,
    PAYMENT_REFUNDED, TAX_WITHHELD, DOUBTFUL_PROVISION, DOUBTFUL_REVERSAL
}
