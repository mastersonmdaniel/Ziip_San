package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.api.ApiException;

import java.util.Optional;
import java.util.UUID;

/**
 * 2a via do titular autenticado (P3.3): PDF da conta em aberto e Pix copia
 * e cola da P3.2, SOMENTE para unidade vinculada a conta do portal (o
 * vinculo em si e da P3.20). Download e registrado pelo chamador no
 * {@code personal_data_access_log} (rascunho em {@link PortalRequestPolicy}).
 */
public final class SecondCopyService {

    public record SecondCopy(UUID billId, byte[] pdf, String pixCopyPaste) { }

    public interface OpenBillPort {
        boolean isUnitLinkedToConsumer(UUID tenantId, String consumerSubject, UUID unitId);
        Optional<UUID> openBillId(UUID tenantId, UUID unitId);
        byte[] secondCopyPdf(UUID tenantId, UUID billId);
        /** Pix copia e cola da cobranca da P3.2 para a conta. */
        Optional<String> pixCopyPaste(UUID tenantId, UUID billId);
    }

    private final OpenBillPort port;

    public SecondCopyService(OpenBillPort port) {
        if (port == null) {
            throw new IllegalArgumentException("port e obrigatoria");
        }
        this.port = port;
    }

    public SecondCopy secondCopy(UUID tenantId, String consumerSubject, UUID unitId) {
        if (tenantId == null || consumerSubject == null || consumerSubject.isBlank() || unitId == null) {
            throw new IllegalArgumentException("tenant, consumidor e unidade sao obrigatorios");
        }
        if (!port.isUnitLinkedToConsumer(tenantId, consumerSubject, unitId)) {
            throw new ApiException.Forbidden("Unidade nao vinculada a esta conta do portal.");
        }
        UUID billId = port.openBillId(tenantId, unitId)
                .orElseThrow(() -> new ApiException.NotFound("Nenhuma conta em aberto para a unidade."));
        byte[] pdf = port.secondCopyPdf(tenantId, billId);
        if (pdf == null || pdf.length == 0) {
            throw new IllegalStateException("2a via sem PDF gerado para a conta " + billId);
        }
        String pix = port.pixCopyPaste(tenantId, billId)
                .orElseThrow(() -> new IllegalStateException("conta sem Pix copia e cola da P3.2: " + billId));
        return new SecondCopy(billId, pdf, pix);
    }
}
