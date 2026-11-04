package br.com.ziipsan.app.config;

import br.com.ziipsan.platform.documents.TicketAttachmentService;
import br.com.ziipsan.platform.infra.queue.MessageQueue;
import br.com.ziipsan.platform.infra.scan.ClamAvScanner;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import br.com.ziipsan.platform.infra.scan.ScannerUnavailableException;
import br.com.ziipsan.platform.infra.storage.LocalFileObjectStorage;
import br.com.ziipsan.platform.infra.storage.ObjectStorage;
import br.com.ziipsan.platform.infra.events.BusinessEventPublisher;
import br.com.ziipsan.platform.infra.events.SupportEventCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Fiação dos chamados de suporte (P1.23).
 *
 * <p>O {@link br.com.ziipsan.core.support.TicketService} NÃO é bean: ele é
 * vinculado a um tenant, como o {@code TenantParameterService} — cada
 * controlador o constrói por requisição com o tenant do token. Aqui ficam
 * só as dependências sem tenant: armazenamento, varredor, pipeline de
 * anexos e publicador de eventos da outbox.
 *
 * <p>Sem host do clamd, o varredor é "indisponível" (fail-closed da P1.20):
 * o anexo fica NOT_SCANNED e o download é recusado, até o daemon existir.
 * Sem {@code local-root}, o armazenamento local cai em
 * {@code <tmp>/ziipsan-objects}; o S3 é outra frente.
 */
@Configuration
public class SupportConfig {

    @Bean
    public ObjectStorage ticketObjectStorage(ZiipsanProperties properties) {
        String root = properties.storage().localRoot();
        Path path = root == null || root.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "ziipsan-objects")
                : Path.of(root);
        return new LocalFileObjectStorage(path);
    }

    @Bean
    public FileScanner ticketFileScanner(ZiipsanProperties properties) {
        String host = properties.scanner().clamavHost();
        if (host == null || host.isBlank()) {
            return new FileScanner() {
                @Override
                public ScanResult scan(InputStream content) throws java.io.IOException {
                    throw new ScannerUnavailableException("clamd não configurado (ziipsan.scanner.clamav-host)");
                }

                @Override
                public java.util.Optional<java.time.Instant> signatureUpdatedAt() {
                    return java.util.Optional.empty();
                }
            };
        }
        return new ClamAvScanner(host, properties.scanner().clamavPort(),
                properties.scanner().timeoutMillis(), null);
    }

    @Bean
    public TicketAttachmentService ticketAttachmentService(ObjectStorage ticketObjectStorage,
                                                           ZiipsanProperties properties) {
        return new TicketAttachmentService(ticketObjectStorage, properties.storage().bucket());
    }

    /** Publicador de eventos de chamado, validado pelo catálogo da P1.23. */
    @Bean
    public BusinessEventPublisher ticketEventPublisher(MessageQueue messageQueue) {
        return new BusinessEventPublisher(messageQueue, SupportEventCatalog.catalog());
    }
}
