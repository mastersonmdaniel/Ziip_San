package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.RenderedMessage;

import java.util.Optional;
import java.util.UUID;

/**
 * Portas da fatura por e-mail (ED-41), agrupadas como em
 * {@code NegativationPorts} da ED-30. Nenhuma delas inventa conteúdo de
 * outro dono: o PDF é do faturamento/fiscal, o link é do portal e o opt-in
 * é do registro de preferências/consentimentos.
 */
public final class BillEmailPorts {

    private BillEmailPorts() {
    }

    /**
     * Referência ao PDF da fatura emitido pelo faturamento (P2.5/P2.6): a
     * ED-41 anexa a REFERÊNCIA do documento já emitido — não gera PDF nem
     * altera o documento fiscal.
     */
    public record BillPdfAttachment(String documentRef, String fileName) {
        public BillPdfAttachment {
            if (documentRef == null || documentRef.isBlank() || fileName == null || fileName.isBlank()) {
                throw new IllegalArgumentException("anexo da fatura exige a referência e o nome do documento");
            }
        }
    }

    /** Porta para o documento da fatura: devolve o PDF emitido da conta, se ele existir. */
    public interface BillPdfPort {
        Optional<BillPdfAttachment> billPdf(UUID tenantId, UUID billId);
    }

    /**
     * Porta para o portal do consumidor (P3.20/P3.3): o link da fatura é
     * AUTENTICADO — gerado pelo portal com a autenticação dele; esta
     * entrega não monta URL nem token (formato é do portal).
     */
    public interface PortalLinkPort {
        String authenticatedBillLink(UUID tenantId, UUID customerId, UUID billId);
    }

    /**
     * Manifestação do morador sobre o PDF anexo, lida do registro de
     * preferências da P3.36 ligado ao consentimento da P1.3:
     * presente {@code true} = opt-in, presente {@code false} = opt-out,
     * vazio = sem manifestação registrada.
     */
    public interface BillPdfOptInReader {
        Optional<Boolean> residentPdfOptIn(UUID tenantId, UUID customerId);
    }

    /**
     * Envio do e-mail da fatura a partir do domínio do tenant, com anexo
     * opcional: a contrapartida com anexo do {@code EmailGateway} da P3.5
     * (que não tem anexo no contrato). Devolve o id da mensagem no
     * provedor, a mesma chave de correlação dos eventos da P3.5.
     */
    public interface BillEmailSender {
        String send(UUID tenantId, String fromDomain, String toAddress, RenderedMessage message,
                    Optional<BillPdfAttachment> attachment);
    }
}
