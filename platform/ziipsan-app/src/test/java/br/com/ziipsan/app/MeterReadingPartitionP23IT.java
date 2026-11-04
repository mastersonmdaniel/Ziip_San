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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.3 (V5_4) no PostgreSQL real, sobre o tenant provisionado.
 *
 * <p>Prova ao vivo: meter_readings e particionada por mes de referencia com PK
 * (tenant_id, reference, id); a chave de idempotencia e unica por (tenant, mes, chave)
 * e parcial (so linhas com chave); leitura reenviada com a mesma chave no mesmo mes
 * nao duplica, a mesma chave em outro mes e outra leitura, e leituras sem chave nao
 * se dedupam; consumption_records ganha reading_reference com trigger de
 * preenchimento (inserir sem o mes continua funcionando, e mes divergente e
 * recusado); mes sem particao falha ate meter_readings_create_partition criar.
 *
 * <p>Tudo numa transacao desfeita no fim de cada teste, exceto a criacao de particoes
 * (DDL do migrador, removida no teardown quando fora da janela da migration).
 * Nenhum dado de teste sobrevive. Dados 100% sinteticos.
 */
class MeterReadingPartitionP23IT {

    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

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
                // DETACH antes do DROP: a FK de consumption_records para o pai
                // depende da particao e um DROP direto falha com
                // "other objects depend on it".
                st.execute("ALTER TABLE " + SCHEMA + ".meter_readings DETACH PARTITION " + SCHEMA + ".meter_readings_2035_06");
                st.execute("DROP TABLE IF EXISTS " + SCHEMA + ".meter_readings_2035_06");
            }
        } catch (SQLException ignored) {
            // Banco descartavel; a limpeza e melhor esforco.
        }
    }

    @Test
    @DisplayName("V5_4: meter_readings particionada, PK com o mes, indice de idempotencia e FK da filha")
    void partitionedStructure() throws SQLException {
        try (Connection c = migrator();
             Statement st = c.createStatement()) {
            assertThat(count(st, "SELECT count(*) FROM pg_partitioned_table WHERE partrelid = '"
                    + SCHEMA + ".meter_readings'::regclass")).isOne();
            try (ResultSet rs = st.executeQuery("SELECT pg_get_constraintdef(oid) FROM pg_constraint"
                    + " WHERE conrelid = '" + SCHEMA + ".meter_readings'::regclass AND contype = 'p'")) {
                rs.next();
                assertThat(rs.getString(1)).contains("PRIMARY KEY (tenant_id, reference, id)");
            }
            // Janela da migration: 2026-01 existe.
            assertThat(count(st, "SELECT count(*) FROM pg_inherits WHERE inhparent = '"
                    + SCHEMA + ".meter_readings'::regclass AND inhrelid = '" + SCHEMA
                    + ".meter_readings_2026_01'::regclass")).isOne();
            // Indice de idempotencia: unico e parcial (so linhas com chave).
            try (ResultSet rs = st.executeQuery("SELECT indexdef FROM pg_indexes WHERE schemaname = '"
                    + SCHEMA + "' AND indexname = 'idx_readings_idempotency'")) {
                assertThat(rs.next()).isTrue();
                String def = rs.getString(1);
                assertThat(def).contains("UNIQUE");
                assertThat(def).contains("(tenant_id, reference, idempotency_key)");
                // pg_indexes rende o predicado parcial com parenteses.
                assertThat(def).contains("WHERE (idempotency_key IS NOT NULL)");
            }
            // consumption_records: coluna nova (NULL quando reading_id e NULL) e FK com o mes.
            assertThat(columnNullable(st, "consumption_records", "reading_reference")).isEqualTo("YES");
            assertThat(constraintDef(st, "consumption_records", "fk_consumption_reading"))
                    .contains("FOREIGN KEY (tenant_id, reading_reference, reading_id) REFERENCES");
            // Trigger de preenchimento.
            assertThat(count(st, "SELECT count(*) FROM pg_trigger WHERE tgname = "
                    + "'trg_consumption_fill_reading_month' AND NOT tgisinternal")).isOne();
            // Grant refeito na recriacao (o provisionamento concede depois, mas o
            // tenant existente que sobe so com o migrador depende da migration).
            try (ResultSet rs = st.executeQuery("SELECT has_table_privilege('ziipsan_app', '"
                    + SCHEMA + ".meter_readings', 'SELECT, INSERT, UPDATE, DELETE')")) {
                rs.next();
                assertThat(rs.getBoolean(1)).isTrue();
            }
        }
    }

    @Test
    @DisplayName("V5_4: reenvio com a mesma chave no mesmo mes nao duplica; outro mes e outra leitura; sem chave nao dedupa")
    void resubmittedReadingWithSameKeyDoesNotDuplicate() throws SQLException {
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, "P23-METER-01");
                UUID installation = insertInstallation(c, unit, "P23-HID-01");
                // Primeira leitura com chave.
                insertReading(c, unit, installation, "2026-11-01", "P23-KEY-01", 1000);
                // Reenvio: mesma chave, mesmo mes, outro valor — recusado pelo indice unico.
                // A mensagem cita o indice DA PARTICAO (meter_readings_2026_11_..._idx),
                // nao o nome do indice pai: o assert e pelo texto estavel da violacao.
                var savepoint = c.setSavepoint();
                assertThatThrownBy(() -> insertReading(c, unit, installation, "2026-11-01", "P23-KEY-01", 1005))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("duplicate key value violates unique constraint");
                c.rollback(savepoint);
                assertThat(countReadingsWithKey(c, "P23-KEY-01", "2026-11-01")).isOne();
                // Mesma chave em outro mes: outra leitura (a chave de particao esta no indice).
                insertReading(c, unit, installation, "2026-12-01", "P23-KEY-01", 1100);
                assertThat(countReadingsWithKey(c, "P23-KEY-01", "2026-12-01")).isOne();
                assertThat(countReadingsWithKey(c, "P23-KEY-01", null)).isEqualTo(2);
                // Sem chave: duas leituras no mesmo mes nao colidem (NULLs sao distintos e o
                // indice parcial as exclui). A contagem tem escopo de unidade: o tenant
                // e compartilhado entre os ITs da JVM e outro teste pode ter leituras
                // sem chave no mesmo mes.
                insertReading(c, unit, installation, "2026-11-01", null, 1010);
                insertReading(c, unit, installation, "2026-11-01", null, 1020);
                assertThat(countReadingsWithoutKey(c, unit, "2026-11-01")).isEqualTo(2);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("V5_4: trigger preenche reading_reference; mes divergente recusado")
    void consumptionRecordMonthFilledByTrigger() throws SQLException {
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, "P23-METER-02");
                UUID installation = insertInstallation(c, unit, "P23-HID-02");
                UUID reading = insertReading(c, unit, installation, "2026-11-01", "P23-KEY-02", 2000);
                // Registro sem mes da leitura: o trigger preenche com o mes da leitura.
                UUID record = insertConsumptionRecord(c, unit, reading, null);
                assertThat(recordReadingMonth(c, record)).isEqualTo("2026-11-01");
                // Mes divergente da leitura: recusado pelo trigger.
                var savepoint = c.setSavepoint();
                assertThatThrownBy(() -> insertConsumptionRecord(c, unit, reading, "2026-10-01"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("difere");
                c.rollback(savepoint);
                // Sem leitura: reading_reference fica NULL. Segunda unidade: o indice
                // unico de consumo vale por (unidade, servico, referencia).
                UUID unitOrphan = insertUnit(c, "P23-METER-02B");
                UUID orphan = insertConsumptionRecord(c, unitOrphan, null, null);
                assertThat(recordReadingMonthOrNull(c, orphan)).isNull();
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("V5_4: mes sem particao falha; meter_readings_create_partition resolve; sem DEFAULT")
    void missingPartitionFailsUntilCreated() throws SQLException {
        // Tx 1: prova que o mes sem particao falha.
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, "P23-METER-03");
                UUID installation = insertInstallation(c, unit, "P23-HID-03");
                // A falha esperada aborta a transacao: sem savepoint, o
                // insert seguinte falharia com "current transaction is
                // aborted" (mesmo padrao do BillingPartitionP23IT).
                var savepoint = c.setSavepoint();
                assertThatThrownBy(() -> insertReading(c, unit, installation, FAR_MONTH, "P23-KEY-03", 3000))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("no partition of relation");
                c.rollback(savepoint);
            } finally {
                c.rollback();
            }
        }
        // A criacao da particao NAO pode rodar com a transacao do tenant
        // aberta: CREATE TABLE ... PARTITION OF exige ShareRowExclusiveLock
        // na tabela referenciada pela FK (meter_installations), e a
        // transacao do tenant detem RowExclusiveLock nela pelo insert da
        // instalacao. O migrador bloquearia ate o idle-in-transaction
        // timeout de 30 s (security.sql) matar a conexao do teste — provado
        // na integracao P2A com pg_stat_activity (migrador em
        // Lock/relation), pg_locks (ShareRowExclusiveLock nao concedido em
        // meter_installations) e o thread dump do fork (thread parada no
        // execute do create_partition). Por isso a tx 1 termina antes do
        // DDL e a tx 2 recomeca depois dele.
        try (Connection m = migrator(); Statement st = m.createStatement()) {
            st.execute("SELECT " + SCHEMA + ".meter_readings_create_partition(DATE '" + FAR_MONTH + "')");
        }
        // Tx 2: com a particao criada, o insert passa. Unidade e instalacao
        // sao reinseridas (a tx 1 fez rollback; matricula e hidrometro
        // voltam a estar livres).
        try (Connection c = asTenant()) {
            try {
                UUID unit = insertUnit(c, "P23-METER-03");
                UUID installation = insertInstallation(c, unit, "P23-HID-03");
                UUID reading = insertReading(c, unit, installation, FAR_MONTH, "P23-KEY-03", 3000);
                assertThat(reading).isNotNull();
            } finally {
                c.rollback();
            }
        }
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

    private static UUID insertInstallation(Connection c, UUID unit, String meterNumber) throws SQLException {
        UUID meter = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".meters (id, tenant_id, meter_number, digits, status) VALUES (?, ?, ?, 5, 'AVAILABLE')")) {
            ps.setObject(1, meter);
            ps.setObject(2, TENANT);
            ps.setString(3, meterNumber);
            ps.executeUpdate();
        }
        UUID installation = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".meter_installations (id, tenant_id, meter_id, unit_id, kind, installed_on, installed_reading, is_replacement) "
                        + "VALUES (?, ?, ?, ?, 'WATER', DATE '2026-01-01', 0, FALSE)")) {
            ps.setObject(1, installation);
            ps.setObject(2, TENANT);
            ps.setObject(3, meter);
            ps.setObject(4, unit);
            ps.executeUpdate();
        }
        return installation;
    }

    private static UUID insertReading(Connection c, UUID unit, UUID installation, String month, String key, int value) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".meter_readings (id, tenant_id, unit_id, installation_id, kind, reference, "
                        + "read_on, reading, reading_status, source, captured_at, idempotency_key) "
                        + "VALUES (?, ?, ?, ?, 'WATER', DATE '" + month + "', DATE '" + month + "', ?, 'REALIZED', 'MANUAL', NOW(), ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setObject(3, unit);
            ps.setObject(4, installation);
            ps.setInt(5, value);
            ps.setString(6, key);
            ps.executeUpdate();
        }
        return id;
    }

    private static int countReadingsWithKey(Connection c, String key, String month) throws SQLException {
        String sql = "SELECT count(*) FROM " + SCHEMA + ".meter_readings WHERE tenant_id = ? AND idempotency_key = ?"
                + (month == null ? "" : " AND reference = DATE '" + month + "'");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, TENANT);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int countReadingsWithoutKey(Connection c, UUID unit, String month) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM " + SCHEMA + ".meter_readings WHERE tenant_id = ? AND unit_id = ? AND reference = DATE '" + month + "' AND idempotency_key IS NULL")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, unit);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static UUID insertConsumptionRecord(Connection c, UUID unit, UUID reading, String readingMonth) throws SQLException {
        UUID id = UUID.randomUUID();
        String monthSql = readingMonth == null ? "NULL" : "DATE '" + readingMonth + "'";
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + SCHEMA + ".consumption_records (id, tenant_id, unit_id, service_kind, reference, "
                        + "billed_consumption, calculated_consumption, billable, billing_paused, consumption_type, "
                        + "discarded_remainder, reading_id, reading_reference) "
                        + "VALUES (?, ?, ?, 'WATER', DATE '2026-11-01', 0, 0, FALSE, FALSE, 'REAL', 0, ?, " + monthSql + ")")) {
            ps.setObject(1, id);
            ps.setObject(2, TENANT);
            ps.setObject(3, unit);
            ps.setObject(4, reading);
            ps.executeUpdate();
        }
        return id;
    }

    private static String recordReadingMonth(Connection c, UUID record) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT reading_reference FROM " + SCHEMA + ".consumption_records WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, record);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDate(1).toString();
            }
        }
    }

    private static String recordReadingMonthOrNull(Connection c, UUID record) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT reading_reference FROM " + SCHEMA + ".consumption_records WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, record);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                java.sql.Date d = rs.getDate(1);
                return d == null ? null : d.toString();
            }
        }
    }
}
