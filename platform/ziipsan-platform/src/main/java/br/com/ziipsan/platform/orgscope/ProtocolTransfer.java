package br.com.ziipsan.platform.orgscope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Um trâmite gravado (espelho de {@code protocol_transfers}, V1_8): a
 * trilha só de inserção de que o protocolo saiu de uma unidade para outra,
 * por quem, por quê e com QUAIS OS acompanhando ({@code movedOrderIds} —
 * gravado em JSONB pela classe Json do core na loja JDBC). A primeira
 * atribuição de unidade ao protocolo NÃO é trâmite: ela nasce em
 * {@code protocol_org_assignments} sem linha aqui (from = null é reservado
 * a uma futura importação de protocolos já tramitados, fora desta tarefa).
 */
public record ProtocolTransfer(UUID id, UUID tenantId, UUID protocolId, UUID fromOrgUnitId,
                               UUID toOrgUnitId, UUID movedBy, String reason,
                               List<UUID> movedOrderIds, Instant createdAt) {

    public ProtocolTransfer {
        if (id == null || tenantId == null || protocolId == null || toOrgUnitId == null
                || movedBy == null || createdAt == null) {
            throw new IllegalArgumentException("trâmite exige id, tenant, protocolo, destino, autor e criação");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("trâmite exige o motivo");
        }
        if (toOrgUnitId.equals(fromOrgUnitId)) {
            throw new IllegalArgumentException("trâmite exige destino diferente da origem");
        }
        movedOrderIds = movedOrderIds == null ? List.of() : List.copyOf(movedOrderIds);
    }
}
