package br.com.ziipsan.platform.documents;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.support.TicketService;
import br.com.ziipsan.platform.infra.scan.ContentTypeDetector;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import br.com.ziipsan.platform.infra.scan.ScannerUnavailableException;
import br.com.ziipsan.platform.infra.storage.ObjectStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Anexos de chamado pelo pipeline da P1.20, sem duplicar o pipeline de
 * documentos: o fluxo é o mesmo (quarentena → varredura → liberado ou
 * bloqueado), só muda a tabela de registro ({@code support.ticket_attachment}
 * em vez de {@code documents}) e a chave do objeto.
 *
 * <p>Ordem, sem exceção (DocumentService):
 * <ol>
 *   <li>upload: tipo detectado pelo CONTEÚDO (nunca pela extensão), fora da
 *       lista do AJUDA §11 é recusado; SHA-256 calculado; bytes gravados na
 *       chave de quarentena e linha criada {@code QUARANTINED};</li>
 *   <li>scan: o varredor (interface {@link FileScanner} — ClamAV ou o serviço
 *       da hospedagem) examina os bytes; {@code CLEAN} move o objeto para a
 *       chave de liberados e libera o download; {@code INFECTED} bloqueia e
 *       registra a ameaça; varredor indisponível deixa {@code NOT_SCANNED} —
 *       nunca {@code CLEAN};</li>
 *   <li>download: somente {@code CLEAN} e servido; qualquer outro estado
 *       recusa (o gate vive no {@link TicketService}).</li>
 * </ol>
 *
 * <p>Sem anotações Spring de propósito (como o DocumentService): o serviço é
 * construído explicitamente pela frente consumidora com o armazenamento e o
 * varredor do ambiente. O transporte REST desta frente é JSON com base64
 * (não há multipart no código — ZiipsanProperties, limite de corpo 1 MiB);
 * o teto de 10 MB do AJUDA §11 é conferido aqui, mas o corpo da requisição
 * o alcança antes: anexo grande pela API REST v0 só depois do transporte
 * multipart, que é pendência declarada, não um limite fingido.
 */
public final class TicketAttachmentService {

    private final ObjectStorage storage;
    private final String bucket;

    public TicketAttachmentService(ObjectStorage storage, String bucket) {
        this.storage = Objects.requireNonNull(storage, "storage e obrigatorio");
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket obrigatorio");
        }
        this.bucket = bucket;
    }

    /** Chave de quarentena; a de liberados troca o segmento {@code quarantine} por {@code released}. */
    static String quarantineKey(UUID tenantId, String ticketNumber, UUID attachmentId) {
        return "support/" + tenantId + "/" + ticketNumber + "/quarantine/" + attachmentId;
    }

    static String releasedKey(UUID tenantId, String ticketNumber, UUID attachmentId) {
        return "support/" + tenantId + "/" + ticketNumber + "/released/" + attachmentId;
    }

    /**
     * Recebe os bytes e os deixa em quarentena. Não varre aqui: a varredura é
     * {@link #scan}, para o upload não depender da disponibilidade do daemon.
     * O {@code ticketNumber} compõe a chave; o registro é criado primeiro com
     * uma chave provisória e corrigido com o id real? Não — o id é gerado
     * aqui e passado ao registro, para a chave ser definitiva desde o início.
     */
    public TicketService.TicketAttachment upload(TicketService tickets, UUID ticketId, UUID messageId,
                                                 String ticketNumber, String fileName, byte[] content) {
        if (tickets == null || ticketId == null || messageId == null || ticketNumber == null
                || fileName == null || content == null) {
            throw new IllegalArgumentException("tickets, ticketId, messageId, ticketNumber, fileName e content sao obrigatorios");
        }
        if (content.length == 0 || content.length > TicketService.MAX_ATTACHMENT_BYTES) {
            throw ApiException.Validation.of(new ApiException.FieldError("file", "invalid",
                    "Anexo vazio ou maior que 10 MB."));
        }
        String detectedMime = ContentTypeDetector.detect(content);
        if (!TicketService.ALLOWED_MIMES.contains(detectedMime)) {
            throw ApiException.Validation.of(new ApiException.FieldError("file", "invalid",
                    "Tipo de arquivo não aceito para chamado (detectado pelo conteúdo)."));
        }
        // O registro precisa do id antes da chave: gera-se aqui e o serviço o aceita
        // via registerAttachmentWithId; sem isso a chave provisória viraria lixo.
        UUID attachmentId = UUID.randomUUID();
        String key = quarantineKey(ticketsTenant(tickets), ticketNumber, attachmentId);
        storage.put(bucket, key, new ByteArrayInputStream(content), detectedMime);
        try {
            return tickets.registerAttachmentWithId(attachmentId, ticketId, messageId, fileName,
                    detectedMime, content.length, sha256(content), key);
        } catch (RuntimeException e) {
            storage.delete(bucket, key);
            throw e;
        }
    }

    /** Varre o anexo em quarentena; move para liberados só quando CLEAN. */
    public TicketService.TicketAttachment scan(TicketService tickets, UUID attachmentId,
                                               String ticketNumber, FileScanner scanner) {
        if (tickets == null || attachmentId == null || scanner == null) {
            throw new IllegalArgumentException("tickets, attachmentId e scanner sao obrigatorios");
        }
        TicketService.TicketAttachment current = tickets.findAttachmentById(attachmentId);
        if (!current.scanStatus().equals("QUARANTINED") && !current.scanStatus().equals("NOT_SCANNED")) {
            return current;
        }
        try (InputStream in = storage.get(bucket, current.objectKey())
                .orElseThrow(() -> new IllegalStateException("objeto do anexo ausente: " + current.objectKey()))) {
            FileScanner.ScanResult result = scanner.scan(in);
            if (result.verdict() == FileScanner.ScanVerdict.CLEAN) {
                String released = releasedKey(ticketsTenant(tickets), ticketNumber, attachmentId);
                try (InputStream bytes = storage.get(bucket, current.objectKey())
                        .orElseThrow(() -> new IllegalStateException("objeto do anexo ausente: " + current.objectKey()))) {
                    storage.put(bucket, released, bytes, current.detectedMime());
                }
                storage.delete(bucket, current.objectKey());
                tickets.moveAttachmentKey(attachmentId, released);
                return tickets.markScan(attachmentId, "CLEAN", null);
            }
            return tickets.markScan(attachmentId, "INFECTED", result.threat().orElse("ameaça desconhecida"));
        } catch (ScannerUnavailableException e) {
            return tickets.markScan(attachmentId, "NOT_SCANNED", null);
        } catch (IOException e) {
            throw new IllegalStateException("falha ao varrer o anexo: " + e.getMessage(), e);
        }
    }

    /** Bytes para download; só chamado depois do gate CLEAN do TicketService. */
    public InputStream download(TicketService.TicketAttachment attachment) {
        return storage.get(bucket, attachment.objectKey())
                .orElseThrow(() -> new IllegalStateException("objeto do anexo ausente: " + attachment.objectKey()));
    }

    private static UUID ticketsTenant(TicketService tickets) {
        return tickets.tenantId();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }
}
