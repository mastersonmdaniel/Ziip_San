package br.com.ziipsan.core.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas da V10_3 sem banco, no padrão dos MigrationConsistencyTest do
 * projeto: os CHECKs da migration listam exatamente os enums do código, os
 * vínculos por modo estão no banco e a migration não semeia nada.
 */
class AccessMigrationConsistencyTest {

    private static final String SQL = readSql();

    private static String readSql() {
        try (InputStream in = AccessMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/core/V10_3__assisted_and_emergency_access.sql")) {
            assertThat(in).as("migration V10_3 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("CHECK de mode lista exatamente AccessMode e o de actor_role lista os papéis da P0.43")
    void checksMatchEnums() {
        for (String name : Arrays.stream(AccessMode.values()).map(Enum::name)
                .collect(Collectors.toSet())) {
            assertThat(SQL).as("modo %s no CHECK", name).contains("'" + name + "'");
        }
        for (String role : PlatformActor.PLATFORM_ROLES) {
            assertThat(SQL).as("papel %s no CHECK", role).contains("'" + role + "'");
        }
        // As operações (AccessOperation) NÃO são coluna da V10_3: vivem na
        // regra (AccessPolicy) e no metadata da auditoria. Um loop anterior
        // aqui só afirmava que o nome do enum não é branco — asserção vacua,
        // removida na revisão cruzada; a classificação das operações é
        // coberta por AccessPolicyTest e AccessGuardTest.
    }

    @Test
    @DisplayName("as duas tabelas existem, com RLS forçada e o vínculo fechado por modo no CHECK")
    void tablesRlsAndModeLinkage() {
        assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS support.assisted_consent");
        assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS support.access_session");
        assertThat(SQL).contains("ALTER TABLE support.assisted_consent FORCE ROW LEVEL SECURITY");
        assertThat(SQL).contains("ALTER TABLE support.access_session FORCE ROW LEVEL SECURITY");
        assertThat(SQL).contains("mode = 'ASSISTED' AND consent_id IS NOT NULL AND incident_id IS NULL");
        assertThat(SQL).contains("mode = 'EMERGENCY' AND incident_id IS NOT NULL AND consent_id IS NULL");
        assertThat(SQL).contains("actor_role = 'PLATFORM_OWNER' AND mfa_step_up_at IS NOT NULL");
        assertThat(SQL).contains("notice_due_at");
        assertThat(SQL).contains("must_end_by");
        // incident_id SEM FK nesta versao, declarado no cabecalho (registro e da P3.10)
        assertThat(SQL).doesNotContain("incident_id UUID REFERENCES");
    }

    @Test
    @DisplayName("a V10_3 não semeia dado nenhum e não concede DELETE a ziipsan_app")
    void noSeedAndNoDelete() {
        assertThat(SQL).doesNotContain("INSERT INTO support.assisted_consent");
        assertThat(SQL).doesNotContain("INSERT INTO support.access_session");
        assertThat(SQL).doesNotContain("DELETE ON support");
    }
}
