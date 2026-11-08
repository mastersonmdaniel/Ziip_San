package br.com.ziipsan.platform.portal.consumer;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Conta em formato acessivel A PEDIDO (P3.20; indicador da P1.3, RAS-10):
 * quando o consumidor pede (ou o indicador da unidade esta ativo), a conta
 * sai em representacao estruturada e legivel por tecnologia assistiva
 * (JSON via {@link Json} do core), nunca so como imagem/PDF escaneado.
 * Sem pedido e sem indicador, o pedido e recusado: formato acessivel e
 * direito sob demanda, nao um segundo canal de download.
 */
public final class AccessibleBillService {

    public record AccessibleBill(UUID billId, String referenceLabel, String amountLabel,
                                 String dueDateLabel, String consumptionLabel) { }

    public interface AccessiblePort {
        boolean isUnitLinkedToConsumer(UUID tenantId, String consumerSubject, UUID unitId);
        boolean accessibilityIndicatorActive(UUID tenantId, UUID unitId);
        Optional<AccessibleBill> accessibleBill(UUID tenantId, UUID billId);
        Optional<UUID> unitOfBill(UUID tenantId, UUID billId);
    }

    public interface AccessLogPort {
        void recordDownload(UUID tenantId, String consumerSubject, UUID billId, String documentKind);
    }

    private final AccessiblePort port;
    private final AccessLogPort accessLog;

    public AccessibleBillService(AccessiblePort port, AccessLogPort accessLog) {
        if (port == null || accessLog == null) {
            throw new IllegalArgumentException("ports sao obrigatorias");
        }
        this.port = port;
        this.accessLog = accessLog;
    }

    public String accessibleBillJson(UUID tenantId, String consumerSubject, UUID billId, boolean requested) {
        if (tenantId == null || consumerSubject == null || consumerSubject.isBlank() || billId == null) {
            throw new IllegalArgumentException("tenant, consumidor e conta sao obrigatorios");
        }
        // D7: mesma uniformizacao do BillDocumentService — conta
        // inexistente responde igual a conta nao vinculada (403).
        UUID unitId = port.unitOfBill(tenantId, billId)
                .orElseThrow(() -> new ApiException.Forbidden(
                        "Conta de unidade nao vinculada a esta conta do portal."));
        if (!port.isUnitLinkedToConsumer(tenantId, consumerSubject, unitId)) {
            throw new ApiException.Forbidden("Conta de unidade nao vinculada a esta conta do portal.");
        }
        if (!requested && !port.accessibilityIndicatorActive(tenantId, unitId)) {
            throw new ApiException.BadRequest("accessible-format-not-requested",
                    "Formato acessivel e fornecido a pedido ou pelo indicador da unidade (RAS-10).");
        }
        AccessibleBill bill = port.accessibleBill(tenantId, billId)
                .orElseThrow(() -> new ApiException.NotFound("Conta sem representacao acessivel."));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("billId", bill.billId().toString());
        body.put("reference", bill.referenceLabel());
        body.put("amount", bill.amountLabel());
        body.put("dueDate", bill.dueDateLabel());
        body.put("consumption", bill.consumptionLabel());
        accessLog.recordDownload(tenantId, consumerSubject, billId, "ACCESSIBLE_BILL");
        return Json.write(body);
    }
}
