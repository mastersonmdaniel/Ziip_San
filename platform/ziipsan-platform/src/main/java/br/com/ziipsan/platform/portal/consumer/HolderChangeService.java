package br.com.ziipsan.platform.portal.consumer;

import java.time.Instant;
import java.util.UUID;

/**
 * Troca de titularidade (P3.20): REVOGA todos os vinculos da unidade e
 * INVALIDA o codigo de vinculacao vigente — o novo titular recebe codigo
 * novo (geracao e impressao sao da fatura/P3.3) e o antigo nao vincula
 * mais ninguem. A revogacao e registrada com o motivo.
 */
public final class HolderChangeService {

    public interface RevocationPort {
        int revokeAllLinksOfUnit(UUID tenantId, UUID unitId, String reason, Instant at);
        void invalidateLinkingCode(UUID tenantId, UUID unitId, Instant at);
    }

    public static final String REASON_HOLDER_CHANGE = "HOLDER_CHANGE";

    private final RevocationPort port;

    public HolderChangeService(RevocationPort port) {
        if (port == null) {
            throw new IllegalArgumentException("port e obrigatoria");
        }
        this.port = port;
    }

    /** @return quantos vinculos foram revogados */
    public int onHolderChange(UUID tenantId, UUID unitId) {
        if (tenantId == null || unitId == null) {
            throw new IllegalArgumentException("tenant e unidade sao obrigatorios");
        }
        Instant now = Instant.now();
        int revoked = port.revokeAllLinksOfUnit(tenantId, unitId, REASON_HOLDER_CHANGE, now);
        port.invalidateLinkingCode(tenantId, unitId, now);
        return revoked;
    }
}
