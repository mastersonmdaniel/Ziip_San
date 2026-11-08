package br.com.ziipsan.core.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Livro de movimentacoes (ledger imutavel) do almoxarifado: toda operacao
 * da ED-58 grava uma linha aqui E uma linha de auditoria. {@code qty} e
 * sempre positiva; o efeito no saldo esta no {@link Type}.
 */
public record StockMovement(UUID id, UUID tenantId, Type type, String itemCode, String locationCode,
                            BigDecimal qty, BigDecimal unitCost, String reference, UUID actorId,
                            String reason, Instant at) {
    public enum Type {
        ENTRY,            // entrada (manual ou NF-e): fisico +
        ADJUSTMENT,       // ajuste com justificativa: fisico +/- (sinal em qtySign)
        RESERVATION,      // aprovacao: reservado +
        RESERVATION_RELEASE, // cancelamento/liberacao: reservado -
        DELIVERY,         // entrega registrada: reservado - e fisico -
        RETURN,           // sobra devolvida no encerramento: fisico +
        WRITE_OFF_USED,   // baixa do usado alem do entregue (automatica, piso zero)
        FIELD_WRITE_OFF,  // baixa da sincronizacao de campo ED-20 (automatica, piso zero)
        TRANSFER_OUT,     // saida para transito: fisico origem -
        TRANSFER_IN,      // chegada do transito: fisico destino +
        INVENTORY_COUNT,  // conferencia de inventario: fisico := contado (diferenca registrada)
        SCRAP             // sucata de serializado: fisico -
    }

    public StockMovement {
        Objects.requireNonNull(id, "id do movimento e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(type, "tipo do movimento e obrigatorio");
        if (itemCode == null || itemCode.isBlank()) throw new IllegalArgumentException("item do movimento e obrigatorio");
        if (locationCode == null || locationCode.isBlank()) throw new IllegalArgumentException("local do movimento e obrigatorio");
        if (qty == null || qty.signum() == 0) throw new IllegalArgumentException("quantidade do movimento nao pode ser nula/zero");
        // So ADJUSTMENT e INVENTORY_COUNT carregam sinal (diferenca conferida); os demais tipos sao sempre positivos.
        if (type != Type.ADJUSTMENT && type != Type.INVENTORY_COUNT && qty.signum() < 0)
            throw new IllegalArgumentException("quantidade negativa so em ajuste/contagem: " + type);
        Objects.requireNonNull(actorId, "ator do movimento e obrigatorio");
        Objects.requireNonNull(at, "instante do movimento e obrigatorio");
    }
}
