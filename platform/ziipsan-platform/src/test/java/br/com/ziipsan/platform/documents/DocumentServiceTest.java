package br.com.ziipsan.platform.documents;

import br.com.ziipsan.platform.documents.DocumentRecords.DataClassification;
import br.com.ziipsan.platform.documents.DocumentRecords.Document;
import br.com.ziipsan.platform.documents.DocumentRecords.DocumentType;
import br.com.ziipsan.platform.documents.DocumentRecords.RetentionClass;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanEvent;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanEventType;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanStatus;
import br.com.ziipsan.platform.documents.DocumentRecords.SignatureStatus;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import br.com.ziipsan.platform.infra.scan.ScannerUnavailableException;
import br.com.ziipsan.platform.infra.storage.ObjectStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.20: fluxo do pipeline sem banco e sem daemon — repositorio em memoria,
 * armazenamento em memoria e varredor FALSO da interface (nunca fingindo ser
 * um scan real: o falso decide pelo marcador EICAR / por indisponibilidade
 * simulada, e o teste o chama de falso). O fluxo contra o PostgreSQL real
 * esta no DocumentPipelineIT; o protocolo real do clamd, no ClamAvScannerTest.
 */
class DocumentServiceTest {

    static final String EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
    static final byte[] CLEAN_PDF =
            "%PDF-1.7\nconteudo limpo sintético".getBytes(StandardCharsets.UTF_8);
    static final byte[] PNG_BYTES = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();

    /** Varredor falso: EICAR -> INFECTED, resto -> CLEAN. */
    private static final class FakeScanner implements FileScanner {
        private final Instant avDbUpdatedAt;

        FakeScanner(Instant avDbUpdatedAt) {
            this.avDbUpdatedAt = avDbUpdatedAt;
        }

        @Override
        public ScanResult scan(InputStream content) throws IOException {
            String text = new String(content.readAllBytes(), StandardCharsets.US_ASCII);
            if (text.contains("EICAR")) {
                return ScanResult.infected("EICAR-Test-Signature", "fake-scanner",
                        Optional.ofNullable(avDbUpdatedAt));
            }
            return ScanResult.clean("fake-scanner", Optional.ofNullable(avDbUpdatedAt));
        }

        @Override
        public Optional<Instant> signatureUpdatedAt() {
            return Optional.ofNullable(avDbUpdatedAt);
        }
    }

    /** Varredor falso indisponivel: sempre falha, sem idade conhecida. */
    private static final class DownScanner implements FileScanner {
        @Override
        public ScanResult scan(InputStream content) throws IOException {
            throw new ScannerUnavailableException("daemon fora do ar (simulado)");
        }

        @Override
        public Optional<Instant> signatureUpdatedAt() {
            return Optional.empty();
        }
    }

    private static final class MemoryStorage implements ObjectStorage {
        private final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public void put(String bucket, String key, InputStream content, String contentType) {
            try {
                objects.put(bucket + "/" + key, content.readAllBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Optional<InputStream> get(String bucket, String key) {
            byte[] bytes = objects.get(bucket + "/" + key);
            return bytes == null ? Optional.empty() : Optional.of(new ByteArrayInputStream(bytes));
        }

        @Override
        public boolean exists(String bucket, String key) {
            return objects.containsKey(bucket + "/" + key);
        }

        @Override
        public void delete(String bucket, String key) {
            objects.remove(bucket + "/" + key);
        }
    }

    /** Repositorio em memoria que imita as unicidades do DDL (codigo por tenant). */
    private static final class MemoryRepository implements DocumentRepository {
        private final Clock clock;
        private final Map<String, DocumentType> types = new HashMap<>();
        private final Map<String, RetentionClass> retentions = new HashMap<>();
        private final Map<String, Document> documents = new HashMap<>();
        private final List<ScanEvent> events = new ArrayList<>();

        MemoryRepository(Clock clock) {
            this.clock = clock;
        }

        @Override
        public DocumentType createDocumentType(UUID tenantId, String code, String label) {
            if (types.containsKey(tenantId + "/" + code)) {
                throw new DocumentStoreException("codigo duplicado: " + code, null);
            }
            DocumentType type = new DocumentType(UUID.randomUUID(), tenantId, code, label,
                    true, clock.instant(), clock.instant());
            types.put(tenantId + "/" + type.id(), type);
            types.put(tenantId + "/" + code, type);
            return type;
        }

        @Override
        public Optional<DocumentType> findDocumentType(UUID tenantId, UUID documentTypeId) {
            return Optional.ofNullable(types.get(tenantId + "/" + documentTypeId));
        }

        @Override
        public RetentionClass createRetentionClass(UUID tenantId, String code, String label,
                                                   int retentionMonths) {
            if (retentions.containsKey(tenantId + "/" + code)) {
                throw new DocumentStoreException("codigo duplicado: " + code, null);
            }
            RetentionClass retention = new RetentionClass(UUID.randomUUID(), tenantId, code, label,
                    retentionMonths, true, clock.instant(), clock.instant());
            retentions.put(tenantId + "/" + retention.id(), retention);
            retentions.put(tenantId + "/" + code, retention);
            return retention;
        }

        @Override
        public Optional<RetentionClass> findRetentionClass(UUID tenantId, UUID retentionClassId) {
            return Optional.ofNullable(retentions.get(tenantId + "/" + retentionClassId));
        }

        @Override
        public Document insertDocument(Document document) {
            Document saved = stamped(document);
            documents.put(document.tenantId() + "/" + document.id(), saved);
            return saved;
        }

        @Override
        public Optional<Document> findDocument(UUID tenantId, UUID documentId) {
            return Optional.ofNullable(documents.get(tenantId + "/" + documentId));
        }

        @Override
        public Document updateDocument(Document document) {
            if (!documents.containsKey(document.tenantId() + "/" + document.id())) {
                throw new DocumentStoreException("documento inexistente", null);
            }
            Document saved = stamped(document);
            documents.put(document.tenantId() + "/" + document.id(), saved);
            return saved;
        }

        @Override
        public ScanEvent insertEvent(UUID tenantId, Optional<UUID> documentId,
                                     ScanEventType eventType, String threat, String detail) {
            ScanEvent event = new ScanEvent(UUID.randomUUID(), tenantId, documentId,
                    eventType, threat, detail, clock.instant());
            events.add(event);
            return event;
        }

        @Override
        public List<ScanEvent> listEvents(UUID tenantId, UUID documentId) {
            return events.stream()
                    .filter(e -> e.tenantId().equals(tenantId)
                            && e.documentId().map(documentId::equals).orElse(false))
                    .toList();
        }

        @Override
        public Optional<ScanEvent> latestEventByType(UUID tenantId, ScanEventType eventType) {
            return events.stream()
                    .filter(e -> e.tenantId().equals(tenantId) && e.eventType() == eventType)
                    .reduce((first, second) -> second);
        }

        private Document stamped(Document document) {
            return new Document(document.id(), document.tenantId(), document.documentTypeId(),
                    document.retentionClassId(), document.dataClassification(),
                    document.scanStatus(), document.originalFilename(),
                    document.declaredContentType(), document.detectedContentType(),
                    document.sizeBytes(), document.sha256(), document.storageBucket(),
                    document.storageKey(), document.immutable(), document.scannedAt(),
                    document.scanner(), document.avDbUpdatedAt(), document.threat(),
                    document.createdAt() == null ? clock.instant() : document.createdAt(),
                    clock.instant());
        }
    }

    private MemoryStorage storage;
    private MemoryRepository repository;
    private DocumentType type;
    private RetentionClass retention;

    @BeforeEach
    void setUp() {
        storage = new MemoryStorage();
        repository = new MemoryRepository(Clock.fixed(NOW, ZoneOffset.UTC));
        DocumentService catalog = service(new FakeScanner(NOW));
        type = catalog.createDocumentType(TENANT, "OFICIO", "Ofício");
        retention = catalog.createRetentionClass(TENANT, "G6_CORRENTE", "Corrente G6", 60);
    }

    private DocumentService service(FileScanner scanner) {
        return new DocumentService(repository, storage, "documents", scanner,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(7));
    }

    private Document uploadPdf(DocumentService svc, boolean immutable) {
        return svc.upload(TENANT, type.id(), retention.id(), DataClassification.ID,
                "documento.pdf", "application/pdf", CLEAN_PDF, immutable);
    }

    @Test
    @DisplayName("upload fica em quarentena: sem download, com SHA-256 e evento")
    void uploadIsQuarantined() throws Exception {
        DocumentService svc = service(new FakeScanner(NOW));
        Document doc = uploadPdf(svc, false);
        assertThat(doc.scanStatus()).isEqualTo(ScanStatus.QUARANTINED);
        assertThat(doc.sha256()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(CLEAN_PDF)));
        assertThat(doc.detectedContentType()).isEqualTo("application/pdf");
        assertThat(doc.storageKey()).startsWith("quarantine/");
        assertThatThrownBy(() -> svc.download(TENANT, doc.id()))
                .isInstanceOf(DocumentNotReleasedException.class);
        assertThat(svc.events(TENANT, doc.id()))
                .extracting(ScanEvent::eventType)
                .containsExactly(ScanEventType.SCAN_QUARANTINED);
    }

    @Test
    @DisplayName("tipo detectado pelo conteudo: .pdf com bytes PNG e image/png, e vice-versa")
    void detectedTypeIgnoresExtension() {
        DocumentService svc = service(new FakeScanner(NOW));
        Document pngAsPdf = svc.upload(TENANT, type.id(), retention.id(), DataClassification.ID,
                "documento.pdf", "application/pdf", PNG_BYTES, false);
        assertThat(pngAsPdf.detectedContentType()).isEqualTo("image/png");
        Document pdfAsPng = svc.upload(TENANT, type.id(), retention.id(), DataClassification.ID,
                "foto.png", "image/png", CLEAN_PDF, false);
        assertThat(pdfAsPng.detectedContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("PDF limpo: scan libera, download serve os bytes, quarentena some")
    void cleanPdfIsReleased() throws Exception {
        DocumentService svc = service(new FakeScanner(NOW));
        Document doc = uploadPdf(svc, false);
        Document clean = svc.scan(TENANT, doc.id());
        assertThat(clean.scanStatus()).isEqualTo(ScanStatus.CLEAN);
        assertThat(clean.storageKey()).startsWith("released/");
        assertThat(svc.download(TENANT, doc.id())).isEqualTo(CLEAN_PDF);
        assertThat(storage.exists("documents", doc.storageKey())).isFalse();
        assertThat(svc.events(TENANT, doc.id()))
                .extracting(ScanEvent::eventType)
                .containsExactly(ScanEventType.SCAN_QUARANTINED, ScanEventType.SCAN_CLEAN);
    }

    @Test
    @DisplayName("EICAR: scan bloqueia com alerta, threat registrado, download recusado")
    void eicarIsBlockedWithAlert() throws Exception {
        DocumentService svc = service(new FakeScanner(NOW));
        Document doc = svc.upload(TENANT, type.id(), retention.id(), DataClassification.ID,
                "anexo.txt", "text/plain", EICAR.getBytes(StandardCharsets.US_ASCII), false);
        Document blocked = svc.scan(TENANT, doc.id());
        assertThat(blocked.scanStatus()).isEqualTo(ScanStatus.INFECTED);
        assertThat(blocked.threat()).isEqualTo("EICAR-Test-Signature");
        assertThatThrownBy(() -> svc.download(TENANT, doc.id()))
                .isInstanceOf(DocumentNotReleasedException.class);
        List<ScanEvent> events = svc.events(TENANT, doc.id());
        assertThat(events).extracting(ScanEvent::eventType)
                .containsExactly(ScanEventType.SCAN_QUARANTINED, ScanEventType.SCAN_INFECTED);
        assertThat(events.get(1).threat()).isEqualTo("EICAR-Test-Signature");
    }

    @Test
    @DisplayName("varredor indisponivel: NOT_SCANNED com evento, nunca CLEAN, download recusado")
    void unavailableScannerNeverMarksClean() throws Exception {
        DocumentService svc = service(new DownScanner());
        Document doc = uploadPdf(svc, false);
        assertThatThrownBy(() -> svc.scan(TENANT, doc.id()))
                .isInstanceOf(ScannerUnavailableException.class);
        assertThat(svc.find(TENANT, doc.id()).orElseThrow().scanStatus())
                .isEqualTo(ScanStatus.NOT_SCANNED);
        assertThat(svc.events(TENANT, doc.id()))
                .extracting(ScanEvent::eventType)
                .containsExactly(ScanEventType.SCAN_QUARANTINED, ScanEventType.SCAN_UNAVAILABLE);
        assertThatThrownBy(() -> svc.download(TENANT, doc.id()))
                .isInstanceOf(DocumentNotReleasedException.class);
    }

    @Test
    @DisplayName("documento imutavel recusa substituicao e regeneracao; mutavel aceita e volta a quarentena")
    void immutableRefusesReplaceAndRegenerate() throws Exception {
        DocumentService svc = service(new FakeScanner(NOW));
        Document immutable = uploadPdf(svc, true);
        assertThatThrownBy(() -> svc.replace(TENANT, immutable.id(), CLEAN_PDF, "novo.pdf", null))
                .isInstanceOf(ImmutableDocumentException.class);
        assertThatThrownBy(() -> svc.regenerate(TENANT, immutable.id(), CLEAN_PDF, "novo.pdf", null))
                .isInstanceOf(ImmutableDocumentException.class);
        Document mutable = svc.scan(TENANT, uploadPdf(svc, false).id());
        assertThat(mutable.scanStatus()).isEqualTo(ScanStatus.CLEAN);
        Document replaced = svc.replace(TENANT, mutable.id(), PNG_BYTES, "nova.png", "image/png");
        assertThat(replaced.scanStatus()).isEqualTo(ScanStatus.QUARANTINED);
        assertThatThrownBy(() -> svc.download(TENANT, mutable.id()))
                .isInstanceOf(DocumentNotReleasedException.class);
        assertThat(svc.scan(TENANT, mutable.id()).scanStatus()).isEqualTo(ScanStatus.CLEAN);
    }

    @Test
    @DisplayName("XML autorizado imutavel nunca e regerado")
    void authorizedXmlIsNeverRegenerated() {
        DocumentService svc = service(new FakeScanner(NOW));
        byte[] xml = "<?xml version=\"1.0\"?><nfeProc/>\n".getBytes(StandardCharsets.UTF_8);
        Document authorized = svc.upload(TENANT, type.id(), retention.id(),
                DataClassification.FIN, "nfe-autorizada.xml", "application/xml", xml, true);
        assertThat(authorized.detectedContentType()).isEqualTo("application/xml");
        assertThatThrownBy(() -> svc.regenerate(TENANT, authorized.id(), xml,
                "nfe-autorizada.xml", "application/xml"))
                .isInstanceOf(ImmutableDocumentException.class);
    }

    @Test
    @DisplayName("catalogo: code em branco, tipo/classe inexistente e codigo duplicado sao recusados")
    void catalogValidation() {
        DocumentService svc = service(new FakeScanner(NOW));
        assertThatThrownBy(() -> svc.createDocumentType(TENANT, "  ", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.createRetentionClass(TENANT, "X", "x", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.createDocumentType(TENANT, "OFICIO", "outro"))
                .isInstanceOf(DocumentStoreException.class);
        assertThatThrownBy(() -> svc.upload(TENANT, UUID.randomUUID(), retention.id(),
                DataClassification.ID, "a.pdf", null, CLEAN_PDF, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.upload(TENANT, type.id(), UUID.randomUUID(),
                DataClassification.ID, "a.pdf", null, CLEAN_PDF, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("idade das assinaturas: FRESH dentro do limite, STALE com um alerta, UNKNOWN sem alerta")
    void signatureFreshness() {
        DocumentService fresh = service(new FakeScanner(NOW.minus(Duration.ofDays(1))));
        assertThat(fresh.checkSignatureFreshness(TENANT).status()).isEqualTo(SignatureStatus.FRESH);
        DocumentService stale = service(new FakeScanner(NOW.minus(Duration.ofDays(10))));
        assertThat(stale.checkSignatureFreshness(TENANT).status()).isEqualTo(SignatureStatus.STALE);
        assertThat(stale.checkSignatureFreshness(TENANT).status()).isEqualTo(SignatureStatus.STALE);
        long staleAlerts = repository.latestEventByType(TENANT, ScanEventType.SIGNATURE_STALE)
                .stream().count();
        assertThat(staleAlerts).isEqualTo(1);
        DocumentService unknown = service(new DownScanner());
        assertThat(unknown.checkSignatureFreshness(TENANT).status())
                .isEqualTo(SignatureStatus.UNKNOWN);
    }

    @Test
    @DisplayName("isolamento por tenant: documento de outro tenant nao existe para este")
    void tenantIsolation() {
        DocumentService svc = service(new FakeScanner(NOW));
        Document doc = uploadPdf(svc, false);
        assertThat(svc.find(OTHER_TENANT, doc.id())).isEmpty();
        assertThatThrownBy(() -> svc.scan(OTHER_TENANT, doc.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
