package br.com.ziipsan.app;

import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceGate;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceService;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceService.IssueRequest;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceService.IssueResult;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuerRouter;
import br.com.ziipsan.commercial.fiscal.persistence.FilesystemHomologationXmlArea;
import br.com.ziipsan.commercial.fiscal.persistence.JdbcCertificateVault;
import br.com.ziipsan.commercial.fiscal.persistence.JdbcFiscalDocumentRepository;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentIssuer.FiscalDocumentType;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentRepository.DocumentRecord;
import br.com.ziipsan.commercial.fiscal.port.RestrictedProductionUnavailableException;
import br.com.ziipsan.commercial.fiscal.port.SefinRestrictedClient;
import br.com.ziipsan.commercial.fiscal.port.SimulatedFiscalIssuer;
import br.com.ziipsan.commercial.fiscal.rules.DpsId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT da V4_5 (P1.8) contra o banco dedicado de integração: a migration roda no
 * provisionamento do tenant (prova de que a faixa V4_5 entra no histórico do Flyway),
 * a numeração concorrente não repete nem perde número, o fluxo emitir → cancelar →
 * substituir persiste os estados e os elos, o XML autorizado é somente-inserção e a
 * homologação nunca cai nas tabelas do produto.
 *
 * <p>Roda no failsafe ({@code mvn verify}) com o banco dedicado (ci-linux.sh, porta
 * 55433 por padrão; a frente fiscal usa 55438 quando roda isolada). Sem as variáveis
 * de integração, {@link ProvisionedTenant} falha em vez de fingir um banco.
 */
class FiscalV45IT {

    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;
    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final String CNPJ = "12ABC34501DE35";
    private static final LocalDate COMPETENCE = LocalDate.of(2026, 11, 3);

    private static UUID issuerId;
    private static JdbcFiscalDocumentRepository repository;
    private static JdbcCertificateVault vault;
    private static FiscalIssuanceService service;
    private static SefinRestrictedClient restricted;

    @BeforeAll
    static void setUp() throws Exception {
        ProvisionedTenant.ensure();
        repository = new JdbcFiscalDocumentRepository(ProvisionedTenant.appDs, SCHEMA);
        vault = new JdbcCertificateVault(ProvisionedTenant.appDs, SCHEMA);
        restricted = new SefinRestrictedClient();
        FiscalIssuerRouter router = new FiscalIssuerRouter(FiscalIssuanceGate.closed(),
                new SimulatedFiscalIssuer(FiscalDocumentType.NFSE_NACIONAL), restricted);
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T13:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        service = new FiscalIssuanceService(repository, router, repository, clock);

        issuerId = insertIssuer();
        repository.ensureSeries(TENANT, issuerId, "NFSE_NACIONAL", "00001");
        repository.ensureSeries(TENANT, issuerId, "NFSE_NACIONAL", "00002");
        insertRate("ISS", "0.02");
        insertRate("IRRF", "0.015");
        insertRate("CSRF", "0.0465");
        insertRate("CBS", "0.009");
        insertRate("IBS", "0.001");
    }

    // ----------------------------------------------------------------------------------
    // Helpers de banco (conexão nova por uso: o appDs é DriverManager, sem pool para vazar
    // tenant entre usos; search_path + current_tenant por sessão, na conexão que morre aqui).
    // ----------------------------------------------------------------------------------

    private static Connection open() throws SQLException {
        Connection connection = ProvisionedTenant.appDs.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + SCHEMA + ", public");
        }
        try (PreparedStatement setTenant = connection.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, false)")) {
            setTenant.setString(1, TENANT.toString());
            setTenant.executeQuery();
        }
        return connection;
    }

    private static UUID insertIssuer() throws Exception {
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(CNPJ.getBytes(StandardCharsets.UTF_8)));
        try (Connection connection = open();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO fiscal_issuers (tenant_id, name, ibge, document_kind, document_hash, document_masked) "
                                + "VALUES (?, 'Emitente Sintetico de Teste', '4205407', 'CNPJ', ?, '**.***.***/01DE-**') "
                                + "RETURNING id")) {
            insert.setObject(1, TENANT);
            insert.setString(2, hash);
            try (ResultSet rows = insert.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return (UUID) rows.getObject(1);
            }
        }
    }

    private static void insertRate(String code, String rate) throws Exception {
        try (Connection connection = open();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO tax_rates (tenant_id, tax_code, scope, valid_from, valid_to, rate) "
                                + "VALUES (?, ?, 'NATIONAL', DATE '2026-01-01', NULL, ?)")) {
            insert.setObject(1, TENANT);
            insert.setString(2, code);
            insert.setBigDecimal(3, new BigDecimal(rate));
            insert.executeUpdate();
        }
    }

    private static IssueRequest request(String series, UUID sourceId) {
        return new IssueRequest(TENANT, issuerId, CNPJ, DpsId.TYPE_CNPJ, FiscalDocumentType.NFSE_NACIONAL,
                series, "BILL", sourceId, new BigDecimal("5440.00"), COMPETENCE,
                FiscalIssuanceGate.Environment.SIMULATED, false, "Tomador Sintetico de Teste", true);
    }

    private static long count(String sql) throws Exception {
        try (Connection connection = open();
                PreparedStatement query = connection.prepareStatement(sql);
                ResultSet rows = query.executeQuery()) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    // ----------------------------------------------------------------------------------
    // Testes
    // ----------------------------------------------------------------------------------

    @Test
    @DisplayName("V4_5: migration no historico do Flyway e as 12 tabelas no schema do tenant")
    void migrationApplied() throws Exception {
        try (Connection connection = ProvisionedTenant.migratorDs.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT COUNT(*) FROM " + SCHEMA + ".flyway_schema_history WHERE script LIKE 'V4_5%'")) {
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(1L);
            }
        }
        long tables = count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '" + SCHEMA + "' "
                + "AND table_name IN ('tax_rates','fiscal_issuers','fiscal_certificates','fiscal_certificate_access_log',"
                + "'fiscal_series','fiscal_documents','fiscal_document_taxes','fiscal_document_events',"
                + "'fiscal_document_batches','fiscal_document_batch_items','fiscal_documents_xml','fiscal_layout_versions')");
        assertThat(tables).isEqualTo(12L);
    }

    @Test
    @DisplayName("tax_rates: vigencia sobreposta recusada, vigencia adjacente aceita")
    void taxRateOverlapRejected() throws Exception {
        try (Connection connection = open();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO tax_rates (tenant_id, tax_code, scope, valid_from, valid_to, rate) "
                                + "VALUES (?, 'PIS', 'NATIONAL', DATE '2026-01-01', DATE '2026-12-31', 0.0065)")) {
            insert.setObject(1, TENANT);
            insert.executeUpdate();
        }
        assertThatThrownBy(() -> {
            try (Connection connection = open();
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO tax_rates (tenant_id, tax_code, scope, valid_from, valid_to, rate) "
                                    + "VALUES (?, 'PIS', 'NATIONAL', DATE '2026-06-01', NULL, 0.0065)")) {
                insert.setObject(1, TENANT);
                insert.executeUpdate();
            }
        }).isInstanceOf(SQLException.class);
        try (Connection connection = open();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO tax_rates (tenant_id, tax_code, scope, valid_from, valid_to, rate) "
                                + "VALUES (?, 'PIS', 'NATIONAL', DATE '2027-01-01', NULL, 0.0065)")) {
            insert.setObject(1, TENANT);
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("numeracao concorrente: 8 emissoes simultaneas, 8 numeros distintos e contiguos, nenhum perdido")
    void concurrentNumberingHasNoGapOrDuplicate() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<IssueResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID source = UUID.randomUUID();
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return service.issue(request("00002", source));
            }));
        }
        ready.await();
        start.countDown();
        Set<Long> numbers = new HashSet<>();
        for (Future<IssueResult> future : futures) {
            IssueResult result = future.get();
            assertThat(result.status()).isEqualTo("AUTHORIZED");
            numbers.add(result.number());
        }
        pool.shutdown();
        assertThat(numbers).hasSize(threads);
        long min = numbers.stream().mapToLong(Long::longValue).min().orElseThrow();
        long max = numbers.stream().mapToLong(Long::longValue).max().orElseThrow();
        assertThat(max - min).isEqualTo(threads - 1L);
        assertThat(count("SELECT COUNT(*) FROM fiscal_documents WHERE tenant_id = '" + TENANT
                + "' AND series = '00002'")).isEqualTo(threads);
        assertThat(count("SELECT next_number FROM fiscal_series WHERE tenant_id = '" + TENANT
                + "' AND series = '00002'")).isEqualTo(min + threads);
    }

    @Test
    @DisplayName("fluxo: emitir, cancelar (idempotente), substituir, cancelar em lote com segundo aprovador")
    void issueCancelSubstituteFlow() throws Exception {
        IssueResult issued = service.issue(request("00001", UUID.randomUUID()));
        assertThat(issued.status()).isEqualTo("AUTHORIZED");
        assertThat(issued.dpsId()).hasSize(42);
        assertThat(issued.protocol()).startsWith("SIM-");

        // O XML autorizado guardado corresponde ao XML nao assinado do documento.
        DocumentRecord document = repository.findDocument(TENANT, issued.documentId()).orElseThrow();
        String unsignedXml;
        String storedSha;
        byte[] storedGzip;
        try (Connection connection = open();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT d.unsigned_xml, x.sha256, x.xml_gzip, x.source FROM fiscal_documents d "
                                + "JOIN fiscal_documents_xml x ON x.document_id = d.id "
                                + "WHERE d.tenant_id = ? AND d.id = ?")) {
            query.setObject(1, TENANT);
            query.setObject(2, issued.documentId());
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                unsignedXml = rows.getString(1);
                storedSha = rows.getString(2);
                storedGzip = rows.getBytes(3);
                assertThat(rows.getString(4)).isEqualTo("SIMULATOR");
            }
        }
        assertThat(document.unsignedXml()).isEqualTo(unsignedXml);
        assertThat(storedSha).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(unsignedXml.getBytes(StandardCharsets.UTF_8))));
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(storedGzip))) {
            assertThat(new String(gzip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(unsignedXml);
        }

        var firstCancel = service.cancel(TENANT, issued.documentId(), "Erro no servico", UUID.randomUUID());
        var secondCancel = service.cancel(TENANT, issued.documentId(), "Erro no servico", UUID.randomUUID());
        assertThat(firstCancel.eventId()).isEqualTo(secondCancel.eventId());
        assertThat(firstCancel.eventId()).hasSize(59);
        assertThat(repository.findDocument(TENANT, issued.documentId()).orElseThrow().status())
                .isEqualTo("CANCELLED");

        IssueResult original = service.issue(request("00001", UUID.randomUUID()));
        var substituted = service.substitute(TENANT, original.documentId(), request("00001", UUID.randomUUID()),
                "Servico errado", UUID.randomUUID());
        DocumentRecord originalRow = repository.findDocument(TENANT, original.documentId()).orElseThrow();
        assertThat(originalRow.status()).isEqualTo("REPLACED");
        assertThat(originalRow.replacedById()).isEqualTo(substituted.replacementId());
        assertThat(repository.findDocument(TENANT, substituted.replacementId()).orElseThrow().status())
                .isEqualTo("AUTHORIZED");

        IssueResult batchFirst = service.issue(request("00001", UUID.randomUUID()));
        IssueResult batchSecond = service.issue(request("00001", UUID.randomUUID()));
        assertThatThrownBy(() -> service.cancelBatch(TENANT,
                List.of(batchFirst.documentId(), batchSecond.documentId()), "Lote", UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);
        var events = service.cancelBatch(TENANT,
                List.of(batchFirst.documentId(), batchSecond.documentId()), "Lote", UUID.randomUUID(), UUID.randomUUID());
        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.approvedBySecond() != null);
    }

    @Test
    @DisplayName("XML autorizado: UPDATE e DELETE recusados; source HOMOLOGATION recusado pelo CHECK")
    void authorizedXmlIsAppendOnly() throws Exception {
        IssueResult issued = service.issue(request("00001", UUID.randomUUID()));
        assertThatThrownBy(() -> {
            try (Connection connection = open();
                    PreparedStatement update = connection.prepareStatement(
                            "UPDATE fiscal_documents_xml SET sha256 = 'x' WHERE tenant_id = ? AND document_id = ?")) {
                update.setObject(1, TENANT);
                update.setObject(2, issued.documentId());
                update.executeUpdate();
            }
        }).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> {
            try (Connection connection = open();
                    PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM fiscal_documents_xml WHERE tenant_id = ? AND document_id = ?")) {
                delete.setObject(1, TENANT);
                delete.setObject(2, issued.documentId());
                delete.executeUpdate();
            }
        }).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> {
            try (Connection connection = open();
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO fiscal_documents_xml (tenant_id, document_id, xml_gzip, sha256, source) "
                                    + "VALUES (?, ?, '', 'x', 'HOMOLOGATION')")) {
                insert.setObject(1, TENANT);
                insert.setObject(2, issued.documentId());
                insert.executeUpdate();
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("producao restrita: stub falha fechado, sem documento e sem tentativa no stub")
    void restrictedProductionFailsClosed() {
        UUID source = UUID.randomUUID();
        IssueRequest homologation = new IssueRequest(TENANT, issuerId, CNPJ, DpsId.TYPE_CNPJ,
                FiscalDocumentType.NFSE_NACIONAL, "00001", "BILL", source, new BigDecimal("100.00"),
                COMPETENCE, FiscalIssuanceGate.Environment.HOMOLOGATION, false, null, true);
        assertThatThrownBy(() -> service.issue(homologation))
                .isInstanceOf(RestrictedProductionUnavailableException.class);
        assertThat(repository.findBySource(TENANT, "BILL", source, "NFSE_NACIONAL")).isEmpty();
        assertThat(restricted.attempts()).isEqualTo(0);
    }

    @Test
    @DisplayName("cofre: referencia sem segredo, acesso registrado por certificado")
    void certificateAccessIsLogged() throws Exception {
        UUID certificateId;
        try (Connection connection = open();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO fiscal_certificates (tenant_id, issuer_id, thumbprint, vault_ref, key_type, not_after) "
                                + "VALUES (?, ?, 'thumbprint-sintetico-it', 'local-vault:thumbprint-sintetico-it', 'A1', NOW() + INTERVAL '1 year') "
                                + "RETURNING id")) {
            insert.setObject(1, TENANT);
            insert.setObject(2, issuerId);
            try (ResultSet rows = insert.executeQuery()) {
                assertThat(rows.next()).isTrue();
                certificateId = (UUID) rows.getObject(1);
            }
        }
        var ref = vault.certificateFor(TENANT, issuerId);
        assertThat(ref).isPresent();
        assertThat(ref.orElseThrow().vaultRef()).startsWith("local-vault:");
        UUID accessor = UUID.randomUUID();
        vault.recordAccess(TENANT, certificateId, accessor, "IT: emissao simulada");
        assertThat(count("SELECT COUNT(*) FROM fiscal_certificate_access_log WHERE tenant_id = '" + TENANT
                + "' AND certificate_id = '" + certificateId + "' AND accessed_by = '" + accessor + "'"))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("area separada da homologacao: XML assinado fora das tabelas, purga com manifesto")
    void homologationAreaIsSeparate() throws Exception {
        Path root = Path.of("/var/tmp/ziipsan-p1-fiscal-it-" + UUID.randomUUID());
        var area = new FilesystemHomologationXmlArea(root, Clock.systemUTC());
        UUID documentId = UUID.randomUUID();
        long xmlBefore = count("SELECT COUNT(*) FROM fiscal_documents_xml WHERE tenant_id = '" + TENANT + "'");
        Path written = area.write("ciclo-it", documentId, "<xml-assinado-sintetico/>".getBytes(StandardCharsets.UTF_8));
        assertThat(Files.exists(written)).isTrue();
        // A escrita na area separada nao toca nas tabelas do produto.
        assertThat(count("SELECT COUNT(*) FROM fiscal_documents_xml WHERE tenant_id = '" + TENANT + "'"))
                .isEqualTo(xmlBefore);
        var manifest = area.purgeCycle("ciclo-it");
        assertThat(manifest.count()).isEqualTo(1);
        assertThat(Files.exists(written)).isFalse();
        Files.deleteIfExists(root);
    }

    @Test
    @DisplayName("reconciliacao: consulta antes de retransmitir; retransmissao apos PENDING autoriza sem duplicar")
    void reconcileBeforeRetransmit() {
        var slot = repository.reserveSlot(TENANT, issuerId, "NFSE_NACIONAL", "00001", "BILL",
                UUID.randomUUID(), "SIMULATED", null, new BigDecimal("100.00"));
        repository.signDocument(TENANT, slot.documentId(),
                "42054072" + CNPJ + "00001000000000000099", "<DPS/>", List.of());
        repository.markPendingReconciliation(TENANT, slot.documentId());
        assertThat(service.reconcile(TENANT, slot.documentId())).isEmpty();
        IssueResult retransmitted = service.retransmit(TENANT, slot.documentId());
        assertThat(retransmitted.status()).isEqualTo("AUTHORIZED");
        assertThatThrownBy(() -> service.retransmit(TENANT, slot.documentId()))
                .isInstanceOf(IllegalStateException.class);
    }
}
