package br.com.ziipsan.compliance.lgpd.privacyiso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas do SQL pendente da ED-74 sem banco, no padrão dos
 * MigrationConsistencyTest do projeto: os CHECKs listam EXATAMENTE os
 * enums do código, as tabelas novas são registradas para RLS/auditoria,
 * não há DELETE (desligar não apaga) e o cabeçalho declara a pendência
 * de numeração (o plano reserva a faixa V23_x, sem número para a ED-74).
 */
class Iso27701PendenteSqlConsistencyTest {

    private static final String SQL = readResource("/db/pendente/ED-74__lgpd_privacy_iso27701.sql");

    private static String readResource(String path) {
        try (InputStream in = Iso27701PendenteSqlConsistencyTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    private static Set<String> literalsAfter(String marker) {
        int at = SQL.indexOf(marker);
        assertThat(at).as("marcador %s", marker).isGreaterThanOrEqualTo(0);
        int open = SQL.indexOf('(', at);
        int close = SQL.indexOf(')', open);
        return Arrays.stream(SQL.substring(open + 1, close).split(","))
                .map(s -> s.trim().replace("'", "")).filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("CHECK de papel do SQL é exatamente o enum PrivacyRole")
    void roleCheckMatchesEnum() {
        assertThat(literalsAfter("role IN ("))
                .isEqualTo(enumNames(ProcessingRoleAssignment.PrivacyRole.class));
    }

    @Test
    @DisplayName("CHECKs de audiência e situação são exatamente os enums da autoavaliação")
    void audienceAndConformityMatchEnums() {
        assertThat(literalsAfter("audience IN ("))
                .isEqualTo(enumNames(PrivacyClauseAssessment.Audience.class));
        assertThat(literalsAfter("conformity IN ("))
                .isEqualTo(enumNames(PrivacyClauseAssessment.Conformity.class));
        assertThat(literalsAfter("kind IN ("))
                .isEqualTo(enumNames(PrivacyClauseAssessment.EvidenceKind.class));
    }

    @Test
    @DisplayName("as tabelas novas são registradas para RLS/auditoria")
    void tablesRegistered() {
        for (String table : new String[]{"lgpd_processing_roles", "lgpd_pims_scopes",
                "lgpd_privacy_clause_assessments", "lgpd_privacy_clause_evidence",
                "lgpd_privacy_by_design_checks"}) {
            assertThat(SQL).contains("register_tenant_table('" + table + "')");
        }
    }

    @Test
    @DisplayName("sem DELETE e sem texto de norma: cabeçalho declara pendência V23_x e referências apenas")
    void noDeleteNoNormText() {
        assertThat(SQL.toUpperCase()).doesNotContain("DELETE FROM");
        assertThat(SQL).contains("V23_x");
        assertThat(SQL).contains("PENDENTE");
    }

    @Test
    @DisplayName("verificação desde a concepção: o banco repete a trava do RIPD para tratamento novo/alterado")
    void byDesignDpiaGuardInSql() {
        assertThat(SQL).contains("new_or_changed = FALSE OR");
        assertThat(SQL).contains("dpia_ref IS NOT NULL");
    }
}
