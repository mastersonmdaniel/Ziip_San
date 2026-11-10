package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** E12: consistencia SQL x codigo da migration V106 (o SQL nao e aplicado nesta VM). */
class ThirdPartyMigrationConsistencyTest {

    static String sql() throws IOException {
        String prop = System.getProperty("ed68.sql", "");
        Path p = Path.of(prop);
        if (!Files.exists(p)) p = Path.of("commercial/ziipsan-collection/src/main/resources/db/pendente/V106__dunning_third_party_collection.sql");
        if (!Files.exists(p)) p = Path.of("src/main/resources/db/pendente/V106__dunning_third_party_collection.sql");
        return Files.readString(p);
    }

    static String ddl(String sql) {
        return sql.lines().filter(l -> !l.stripLeading().startsWith("--"))
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test void sqlDeclaresV106DunningNameAndReconfirmationAtLanding() throws IOException {
        String sql = sql();
        assertTrue(sql.contains("V106__dunning_third_party_collection.sql"),
                "nome da migration com `dunning` e numero V106 declarado");
        assertTrue(sql.contains("RECONFIRMAR NO POUSO"),
                "numero reconfirma-se no pouso (lista viva) — declarado no cabecalho");
    }

    @Test void sqlCreatesAllDomainTables() throws IOException {
        String sql = sql();
        for (String table : new String[]{"dunning_third_party_contracts",
                "dunning_third_party_operator_contracts", "dunning_third_party_assignments",
                "dunning_third_party_remunerations", "dunning_third_party_audit_trail"}) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), "tabela ausente: " + table);
            assertTrue(sql.contains("register_tenant_table('" + table + "')"), "registo tenant ausente: " + table);
        }
    }

    @Test void sqlMirrorsDomainChecks() throws IOException {
        String ddl = ddl(sql());
        assertTrue(ddl.contains("remuneration_percent > 0 AND remuneration_percent <= 100"),
                "percentual do contrato: mesma faixa do dominio (0, 100]");
        assertTrue(ddl.contains("window_end >= window_start"), "janela nao invertida no banco");
        assertTrue(ddl.contains("'PAYMENT','INSTALLMENT_AGREEMENT','WINDOW_END'"),
                "causas de retirada enumeradas no banco como no dominio");
        assertTrue(ddl.contains("uq_dunning_third_party_remunerations_event"),
                "idempotencia por evento de pagamento tambem no banco");
    }

    @Test void sqlHasActiveDebtUniquenessForDoubleAssignmentGuard() throws IOException {
        String ddl = ddl(sql());
        assertTrue(ddl.contains("WHERE status = 'ACTIVE'"),
                "trava de dupla cessao activa expressa no SQL (indice unico parcial)");
    }

    @Test void sqlSeedsNothing() throws IOException {
        assertFalse(sql().toUpperCase().contains("INSERT INTO"),
                "nenhum valor/percentual/criterio semeado: tudo e dado do contrato (ADR-0038)");
    }

    @Test void sqlReferencesEd37SupplierRegistryWithoutRecreatingIt() throws IOException {
        String sql = sql();
        assertTrue(sql.contains("REFERENCES cert_suppliers"), "empresa referencia o cadastro da ED-37/P3.31");
        assertFalse(ddl(sql).toUpperCase().contains("CREATE TABLE IF NOT EXISTS CERT_SUPPLIERS"),
                "cadastro da P3.31 nao e recriado (sem cadastro paralelo)");
    }

    @Test void codeTableConceptsMatchSqlColumns() throws IOException {
        // Conceitos centrais do dominio presentes como colunas (espelho E1/E2/E8/E9):
        String ddl = ddl(sql()).toLowerCase();
        for (String column : new String[]{"company_supplier_id", "window_start", "window_end",
                "remuneration_percent", "registered_on", "debt_id", "unit_id", "debtor_identifier",
                "withdrawal_cause", "withdrawn_on", "payment_event_id", "paid_on", "percent_applied",
                "remuneration_amount"}) {
            assertTrue(ddl.contains(column), "coluna ausente no SQL: " + column);
        }
    }
}
