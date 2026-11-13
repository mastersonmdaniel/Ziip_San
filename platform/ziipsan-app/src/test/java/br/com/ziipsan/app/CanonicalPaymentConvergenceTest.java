package br.com.ziipsan.app;

import br.com.ziipsan.core.migration.CoreMigrationRunner;
import br.com.ziipsan.platform.tenant.TenantMigrator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Convergência canónica de {@code payments} (decisão 75, PD-1..PD-6, V112) no
 * PostgreSQL real, pelo caminho real do produto ({@code TenantMigrator}/Flyway).
 *
 * <p>Dois schemas descartáveis, ambos sobre base virgem:
 * <ul>
 *   <li><b>Schema A (derviação semeada):</b> migra-se até à V110 (o estado anterior
 *       à convergência), semeiam-se 5 linhas em formato FEBRABAN pré-migration
 *       (esquema V6_1→V15_1) e corre-se a migration até ao fim — a V112 deriva
 *       cada campo e prova-se campo a campo. É a prova da PD-5 com dados semeados,
 *       porque a P-R27-A-02 provou que NÃO há linhas FEBRABAN vivas nesta VM para
 *       derivar (0 linhas em 12 bases; as tabelas híbridas estão vazias).</li>
 *   <li><b>Schema B (travessia limpa):</b> migra-se do zero até ao fim com a tabela
 *       híbrida vazia — a V112 corre como no-op sobre a tabela vazia e o esquema
 *       final fica idêntico ao do schema A.</li>
 * </ul>
 * Controlos negativos por grupo tocado no fim. Nada sobrevive: os schemas são
 * apagados em {@code @AfterAll}.
 */
class CanonicalPaymentConvergenceTest {

    private static final String SCHEMA_A = "conv_a_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final String SCHEMA_B = "conv_b_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    private static final UUID AGREEMENT = UUID.fromString("a1000000-0000-4000-8000-000000000001");
    private static final UUID RETURN_FILE = UUID.fromString("b1000000-0000-4000-8000-000000000001");
    private static final UUID BILL_1 = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID GUIA_DOC = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID P1 = UUID.fromString("d1000000-0000-4000-8000-000000000001");
    private static final UUID P2 = UUID.fromString("d1000000-0000-4000-8000-000000000002");
    private static final UUID P3 = UUID.fromString("d1000000-0000-4000-8000-000000000003");
    private static final UUID P4 = UUID.fromString("d1000000-0000-4000-8000-000000000004");
    private static final UUID P5 = UUID.fromString("d1000000-0000-4000-8000-000000000005");

    private static DataSource appDs;
    private static DataSource migratorDs;

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável " + name + " não definida");
        }
        return value;
    }

    private static Flyway flyway(String schema, UUID tenantId, String target) {
        var config = Flyway.configure()
                .dataSource(migratorDs)
                .schemas(schema, "public")
                .defaultSchema(schema)
                .createSchemas(false)
                .placeholders(Map.of("tenant_id", tenantId.toString()))
                .locations(TenantMigrator.LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .outOfOrder(false)
                .table("flyway_schema_history");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    @BeforeAll
    static void setUp() throws Exception {
        String url = System.getenv().getOrDefault("ZIIPSAN_IT_JDBC_URL",
                "jdbc:postgresql://127.0.0.1:55439/ziipsan");
        appDs = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("ZIIPSAN_IT_DB_USER", "ziipsan_app"),
                requireEnv("ZIIPSAN_IT_DB_PASSWORD"));
        migratorDs = new DriverManagerDataSource(url,
                System.getenv().getOrDefault("ZIIPSAN_IT_MIGRATOR_USER", "ziipsan_migrator"),
                requireEnv("ZIIPSAN_IT_MIGRATOR_PASSWORD"));

        new CoreMigrationRunner(migratorDs).run(null);
        try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA " + SCHEMA_A + " AUTHORIZATION ziipsan_migrator");
            st.execute("CREATE SCHEMA " + SCHEMA_B + " AUTHORIZATION ziipsan_migrator");
        }
        grantAppPrivilegesOnSchemaCreation(SCHEMA_A);
        grantAppPrivilegesOnSchemaCreation(SCHEMA_B);

        // Schema A: até à V110, semente FEBRABAN, depois até ao fim (V112).
        var upTo110 = flyway(SCHEMA_A, TENANT_A, "110").migrate();
        assertThat(upTo110.migrationsExecuted).as("migrações até à V110").isGreaterThan(0);
        assertThat(flyway(SCHEMA_A, TENANT_A, null).info().current().getVersion().getVersion())
                .as("versão corrente antes da semente").isEqualTo("110");
        grantAppPrivilegesOnTables(SCHEMA_A);
        seedFebrabanRows();
        var toLatest = flyway(SCHEMA_A, TENANT_A, null).migrate();
        assertThat(toLatest.migrationsExecuted).as("só a V112 por aplicar").isEqualTo(1);
        grantAppPrivilegesOnTables(SCHEMA_A);

        // Schema B: travessia limpa do zero (tabela híbrida vazia).
        var clean = new TenantMigrator(migratorDs).migrate(SCHEMA_B, TENANT_B);
        assertThat(clean.success).as("travessia limpa conclui").isTrue();
        grantAppPrivilegesOnTables(SCHEMA_B);
    }

    /**
     * GRANTs do provisionamento real ({@code TenantProvisioningService}): USAGE no
     * schema + default privileges para os objectos que o migrador criar. Sem isto,
     * a role da aplicação não resolve nomes no schema criado pelo migrador.
     */
    private static void grantAppPrivilegesOnSchemaCreation(String schema) throws SQLException {
        try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
            st.execute("GRANT USAGE ON SCHEMA " + schema + " TO ziipsan_app");
            st.execute("ALTER DEFAULT PRIVILEGES FOR ROLE ziipsan_migrator IN SCHEMA " + schema
                    + " GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ziipsan_app");
            st.execute("ALTER DEFAULT PRIVILEGES FOR ROLE ziipsan_migrator IN SCHEMA " + schema
                    + " GRANT USAGE, SELECT ON SEQUENCES TO ziipsan_app");
            st.execute("ALTER DEFAULT PRIVILEGES FOR ROLE ziipsan_migrator IN SCHEMA " + schema
                    + " GRANT EXECUTE ON FUNCTIONS TO ziipsan_app");
        }
    }

    /** GRANTs explícitos pós-migration (idem, sobre as tabelas existentes). */
    private static void grantAppPrivilegesOnTables(String schema) throws SQLException {
        try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
            st.execute("GRANT USAGE ON SCHEMA " + schema + " TO ziipsan_app");
            st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA " + schema
                    + " TO ziipsan_app");
            st.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA " + schema + " TO ziipsan_app");
            st.execute("GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA " + schema + " TO ziipsan_app");
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (migratorDs == null) {
            return;
        }
        try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS " + SCHEMA_A + " CASCADE");
            st.execute("DROP SCHEMA IF EXISTS " + SCHEMA_B + " CASCADE");
        }
    }

    /** Linhas em formato FEBRABAN pré-migration (esquema V6_1→V15_1). */
    private static void seedFebrabanRows() throws SQLException {
        try (Connection c = asTenant(appDs, SCHEMA_A, TENANT_A)) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA_A + ".collection_agreements"
                            + " (id, tenant_id, bank_code, name, agreement_code, document_kind,"
                            + " barcode_layout, valid_from) VALUES (?, ?, '001', 'Convenio Teste',"
                            + " 'AG1', 'BANK_SLIP', '{}'::jsonb, DATE '2026-01-01')")) {
                ps.setObject(1, AGREEMENT);
                ps.setObject(2, TENANT_A);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA_A + ".bank_return_files"
                            + " (id, tenant_id, agreement_id, layout, file_name, nsa, generated_on,"
                            + " bank_code, convenio_code, layout_version, record_count, total_amount,"
                            + " sha256, status) VALUES (?, ?, ?, 'FEBRABAN_150_COLLECTION', 'ret-1.txt',"
                            + " 1, DATE '2026-10-05', '001', 'AG1', 1, 5, 459.15,"
                            + " 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 'RECEIVED')")) {
                ps.setObject(1, RETURN_FILE);
                ps.setObject(2, TENANT_A);
                ps.setObject(3, AGREEMENT);
                ps.executeUpdate();
            }
            // P1: CLASSIFIED + documento-conta (BILL) — deriva CLASSIFICADO + bill_id.
            insertFebraban(c, P1, "CLASSIFIED", "BILL", BILL_1, "100.00", "2026-10-05", RETURN_FILE, true, "rf1-linha-1");
            // P2: DUPLICATE + documento-conta — deriva DUPLICIDADE + bill_id.
            insertFebraban(c, P2, "DUPLICATE", "BILL", BILL_1, "250.50", "2026-10-06", RETURN_FILE, true, "rf1-linha-2");
            // P3: DOC_NOT_FOUND + documento NÃO-conta (GUIA), SEM ficheiro de retorno —
            // deriva NAO_IDENTIFICADO, channel BOLETO na mesma, bill_id NULL.
            insertFebraban(c, P3, "DOC_NOT_FOUND", "GUIA", GUIA_DOC, "75.25", "2026-10-07", null, false, "rf1-linha-3");
            // P4: EXCESS (um dos 12 FEBRABAN sem par P3.1) — preservado verbatim.
            insertFebraban(c, P4, "EXCESS", null, null, "33.40", "2026-10-08", RETURN_FILE, true, "rf1-linha-4");
            // P5: linha com channel já preenchido (PIX) e valores P3.1 parciais —
            // prova o passo 3 (seg/withheld → 0, net → gross, collection_month → dia 1).
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA_A + ".payments (id, tenant_id, agreement_id, amount,"
                            + " paid_on, status, identified, idempotency_key, channel, gross_amount,"
                            + " segregated_tax_amount, withheld_amount, net_amount, collection_month)"
                            + " VALUES (?, ?, ?, 50.00, DATE '2026-10-09', 'OPEN', TRUE,"
                            + " 'pix-sem-fechamento', 'PIX', 50.00, NULL, NULL, NULL, NULL)")) {
                ps.setObject(1, P5);
                ps.setObject(2, TENANT_A);
                ps.setObject(3, AGREEMENT);
                ps.executeUpdate();
            }
            c.commit();
        }
    }

    private static void insertFebraban(Connection c, UUID id, String status, String documentType,
                                       UUID documentId, String amount, String paidOn, UUID returnFile,
                                       boolean identified, String idempotencyKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA_A + ".payments (id, tenant_id, agreement_id, return_file_id,"
                        + " amount, paid_on, status, identified, document_type, document_id,"
                        + " idempotency_key) VALUES (?, ?, ?, ?, ?::numeric, ?::date, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT_A);
            ps.setObject(3, AGREEMENT);
            ps.setObject(4, returnFile);
            ps.setString(5, amount);
            ps.setString(6, paidOn);
            ps.setString(7, status);
            ps.setBoolean(8, identified);
            ps.setString(9, documentType);
            ps.setObject(10, documentId);
            ps.setString(11, idempotencyKey);
            ps.executeUpdate();
        }
    }

    private static Connection asTenant(DataSource ds, String schema, UUID tenant) throws SQLException {
        Connection c = ds.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
            st.execute("SET LOCAL search_path TO " + schema + ", public");
        }
        return c;
    }

    private Map<String, Object> paymentRow(UUID id) throws SQLException {
        try (Connection c = asTenant(appDs, SCHEMA_A, TENANT_A);
             PreparedStatement ps = c.prepareStatement(
                     "SELECT status, channel, gross_amount, segregated_tax_amount, withheld_amount,"
                             + " net_amount, bill_id, collection_month, payer_public_nature, amount,"
                             + " agreement_id, identified, idempotency_key, document_type, document_id"
                             + " FROM payments WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("linha %s existe", id).isTrue();
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("status", rs.getString(1));
                row.put("channel", rs.getString(2));
                row.put("gross", rs.getBigDecimal(3));
                row.put("segregated", rs.getBigDecimal(4));
                row.put("withheld", rs.getBigDecimal(5));
                row.put("net", rs.getBigDecimal(6));
                row.put("billId", rs.getObject(7));
                row.put("collectionMonth", rs.getObject(8));
                row.put("payerPublicNature", rs.getBoolean(9));
                row.put("amount", rs.getBigDecimal(10));
                row.put("agreementId", rs.getObject(11));
                row.put("identified", rs.getObject(12));
                row.put("idempotencyKey", rs.getString(13));
                row.put("documentType", rs.getString(14));
                row.put("documentId", rs.getObject(15));
                c.rollback();
                return row;
            }
        }
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("V112 aplicada com sucesso nos dois schemas; nada por aplicar depois dela")
    void v112AppliedAndNothingPending() throws SQLException {
        try (Connection c = migratorDs.getConnection(); Statement st = c.createStatement()) {
            for (String schema : new String[]{SCHEMA_A, SCHEMA_B}) {
                try (ResultSet rs = st.executeQuery(
                        "SELECT version, success FROM " + schema
                                + ".flyway_schema_history WHERE version = '112'")) {
                    assertThat(rs.next()).as("V112 no histórico de %s", schema).isTrue();
                    assertThat(rs.getBoolean(2)).as("V112 success em %s", schema).isTrue();
                }
            }
        }
        assertThat(new TenantMigrator(migratorDs).migrate(SCHEMA_A, TENANT_A).migrationsExecuted)
                .as("segunda migração é no-op").isEqualTo(0);
        assertThat(new TenantMigrator(migratorDs).migrate(SCHEMA_B, TENANT_B).migrationsExecuted)
                .as("segunda migração é no-op (schema B)").isEqualTo(0);
    }

    @Test
    @DisplayName("PD-5: CLASSIFIED deriva CLASSIFICADO + BOLETO + valores + bill_id + mês dia 1")
    void classifiedDerives() throws SQLException {
        Map<String, Object> row = paymentRow(P1);
        assertThat(row.get("status")).isEqualTo("CLASSIFICADO");
        assertThat(row.get("channel")).isEqualTo("BOLETO");
        assertThat(row.get("gross")).isEqualTo(money("100.00"));
        assertThat(row.get("segregated")).isEqualTo(money("0.00"));
        assertThat(row.get("withheld")).isEqualTo(money("0.00"));
        assertThat(row.get("net")).isEqualTo(money("100.00"));
        assertThat(row.get("billId")).isEqualTo(BILL_1);
        assertThat(String.valueOf(row.get("collectionMonth"))).isEqualTo("2026-10-01");
        assertThat(row.get("payerPublicNature")).isEqualTo(false);
        assertThat(row.get("amount")).isEqualTo(money("100.00"));
        assertThat(row.get("agreementId")).isEqualTo(AGREEMENT);
        assertThat(row.get("identified")).isEqualTo(true);
        assertThat(row.get("idempotencyKey")).isEqualTo("rf1-linha-1");
    }

    @Test
    @DisplayName("PD-5: DUPLICATE deriva DUPLICIDADE + bill_id; DOC_NOT_FOUND deriva NAO_IDENTIFICADO sem bill_id")
    void duplicateAndDocNotFoundDerive() throws SQLException {
        Map<String, Object> p2 = paymentRow(P2);
        assertThat(p2.get("status")).isEqualTo("DUPLICIDADE");
        assertThat(p2.get("channel")).isEqualTo("BOLETO");
        assertThat(p2.get("gross")).isEqualTo(money("250.50"));
        assertThat(p2.get("net")).isEqualTo(money("250.50"));
        assertThat(p2.get("billId")).isEqualTo(BILL_1);
        assertThat(String.valueOf(p2.get("collectionMonth"))).isEqualTo("2026-10-01");

        Map<String, Object> p3 = paymentRow(P3);
        assertThat(p3.get("status")).isEqualTo("NAO_IDENTIFICADO");
        assertThat(p3.get("channel")).isEqualTo("BOLETO");
        assertThat(p3.get("gross")).isEqualTo(money("75.25"));
        assertThat(p3.get("net")).isEqualTo(money("75.25"));
        // Controlo negativo: document_type GUIA (não-conta) deixa bill_id NULL.
        assertThat(p3.get("billId")).isNull();
        assertThat(p3.get("documentType")).isEqualTo("GUIA");
        assertThat(p3.get("documentId")).isEqualTo(GUIA_DOC);
        assertThat(p3.get("identified")).isEqualTo(false);
        assertThat(String.valueOf(p3.get("collectionMonth"))).isEqualTo("2026-10-01");
    }

    @Test
    @DisplayName("PD-2: EXCESS (sem par P3.1) preserva-se verbatim no vocabulário unificado")
    void excessPreservedVerbatim() throws SQLException {
        Map<String, Object> row = paymentRow(P4);
        assertThat(row.get("status")).isEqualTo("EXCESS");
        assertThat(row.get("channel")).isEqualTo("BOLETO");
        assertThat(row.get("gross")).isEqualTo(money("33.40"));
        assertThat(row.get("net")).isEqualTo(money("33.40"));
        assertThat(row.get("billId")).isNull();
    }

    @Test
    @DisplayName("Passo 3: linha com channel preenchido e valores parciais fica fechada (net = gross, mês dia 1)")
    void partialP31RowCompleted() throws SQLException {
        Map<String, Object> row = paymentRow(P5);
        assertThat(row.get("status")).isEqualTo("OPEN");
        assertThat(row.get("channel")).isEqualTo("PIX");
        assertThat(row.get("gross")).isEqualTo(money("50.00"));
        assertThat(row.get("segregated")).isEqualTo(money("0.00"));
        assertThat(row.get("withheld")).isEqualTo(money("0.00"));
        assertThat(row.get("net")).isEqualTo(money("50.00"));
        assertThat(String.valueOf(row.get("collectionMonth"))).isEqualTo("2026-10-01");
    }

    @Test
    @DisplayName("Esquema final: payer_public_nature existe NOT NULL; aperto das 5 + mês; obrigações anuláveis")
    void finalShape() throws SQLException {
        try (Connection c = migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT column_name, is_nullable, column_default, character_maximum_length"
                             + " FROM information_schema.columns WHERE table_schema = ?"
                             + " AND table_name = 'payments' AND column_name IN"
                             + " ('payer_public_nature','channel','gross_amount','segregated_tax_amount',"
                             + " 'withheld_amount','net_amount','collection_month','amount','agreement_id',"
                             + " 'identified','idempotency_key','status')")) {
            ps.setString(1, SCHEMA_A);
            Map<String, String[]> cols = new java.util.HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cols.put(rs.getString(1), new String[]{rs.getString(2), rs.getString(3),
                            rs.getString(4) == null ? null : rs.getString(4)});
                }
            }
            assertThat(cols).as("as 12 colunas existem").hasSize(12);
            for (String notNull : new String[]{"payer_public_nature", "channel", "gross_amount",
                    "segregated_tax_amount", "withheld_amount", "net_amount", "collection_month"}) {
                assertThat(cols.get(notNull)[0]).as("%s NOT NULL", notNull).isEqualTo("NO");
            }
            for (String nullable : new String[]{"amount", "agreement_id", "identified", "idempotency_key"}) {
                assertThat(cols.get(nullable)[0]).as("%s anulável", nullable).isEqualTo("YES");
            }
            assertThat(cols.get("payer_public_nature")[1]).as("default FALSE").isEqualTo("false");
            assertThat(cols.get("status")[1]).as("default RECEBIDO").isEqualTo("'RECEBIDO'::character varying");
            assertThat(cols.get("status")[2]).as("status VARCHAR(30)").isEqualTo("30");
        }
        try (Connection c = migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT pg_get_constraintdef(oid) FROM pg_constraint"
                             + " WHERE conrelid = (? || '.payments')::regclass AND conname = ?")) {
            String def = null;
            ps.setString(1, SCHEMA_A);
            ps.setString(2, "chk_payments_status_unified");
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("CHECK unificado existe").isTrue();
                def = rs.getString(1);
            }
            for (String literal : new String[]{"CLASSIFIED", "DUPLICATE", "DOC_NOT_FOUND", "EXCESS",
                    "TO_WRITE_DOWN", "AMOUNT_MISMATCH", "OPEN", "DUPLICATE_REFUNDED", "TO_ACCOUNT",
                    "DOC_PRESCRIBED", "DOC_PARCELED", "DOC_CANCELED", "DOC_PROCESSING_ERROR",
                    "CREDIT_RECOVERY_CANCELED", "CREDIT_RECOVERY_DUPLICATE",
                    "RECEBIDO", "CLASSIFICADO", "BAIXADO", "DUPLICIDADE", "NAO_IDENTIFICADO", "TRANSFERIDO"}) {
                assertThat(def).as("CHECK contém %s", literal).contains("'" + literal + "'");
            }
            for (String check : new String[]{"chk_payments_boleto_agreement", "chk_payments_boleto_amount",
                    "chk_payments_boleto_identified", "chk_payments_boleto_idempotency",
                    "chk_payments_channel", "chk_payments_gross_amount_positive",
                    "chk_payments_amount_positive"}) {
                ps.setString(1, SCHEMA_A);
                ps.setString(2, check);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("CHECK %s existe", check).isTrue();
                }
            }
        }
    }

    @Test
    @DisplayName("Views: RPT-001 vê a linha derivada CLASSIFICADO; RPT-002 a NAO_IDENTIFICADO; RPT-003 a DUPLICIDADE")
    void viewsSeeDerivedRows() throws SQLException {
        try (Connection c = asTenant(appDs, SCHEMA_A, TENANT_A);
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT data, canal, pagamentos, valor_bruto FROM rpt_arrecadacao_diaria")) {
                assertThat(rs.next()).as("RPT-001 tem uma linha").isTrue();
                assertThat(String.valueOf(rs.getObject(1))).isEqualTo("2026-10-05");
                assertThat(rs.getString(2)).isEqualTo("BOLETO");
                assertThat(rs.getLong(3)).isEqualTo(1L);
                assertThat(rs.getBigDecimal(4)).isEqualByComparingTo(money("100.00"));
                assertThat(rs.next()).as("RPT-001 tem só uma linha").isFalse();
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT payment_id, valor FROM rpt_pagamentos_nao_identificados")) {
                assertThat(rs.next()).as("RPT-002 tem uma linha").isTrue();
                assertThat(rs.getObject(1)).isEqualTo(P3);
                assertThat(rs.getBigDecimal(2)).isEqualByComparingTo(money("75.25"));
                assertThat(rs.next()).as("RPT-002 tem só uma linha").isFalse();
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT payment_id, valor FROM rpt_pagamentos_duplicidade")) {
                assertThat(rs.next()).as("RPT-003 tem uma linha").isTrue();
                assertThat(rs.getObject(1)).isEqualTo(P2);
                assertThat(rs.getBigDecimal(2)).isEqualByComparingTo(money("250.50"));
                assertThat(rs.next()).as("RPT-003 tem só uma linha").isFalse();
            }
            c.rollback();
        }
    }

    @Test
    @DisplayName("Schema B (travessia limpa): V112 corre sobre a híbrida vazia; 0 linhas; mesmo aperto")
    void cleanTraversalOnEmptyHybrid() throws SQLException {
        try (Connection c = asTenant(appDs, SCHEMA_B, TENANT_B);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM payments")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong(1)).as("payments vazia").isEqualTo(0L);
            c.rollback();
        }
        try (Connection c = migratorDs.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT is_nullable FROM information_schema.columns WHERE table_schema = ?"
                             + " AND table_name = 'payments' AND column_name = 'channel'")) {
            ps.setString(1, SCHEMA_B);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("NO");
            }
        }
    }

    // ------------------------------------------------------- controlos negativos

    /**
     * Insere uma linha nova pós-V112. Os quatro campos de obrigação vêm preenchidos
     * por omissão (regime integral); passar {@code null} num deles testa o relaxamento.
     * (Os parâmetros são explícitos — {@code Map.of} recusa valores null.)
     */
    private void insertNewRow(String channel, String status, UUID agreementId,
                              BigDecimal amount, Boolean identified, String idempotencyKey) throws SQLException {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO payments (id, tenant_id, channel, gross_amount, segregated_tax_amount,"
                + " withheld_amount, net_amount, paid_on, collection_month, status, agreement_id, amount,"
                + " identified, idempotency_key) VALUES (?, ?, ?, 10.00, 0, 0, 10.00,"
                + " DATE '2026-10-10', DATE '2026-10-01', ?, ?, ?, ?, ?)";
        try (Connection c = asTenant(appDs, SCHEMA_A, TENANT_A);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT_A);
            ps.setString(3, channel);
            ps.setString(4, status);
            setNullableUuid(ps, 5, agreementId);
            setNullableMoney(ps, 6, amount);
            if (identified == null) {
                ps.setNull(7, java.sql.Types.BOOLEAN);
            } else {
                ps.setBoolean(7, identified);
            }
            ps.setString(8, idempotencyKey);
            ps.executeUpdate();
            c.rollback();
        }
    }

    /** Linha válida por omissão (BOLETO com o regime integral todo preenchido). */
    private void insertValidRow() throws SQLException {
        insertNewRow("BOLETO", "RECEBIDO", AGREEMENT, money("10.00"), true, "nc-valida");
    }

    private static void setNullableUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setNullableMoney(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    @Test
    @DisplayName("Negativo: status fora do vocabulário unificado é recusado (23514)")
    void statusOutsideVocabularyRejected() {
        assertThatThrownBy(() -> insertNewRow("PIX", "INVENTADO", AGREEMENT, money("10.00"), true, "nc-1"))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"))
                .hasMessageContaining("chk_payments_status_unified");
    }

    @Test
    @DisplayName("Negativo: canal BOLETO sem convénio / valor / identificado / chave é recusado (23514)")
    void boletoWithoutObligationsRejected() {
        assertThatThrownBy(() -> insertNewRow("BOLETO", "RECEBIDO", null, money("10.00"), true, "nc-2"))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"))
                .hasMessageContaining("chk_payments_boleto_agreement");
        assertThatThrownBy(() -> insertNewRow("BOLETO", "RECEBIDO", AGREEMENT, null, true, "nc-3"))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"))
                .hasMessageContaining("chk_payments_boleto_amount");
        assertThatThrownBy(() -> insertNewRow("BOLETO", "RECEBIDO", AGREEMENT, money("10.00"), null, "nc-4"))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"))
                .hasMessageContaining("chk_payments_boleto_identified");
        assertThatThrownBy(() -> insertNewRow("BOLETO", "RECEBIDO", AGREEMENT, money("10.00"), true, null))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"))
                .hasMessageContaining("chk_payments_boleto_idempotency");
    }

    @Test
    @DisplayName("Negativo: DEBITO_AUTOMATICO sem convénio é recusado (regime integral, D6)")
    void debitoAutomaticoWithoutAgreementRejected() {
        assertThatThrownBy(() -> insertNewRow("DEBITO_AUTOMATICO", "RECEBIDO", null, money("10.00"), true, "nc-5"))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("23514"))
                .hasMessageContaining("chk_payments_boleto_agreement");
    }

    @Test
    @DisplayName("Positivo: canal PIX sem convénio/valor/identificado/chave escreve; valor FEBRABAN preservado escreve")
    void pixWithoutObligationsWrites() throws SQLException {
        insertNewRow("PIX", "RECEBIDO", null, null, null, null);
        insertNewRow("BOLETO", "CLASSIFIED", AGREEMENT, money("10.00"), true, "nc-6");
    }
}
