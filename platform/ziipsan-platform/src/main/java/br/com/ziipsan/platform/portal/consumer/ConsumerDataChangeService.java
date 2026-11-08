package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;

import java.util.UUID;

/**
 * Alteracao de dados pelo consumidor (P3.20): segue o protocolo e a OS
 * interna da P3.3 — NENHUMA alteracao e aplicada direto pelo portal; o
 * pedido abre protocolo de atendimento (P1.36) e OS interna, e a
 * efetivacao e do atendimento. Unidade nao vinculada nem abre pedido.
 */
public final class ConsumerDataChangeService {

    public interface RequestPort {
        boolean isUnitLinkedToConsumer(UUID tenantId, String consumerSubject, UUID unitId);
        /** Abre protocolo P1.36 + OS interna (adaptador sobre a P3.3/P1.36). @return numero do protocolo */
        String openProtocolAndInternalOrder(UUID tenantId, String consumerSubject, UUID unitId,
                                            String changeKind, String requestedDataJson);
    }

    private final RequestPort port;

    public ConsumerDataChangeService(RequestPort port) {
        if (port == null) {
            throw new IllegalArgumentException("port e obrigatoria");
        }
        this.port = port;
    }

    public String requestChange(UUID tenantId, String consumerSubject, UUID unitId,
                                String changeKind, String requestedDataJson) {
        if (tenantId == null || consumerSubject == null || consumerSubject.isBlank() || unitId == null
                || changeKind == null || changeKind.isBlank()
                || requestedDataJson == null || requestedDataJson.isBlank()) {
            throw new IllegalArgumentException("tenant, consumidor, unidade, tipo e dados sao obrigatorios");
        }
        if (!port.isUnitLinkedToConsumer(tenantId, consumerSubject, unitId)) {
            throw new ApiException.Forbidden("Unidade nao vinculada a esta conta do portal.");
        }
        return port.openProtocolAndInternalOrder(tenantId, consumerSubject, unitId, changeKind, requestedDataJson);
    }
}
