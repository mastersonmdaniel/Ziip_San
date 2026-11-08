package br.com.ziipsan.commercial.fiscal.queue;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Filtros da fila (aceite da P2.33): por estado da fila, por competência e por grupo
 * de faturamento. Filtro nulo = sem restrição naquela dimensão; as três dimensões se
 * combinam por E.
 */
public record FiscalQueueQuery(FiscalQueueState state, YearMonth competence, UUID billingGroupId) {

    public static FiscalQueueQuery all() {
        return new FiscalQueueQuery(null, null, null);
    }
}
