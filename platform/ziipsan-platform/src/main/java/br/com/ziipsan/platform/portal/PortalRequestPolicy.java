package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Pedido/alteracao do consumidor pelo portal (P3.3): gera protocolo da
 * P1.36 + OS interna (com aprovacao conforme o tipo) e o consumidor
 * acompanha a OS pelo protocolo. Pedido em nome do titular usa a
 * representacao legal / terceiro autorizado da P1.41 (CAD-66), com
 * escopo, validade e registro no {@code personal_data_access_log}.
 *
 * <p>Aqui vivem as travas (representacao valida e dentro do escopo,
 * registro de acesso obrigatorio, canal PORTAL). A abertura em si e das
 * portas {@link ProtocolPort}/{@link ServiceOrderPort}, cujos adaptadores
 * chamam {@code ProtocolService}/{@code ServiceOrderService} do core —
 * fiacao da integracao, nao inventada aqui.
 */
public final class PortalRequestPolicy {

    /** Representacao da P1.41: quem pede em nome do titular, com escopo e validade. */
    public record Representation(UUID representativeCustomerId, UUID holderCustomerId,
                                 Set<String> scopes, Instant validFrom, Instant validTo) {
        public Representation {
            if (representativeCustomerId == null || holderCustomerId == null
                    || scopes == null || scopes.isEmpty() || validFrom == null || validTo == null
                    || !validTo.isAfter(validFrom)) {
                throw new IllegalArgumentException("representacao exige representante, titular, escopo e validade");
            }
            scopes = Set.copyOf(scopes);
        }

        public boolean covers(String operationScope, Instant now) {
            return scopes.contains(operationScope) && !now.isBefore(validFrom) && now.isBefore(validTo);
        }
    }

    /** Rascunho do registro em personal_data_access_log (nunca documento em claro; so o indice cego). */
    public record AccessLogDraft(UUID tenantId, UUID actorCustomerId, String accessType,
                                 String entityType, UUID entityId, String documentBlindIndex,
                                 String purpose, String result, Instant accessedAt) { }

    public interface ProtocolPort {
        /** Abre o protocolo (canal PORTAL) e devolve o id. */
        UUID openProtocol(UUID tenantId, String specCode, UUID consumerUnitId, UUID requesterCustomerId);
        /** Situacao legivel pelo consumidor para acompanhar a OS pelo protocolo. */
        String trackingStatus(UUID tenantId, UUID protocolId);
    }

    public interface ServiceOrderPort {
        /** Cria a OS interna ligada ao protocolo; aprovacao conforme o tipo e do motor de OS. */
        UUID createInternalOrder(UUID tenantId, UUID protocolId, String serviceTypeCode, UUID consumerUnitId);
    }

    public record OpenedRequest(UUID protocolId, UUID serviceOrderId, AccessLogDraft accessLog) { }

    private final ProtocolPort protocols;
    private final ServiceOrderPort orders;

    public PortalRequestPolicy(ProtocolPort protocols, ServiceOrderPort orders) {
        if (protocols == null || orders == null) {
            throw new IllegalArgumentException("ports de protocolo e OS sao obrigatorias");
        }
        this.protocols = protocols;
        this.orders = orders;
    }

    /**
     * Abre pedido do proprio titular ({@code representation == null}) ou de
     * representante autorizado. Representacao fora do escopo ou vencida =
     * recusa, e a tentativa recusada tambem gera rascunho de registro.
     */
    public OpenedRequest openRequest(UUID tenantId, UUID holderCustomerId, Representation representation,
                                     String operationScope, String specCode, String serviceTypeCode,
                                     UUID consumerUnitId, String holderDocumentBlindIndex, Instant now) {
        if (tenantId == null || holderCustomerId == null || operationScope == null || operationScope.isBlank()
                || specCode == null || specCode.isBlank() || serviceTypeCode == null || serviceTypeCode.isBlank()
                || consumerUnitId == null || now == null) {
            throw new IllegalArgumentException("tenant, titular, escopo, especificacao, tipo de OS, unidade e instante sao obrigatorios");
        }
        UUID requester = holderCustomerId;
        if (representation != null) {
            if (!representation.holderCustomerId().equals(holderCustomerId)) {
                // Tentativa negada TAMBEM gera registro (revisao cruzada da
                // P3.3): negar sem rascunho deixava esta tentativa fora do
                // personal_data_access_log, contra o aceite.
                AccessLogDraft denied = new AccessLogDraft(tenantId, representation.representativeCustomerId(),
                        "PERSONAL_READ", "CONSUMER_UNIT", consumerUnitId, holderDocumentBlindIndex,
                        "portal request on behalf: " + operationScope, "DENIED", now);
                throw new RepresentationDeniedException("Representacao nao pertence a este titular.", denied);
            }
            if (!representation.covers(operationScope, now)) {
                AccessLogDraft denied = new AccessLogDraft(tenantId, representation.representativeCustomerId(),
                        "PERSONAL_READ", "CONSUMER_UNIT", consumerUnitId, holderDocumentBlindIndex,
                        "portal request on behalf: " + operationScope, "DENIED", now);
                throw new RepresentationDeniedException("Representacao fora do escopo ou da validade (P1.41).", denied);
            }
            requester = representation.representativeCustomerId();
        }
        // O solicitante do protocolo e quem de fato pede (o representante,
        // papel THIRD_PARTY da P1.36), nao o titular em cujo nome se pede.
        UUID protocolId = protocols.openProtocol(tenantId, specCode, consumerUnitId, requester);
        UUID orderId = orders.createInternalOrder(tenantId, protocolId, serviceTypeCode, consumerUnitId);
        AccessLogDraft log = new AccessLogDraft(tenantId, requester, "PERSONAL_READ",
                "CONSUMER_UNIT", consumerUnitId, holderDocumentBlindIndex,
                "portal request: " + operationScope, "FOUND", now);
        return new OpenedRequest(protocolId, orderId, log);
    }

    public String track(UUID tenantId, UUID protocolId) {
        return protocols.trackingStatus(tenantId, protocolId);
    }

    /** Recusa de representacao carregando o registro da tentativa (o chamador persiste no personal_data_access_log). */
    public static final class RepresentationDeniedException extends ApiException.Forbidden {
        private final AccessLogDraft accessLog;
        public RepresentationDeniedException(String message, AccessLogDraft accessLog) {
            super(message);
            this.accessLog = accessLog;
        }
        public AccessLogDraft accessLog() { return accessLog; }
    }
}
