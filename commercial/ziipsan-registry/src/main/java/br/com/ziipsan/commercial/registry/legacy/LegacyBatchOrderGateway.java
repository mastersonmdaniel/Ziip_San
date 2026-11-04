package br.com.ziipsan.commercial.registry.legacy;

import java.util.List;
import java.util.UUID;

/**
 * Porta da OS interna de lote (P1.2, P1.31/ADR-0039): cada carga gera uma
 * OS interna que referencia as unidades aplicadas (um alvo
 * {@code CONSUMER_UNIT} por unidade) e aplica o efeito de confirmação pela
 * regra da P1.31 — efeito aplicado uma vez por alvo, na mesma transação
 * que marca o efeito, com encerramento só depois de todos os efeitos
 * aplicados. Correção posterior é por OS inversa do lote
 * ({@code reverses_order_id}), nunca por reescrita.
 *
 * <p>Reaplicação: uma ordem de tentativa anterior que não fechou fica
 * como está (histórico da tentativa, sem apagar); a nova tentativa abre
 * uma ordem nova e o lote aponta para ela. A finalização da linhagem
 * pelo manipulador do efeito é idempotente, então a unidade nunca é
 * confirmada duas vezes.
 */
public interface LegacyBatchOrderGateway {

    record OpenedOrder(UUID orderId, String status) {
    }

    /**
     * Abre a OS de lote para as unidades aplicadas, executa os efeitos de
     * confirmação e a encerra como executada. Devolve a ordem fechada.
     */
    OpenedOrder openBatchOrder(LegacyBatch batch, List<StagedUnit> appliedUnits, UUID actorId);
}
