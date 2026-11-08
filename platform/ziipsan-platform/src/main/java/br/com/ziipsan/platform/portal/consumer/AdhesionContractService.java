package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato de adesao e regulamento da prestacao do tenant (P3.20; NR 11,
 * arts. 18 e 53): publicados e VERSIONADOS. Na LIGACAO NOVA e na TROCA DE
 * TITULARIDADE, a versao VIGENTE do contrato de adesao e entregue ao
 * titular no portal E no comprovante do protocolo, e a versao entregue e a
 * CIENCIA ficam registradas na unidade consumidora.
 */
public final class AdhesionContractService {

    public enum DeliveryTrigger { NEW_CONNECTION, HOLDER_CHANGE }

    public enum DeliveryChannel { PORTAL, PROTOCOL_RECEIPT }

    public record PublishedContract(String kind, String version, String contentHash, Instant publishedAt) { }

    public record DeliveryRecord(UUID unitId, String contractVersion, DeliveryTrigger trigger,
                                 DeliveryChannel channel, Instant deliveredAt, Instant acknowledgedAt) { }

    public interface ContractPort {
        Optional<PublishedContract> currentAdhesionContract(UUID tenantId);
        Optional<PublishedContract> currentServiceRegulation(UUID tenantId);
        void recordDelivery(UUID tenantId, DeliveryRecord record);
        Optional<DeliveryRecord> deliveryOf(UUID tenantId, UUID unitId, String contractVersion);
        /**
         * Ciencia: UPDATE de {@code acknowledged_at} na(s) linha(s) de
         * entrega EXISTENTE(S) de (tenant, unidade, versao) — correcao D3:
         * a ciencia NUNCA insere uma nova linha de entrega.
         */
        void acknowledgeDelivery(UUID tenantId, UUID unitId, String contractVersion, Instant acknowledgedAt);
    }

    private final ContractPort port;

    public AdhesionContractService(ContractPort port) {
        if (port == null) {
            throw new IllegalArgumentException("port e obrigatoria");
        }
        this.port = port;
    }

    /** Publicacao exige contrato E regulamento vigentes; sem um dos dois, nada se entrega como vigente. */
    public PublishedContract requirePublishedAdhesion(UUID tenantId) {
        PublishedContract adhesion = port.currentAdhesionContract(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "contrato de adesao vigente nao publicado para o tenant"));
        if (port.currentServiceRegulation(tenantId).isEmpty()) {
            throw new IllegalStateException("regulamento da prestacao vigente nao publicado para o tenant");
        }
        return adhesion;
    }

    /** Entrega nos DOIS canais (portal + comprovante do protocolo) e registra versao na unidade. */
    public DeliveryRecord deliverOnTrigger(UUID tenantId, UUID unitId, DeliveryTrigger trigger) {
        if (tenantId == null || unitId == null || trigger == null) {
            throw new IllegalArgumentException("tenant, unidade e gatilho sao obrigatorios");
        }
        PublishedContract current = requirePublishedAdhesion(tenantId);
        Instant now = Instant.now();
        port.recordDelivery(tenantId, new DeliveryRecord(
                unitId, current.version(), trigger, DeliveryChannel.PORTAL, now, null));
        DeliveryRecord receipt = new DeliveryRecord(
                unitId, current.version(), trigger, DeliveryChannel.PROTOCOL_RECEIPT, now, null);
        port.recordDelivery(tenantId, receipt);
        return receipt;
    }

    /** Ciencia do titular registrada contra a versao entregue (nunca contra "a vigente" generica). */
    public void acknowledge(UUID tenantId, UUID unitId, String contractVersion) {
        if (contractVersion == null || contractVersion.isBlank()) {
            throw new IllegalArgumentException("versao do contrato e obrigatoria para a ciencia");
        }
        // D3: exige a entrega existente e preenche acknowledged_at NELA
        // (UPDATE pela porta), sem inserir copia como nova entrega.
        port.deliveryOf(tenantId, unitId, contractVersion)
                .orElseThrow(() -> new ApiException.BadRequest("contract-delivery-not-found",
                        "Nao ha entrega registrada desta versao para a unidade."));
        port.acknowledgeDelivery(tenantId, unitId, contractVersion, Instant.now());
    }
}
