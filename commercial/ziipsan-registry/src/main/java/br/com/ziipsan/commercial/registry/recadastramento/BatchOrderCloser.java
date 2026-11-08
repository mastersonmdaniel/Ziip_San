package br.com.ziipsan.commercial.registry.recadastramento;

import java.util.UUID;

/**
 * Encerramento executado da OS de lote do recadastramento (ED-16), como
 * porta: executar a OS dispara, no motor da P1.31, o efeito declarado
 * por alvo — aplicado uma unica vez por alvo —, cuja confirmacao volta
 * ao servico pelo manipulador de confirmacao do gateway de lote
 * ({@code BatchOrderGateway.CorrectionConfirmationHandler} da P3.32,
 * ligado na fiacao ao {@code RecadastramentoService.onBatchEffectConfirmed}).
 * A implementacao de producao e o
 * {@code ServiceOrderRecadastramentoBatchGateway} desta entrega.
 */
public interface BatchOrderCloser {

    void executeAndClose(UUID orderId, UUID actorId);
}
