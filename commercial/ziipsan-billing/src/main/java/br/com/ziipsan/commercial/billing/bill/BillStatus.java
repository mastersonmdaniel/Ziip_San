package br.com.ziipsan.commercial.billing.bill;

/**
 * Estados da conta (relatório 02, seção 5.3, "DebitoCreditoSituacao"). Os nomes são próprios; o
 * número entre parênteses é o código do estado no sistema legado, só para quem for migrar dados.
 *
 * <p>{@link #live()} define quais estados ocupam o par (unidade, referência): é o que o índice único
 * parcial {@code idx_bills_unit_reference_live} do banco usa para garantir idempotência. Conta
 * cancelada, cancelada por retificação ou com erro de processamento libera o par (uma retificação
 * precisa conviver com a conta que ela substitui; um erro precisa poder ser reprocessado).
 */
public enum BillStatus {
    NORMAL(true),                       // 0
    RECTIFIED(true),                    // 1
    ADDED(true),                        // 2  incluída manualmente
    CANCELLED(false),                   // 3
    CANCELLED_BY_RECTIFICATION(false),  // 4
    INSTALLMENT(true),                  // 5  parcelada
    CARRIED_FORWARD(true),              // 6  arrastada
    INSTALLMENT_DOWN_PAYMENT(true),     // 7  entrada de parcelamento
    PRESCRIBED_DEBT(true),              // 8  débito prescrito
    PRE_BILLED(true),                   // 9  pré-faturada (impressão simultânea)
    CREDIT_CARD(true),                  // 10
    PROCESSING_ERROR(false),            // 11
    PRESCRIBED_ADDED_BILLS(true),       // 12
    PAID(true);                         // 13

    private final boolean live;

    BillStatus(boolean live) {
        this.live = live;
    }

    /** Ocupa o par (unidade, referência)? */
    public boolean live() {
        return live;
    }
}
