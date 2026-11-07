package br.com.ziipsan.platform.central;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Instantâneo AGREGADO da fila fiscal de um tenant, como a Central a
 * consome (só contagens por estado e a maior idade — V9_1/ADR-0018).
 * Os estados são os 9 do documento fiscal da P2.6 ({@code NfageStatus},
 * dicionário 23b §3.13): RESERVED, SIGNED, TRANSMITTED, AUTHORIZED,
 * REJECTED, PENDING_RECONCILIATION, CANCELLED, REPLACED, VOIDED.
 *
 * <p><b>Fila</b> = documentos aguardando desfecho, isto é, os vivos de
 * {@code NfageStateMachine.isLive} menos os já autorizados:
 * RESERVED + SIGNED + TRANSMITTED + PENDING_RECONCILIATION. REJECTED é
 * terminal (a correção é documento novo, P2.6) e AUTHORIZED já teve
 * desfecho: nenhum dos dois é fila. A fila OPERADA pelo tenant
 * (rejeições, contingência, reenvio) é da P2.33 — aqui há só a contagem
 * agregada da operadora.
 *
 * <p>Estado desconhecido, contagem negativa ou estado ausente do mapa
 * são erro explícito: o produtor declara os 9 estados (zero inclusive),
 * para a ausência não ser lida como zero em silêncio.
 */
public record FiscalQueueSnapshot(UUID tenantId, Map<String, Long> countByStatus, long oldestPendingAgeSeconds) {

    public static final Set<String> DOCUMENT_STATUSES = Set.of(
            "RESERVED", "SIGNED", "TRANSMITTED", "AUTHORIZED", "REJECTED",
            "PENDING_RECONCILIATION", "CANCELLED", "REPLACED", "VOIDED");

    /** Estados que compõem a fila (aguardando desfecho). */
    public static final Set<String> QUEUE_STATUSES = Set.of(
            "RESERVED", "SIGNED", "TRANSMITTED", "PENDING_RECONCILIATION");

    public FiscalQueueSnapshot {
        Objects.requireNonNull(tenantId, "tenant_id é obrigatório no instantâneo fiscal");
        if (countByStatus == null) {
            throw new IllegalArgumentException("count_by_status é obrigatório no instantâneo fiscal");
        }
        for (String status : DOCUMENT_STATUSES) {
            Long count = countByStatus.get(status);
            if (count == null) {
                throw new IllegalArgumentException(
                        "Contagem ausente para o estado " + status + " no instantâneo fiscal: "
                                + "o produtor declara os 9 estados (zero inclusive)");
            }
            if (count < 0) {
                throw new IllegalArgumentException("Contagem negativa para o estado " + status + ": " + count);
            }
        }
        for (String status : countByStatus.keySet()) {
            if (!DOCUMENT_STATUSES.contains(status)) {
                throw new IllegalArgumentException("Estado fiscal desconhecido no instantâneo: " + status);
            }
        }
        if (oldestPendingAgeSeconds < 0) {
            throw new IllegalArgumentException("oldest_pending_age_s negativo: " + oldestPendingAgeSeconds);
        }
        countByStatus = Map.copyOf(countByStatus);
    }

    /** Tamanho da fila: soma dos estados aguardando desfecho. */
    public long queueSize() {
        long total = 0;
        for (String status : QUEUE_STATUSES) {
            total += countByStatus.get(status);
        }
        return total;
    }
}
