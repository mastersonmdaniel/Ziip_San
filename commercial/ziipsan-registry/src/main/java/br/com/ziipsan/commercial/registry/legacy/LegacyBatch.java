package br.com.ziipsan.commercial.registry.legacy;

import java.util.UUID;

/**
 * Um lote de carga do legado (P1.2): uma execução do motor sobre uma fonte,
 * com um mapeamento, identificada por (sistema de origem, chave externa).
 * A chave externa é o que torna a reexecução idempotente: recarregar a
 * mesma chave devolve este lote, sem duplicar staging, unidades nem OS.
 */
public record LegacyBatch(
        UUID id,
        String sourceSystem,
        String mappingName,
        String mappingVersion,
        String externalBatchKey,
        String sourceKind,
        Status status,
        UUID batchOrderId) {

    public enum Status { STAGED, APPLIED }

    public boolean isApplied() {
        return status == Status.APPLIED;
    }
}
