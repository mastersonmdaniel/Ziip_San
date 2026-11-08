package br.com.ziipsan.commercial.fiscal.queue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta da trilha de ações do operador (tabela da V4_11). A unicidade da
 * {@code idempotencyKey} também é garantida no banco (índice único por tenant); as
 * duas camadas se somam, como na emissão da P2.6.
 */
public interface FiscalOperatorTrailPort {

    void record(FiscalOperatorAction action);

    void updateStatus(UUID tenantId, UUID actionId, FiscalOperatorActionStatus status, String protocol);

    Optional<FiscalOperatorAction> findByIdempotencyKey(UUID tenantId, String idempotencyKey);

    /** Pedido de cancelamento ainda pendente do documento, se existir. */
    Optional<FiscalOperatorAction> findPendingCancellation(UUID tenantId, UUID fiscalDocumentId);

    List<FiscalOperatorAction> findByDocument(UUID tenantId, UUID fiscalDocumentId);
}
