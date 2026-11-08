package br.com.ziipsan.core.backoffice;

import java.util.Objects;
import java.util.UUID;

/**
 * Anexos da OS (ED-36; POC-46). O arquivo em si vive nos documentos da
 * P1.20; aqui fica a ligacao OS-documento com o estado da varredura: pelo
 * ADR-0020 (ClamAV), arquivo enviado fica em QUARENTENA ate a varredura e
 * so entao e liberado — anexo em quarentena ou infectado NAO se vincula
 * como evidencia da OS (a foto da baixa posterior, inclusive, precisa estar
 * CLEARED para a baixa valer — exigencia composta aplicada em
 * {@code RetroactiveClose.validate(close, dias, foto)}, correcao D4).
 */
public final class OrderAttachment {

    public enum Kind { PHOTO, DOCUMENT, OTHER }
    public enum ScanStatus { QUARANTINE, CLEARED, INFECTED }

    public record Attachment(UUID orderId, UUID documentId, Kind kind, ScanStatus scanStatus, String contentType, long sizeBytes) {
        public Attachment {
            Objects.requireNonNull(orderId, "OS e obrigatoria");
            Objects.requireNonNull(documentId, "documento da P1.20 e obrigatorio");
            Objects.requireNonNull(kind, "tipo do anexo e obrigatorio");
            Objects.requireNonNull(scanStatus, "estado da varredura (ADR-0020) e obrigatorio");
            if (contentType == null || contentType.isBlank()) throw new IllegalArgumentException("content-type do anexo e obrigatorio");
            if (sizeBytes <= 0) throw new IllegalArgumentException("anexo vazio nao e anexo");
        }
        public boolean linkable() { return scanStatus == ScanStatus.CLEARED; }
    }

    private OrderAttachment() {}

    public static Attachment requireLinkable(Attachment attachment) {
        Objects.requireNonNull(attachment, "anexo e obrigatorio");
        if (!attachment.linkable())
            throw new IllegalStateException("anexo " + attachment.documentId() + " nao liberado pela varredura ("
                    + attachment.scanStatus() + ", ADR-0020): quarentena/infectado nao vira evidencia da OS");
        return attachment;
    }
}
