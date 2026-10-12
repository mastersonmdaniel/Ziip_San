package br.com.sigsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * As garantias que a migration do cadastro promete, exercitadas no banco real: unicidade da
 * inscrição, um nome na conta, documento único por índice cego, isolamento entre tenants e trilha
 * de auditoria. São regras que NÃO podem depender só da aplicação: a API, a importação de legado e o
 * recadastramento em campo entram por caminhos diferentes, e só o banco vê todos.
 */
class RegistrySchemaTest {

    private static final UUID T = ProvisionedTenant.TENANT_ID;
    private static final UUID OTHER_TENANT = UUID.randomUUID();
    private static final String S = ProvisionedTenant.SCHEMA;
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    private static UUID block;
    private static UUID unit;
    private static UUID customer1;
    private static UUID customer2;

    @FunctionalInterface
    interface Work {
        void run(Statement st) throws SQLException;
    }

    @BeforeAll
    static void baseData() throws SQLException {
        ProvisionedTenant.ensure();
        inTenant(T, true, st -> {
            UUID locality = insert(st, "INSERT INTO " + S + ".localities(tenant_id, code, name) VALUES ('" + T + "', 1, 'Localidade') RETURNING id");
            UUID sector = insert(st, "INSERT INTO " + S + ".commercial_sectors(tenant_id, locality_id, code, name) VALUES ('" + T + "', '" + locality + "', 1, 'Setor') RETURNING id");
            block = insert(st, "INSERT INTO " + S + ".blocks(tenant_id, sector_id, code, has_water_network, has_sewer_network) VALUES ('" + T + "', '" + sector + "', 1, true, true) RETURNING id");
            unit = insert(st, "INSERT INTO " + S + ".consumer_units(tenant_id, matricula, dv, block_id, lot, sublot) VALUES ('" + T + "', '100', '9', '" + block + "', 1, 0) RETURNING id");
            customer1 = insert(st, customerSql("Maria", HASH_A));
            customer2 = insert(st, customerSql("João", HASH_B));
        });
    }

    private static String customerSql(String name, String hash) {
        return "INSERT INTO " + S + ".customers(tenant_id, person_type, name, document_ciphertext, document_hash, document_masked) "
                + "VALUES ('" + T + "', 'PF', '" + name + "', 'cifrado', '" + hash + "', '***.982.247-**') RETURNING id";
    }

    // ---------- unidade ----------

    @Test
    @DisplayName("inscrição (quadra + lote + sublote) é única entre unidades ativas")
    void inscriptionIsUnique() throws SQLException {
        expect("23505", st -> st.execute("INSERT INTO " + S + ".consumer_units(tenant_id, matricula, dv, block_id, lot, sublot) "
                + "VALUES ('" + T + "', '101', '7', '" + block + "', 1, 0)"));
    }

    @Test
    @DisplayName("unidade excluída (lógica) libera a inscrição")
    void deletedUnitFreesTheInscription() throws SQLException {
        inTenant(T, false, st -> {
            st.execute("UPDATE " + S + ".consumer_units SET deleted_at = now(), deleted_reason = 'teste' WHERE id = '" + unit + "'");
            st.execute("INSERT INTO " + S + ".consumer_units(tenant_id, matricula, dv, block_id, lot, sublot) "
                    + "VALUES ('" + T + "', '102', '5', '" + block + "', 1, 0)");
        });
    }

    @Test
    @DisplayName("unidade com quadra precisa de lote e sublote (senão a unicidade não valeria)")
    void blockRequiresLotAndSublot() throws SQLException {
        expect("23514", st -> st.execute("INSERT INTO " + S + ".consumer_units(tenant_id, matricula, dv, block_id) "
                + "VALUES ('" + T + "', '103', '3', '" + block + "')"));
    }

    @Test
    @DisplayName("status do cadastro só aceita ACTIVE ou PROVISIONAL")
    void unitStatusIsConstrained() throws SQLException {
        expect("23514", st -> st.execute("UPDATE " + S + ".consumer_units SET status = 'QUALQUER' WHERE id = '" + unit + "'"));
    }

    @Test
    @DisplayName("unidade provisória pode existir sem quadra")
    void provisionalUnitWithoutBlock() throws SQLException {
        inTenant(T, false, st -> st.execute("INSERT INTO " + S + ".consumer_units(tenant_id, matricula, dv, status) "
                + "VALUES ('" + T + "', '104', '1', 'PROVISIONAL')"));
    }

    // ---------- cliente ----------

    @Test
    @DisplayName("documento (índice cego) é único entre clientes ativos")
    void documentIsUnique() throws SQLException {
        expect("23505", st -> st.execute(customerSql("Outra Maria", HASH_A).replace(" RETURNING id", "")));
    }

    @Test
    @DisplayName("cliente excluído (lógico) libera o documento")
    void deletedCustomerFreesTheDocument() throws SQLException {
        inTenant(T, false, st -> {
            st.execute("UPDATE " + S + ".customers SET deleted_at = now(), deleted_reason = 'teste' WHERE id = '" + customer1 + "'");
            st.execute(customerSql("Nova Maria", HASH_A).replace(" RETURNING id", ""));
        });
    }

    @Test
    @DisplayName("documento cifrado e índice cego andam juntos: um sem o outro é recusado")
    void ciphertextAndHashMustBeSetTogether() throws SQLException {
        expect("23514", st -> st.execute("INSERT INTO " + S + ".customers(tenant_id, person_type, name, document_ciphertext) "
                + "VALUES ('" + T + "', 'PF', 'Sem hash', 'cifrado')"));
    }

    @Test
    @DisplayName("tipo de pessoa só aceita PF ou PJ; dia de vencimento de 1 a 31")
    void customerChecks() throws SQLException {
        expect("23514", st -> st.execute("INSERT INTO " + S + ".customers(tenant_id, person_type, name) VALUES ('" + T + "', 'XX', 'a')"));
        expect("23514", st -> st.execute("INSERT INTO " + S + ".customers(tenant_id, person_type, name, due_day) VALUES ('" + T + "', 'PF', 'a', 32)"));
    }

    @Test
    @DisplayName("no máximo um contato principal de cada tipo por cliente")
    void onePrimaryContactPerKind() throws SQLException {
        expect("23505", st -> {
            st.execute(contactSql("EMAIL", "a@x.com.br", true));
            st.execute(contactSql("EMAIL", "b@x.com.br", true));
        });
        inTenant(T, false, st -> {
            st.execute(contactSql("EMAIL", "a@x.com.br", true));
            st.execute(contactSql("PHONE", "+5511999999999", true));
        });
    }

    private static String contactSql(String kind, String value, boolean primary) {
        return "INSERT INTO " + S + ".customer_contacts(tenant_id, customer_id, kind, value, primary_contact) VALUES ('"
                + T + "', '" + customer2 + "', '" + kind + "', '" + value + "', " + primary + ")";
    }

    // ---------- vínculo cliente-imóvel ----------

    private static String relation(UUID cust, String role, String start, String end, boolean onBill) {
        return "INSERT INTO " + S + ".unit_customers(tenant_id, unit_id, customer_id, role, start_date, end_date, show_on_bill) VALUES ('"
                + T + "', '" + unit + "', '" + cust + "', '" + role + "', '" + start + "', "
                + (end == null ? "NULL" : "'" + end + "'") + ", " + onBill + ")";
    }

    @Test
    @DisplayName("exatamente um nome na conta entre os vínculos vigentes")
    void onlyOneBillNameAtATime() throws SQLException {
        expect("23505", st -> {
            st.execute(relation(customer1, "USER", "2025-01-01", null, true));
            st.execute(relation(customer2, "OWNER", "2025-01-01", null, true));
        });
        // encerrando o primeiro, o segundo pode assumir
        inTenant(T, false, st -> {
            st.execute(relation(customer1, "USER", "2020-01-01", "2024-12-31", true));
            st.execute(relation(customer2, "OWNER", "2025-01-01", null, true));
        });
    }

    @Test
    @DisplayName("o mesmo cliente não aparece duas vezes, com o mesmo papel, ao mesmo tempo")
    void sameCustomerSameRoleOnce() throws SQLException {
        expect("23505", st -> {
            st.execute(relation(customer1, "USER", "2025-01-01", null, false));
            st.execute(relation(customer1, "USER", "2025-06-01", null, false));
        });
        inTenant(T, false, st -> {
            st.execute(relation(customer1, "USER", "2025-01-01", null, false));
            st.execute(relation(customer1, "OWNER", "2025-01-01", null, false));
        });
    }

    @Test
    @DisplayName("fim do vínculo anterior ao início é recusado; papel desconhecido também")
    void relationChecks() throws SQLException {
        expect("23514", st -> st.execute(relation(customer1, "USER", "2025-05-01", "2025-04-01", false)));
        expect("23514", st -> st.execute(relation(customer1, "DONO", "2025-05-01", null, false)));
    }

    @Test
    @DisplayName("economias da subcategoria: no mínimo 1, sem repetir a subcategoria na unidade")
    void subcategoryChecks() throws SQLException {
        inTenant(T, false, st -> {
            UUID category = insert(st, "INSERT INTO " + S + ".categories(tenant_id, code, name) VALUES ('" + T + "', 'C1', 'Categoria') RETURNING id");
            UUID sub = insert(st, "INSERT INTO " + S + ".subcategories(tenant_id, category_id, code, name) VALUES ('" + T + "', '" + category + "', 'S1', 'Sub') RETURNING id");
            st.execute("INSERT INTO " + S + ".unit_subcategories(tenant_id, unit_id, subcategory_id, economies) VALUES ('" + T + "', '" + unit + "', '" + sub + "', 2)");
            try {
                st.execute("SAVEPOINT repeat");
                st.execute("INSERT INTO " + S + ".unit_subcategories(tenant_id, unit_id, subcategory_id, economies) VALUES ('" + T + "', '" + unit + "', '" + sub + "', 1)");
                fail("subcategoria repetida na unidade deveria ser recusada");
            } catch (SQLException e) {
                assertThat(e.getSQLState()).isEqualTo("23505");
                st.execute("ROLLBACK TO SAVEPOINT repeat");
            }
            try {
                st.execute("SAVEPOINT zero");
                st.execute("UPDATE " + S + ".unit_subcategories SET economies = 0 WHERE unit_id = '" + unit + "'");
                fail("0 economias deveria ser recusado");
            } catch (SQLException e) {
                assertThat(e.getSQLState()).isEqualTo("23514");
                st.execute("ROLLBACK TO SAVEPOINT zero");
            }
        });
    }

    // ---------- isolamento e auditoria ----------

    @Test
    @DisplayName("chave estrangeira composta: referenciar a unidade de outro tenant é recusado")
    void compositeForeignKeyBlocksCrossTenantReferences() throws SQLException {
        // Contexto do OUTRO tenant, linha do outro tenant (passa na RLS), apontando para a unidade do tenant T
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + OTHER_TENANT + "', true)");
                st.execute("INSERT INTO " + S + ".customers(tenant_id, person_type, name) VALUES ('" + OTHER_TENANT + "', 'PF', 'Intruso')");
                UUID intruder = null;
                try (ResultSet rs = st.executeQuery("SELECT id FROM " + S + ".customers WHERE tenant_id = '" + OTHER_TENANT + "'")) {
                    rs.next();
                    intruder = UUID.fromString(rs.getString(1));
                }
                try {
                    st.execute("INSERT INTO " + S + ".unit_customers(tenant_id, unit_id, customer_id, role, start_date) VALUES ('"
                            + OTHER_TENANT + "', '" + unit + "', '" + intruder + "', 'USER', '2025-01-01')");
                    fail("vínculo de um tenant para a unidade de outro deveria ser recusado");
                } catch (SQLException e) {
                    assertThat(e.getSQLState()).isEqualTo("23503");
                }
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("outro tenant não enxerga nenhum cliente nem unidade (RLS)")
    void otherTenantSeesNothing() throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + OTHER_TENANT + "', true)");
                for (String table : new String[]{"customers", "consumer_units", "unit_customers", "blocks", "localities"}) {
                    try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + S + "." + table)) {
                        rs.next();
                        assertThat(rs.getInt(1)).as("linhas de %s visíveis ao outro tenant", table).isZero();
                    }
                }
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("sem contexto de tenant, a role da aplicação não enxerga nada")
    void applicationRoleWithoutContextSeesNothing() throws SQLException {
        try (Connection c = ProvisionedTenant.appDs.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM " + S + ".customers")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("alterações em unidade e cliente geram trilha em public.audit_log")
    void changesAreAudited() throws SQLException {
        inTenant(T, false, st -> {
            st.execute("UPDATE " + S + ".consumer_units SET notes = 'auditoria' WHERE id = '" + unit + "'");
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM public.audit_log WHERE entity_id = '" + unit + "'")) {
                rs.next();
                assertThat(rs.getInt(1)).as("registros de auditoria da unidade").isGreaterThanOrEqualTo(2); // CREATE + UPDATE
            }
        });
    }

    @Test
    @DisplayName("o documento em claro não aparece na trilha de auditoria")
    void auditTrailHasNoClearDocument() throws SQLException {
        inTenant(T, false, st -> {
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM public.audit_log WHERE new_values::text LIKE '%52998224725%'")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        });
    }

    // ---------- infraestrutura de teste ----------

    private static UUID insert(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return UUID.fromString(rs.getString(1));
        }
    }

    /** Executa no contexto do tenant, confirmando (commit) ou descartando (rollback) ao fim. */
    private static void inTenant(UUID tenant, boolean commit, Work work) throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
                work.run(st);
                if (commit) c.commit(); else c.rollback();
            } catch (SQLException | RuntimeException | Error e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** O trabalho deve falhar com o SQLState dado. A transação é sempre descartada. */
    private static void expect(String sqlState, Work work) throws SQLException {
        try {
            inTenant(T, false, work);
            fail("esperava a falha SQLState " + sqlState + ", mas o comando foi aceito");
        } catch (SQLException e) {
            assertThat(e.getSQLState()).as("SQLState: %s", e.getMessage()).isEqualTo(sqlState);
        }
    }
}
