package br.com.ziipsan.grs.mtr.integration;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E7/E8.
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Submissão assistida (ED-104, E7/E8): monta a projecção interna do MTR e tenta a porta.
 * Sem adapter registado, recusa ruidosamente e o MTR permanece NOT_SUBMITTED/PENDING, sem
 * protocolo, sem resposta e sem número oficial — provado por teste. Com adapter (integração
 * futura, pendência P-ED104-05), o recibo grava número oficial, protocolo e estado SYNCED:
 * é o único caminho que pode produzir SYNCED.
 */
public final class GrsSubmissionService {

    private final GrsSubmissionPorts.AdapterRegistry registry;

    public GrsSubmissionService(GrsSubmissionPorts.AdapterRegistry registry) {
        this.registry = registry;
    }

    public GrsSubmissionPorts.SubmissionPayload payloadOf(WasteMtr mtr) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("internalNumber", mtr.getMtrNumber());
        fields.put("status", String.valueOf(mtr.getStatus()));
        fields.put("quantityKg", mtr.getQuantityKg() == null ? null : mtr.getQuantityKg().toPlainString());
        fields.put("wasteClassCode", mtr.getWasteClass() == null ? null : mtr.getWasteClass().getCode());
        fields.put("officialLayout", "[SEM FONTE] leiaute oficial do WS não confirmado (P-ED104-01)");
        return new GrsSubmissionPorts.SubmissionPayload(mtr.getMtrNumber(), "MTR", fields);
    }

    /** @throws GrsSubmissionPorts.SubmissionRefusedException sem adapter IMA registado */
    public GrsSubmissionPorts.SubmissionReceipt submitToIma(WasteMtr mtr) {
        GrsSubmissionPorts.ImaSubmissionPort port = registry.ima().orElseThrow(() ->
                new GrsSubmissionPorts.SubmissionRefusedException(
                        "submissão ao Sistema MTR do IMA recusada: nenhum adapter registado "
                                + "(leiaute oficial [SEM FONTE], P-ED104-01). O MTR "
                                + mtr.getMtrNumber() + " permanece não submetido."));
        GrsSubmissionPorts.SubmissionReceipt receipt = port.submitMtr(payloadOf(mtr));
        applyReceipt(mtr, receipt);
        return receipt;
    }

    /** @throws GrsSubmissionPorts.SubmissionRefusedException sem adapter SINIR registado */
    public GrsSubmissionPorts.SubmissionReceipt syncWithSinir(WasteMtr mtr) {
        GrsSubmissionPorts.SinirSyncPort port = registry.sinir().orElseThrow(() ->
                new GrsSubmissionPorts.SubmissionRefusedException(
                        "sincronização com o SINIR recusada: nenhum adapter registado "
                                + "(manual do WS federal não é público — [SEM FONTE], P-ED104-01; "
                                + "a chamada removida na P0.25 não é reposta). O MTR "
                                + mtr.getMtrNumber() + " permanece não submetido."));
        GrsSubmissionPorts.SubmissionReceipt receipt = port.syncMtr(payloadOf(mtr));
        applyReceipt(mtr, receipt);
        return receipt;
    }

    private void applyReceipt(WasteMtr mtr, GrsSubmissionPorts.SubmissionReceipt receipt) {
        mtr.setOfficialMtrNumber(receipt.officialNumber());
        mtr.setSinirProtocol(receipt.protocol());
        mtr.setSinirSyncStatus(WasteMtr.SinirSyncStatus.SYNCED);
        mtr.setSinirSyncAt(OffsetDateTime.now());
        mtr.setSinirResponse(Json.write(Map.of(
                "system", receipt.systemCode(),
                "officialNumber", receipt.officialNumber(),
                "protocol", receipt.protocol())));
    }
}
