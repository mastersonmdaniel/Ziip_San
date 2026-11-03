package br.com.ziipsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catálogo {@code data_classification} (P0.11): toda coluna com nome de dado pessoal, numa
 * tabela auditada, tem classificação (ID, CT, LOC, FIN, SOC ou SENS), e toda coluna com
 * regra de redação tem classificação — o espelho do {@link AuditRedactionCoverageTest}
 * para o catálogo. Uma coluna nova que case com o padrão QUEBRA o build até ser
 * classificada; a regra vale para toda tarefa que cria tabela.
 */
class DataClassificationCoverageTest {

    private static final Set<String> CLASSES = Set.of("ID", "CT", "LOC", "FIN", "SOC", "SENS");

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    @Test
    @DisplayName("toda coluna com nome de dado pessoal numa tabela auditada tem classificação")
    void everyPersonalColumnIsClassified() throws SQLException {
        List<String> unclassified = new ArrayList<>();
        String sql = """
                SELECT c.table_name, c.column_name,
                       EXISTS (SELECT 1 FROM public.data_classification d
                               WHERE d.table_name = c.table_name AND d.column_name = c.column_name) AS has_class
                FROM information_schema.columns c
                WHERE c.table_schema = ?
                  AND EXISTS (SELECT 1 FROM pg_trigger t
                              JOIN pg_class cl ON cl.oid = t.tgrelid
                              JOIN pg_namespace n ON n.oid = cl.relnamespace
                              WHERE t.tgname = 'audit_trigger' AND n.nspname = c.table_schema
                                AND cl.relname = c.table_name)
                ORDER BY 1, 2""";
        int audited = 0;
        try (Connection conn = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ProvisionedTenant.SCHEMA);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    audited++;
                    String table = rs.getString(1);
                    String column = rs.getString(2);
                    String key = table + "." + column;
                    if (PersonalDataColumns.PERSONAL.matcher(column).matches() && !rs.getBoolean(3)
                            && !PersonalDataColumns.NOT_PERSONAL.containsKey(key)) {
                        unclassified.add(key);
                    }
                }
            }
        }
        assertThat(audited).as("colunas de tabelas auditadas encontradas").isPositive();
        assertThat(unclassified)
                .as("colunas sem linha em data_classification nem justificativa em NOT_PERSONAL")
                .isEmpty();
    }

    @Test
    @DisplayName("toda coluna com regra de redação tem classificação")
    void everyRedactionRuleIsClassified() throws SQLException {
        List<String> missing = new ArrayList<>();
        String sql = """
                SELECT r.table_name, r.column_name
                FROM public.audit_redaction_rule r
                WHERE NOT EXISTS (SELECT 1 FROM public.data_classification d
                                  WHERE d.table_name = r.table_name AND d.column_name = r.column_name)
                ORDER BY 1, 2""";
        try (Connection conn = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                missing.add(rs.getString(1) + "." + rs.getString(2));
            }
        }
        assertThat(missing).as("regras de redação sem classificação").isEmpty();
    }

    @Test
    @DisplayName("toda classificação usa classe válida e aponta para coluna existente")
    void classesAreValidAndNotStale() throws SQLException {
        List<String> invalid = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        try (Connection conn = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT table_name, column_name, class FROM public.data_classification ORDER BY 1, 2");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String table = rs.getString(1);
                String column = rs.getString(2);
                String clazz = rs.getString(3);
                if (!CLASSES.contains(clazz)) {
                    invalid.add(table + "." + column + " -> " + clazz);
                }
                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT 1 FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
                    check.setString(1, ProvisionedTenant.SCHEMA);
                    check.setString(2, table);
                    check.setString(3, column);
                    try (ResultSet found = check.executeQuery()) {
                        if (!found.next()) {
                            stale.add(table + "." + column);
                        }
                    }
                }
            }
        }
        assertThat(invalid).as("classificações com classe fora de ID, CT, LOC, FIN, SOC, SENS").isEmpty();
        assertThat(stale).as("classificações sem coluna correspondente").isEmpty();
    }

    @Test
    @DisplayName("P0.11: consumer_units.coordinates é LOC (isenção revista, não mais NOT_PERSONAL)")
    void consumerUnitCoordinatesIsLoc() throws SQLException {
        String clazz = null;
        try (Connection conn = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT class FROM public.data_classification WHERE table_name = 'consumer_units' AND column_name = 'coordinates'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    clazz = rs.getString(1);
                }
            }
        }
        assertThat(clazz).as("classificação de consumer_units.coordinates").isEqualTo("LOC");
        assertThat(PersonalDataColumns.NOT_PERSONAL).doesNotContainKey("consumer_units.coordinates");
    }
}
