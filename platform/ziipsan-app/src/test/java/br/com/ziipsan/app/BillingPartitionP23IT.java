package br.com.ziipsan.app;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.3 (V4_14) no PostgreSQL real, sobre o tenant provisionado.
 *
 * <p>Prova ao vivo: bills e bill_items sao particionadas por mes de referencia com PK
 * (tenant_id, reference_month, id); as filhas ganham reference_month com trigger de
 * preenchimento (debito/credito/tributo e origem: inserir sem o mes continua
 * funcionando, e mes divergente e recusado; bill_items e a excecao -- particionada,
 * o roteamento precede o trigger, de modo que item sem mes falha e item com mes
 * divergente e recusado); mes sem particao falha ate bills_create_partition criar;
 * 100 mil contas de um lote (run_id) sao auditadas e a consulta da trilha do lote
 * inteiro e podada para a particao do lote (plano de execucao conferido).
 *
 * <p>Tudo numa transacao desfeita no fim de cada teste, exceto a criacao de particoes
 * (DDL do migrador, removida no teardown quando fora da janela da migration).
 * Nenhum dado de teste sobrevive. Dados 100% sinteticos.
 */
class BillingPartitionP23IT {

    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

    /** Mes do lote de volume (criado explicitamente pela funcao, fora da janela). */
    private static final String LOT_MONTH = "2026-03-01";
    /** Mes fora de qualquer janela, para provar a falta de particao DEFAULT. */
    private static final String FAR_MONTH = "2035-06-01";

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        try (Connection c = migrator()) {
            try (Statement st = c.createStatement()) {
                // DETACH antes do DROP: as FKs do pai (fk_bill_origin, fk_bitem_bill,
                // fk_bdebit_bill, fk_bcredit_bill, fk_btax_bill) dependem da particao
                // e um DROP direto falha com "other objects depend on it".
                st.execute("ALTER TABLE " + SCHEMA + ".bills DETACH PARTITION " + SCHEMA + ".bills_2035_06");
                st.execute("DROP TABLE IF EXISTS " + SCHEMA + ".bills_2035_06");
                st.execute("ALTER TABLE " + SCHEMA + ".bill_items DETACH PARTITION " + SCHEMA + ".bill_items_2035_06");
                st.execute("DROP TABLE IF EXISTS " + SCHEMA + ".bill_items_2035_06");
            }
        } catch (SQLException ignored) {
            // Banco descartavel; a limpeza e melhor esforco.
        }
    }

    @Test
    @DisplayName("V4_14: bills e bill_items particionadas, PK com o mes, FKs e triggers")
    void partitionedStructure() throws SQLException {
        try (Connection c = migrator();
             Statement st = c.createStatement()) {
            assertThat(count(st, "SELECT count(*) FROM pg_partitioned_table WHERE partrelid = '"
                    + SCHEMA + ".bills'::regclass")).isOne();
            assertThat(count(st, "SELECT count(*) FROM pg_partitioned_table WHERE partrelid = '"
                    + SCHEMA + ".bill_items'::regclass")).isOne();
            try (ResultSet rs = st.executeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint"
                    + " WHERE conrelid = '" + SCHEMA + ".bills'::regclass AND contype = 'p'")) {
                rs.next();
                assertThat(rs.getString(1)).contains("PRIMARY KEY (tenant_id, reference_month, id)");
            }
            // Janela da migration: 2026-01 existe para as duas tabelas.
            assertThat(count(st, "SELECT count(*) FROM pg_inherits WHERE inhparent = '"
                    + SCHEMA + ".bills'::regclass AND inhrelid = '" + SCHEMA + ".bills_2026_01'::regclass")).isOne();
            assertThat(count(st, "SELECT count(*) FROM pg_inherits WHERE inhparent = '"
                    + SCHEMA + ".bill_items'::regclass AND inhrelid = '" + SCHEMA + ".bill_items_2026_01'::regclass")).isOne();
            // Colunas novas e FKs com o mes.
            assertThat(columnNullable(st, "bills", "origin_reference_month")).isEqualTo("YES");
            assertThat(columnNullable(st, "bill_debits", "reference_month")).isEqualTo("NO");
            assertThat(columnNullable(st, "bill_credits", "reference_month")).isEqualTo("NO");
            assertThat(columnNullable(st, "bill_taxes", "reference_month")).isEqualTo("NO");
            assertThat(constraintDef(st, "bill_items", "fk_bitem_bill"))
                    .contains("FOREIGN KEY (tenant_id, reference_month, bill_id) REFERENCES");
            assertThat(constraintDef(st, "bills", "fk_bill_origin"))
                    .contains("FOREIGN KEY (tenant_id, origin_reference_month, origin_bill_id) REFERENCES");
            // Triggers de preenchimento nas 5 tabelas-pai. O filtro por tgrelid e
            // necessario: o PostgreSQL clona o trigger da tabela particionada para
            // cada particao (e os clones NAO sao tgisinternal), de modo que contar
            // so por nome contaria 1 + numero de particoes, nao 5. A comparacao e
            // por OID (tgrelid IN ...::regclass): regclass::text rende sem schema
            // quando o schema esta no search_path da sessao, e o filtro por texto
            // com schema nao casaria.
            assertThat(count(st, "SELECT count(*) FROM pg_trigger WHERE tgname IN "
                    + "('trg_bills_fill_origin_month', 'trg_bill_items_fill_month', 'trg_bill_debits_fill_month', "
                    + "'trg_bill_credits_fill_month', 'trg_bill_taxes_fill_month') AND tgrelid IN ('"
                    + SCHEMA + ".bills'::regclass, '" + SCHEMA + ".bill_items'::regclass, '"
                    + SCHEMA + ".bill_debits'::regclass, '" + SCHEMA + ".bill_credits'::regclass, '"
                    + SCHEMA + ".bill_taxes'::regclass)")).isEqualTo(5);
            // Grants refeitos na recriacao (o provisionamento concede depois, mas o
            // tenant existente que sobe so com o migrador depende da migration).
            try (ResultSet rs = st.executeQuery("SELECT has_table_privilege('ziipsan_app', '"
                    + SCHEMA + ".bills', 'SELECT, INSERT, UPDATE, DELETE')")) {
                rs.next();
                assertThat(rs.getBoolean(1)).isTrue();
            }
            try (ResultSet rs = st.executeQuery("SELECT has_table_privilege('ziipsan_app', '"
                    + SCHEMA + ".bill_items', 'SELECT, INSERT, UPDATE, DELETE')")) {
                rs.next();
                assertThat(rs.getBoolean(1)).isTrue();
            }
        }
    }

    @Test
    @DisplayName("V4_14: trigger preenche o mes; mes divergente recusado; conta sem lote aceita")
    void fillTriggersAndLegacyInserts() throws SQLException {
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, "P23-COMPAT-01");
                UUID category = insertCategory(c, "P23-C");
                UUID tariffVersion = insertTariffVersion(c, "P23-T");
                // Padrao existente (P1): conta sem lote continua aceita.
                UUID bill = insertBill(c, unit, "2026-11-01", null);
                // Item COM o mes da conta: aceito (o mes e obrigatorio no insert --
                // bill_items e particionada e o roteamento precede o trigger, de modo
                // que um item sem mes falha com "no partition" antes de ser preenchido).
                UUID item = insertItem(c, bill, "2026-11-01", category, tariffVersion);
                assertThat(itemMonth(c, item)).isEqualTo("2026-11-01");
                // Item sem mes: recusado pelo roteamento, nao preenchido pelo trigger.
                var savepoint0 = c.setSavepoint();
                assertThatThrownBy(() -> insertItem(c, bill, null, category, tariffVersion))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("no partition of relation");
                c.rollback(savepoint0);
                // Item com mes divergente: recusado pelo trigger.
                var savepoint = c.setSavepoint();
                assertThatThrownBy(() -> insertItem(c, bill, "2026-10-01", category, tariffVersion))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("difere");
                c.rollback(savepoint);
                // Debito, credito e tributo sem mes: o trigger preenche (tabelas nao
                // particionadas: aqui o preenchimento funciona).
                assertThat(debitMonth(c, insertDebit(c, bill, unit))).isEqualTo("2026-11-01");
                assertThat(creditMonth(c, insertCredit(c, bill, unit))).isEqualTo("2026-11-01");
                assertThat(taxMonth(c, insertTax(c, bill))).isEqualTo("2026-11-01");
                // Origem sem mes: o trigger preenche com o mes da conta de origem.
                // Segunda unidade: o indice parcial permite uma so conta viva por
                // unidade e mes, e a conta de origem ja ocupa 2026-11 da primeira.
                UUID unit2 = insertUnit(c, "P23-COMPAT-02");
                UUID rectified = insertBillWithOrigin(c, unit2, "2026-11-01", bill, null);
                assertThat(billOriginMonth(c, rectified)).isEqualTo("2026-11-01");
                // Origem com mes divergente: recusada.
                var savepoint2 = c.setSavepoint();
                assertThatThrownBy(() -> insertBillWithOrigin(c, unit2, "2026-11-01", bill, "2026-10-01"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("difere");
                c.rollback(savepoint2);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("V4_14: mes sem particao falha; bills_create_partition resolve; sem DEFAULT")
    void missingPartitionFailsUntilCreated() throws SQLException {
        // Tx 1: prova que o mes sem particao falha.
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, "P23-FAR-01");
                // A falha esperada aborta a transacao: sem savepoint, o
                // insert seguinte falharia com "current transaction is
                // aborted" (mesmo padrao ja usado neste arquivo).
                var savepoint = c.setSavepoint();
                assertThatThrownBy(() -> insertBill(c, unit, FAR_MONTH, null))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("no partition of relation");
                c.rollback(savepoint);
            } finally {
                c.rollback();
            }
        }
        // A criacao da particao NAO pode rodar com a transacao do tenant
        // aberta: CREATE TABLE ... PARTITION OF exige ShareRowExclusiveLock
        // na tabela referenciada pela FK (consumer_units), e a transacao do
        // tenant detem RowExclusiveLock nela pelo insert da unidade. O
        // migrador bloquearia ate o idle-in-transaction timeout de 30 s
        // (security.sql) matar a conexao do teste — provado na integracao
        // P2A com pg_stat_activity (migrador em Lock/relation) e pg_locks
        // (ShareRowExclusiveLock nao concedido em consumer_units). Por isso
        // a tx 1 termina antes do DDL e a tx 2 recomeca depois dele.
        try (Connection m = migrator(); Statement st = m.createStatement()) {
            st.execute("SELECT " + SCHEMA + ".bills_create_partition(DATE '" + FAR_MONTH + "')");
        }
        // Tx 2: com a particao criada, o insert passa. A unidade e
        // reinserida (a tx 1 fez rollback; a matricula volta a estar livre).
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, "P23-FAR-01");
                UUID bill = insertBill(c, unit, FAR_MONTH, null);
                assertThat(bill).isNotNull();
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("P2.3: 100 mil contas de um lote auditadas por run_id, trilha podada a particao do lote")
    void hundredThousandBillsAuditedByRunIdWithPrunedTrail() throws SQLException {
        UUID runId = UUID.randomUUID();
        UUID batch;
        try (Connection m = migrator(); Statement st = m.createStatement()) {
            st.execute("SELECT " + SCHEMA + ".bills_create_partition(DATE '" + LOT_MONTH + "')");
        }
        try (Connection c = asTenant()) {
            try {
                batch = insertBatch(c, runId, LOT_MONTH);
                UUID category = insertCategory(c, "P23-VOL-C");
                UUID tariffVersion = insertTariffVersion(c, "P23-VOL-T");
                disableAuditTriggers();
                disableFillMonthTrigger();
                try {
                    insertVolumeUnits(c, 100_000, "P23VOL-");
                    insertVolumeBills(c, batch);
                    insertVolumeItems(c, batch, category, tariffVersion);
                } finally {
                    enableFillMonthTrigger();
                    enableAuditTriggers();
                }
                // Auditoria por run_id: contagem e total do lote inteiro.
                assertThat(countBillsOfBatch(c, batch)).isEqualTo(100_000);
                assertThat(sumBillsOfBatch(c, batch)).isEqualTo("10000000.00");
                assertThat(countItemsOfBatch(c, batch)).isEqualTo(100_000);
                assertThat(countBillsOfRun(c, runId)).isEqualTo(100_000);
            } finally {
                c.rollback();
            }
        }
        // Plano da trilha do lote: literais, nao parametros (a poda em tempo de
        // planejamento so acontece com constantes, como no AuditPartitionTest).
        String plan;
        try (Connection m = migrator(); Statement st = m.createStatement();
             ResultSet rs = st.executeQuery("EXPLAIN (COSTS OFF) SELECT b.id FROM " + SCHEMA + ".bills b"
                     + " WHERE b.tenant_id = '" + TENANT + "'"
                     + " AND b.reference_month = DATE '" + LOT_MONTH + "'"
                     + " AND b.audit_batch_id = '" + batch + "'")) {
            StringBuilder out = new StringBuilder();
            while (rs.next()) {
                out.append(rs.getString(1)).append('\n');
            }
            plan = out.toString();
        }
        assertThat(plan)
                .contains("bills_2026_03")
                .doesNotContain("bills_2026_01")
                .doesNotContain("bills_2026_02")
                .doesNotContain("bills_2026_04");
    }

    // ------------------------------------------------------------------

    /** Conexao do migrador com o schema do tenant em primeiro lugar no search_path. */
    private static Connection migrator() throws SQLException {
        Connection c = ProvisionedTenant.migratorDs.getConnection();
        try (Statement st = c.createStatement()) {
            st.execute("SET search_path TO " + SCHEMA + ", public");
        }
        return c;
    }

    private static Connection asTenant() throws SQLException {
        Connection c = ProvisionedTenant.appDs.getConnection();
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + TENANT + "', true)");
        }
        return c;
    }

    private static int count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String columnNullable(Statement st, String table, String column) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT is_nullable FROM information_schema.columns"
                + " WHERE table_schema = '" + SCHEMA + "' AND table_name = '" + table
                + "' AND column_name = '" + column + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static String constraintDef(Statement st, String table, String constraint) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint"
                + " WHERE conrelid = '" + SCHEMA + "." + table + "'::regclass AND conname = '" + constraint + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static UUID insertUnit(Connection c, String matricula) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".consumer_units (id, tenant_id, registration_number, check_digit) VALUES (?, ?, ?, '0')")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setString(3, matricula);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertCategory(Connection c, String code) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".categories (id, tenant_id, code, name) VALUES (?, ?, ?, 'Categoria de teste')")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setString(3, code);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertTariffVersion(Connection c, String tariffCode) throws SQLException {
        UUID tariff = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".tariffs (id, tenant_id, code, name, calc_type) VALUES (?, ?, ?, 'Tarifa de teste', 'BLOCKS')")) {
            ps.setObject(1, tariff);
            ps.setObject(2, TENANT);
            ps.setString(3, tariffCode);
            ps.executeUpdate();
        }
        UUID version = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".tariff_versions (id, tenant_id, tariff_id, valid_from) VALUES (?, ?, ?, DATE '2026-01-01')")) {
            ps.setObject(1, version);
            ps.setObject(2, TENANT);
            ps.setObject(3, tariff);
            ps.executeUpdate();
        }
        return version;
    }

    private static UUID insertBill(Connection c, UUID unit, String month, UUID batch) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".bills (id, tenant_id, unit_id, reference_month, status, water_value, "
                        + "sewer_value, debits_value, credits_value, taxes_value, total_value, audit_batch_id) "
                        + "VALUES (?, ?, ?, DATE '" + month + "', 'NORMAL', 100.00, 0, 0, 0, 0, 100.00, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setObject(3, unit);
            ps.setObject(4, batch);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertBillWithOrigin(Connection c, UUID unit, String month, UUID origin, String originMonth) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".bills (id, tenant_id, unit_id, reference_month, status, water_value, "
                        + "sewer_value, debits_value, credits_value, taxes_value, total_value, origin_bill_id, origin_reference_month) "
                        + "VALUES (?, ?, ?, DATE '" + month + "', 'NORMAL', 100.00, 0, 0, 0, 0, 100.00, ?, "
                        + (originMonth == null ? "NULL" : "DATE '" + originMonth + "'") + ")")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setObject(3, unit);
            ps.setObject(4, origin);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertItem(Connection c, UUID bill, String month, UUID category, UUID tariffVersion) throws SQLException {
        UUID id = UUID.randomUUID();
        String monthSql = month == null ? "NULL" : "DATE '" + month + "'";
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".bill_items (id, tenant_id, bill_id, reference_month, service_kind, "
                        + "category_id, tariff_version_id, period_from, period_to, proration_factor, line_type, "
                        + "economies, social_economies, from_m3, consumption, gross_value, discount_value, net_value) "
                        + "VALUES (?, ?, ?, " + monthSql + ", 'WATER', ?, ?, DATE '2026-11-01', DATE '2026-11-30', "
                        + "1.0000, 'MINIMUM', 1, 0, 0, 0, 100.00, 0, 100.00)")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setObject(3, bill);
            ps.setObject(4, category);
            ps.setObject(5, tariffVersion);
            ps.executeUpdate();
        }
        return id;
    }

    private static String itemMonth(Connection c, UUID item) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT reference_month FROM " + SCHEMA + ".bill_items WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, item);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDate(1).toString();
            }
        }
    }

    private static UUID insertDebit(Connection c, UUID bill, UUID unit) throws SQLException {
        UUID receivable = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".debit_receivables (id, tenant_id, unit_id, type_code, total_amount, installments, generates_bill) "
                        + "VALUES (?, ?, ?, 'P23-DEB', 50.00, 1, TRUE)")) {
            ps.setObject(1, receivable);
            ps.setObject(2, TENANT);
            ps.setObject(3, unit);
            ps.executeUpdate();
        }
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".bill_debits (id, tenant_id, bill_id, receivable_id, type_code, installment_no, installment_total, amount) "
                        + "VALUES (?, ?, ?, ?, 'P23-DEB', 1, 1, 50.00)")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setObject(3, bill);
            ps.setObject(4, receivable);
            ps.executeUpdate();
        }
        return id;
    }

    private static String debitMonth(Connection c, UUID debit) throws SQLException {
        return childMonth(c, "bill_debits", debit);
    }

    private static UUID insertCredit(Connection c, UUID bill, UUID unit) throws SQLException {
        UUID receivable = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".credit_receivables (id, tenant_id, unit_id, origin, total_amount, installments) "
                        + "VALUES (?, ?, ?, 'P23-CRED', 10.00, 1)")) {
            ps.setObject(1, receivable);
            ps.setObject(2, TENANT);
            ps.setObject(3, unit);
            ps.executeUpdate();
        }
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".bill_credits (id, tenant_id, bill_id, receivable_id, origin, applied_amount, residual_after) "
                        + "VALUES (?, ?, ?, ?, 'P23-CRED', 10.00, 0)")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setObject(3, bill);
            ps.setObject(4, receivable);
            ps.executeUpdate();
        }
        return id;
    }

    private static String creditMonth(Connection c, UUID credit) throws SQLException {
        return childMonth(c, "bill_credits", credit);
    }

    private static UUID insertTax(Connection c, UUID bill) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".bill_taxes (id, tenant_id, bill_id, tax_code, apply_order, rate_pct, base_amount, amount) "
                        + "VALUES (?, ?, ?, 'P23-TAX', 1, 1.0000, 100.00, 1.00)")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setObject(3, bill);
            ps.executeUpdate();
        }
        return id;
    }

    private static String taxMonth(Connection c, UUID tax) throws SQLException {
        return childMonth(c, "bill_taxes", tax);
    }

    private static String childMonth(Connection c, String table, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT reference_month FROM " + SCHEMA + "." + table + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDate(1).toString();
            }
        }
    }

    private static String billOriginMonth(Connection c, UUID bill) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT origin_reference_month FROM " + SCHEMA + ".bills WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, bill);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDate(1).toString();
            }
        }
    }

    private static UUID insertBatch(Connection c, UUID runId, String month) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".bill_audit_batches (id, tenant_id, run_id, reference_month, status) "
                        + "VALUES (?, ?, ?, DATE '" + month + "', 'OPEN')")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setObject(3, runId);
            ps.executeUpdate();
        }
        return id;
    }

    /**
     * O trigger de auditoria gravaria 200 mil linhas em public.audit_log para a carga
     * de volume (uma por conta e por item). A prova de volume e sobre o lote, nao sobre
     * o audit_log: o trigger e desligado so durante a carga (pai e particoes) e
     * religado em seguida, no finally do teste. A P2-A (integracao) acrescentou
     * consumer_units a lista: o lote insere 100 mil unidades num unico
     * INSERT...SELECT e o trigger de auditoria de consumer_units estourava o
     * statement_timeout de 5 s do papel da app no meio da carga (o erro
     * aparecia em audit_redact durante o insert das unidades). Nenhuma
     * assercao do teste le o audit_log: contagem, soma e poda do plano sao
     * sobre bills/bill_items e o lote.
     */
    private static void disableAuditTriggers() throws SQLException {
        setAuditTriggers(false);
    }

    private static void enableAuditTriggers() throws SQLException {
        setAuditTriggers(true);
    }

    private static void setAuditTriggers(boolean enable) throws SQLException {
        String verb = enable ? "ENABLE" : "DISABLE";
        try (Connection m = migrator(); Statement st = m.createStatement()) {
            st.execute("ALTER TABLE " + SCHEMA + ".bills " + verb + " TRIGGER audit_trigger");
            st.execute("ALTER TABLE " + SCHEMA + ".bill_items " + verb + " TRIGGER audit_trigger");
            st.execute("ALTER TABLE " + SCHEMA + ".consumer_units " + verb + " TRIGGER audit_trigger");
            // Um Statement so mantem um ResultSet aberto: executar o ALTER
            // no mesmo Statement fecharia o ResultSet no primeiro loop
            // ("This ResultSet is closed"). Coleta primeiro, altera depois.
            List<String> partitions = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT inhrelid::regclass::text FROM pg_inherits WHERE inhparent IN ('"
                    + SCHEMA + ".bills'::regclass, '" + SCHEMA + ".bill_items'::regclass)")) {
                while (rs.next()) {
                    partitions.add(rs.getString(1));
                }
            }
            for (String part : partitions) {
                st.execute("ALTER TABLE " + part + " " + verb + " TRIGGER audit_trigger");
            }
        }
    }

    /**
     * Carga em lotes de 10 mil, pelo mesmo motivo dos lotes de contas e
     * itens (statement_timeout de 5 s do papel da app): o INSERT...SELECT
     * unico de 100 mil unidades ficou no limite do timeout (passou numa
     * rodada standalone, estourou em outra — variacao de carga da VM).
     * Cada lote cabe com folga; o total final segue assertado.
     */
    /**
     * O trigger trg_bill_items_fill_month e desligado SO durante a carga
     * de volume (e religado no finally, junto com os de auditoria). Motivo
     * medido em scratch: a busca do mes da conta-pai e SQL dinamico por
     * (tenant_id, id) sem a chave de particao, e o plano escolhe SEQ SCAN
     * da particao do lote — ~9,5 ms por linha. Com ele ativo, qualquer
     * lote com mais de ~500 itens estoura os 5 s do papel da app. A carga
     * e consistente por construcao (todo item carrega LOT_MONTH, o mes das
     * contas do lote), e o comportamento do trigger segue coberto pelo
     * teste dedicado fillTriggersAndLegacyInserts — nenhuma assercao do
     * teste de volume le o mes preenchido pelo trigger. As FKs continuam
     * ativas (sao constraints, nao triggers). Mesmo precedente do
     * desligamento dos triggers de auditoria durante a carga.
     */
    private static void disableFillMonthTrigger() throws SQLException {
        setFillMonthTrigger(false);
    }

    private static void enableFillMonthTrigger() throws SQLException {
        setFillMonthTrigger(true);
    }

    private static void setFillMonthTrigger(boolean enable) throws SQLException {
        String verb = enable ? "ENABLE" : "DISABLE";
        try (Connection m = migrator(); Statement st = m.createStatement()) {
            st.execute("ALTER TABLE " + SCHEMA + ".bill_items " + verb + " TRIGGER trg_bill_items_fill_month");
        }
    }

    private static void insertVolumeUnits(Connection c, int count, String prefix) throws SQLException {
        int chunkSize = 10_000;
        int total = 0;
        for (int from = 1; from <= count; from += chunkSize) {
            int to = Math.min(from + chunkSize - 1, count);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".consumer_units (id, tenant_id, registration_number, check_digit) "
                            + "SELECT uuid_generate_v4(), ?, '" + prefix + "' || g, '0' FROM generate_series(?, ?) g")) {
                ps.setObject(1, TENANT);
                ps.setInt(2, from);
                ps.setInt(3, to);
                total += ps.executeUpdate();
            }
        }
        assertThat(total).isEqualTo(count);
    }

    /**
     * Carga em lotes de 10 mil: um unico INSERT...SELECT de 100 mil contas
     * estoura o statement_timeout de 5 s do papel da app (security.sql, por
     * desenho) no cheque de FK por linha. Cada lote cabe com folga no limite
     * e o total final segue assertado em 100 mil — nenhuma assercao muda,
     * so o tamanho do statement. O mesmo vale para os itens.
     */
    private static void insertVolumeBills(Connection c, UUID batch) throws SQLException {
        int total = 0;
        for (int chunk = 0; chunk < 10; chunk++) {
            int from = chunk * 10_000 + 1;
            int to = (chunk + 1) * 10_000;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".bills (tenant_id, unit_id, reference_month, status, water_value, "
                            + "sewer_value, debits_value, credits_value, taxes_value, total_value, audit_batch_id) "
                            + "SELECT ?, u.id, DATE '" + LOT_MONTH + "', 'NORMAL', 100.00, 0, 0, 0, 0, 100.00, ? "
                            + "FROM " + SCHEMA + ".consumer_units u "
                            + "WHERE u.tenant_id = ? AND u.registration_number LIKE 'P23VOL-%' "
                            + "AND CAST(substring(u.registration_number FROM 8) AS INTEGER) BETWEEN ? AND ?")) {
                ps.setObject(1, TENANT);
                ps.setObject(2, batch);
                ps.setObject(3, TENANT);
                ps.setInt(4, from);
                ps.setInt(5, to);
                total += ps.executeUpdate();
            }
        }
        assertThat(total).isEqualTo(100_000);
    }

    private static void insertVolumeItems(Connection c, UUID batch, UUID category, UUID tariffVersion) throws SQLException {
        // Sem OFFSET e sem ANY: ambos re-escaneiam as 100 mil contas do
        // lote a cada chunk (o PG 17 nao faz skip scan; o filtro por
        // audit_batch_id esta fora do indice e o ANY espalhado exige
        // varrer o range inteiro do tenant na particao). Busca-se a lista
        // de contas uma vez, ordenada, e cada lote usa um RANGE CONTIGUO
        // de ids (id > anterior AND id <= ultimo): o plano e um range
        // scan de exatamente 1 mil entradas, sem re-escaneamento. O filtro
        // por mes na lista e nos lotes permite o pruning para a particao
        // do lote (2026_03 e exclusiva do teste de volume neste schema).
        // O ORDER BY b.id e ESSENCIAL, nao cosmetico: sem ele o planner
        // escolhe o indice (tenant, mes, status) e filtra por id visitando
        // as 100 mil contas da particao por lote (~6,4 s por lote, provado
        // com auto_explain na integracao P2A); com ele, o planner usa a PK
        // (tenant, mes, id) como range scan ordenado, sem sort e sem filtro.
        // O trigger bills_fill_child_month esta DESLIGADO durante a carga
        // (ver disableFillMonthTrigger) — com ele ativo, o custo por linha
        // (~9,5 ms de seq scan) estouraria os 5 s em qualquer lote. O
        // total final segue assertado em 100 mil.
        // Nota de produto (para a P2.5, nao deste commit): esse custo por
        // linha vale tambem em producao — um trigger estatico, ou o uso
        // de NEW.reference_month quando informado, eliminaria o replanejo.
        List<UUID> billIds = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT b.id FROM " + SCHEMA + ".bills b "
                        + "WHERE b.tenant_id = ? AND b.reference_month = DATE '" + LOT_MONTH + "' "
                        + "AND b.audit_batch_id = ? ORDER BY b.id")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, batch);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    billIds.add((UUID) rs.getObject(1));
                }
            }
        }
        // Com o trigger de preenchimento desligado durante a carga (ver
        // disableFillMonthTrigger), o custo por linha cai para o cheque de
        // FK + indices — mas, nesta VM carregada, mesmo lotes de 10 mil
        // estouraram os 5 s. Lotes de 1 mil dao 10x de margem sobre o
        // ponto de falha observado; o total final segue assertado.
        assertThat(billIds).hasSize(100_000);
        int total = 0;
        for (int from = 0; from < billIds.size(); from += 1_000) {
            int to = Math.min(from + 1_000, billIds.size()) - 1;
            String rangePredicate = from == 0
                    ? "b.id <= ?"
                    : "b.id > ? AND b.id <= ?";
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".bill_items (tenant_id, bill_id, reference_month, service_kind, "
                            + "category_id, tariff_version_id, period_from, period_to, proration_factor, line_type, "
                            + "economies, social_economies, from_m3, consumption, gross_value, discount_value, net_value) "
                            + "SELECT ?, b.id, DATE '" + LOT_MONTH + "', 'WATER', ?, ?, DATE '" + LOT_MONTH + "', DATE '2026-03-31', "
                            + "1.0000, 'MINIMUM', 1, 0, 0, 0, 100.00, 0, 100.00 "
                            + "FROM " + SCHEMA + ".bills b WHERE b.tenant_id = ? AND b.reference_month = DATE '" + LOT_MONTH + "' "
                            + "AND " + rangePredicate + " ORDER BY b.id")) {
                int param = 1;
                ps.setObject(param++, TENANT);
                ps.setObject(param++, category);
                ps.setObject(param++, tariffVersion);
                ps.setObject(param++, TENANT);
                if (from > 0) {
                    ps.setObject(param++, billIds.get(from - 1));
                }
                ps.setObject(param, billIds.get(to));
                total += ps.executeUpdate();
            }
        }
        assertThat(total).isEqualTo(100_000);
    }

    private static int countBillsOfBatch(Connection c, UUID batch) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM " + SCHEMA + ".bills WHERE tenant_id = ? AND audit_batch_id = ?")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, batch);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static String sumBillsOfBatch(Connection c, UUID batch) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT sum(total_value) FROM " + SCHEMA + ".bills WHERE tenant_id = ? AND audit_batch_id = ?")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, batch);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1).toPlainString();
            }
        }
    }

    private static int countItemsOfBatch(Connection c, UUID batch) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM " + SCHEMA + ".bill_items i JOIN " + SCHEMA + ".bills b "
                        + "ON b.tenant_id = i.tenant_id AND b.id = i.bill_id "
                        + "WHERE i.tenant_id = ? AND b.audit_batch_id = ?")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, batch);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int countBillsOfRun(Connection c, UUID runId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM " + SCHEMA + ".bills b JOIN " + SCHEMA + ".bill_audit_batches a "
                        + "ON a.tenant_id = b.tenant_id AND a.id = b.audit_batch_id "
                        + "WHERE b.tenant_id = ? AND a.run_id = ?")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, runId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
