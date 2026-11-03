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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toda coluna com nome de dado pessoal, numa tabela auditada, tem regra de redação
 * (public.audit_redaction_rule) ou uma justificativa explícita de por que não é dado pessoal.
 *
 * <p>Sem isto, uma coluna nova com CPF ou e-mail entraria em claro na trilha de auditoria sem que
 * ninguém percebesse: foi assim que nome, nascimento, CPF do motorista e IP acabaram em audit_log.
 * Uma coluna nova que case com o padrão abaixo QUEBRA o build até ser classificada.
 */
class AuditRedactionCoverageTest {

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    @Test
    @DisplayName("toda coluna com nome de dado pessoal numa tabela auditada tem regra de redação ou justificativa")
    void everyPersonalColumnIsClassified() throws SQLException {
        List<String> unclassified = new ArrayList<>();
        String sql = """
                SELECT c.table_name, c.column_name,
                       EXISTS (SELECT 1 FROM public.audit_redaction_rule r
                               WHERE r.table_name = c.table_name AND r.column_name = c.column_name) AS has_rule
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
                    if (PersonalDataColumns.PERSONAL.matcher(column).matches() && !rs.getBoolean(3) && !PersonalDataColumns.NOT_PERSONAL.containsKey(key)) {
                        unclassified.add(key);
                    }
                }
            }
        }
        assertThat(audited).as("colunas de tabelas auditadas encontradas").isPositive();
        assertThat(unclassified)
                .as("colunas sem regra em audit_redaction_rule nem justificativa em NOT_PERSONAL")
                .isEmpty();
    }

    @Test
    @DisplayName("nenhuma justificativa aponta para coluna que não existe mais")
    void allowlistIsNotStale() throws SQLException {
        List<String> stale = new ArrayList<>();
        try (Connection conn = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
            for (String key : PersonalDataColumns.NOT_PERSONAL.keySet()) {
                String[] parts = key.split("\\.");
                ps.setString(1, ProvisionedTenant.SCHEMA);
                ps.setString(2, parts[0]);
                ps.setString(3, parts[1]);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        stale.add(key);
                    }
                }
            }
        }
        assertThat(stale).as("entradas de NOT_PERSONAL sem coluna correspondente").isEmpty();
    }
}
