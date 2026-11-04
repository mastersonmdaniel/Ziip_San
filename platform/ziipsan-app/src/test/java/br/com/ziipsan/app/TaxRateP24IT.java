package br.com.ziipsan.app;

import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceGate;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceService;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceService.IssueRequest;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceService.IssueResult;
import br.com.ziipsan.commercial.fiscal.application.FiscalIssuerRouter;
import br.com.ziipsan.commercial.fiscal.application.TaxRateService;
import br.com.ziipsan.commercial.fiscal.persistence.JdbcFiscalDocumentRepository;
import br.com.ziipsan.commercial.fiscal.port.FiscalDocumentIssuer.FiscalDocumentType;
import br.com.ziipsan.commercial.fiscal.port.SefinRestrictedClient;
import br.com.ziipsan.commercial.fiscal.port.SimulatedFiscalIssuer;
import br.com.ziipsan.commercial.fiscal.rules.DpsId;
import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT da P2.4 (CBS/IBS com vigência) contra o banco dedicado de integração.
 *
 * <p>Usa um segundo tenant provisionado (padrão do MatriculaSequenceTest), e não o tenant
 * compartilhado: o FiscalV45IT já registra alíquotas abertas para os cinco tributos no
 * tenant compartilhado, e qualquer linha CBS/IBS nova ali violaria a exclusão de vigências.
 * Aqui o cronograma é cadastrado via {@link TaxRateService} (o caminho de escrita da
 * P2.4, sobre JDBC), e o teste prova, no Postgres de verdade: resolução por vigência,
 * virada de 01/01/2027 no documento emitido, alíquota ausente bloqueando a emissão sem
 * consumir número, e a restrição de exclusão recusando sobreposição no banco.
 *
 * <p>Valores normativos: só os de 2026 (ano de teste: CBS 0,9% e IBS 0,1%). A CBS de
 * 2027 em diante e as linhas do IBS de 2029 a 2032 usam VALORES DE TESTE explicitamente
 * marcados: o plano não publica o valor da CBS plena nem os percentuais da transição
 * do IBS (LC 214/2025, art. 544), e nenhum deles é inventado aqui como normativo.
 *
 * <p>Roda no failsafe ({@code mvn verify}) com o banco dedicado (ci-linux.sh, porta
 * 55433 por padrão; a frente fiscal usa 55438 quando roda isolada). Sem as variáveis
 * de integração, {@link ProvisionedTenant} falha em vez de fingir um banco.
 */
class TaxRateP24IT {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SCHEMA = "it_p24tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String CNPJ = "12ABC34501DE35";

    private static UUID issuerId;
    private static JdbcFiscalDocumentRepository repository;
    private static TaxRateService taxService;
    private static FiscalIssuanceService service;

    @BeforeAll
    static void setUp() throws Exception {
        ProvisionedTenant.ensure();
        var result = new TenantProvisioningService(
                ProvisionedTenant.appDs,
                new JdbcTemplate(ProvisionedTenant.appDs),
                Mockito.mock(AuditLogRepository.class),
                ProvisionedTenant.migratorDs)
                .provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                        .tenantId(TENANT)
                        .schemaName(SCHEMA)
                        .displayName("Tenant tributos P2.4")
                        .cnpj("77666555000144")
                        .email("tributos-p24@teste.com.br")
                        .plan("PRO")
                        .build());
        assertThat(result.success()).as("provisionamento: %s", result.error()).isTrue();

        repository = new JdbcFiscalDocumentRepository(ProvisionedTenant.appDs, SCHEMA);
        taxService = new TaxRateService(repository);
        FiscalIssuerRouter router = new FiscalIssuerRouter(FiscalIssuanceGate.closed(),
                new SimulatedFiscalIssuer(FiscalDocumentType.NFSE_NACIONAL), new SefinRestrictedClient());
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T13:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        service = new FiscalIssuanceService(repository, router, repository, clock);

        issuerId = insertIssuer();
        repository.ensureSeries(TENANT, issuerId, "NFSE_NACIONAL", "00001");
        repository.ensureSeries(TENANT, issuerId, "NFSE_NACIONAL", "00002");

        // Vetor publicado de Florianópolis (FiscalV45IT) para os tributos fora da reforma.
        taxService.register(TENANT, "ISS", "NATIONAL",
                LocalDate.of(2026, 1, 1), null, new BigDecimal("0.02"), "Vetor Florianopolis (porte)");
        taxService.register(TENANT, "IRRF", "NATIONAL",
                LocalDate.of(2026, 1, 1), null, new BigDecimal("0.015"), "Vetor Florianopolis (porte)");
        taxService.register(TENANT, "CSRF", "NATIONAL",
                LocalDate.of(2026, 1, 1), null, new BigDecimal("0.0465"), "Vetor Florianopolis (porte)");
        // 2026, ano de teste (fato normativo do plano).
        taxService.register(TENANT, "CBS", "NATIONAL",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), new BigDecimal("0.009"),
                "LC 214/2025 (ano de teste de 2026; fato normativo do plano)");
        taxService.register(TENANT, "IBS", "NATIONAL",
                LocalDate.of(2026, 1, 1), LocalDate.of(2028, 12, 31), new BigDecimal("0.001"),
                "LC 214/2025 (ano de teste de 2026; fato normativo do plano)");
        // CBS plena a partir de 01/01/2027 — VALOR DE TESTE, não normativo.
        taxService.register(TENANT, "CBS", "NATIONAL",
                LocalDate.of(2027, 1, 1), null, new BigDecimal("0.020"),
                "LC 214/2025 (CBS plena a partir de 01/01/2027) — VALOR DE TESTE");
        // Transição do IBS de 2029 a 2032 (LC 214/2025, art. 544) — VALORES DE TESTE.
        taxService.register(TENANT, "IBS", "NATIONAL",
                LocalDate.of(2029, 1, 1), LocalDate.of(2029, 12, 31), new BigDecimal("0.011"),
                "LC 214/2025, art. 544 — VALOR DE TESTE");
        taxService.register(TENANT, "IBS", "NATIONAL",
                LocalDate.of(2030, 1, 1), LocalDate.of(2030, 12, 31), new BigDecimal("0.012"),
                "LC 214/2025, art. 544 — VALOR DE TESTE");
        taxService.register(TENANT, "IBS", "NATIONAL",
                LocalDate.of(2031, 1, 1), LocalDate.of(2031, 12, 31), new BigDecimal("0.013"),
                "LC 214/2025, art. 544 — VALOR DE TESTE");
        taxService.register(TENANT, "IBS", "NATIONAL",
                LocalDate.of(2032, 1, 1), null, new BigDecimal("0.014"),
                "LC 214/2025, art. 544 — VALOR DE TESTE");
    }

    @AfterAll
    static void tearDown() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
                java.sql.Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            st.execute("DELETE FROM platform.tenant_vertical WHERE tenant_id = (SELECT id FROM public.tenant_catalog WHERE schema_name = '" + SCHEMA + "')");
            st.execute("DELETE FROM public.tenant_catalog WHERE schema_name = '" + SCHEMA + "'");
        }
    }

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

    private static IssueRequest request(String series, UUID sourceId, LocalDate competence) {
        return new IssueRequest(TENANT, issuerId, CNPJ, DpsId.TYPE_CNPJ, FiscalDocumentType.NFSE_NACIONAL,
                series, "BILL", sourceId, new BigDecimal("5440.00"), competence,
                FiscalIssuanceGate.Environment.SIMULATED, false, "Tomador Sintetico de Teste", true);
    }

    private static BigDecimal taxLineRate(UUID documentId, String taxCode) throws Exception {
        try (Connection connection = open();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT rate FROM fiscal_document_taxes WHERE tenant_id = ? AND document_id = ? AND tax_code = ?")) {
            query.setObject(1, TENANT);
            query.setObject(2, documentId);
            query.setString(3, taxCode);
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).as("linha de tributo %s do documento", taxCode).isTrue();
                return rows.getBigDecimal(1);
            }
        }
    }

    private static boolean taxLineInformative(UUID documentId, String taxCode) throws Exception {
        try (Connection connection = open();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT informative FROM fiscal_document_taxes WHERE tenant_id = ? AND document_id = ? AND tax_code = ?")) {
            query.setObject(1, TENANT);
            query.setObject(2, documentId);
            query.setString(3, taxCode);
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getBoolean(1);
            }
        }
    }

    private static long seriesNextNumber(String series) throws Exception {
        try (Connection connection = open();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT next_number FROM fiscal_series WHERE tenant_id = ? AND series = ?")) {
            query.setObject(1, TENANT);
            query.setString(2, series);
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getLong(1);
            }
        }
    }

    @Test
    @DisplayName("cronograma P2.4: 2026, CBS plena e transicao do IBS resolvem por vigencia")
    void scheduleResolves() {
        assertThat(taxService.resolveNational(TENANT, "CBS", LocalDate.of(2026, 12, 31)))
                .isEqualByComparingTo("0.009");
        assertThat(taxService.resolveNational(TENANT, "CBS", LocalDate.of(2027, 1, 1)))
                .isEqualByComparingTo("0.020");
        assertThat(taxService.resolveNational(TENANT, "IBS", LocalDate.of(2028, 12, 31)))
                .isEqualByComparingTo("0.001");
        assertThat(taxService.resolveNational(TENANT, "IBS", LocalDate.of(2029, 6, 1)))
                .isEqualByComparingTo("0.011");
        assertThat(taxService.resolveNational(TENANT, "IBS", LocalDate.of(2030, 6, 1)))
                .isEqualByComparingTo("0.012");
        assertThat(taxService.resolveNational(TENANT, "IBS", LocalDate.of(2031, 6, 1)))
                .isEqualByComparingTo("0.013");
        assertThat(taxService.resolveNational(TENANT, "IBS", LocalDate.of(2032, 1, 1)))
                .isEqualByComparingTo("0.014");
        assertThat(taxService.resolveNational(TENANT, "IBS", LocalDate.of(2035, 6, 1)))
                .isEqualByComparingTo("0.014");
    }

    @Test
    @DisplayName("virada de 01/01/2027 no documento: CBS informativa 0,9% vira nao informativa a aliquota de teste")
    void turnoverFlipsCbsLineOnIssuedDocument() throws Exception {
        IssueResult before = service.issue(request("00001", UUID.randomUUID(), LocalDate.of(2026, 12, 31)));
        IssueResult after = service.issue(request("00001", UUID.randomUUID(), LocalDate.of(2027, 1, 1)));
        assertThat(before.status()).isEqualTo("AUTHORIZED");
        assertThat(after.status()).isEqualTo("AUTHORIZED");
        assertThat(taxLineRate(before.documentId(), "CBS")).isEqualByComparingTo("0.009");
        assertThat(taxLineInformative(before.documentId(), "CBS")).isTrue();
        assertThat(taxLineRate(after.documentId(), "CBS")).isEqualByComparingTo("0.020");
        assertThat(taxLineInformative(after.documentId(), "CBS")).isFalse();
    }

    @Test
    @DisplayName("aliquota ausente para a competencia: erro, nenhum documento e serie sem numero consumido")
    void missingRateBlocksIssueWithoutConsumingNumber() throws Exception {
        UUID source = UUID.randomUUID();
        assertThatThrownBy(() -> service.issue(request("00002", source, LocalDate.of(2025, 6, 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Aliquota ausente");
        assertThat(repository.findBySource(TENANT, "BILL", source, "NFSE_NACIONAL")).isEmpty();
        assertThat(seriesNextNumber("00002")).isEqualTo(1L);
    }

    @Test
    @DisplayName("sobreposicao recusada pelo servico e pela exclusao do banco; vigencia adjacente aceita")
    void overlapRejectedByServiceAndByDatabase() throws Exception {
        assertThatThrownBy(() -> taxService.register(TENANT, "CBS", "NATIONAL",
                LocalDate.of(2026, 6, 1), null, new BigDecimal("0.030"), "Sobreposicao de teste"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sobreposta");
        // Barreira definitiva no banco (V4_5), independente do pré-cheque do serviço.
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
    @DisplayName("base legal da linha de 2026 fica gravada em tax_rates")
    void legalBasisIsPersisted() throws Exception {
        try (Connection connection = open();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT legal_basis FROM tax_rates WHERE tenant_id = ? AND tax_code = 'CBS' "
                                + "AND valid_from = DATE '2026-01-01'")) {
            query.setObject(1, TENANT);
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).contains("LC 214/2025");
            }
        }
    }
}
