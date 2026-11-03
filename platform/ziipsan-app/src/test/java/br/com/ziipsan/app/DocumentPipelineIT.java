package br.com.ziipsan.app;

import br.com.ziipsan.platform.documents.DocumentRecords.DataClassification;
import br.com.ziipsan.platform.documents.DocumentRecords.Document;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanEvent;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanEventType;
import br.com.ziipsan.platform.documents.DocumentRecords.ScanStatus;
import br.com.ziipsan.platform.documents.DocumentRecords.SignatureStatus;
import br.com.ziipsan.platform.documents.DocumentService;
import br.com.ziipsan.platform.documents.ImmutableDocumentException;
import br.com.ziipsan.platform.documents.JdbcDocumentRepository;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import br.com.ziipsan.platform.infra.scan.ScannerUnavailableException;
import br.com.ziipsan.platform.infra.storage.LocalFileObjectStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT da V1_16 (P1.20) contra o banco dedicado de integracao (porta 55453
 * nesta frente, via run-single-it.sh): a migration entra no provisionamento
 * do tenant, e o fluxo quarentena -> varredura -> liberacao/bloqueio roda
 * sobre o PostgreSQL real com o repositorio JDBC e o armazenamento local.
 *
 * <p>O varredor aqui e FALSO da interface (EICAR -> INFECTED, resto -> CLEAN,
 * ou indisponivel simulado): nao ha daemon ClamAV neste sandbox, e o teste
 * nao finge que ha. O protocolo real do clamd esta no ClamAvScannerTest; o
 * que falta para provar contra um clamd real esta no javadoc de ClamAvScanner.
 * Dados e logs de IT ficam em /var/tmp e sao apagados ao fim (@AfterAll).
 */
class DocumentPipelineIT {

    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;
    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
    private static final byte[] CLEAN_PDF =
            "%PDF-1.7\nconteudo limpo sintético".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PNG_BYTES = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};

    private static JdbcDocumentRepository repository;
    private static LocalFileObjectStorage storage;
    private static Path storageRoot;
    private static UUID typeId;
    private static UUID retentionId;

    /** Varredor falso da interface: EICAR -> INFECTED, resto -> CLEAN. */
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

    @BeforeAll
    static void setUp() throws Exception {
        ProvisionedTenant.ensure();
        repository = new JdbcDocumentRepository(ProvisionedTenant.appDs, SCHEMA);
        storageRoot = Files.createDirectories(
                Path.of("/var/tmp/ziip-san-p1b-clamav-docs-" + UUID.randomUUID()));
        storage = new LocalFileObjectStorage(storageRoot);
        DocumentService catalog = service(new FakeScanner(NOW));
        typeId = catalog.createDocumentType(TENANT, "OFICIO-IT", "Ofício (IT)").id();
        retentionId = catalog.createRetentionClass(TENANT, "G6-CORRENTE-IT", "Corrente G6 (IT)", 60).id();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (storageRoot != null && Files.exists(storageRoot)) {
            try (Stream<Path> paths = Files.walk(storageRoot)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Limpeza best-effort; o diretorio e descartavel em /var/tmp.
                    }
                });
            }
        }
    }

    private static DocumentService service(FileScanner scanner) {
        return new DocumentService(repository, storage, "documents", scanner, CLOCK,
                Duration.ofDays(7));
    }

    private static Document uploadPdf(DocumentService svc, boolean immutable) {
        return svc.upload(TENANT, typeId, retentionId, DataClassification.ID,
                "documento.pdf", "application/pdf", CLEAN_PDF, immutable);
    }

    @Test
    @DisplayName("V1_16 aplicada: tabelas, colunas do plano e registro no historico Flyway")
    void migrationApplied() throws Exception {
        try (Connection conn = ProvisionedTenant.migratorDs.getConnection()) {
            for (String table : new String[]{"documents", "document_types", "retention_classes",
                    "document_scan_events"}) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
                    ps.setString(1, SCHEMA);
                    ps.setString(2, table);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).as("tabela %s existe", table).isTrue();
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT column_name FROM information_schema.columns"
                            + " WHERE table_schema = ? AND table_name = 'documents'")) {
                ps.setString(1, SCHEMA);
                try (ResultSet rs = ps.executeQuery()) {
                    java.util.Set<String> columns = new java.util.HashSet<>();
                    while (rs.next()) {
                        columns.add(rs.getString(1));
                    }
                    assertThat(columns).contains("document_type_id", "retention_class_id",
                            "data_classification", "scan_status", "sha256", "immutable",
                            "detected_content_type", "storage_key");
                }
            }
            // O historico do Flyway vive no schema do tenant (defaultSchema).
            // O SET vem antes do prepare: a resolucao do nome nao qualificado
            // usa o search_path da execucao.
            conn.createStatement().execute("SET search_path TO " + SCHEMA + ", public");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT success FROM flyway_schema_history WHERE version = '1.16'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("V1_16 no historico Flyway").isTrue();
                    assertThat(rs.getBoolean(1)).as("V1_16 aplicada com sucesso").isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("fluxo completo no PG: quarentena -> CLEAN -> download dos mesmos bytes")
    void fullFlowOnPostgres() throws Exception {
        DocumentService svc = service(new FakeScanner(NOW));
        Document doc = uploadPdf(svc, false);
        assertThat(doc.scanStatus()).isEqualTo(ScanStatus.QUARANTINED);
        assertThatThrownBy(() -> svc.download(TENANT, doc.id()))
                .isInstanceOf(br.com.ziipsan.platform.documents.DocumentNotReleasedException.class);
        Document clean = svc.scan(TENANT, doc.id());
        assertThat(clean.scanStatus()).isEqualTo(ScanStatus.CLEAN);
        assertThat(svc.download(TENANT, doc.id())).isEqualTo(CLEAN_PDF);
        assertThat(svc.events(TENANT, doc.id()))
                .extracting(ScanEvent::eventType)
                .containsExactly(ScanEventType.SCAN_QUARANTINED, ScanEventType.SCAN_CLEAN);
    }

    @Test
    @DisplayName("EICAR no PG: INFECTED com alerta e download recusado")
    void eicarBlockedOnPostgres() throws Exception {
        DocumentService svc = service(new FakeScanner(NOW));
        Document doc = svc.upload(TENANT, typeId, retentionId, DataClassification.ID,
                "anexo.txt", "text/plain", EICAR.getBytes(StandardCharsets.US_ASCII), false);
        Document blocked = svc.scan(TENANT, doc.id());
        assertThat(blocked.scanStatus()).isEqualTo(ScanStatus.INFECTED);
        assertThat(blocked.threat()).isEqualTo("EICAR-Test-Signature");
        assertThatThrownBy(() -> svc.download(TENANT, doc.id()))
                .isInstanceOf(br.com.ziipsan.platform.documents.DocumentNotReleasedException.class);
        assertThat(svc.events(TENANT, doc.id()))
                .extracting(ScanEvent::eventType)
                .contains(ScanEventType.SCAN_INFECTED);
    }

    @Test
    @DisplayName("varredor indisponivel no PG: NOT_SCANNED, nunca CLEAN")
    void unavailableScannerOnPostgres() throws Exception {
        DocumentService svc = service(new DownScanner());
        Document doc = uploadPdf(svc, false);
        assertThatThrownBy(() -> svc.scan(TENANT, doc.id()))
                .isInstanceOf(ScannerUnavailableException.class);
        assertThat(svc.find(TENANT, doc.id()).orElseThrow().scanStatus())
                .isEqualTo(ScanStatus.NOT_SCANNED);
        assertThatThrownBy(() -> svc.download(TENANT, doc.id()))
                .isInstanceOf(br.com.ziipsan.platform.documents.DocumentNotReleasedException.class);
    }

    @Test
    @DisplayName("tipo pelo conteudo no PG: bytes PNG com nome .pdf viram image/png")
    void detectedTypeOnPostgres() {
        DocumentService svc = service(new FakeScanner(NOW));
        Document doc = svc.upload(TENANT, typeId, retentionId, DataClassification.ID,
                "documento.pdf", "application/pdf", PNG_BYTES, false);
        assertThat(doc.detectedContentType()).isEqualTo("image/png");
        assertThat(svc.find(TENANT, doc.id()).orElseThrow().detectedContentType())
                .isEqualTo("image/png");
    }

    @Test
    @DisplayName("imutabilidade no PG: XML imutavel recusa regeneracao; mutavel substitui e revarre")
    void immutabilityOnPostgres() throws Exception {
        DocumentService svc = service(new FakeScanner(NOW));
        byte[] xml = "<?xml version=\"1.0\"?><nfeProc/>\n".getBytes(StandardCharsets.UTF_8);
        Document authorized = svc.upload(TENANT, typeId, retentionId, DataClassification.FIN,
                "nfe-autorizada.xml", "application/xml", xml, true);
        assertThat(authorized.detectedContentType()).isEqualTo("application/xml");
        assertThatThrownBy(() -> svc.regenerate(TENANT, authorized.id(), xml,
                "nfe-autorizada.xml", "application/xml"))
                .isInstanceOf(ImmutableDocumentException.class);
        Document mutable = svc.scan(TENANT, uploadPdf(svc, false).id());
        assertThat(mutable.scanStatus()).isEqualTo(ScanStatus.CLEAN);
        Document replaced = svc.replace(TENANT, mutable.id(), PNG_BYTES, "nova.png", "image/png");
        assertThat(replaced.scanStatus()).isEqualTo(ScanStatus.QUARANTINED);
        assertThat(svc.scan(TENANT, mutable.id()).scanStatus()).isEqualTo(ScanStatus.CLEAN);
    }

    @Test
    @DisplayName("idade das assinaturas no PG: STALE gera um alerta; segunda checagem nao duplica")
    void signatureFreshnessOnPostgres() throws Exception {
        DocumentService stale = service(new FakeScanner(NOW.minus(Duration.ofDays(10))));
        assertThat(stale.checkSignatureFreshness(TENANT).status()).isEqualTo(SignatureStatus.STALE);
        assertThat(stale.checkSignatureFreshness(TENANT).status()).isEqualTo(SignatureStatus.STALE);
        DocumentService fresh = service(new FakeScanner(NOW.minus(Duration.ofDays(1))));
        // Outro tenant: sem alerta previo, FRESH nao cria evento.
        assertThat(fresh.checkSignatureFreshness(UUID.randomUUID()).status())
                .isEqualTo(SignatureStatus.FRESH);
        // Exatamente um alerta SIGNATURE_STALE para o tenant (sem duplicata).
        // A RLS e FORCADA tambem para o dono da tabela: sem o GUC do tenant,
        // a policy (tenant_id = current_tenant_id()) devolveria 0 linhas mesmo
        // com o evento existindo — o mesmo padrao do repositorio JDBC.
        try (Connection conn = ProvisionedTenant.migratorDs.getConnection()) {
            conn.createStatement().execute("SET search_path TO " + SCHEMA + ", public");
            try (PreparedStatement setTenant = conn.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, false)")) {
                setTenant.setString(1, TENANT.toString());
                setTenant.executeQuery();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM document_scan_events"
                            + " WHERE tenant_id = ? AND event_type = 'SIGNATURE_STALE'")) {
                ps.setObject(1, TENANT);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    @DisplayName("RLS no PG: documento de um tenant nao existe para outro")
    void tenantIsolationOnPostgres() {
        DocumentService svc = service(new FakeScanner(NOW));
        Document doc = uploadPdf(svc, false);
        assertThat(svc.find(UUID.randomUUID(), doc.id())).isEmpty();
    }
}
