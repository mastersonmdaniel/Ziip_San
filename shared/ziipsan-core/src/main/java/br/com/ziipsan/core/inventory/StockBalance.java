package br.com.ziipsan.core.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Saldo de um item num local: FISICO, RESERVADO e DISPONIVEL
 * (= fisico - reservado), com CUSTO MEDIO ponderado movel.
 *
 * <p>Invariantes desta entrega:
 * <ul>
 *   <li>reserva (aprovacao) mexe SO no reservado: o fisico nao muda e o
 *       disponivel PODE ficar negativo — e o "saldo negativo" do aceite,
 *       que gera alerta e entra no relatorio diario de negativos;</li>
 *   <li>baixa AUTOMATICA (encerramento e sincronizacao de campo) nunca
 *       empurra o FISICO abaixo de zero: o servico limita a baixa ao
 *       fisico existente e registra a falta como pendencia de
 *       regularizacao (ver WarehouseService/FieldWriteOffService);</li>
 *   <li>fisico negativo nunca nasce de caminho AUTOMATICO: ele so pode
 *       nascer de ato HUMANO registrado e justificado — a ENTREGA de
 *       requisicao aprovada com falta conhecida pelo aprovador, o AJUSTE
 *       manual justificado ({@code adjust}) e a CONTAGEM de inventario
 *       justificada ({@code inventoryCount}) que apura fisico menor que
 *       o registrado (correcao m1 da revisao ED-58: a frase anterior,
 *       "so nasce de entrega", era imprecisa).</li>
 * </ul>
 */
public final class StockBalance {
    private final String itemCode;
    private final String locationCode;
    private BigDecimal physical = BigDecimal.ZERO;
    private BigDecimal reserved = BigDecimal.ZERO;
    private BigDecimal averageCost = BigDecimal.ZERO;

    public StockBalance(String itemCode, String locationCode) {
        this.itemCode = itemCode; this.locationCode = locationCode;
    }

    public String itemCode() { return itemCode; }
    public String locationCode() { return locationCode; }
    public BigDecimal physical() { return physical; }
    public BigDecimal reserved() { return reserved; }
    public BigDecimal available() { return physical.subtract(reserved); }
    public BigDecimal averageCost() { return averageCost; }
    public BigDecimal stockValue() { return physical.multiply(averageCost).setScale(2, RoundingMode.HALF_UP); }

    /** Entrada com custo: recalcula o custo medio ponderado. Base negativa/zerada: o custo da entrada vira o medio. */
    public void applyEntry(BigDecimal qty, BigDecimal unitCost) {
        requirePositive(qty, "quantidade de entrada");
        if (unitCost == null || unitCost.signum() < 0) throw new IllegalArgumentException("custo unitario da entrada nao pode ser nulo/negativo");
        if (physical.signum() <= 0) {
            averageCost = unitCost.setScale(4, RoundingMode.HALF_UP);
        } else {
            BigDecimal totalQty = physical.add(qty);
            averageCost = physical.multiply(averageCost).add(qty.multiply(unitCost))
                    .divide(totalQty, 4, RoundingMode.HALF_UP);
        }
        physical = physical.add(qty);
    }

    /** Ajuste de inventario/ajuste manual: sinal livre, custo medio intocado (sem nova informacao de custo). */
    public void applyAdjustment(BigDecimal signedQty) {
        if (signedQty == null || signedQty.signum() == 0) throw new IllegalArgumentException("ajuste com quantidade zero nao movimenta");
        physical = physical.add(signedQty);
    }

    public void applyReservation(BigDecimal qty) { requirePositive(qty, "quantidade da reserva"); reserved = reserved.add(qty); }
    public void applyReservationRelease(BigDecimal qty) {
        requirePositive(qty, "quantidade da liberacao");
        if (qty.compareTo(reserved) > 0) throw new IllegalStateException("liberacao maior que o reservado: " + itemCode + "@" + locationCode);
        reserved = reserved.subtract(qty);
    }

    /** Entrega: consome a reserva e o fisico na mesma quantidade. */
    public void applyDelivery(BigDecimal qty) {
        requirePositive(qty, "quantidade da entrega");
        if (qty.compareTo(reserved) > 0) throw new IllegalStateException("entrega maior que o reservado: " + itemCode + "@" + locationCode);
        reserved = reserved.subtract(qty);
        physical = physical.subtract(qty);
    }

    /** Devolucao da sobra no encerramento: volta ao fisico (a reserva ja foi consumida na entrega). */
    public void applyReturn(BigDecimal qty) { requirePositive(qty, "quantidade devolvida"); physical = physical.add(qty); }

    /**
     * Baixa automatica com piso zero: baixa no maximo o fisico existente e
     * devolve a quantidade EFETIVAMENTE baixada; a diferenca e falta que o
     * chamador registra como pendencia — nunca saldo fisico negativo.
     */
    public BigDecimal applyAutomaticWriteOff(BigDecimal qty) {
        requirePositive(qty, "quantidade da baixa");
        BigDecimal writable = qty.min(physical.max(BigDecimal.ZERO));
        physical = physical.subtract(writable);
        return writable;
    }

    static void requirePositive(BigDecimal q, String what) {
        if (q == null || q.signum() <= 0) throw new IllegalArgumentException(what + " deve ser positiva");
    }
}
