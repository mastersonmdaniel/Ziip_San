package br.com.ziipsan.platform.portal.consumer.web;

import br.com.ziipsan.platform.portal.consumer.AccessibleBillService;
import br.com.ziipsan.platform.portal.consumer.AdhesionContractService;
import br.com.ziipsan.platform.portal.consumer.BillDocumentService;
import br.com.ziipsan.platform.portal.consumer.ConsumerAccessGuard;
import br.com.ziipsan.platform.portal.consumer.ConsumerDataChangeService;
import br.com.ziipsan.platform.portal.consumer.ConsumerHistoryService;
import br.com.ziipsan.platform.portal.consumer.ConsumerLinkService;
import br.com.ziipsan.platform.portal.consumer.HolderChangeService;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Borda fina do portal do consumidor (P3.20): historico, documentos e
 * vinculo SOMENTE por canal autenticado (conta propria ou gov.br) — o
 * guard roda ANTES de qualquer servico, em TODAS as frentes do aceite
 * (correcao D5: quitacao, formato acessivel, alteracao de dados, contrato
 * e troca de titularidade expostos no mesmo padrao das frentes originais).
 * O mapeamento HTTP fino (anotacoes/rotas) e da integracao com o BFF da
 * P3.3; esta classe existe para a regra nao depender do transporte.
 */
public final class ConsumerPortalController {

    private final ConsumerLinkService links;
    private final ConsumerHistoryService history;
    private final BillDocumentService documents;
    private final AccessibleBillService accessible;
    private final ConsumerDataChangeService dataChanges;
    private final AdhesionContractService contracts;
    private final HolderChangeService holderChanges;

    public ConsumerPortalController(ConsumerLinkService links, ConsumerHistoryService history,
                                    BillDocumentService documents, AccessibleBillService accessible,
                                    ConsumerDataChangeService dataChanges,
                                    AdhesionContractService contracts,
                                    HolderChangeService holderChanges) {
        if (links == null || history == null || documents == null || accessible == null
                || dataChanges == null || contracts == null || holderChanges == null) {
            throw new IllegalArgumentException("servicos sao obrigatorios");
        }
        this.links = links;
        this.history = history;
        this.documents = documents;
        this.accessible = accessible;
        this.dataChanges = dataChanges;
        this.contracts = contracts;
        this.holderChanges = holderChanges;
    }

    public UUID link(UUID tenantId, String subject, ConsumerAccessGuard.Channel channel,
                     String registration, String code, String holderDocument, String sourceIp) {
        ConsumerAccessGuard.requireAuthenticatedChannel(channel);
        return links.link(tenantId, subject, registration, code, holderDocument, sourceIp);
    }

    public List<ConsumerHistoryService.HistoryEntry> history(UUID tenantId, String subject,
            ConsumerAccessGuard.Channel channel, UUID unitId, YearMonth current) {
        return history.history(tenantId, subject, unitId, current, channel);
    }

    public BillDocumentService.Download danfag(UUID tenantId, String subject,
            ConsumerAccessGuard.Channel channel, UUID billId) {
        ConsumerAccessGuard.requireAuthenticatedChannel(channel);
        return documents.danfagPdf(tenantId, subject, billId);
    }

    public BillDocumentService.Download xml(UUID tenantId, String subject,
            ConsumerAccessGuard.Channel channel, UUID billId) {
        ConsumerAccessGuard.requireAuthenticatedChannel(channel);
        return documents.fiscalXml(tenantId, subject, billId);
    }

    public String settlement(UUID tenantId, String subject,
            ConsumerAccessGuard.Channel channel, UUID billId) {
        ConsumerAccessGuard.requireAuthenticatedChannel(channel);
        return documents.settlement(tenantId, subject, billId);
    }

    public String accessibleBill(UUID tenantId, String subject,
            ConsumerAccessGuard.Channel channel, UUID billId, boolean requested) {
        ConsumerAccessGuard.requireAuthenticatedChannel(channel);
        return accessible.accessibleBillJson(tenantId, subject, billId, requested);
    }

    public String requestDataChange(UUID tenantId, String subject,
            ConsumerAccessGuard.Channel channel, UUID unitId,
            String changeKind, String requestedDataJson) {
        ConsumerAccessGuard.requireAuthenticatedChannel(channel);
        return dataChanges.requestChange(tenantId, subject, unitId, changeKind, requestedDataJson);
    }

    public AdhesionContractService.DeliveryRecord deliverContract(UUID tenantId,
            ConsumerAccessGuard.Channel channel, UUID unitId,
            AdhesionContractService.DeliveryTrigger trigger) {
        ConsumerAccessGuard.requireAuthenticatedChannel(channel);
        return contracts.deliverOnTrigger(tenantId, unitId, trigger);
    }

    public void acknowledgeContract(UUID tenantId, ConsumerAccessGuard.Channel channel,
                                    UUID unitId, String contractVersion) {
        ConsumerAccessGuard.requireAuthenticatedChannel(channel);
        contracts.acknowledge(tenantId, unitId, contractVersion);
    }

    public int holderChange(UUID tenantId, ConsumerAccessGuard.Channel channel, UUID unitId) {
        ConsumerAccessGuard.requireAuthenticatedChannel(channel);
        return holderChanges.onHolderChange(tenantId, unitId);
    }
}
