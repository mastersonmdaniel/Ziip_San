package br.com.ziipsan.platform.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas da V1_15 sem banco, no padrão dos MigrationConsistencyTest do projeto:
 * os CHECKs da migration listam exatamente os enums do código, as duas tabelas são
 * registradas para RLS e a migration não semeia catálogo de outro dono.
 */
class NotificationMigrationConsistencyTest {

    private static final String SQL = readSql();

    private static String readSql() {
        try (InputStream in = NotificationMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V1_15__notifications_and_preferences.sql")) {
            assertThat(in).as("migration V1_15 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("CHECKs de category e channel da V1_15 listam exatamente os enums do código")
    void checksMatchEnums() {
        assertThat(SQL).contains("'SECURITY'", "'PENDING_APPROVAL'", "'LEGAL_NOTICE'");
        for (String name : enumNames(NotificationCategory.class)) {
            assertThat(SQL).as("categoria %s no CHECK", name).contains("'" + name + "'");
        }
        for (String name : enumNames(NotificationChannel.class)) {
            assertThat(SQL).as("canal %s no CHECK", name).contains("'" + name + "'");
        }
        for (String name : enumNames(DigestMode.class)) {
            assertThat(SQL).as("digest %s no CHECK", name).contains("'" + name + "'");
        }
        assertThat(EnumSet.allOf(NotificationCategory.class)).hasSize(7);
    }

    @Test
    @DisplayName("as duas tabelas da reserva V1_15 existem e são registradas para RLS; consumidor exige contato+consentimento")
    void tablesAndRlsRegistration() {
        assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS notifications");
        assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS notification_preferences");
        assertThat(SQL).contains("register_tenant_table('notifications')");
        assertThat(SQL).contains("register_tenant_table('notification_preferences')");
        assertThat(SQL).contains("customer_id IS NULL OR (contact_id IS NOT NULL AND consent_id IS NOT NULL)");
        assertThat(SQL).contains("read_at TIMESTAMPTZ");
    }

    @Test
    @DisplayName("a V1_15 não semeia dado nenhum (catálogos são dos donos: P1.38 eventos, P1.3 finalidades)")
    void noSeedData() {
        assertThat(SQL).doesNotContain("INSERT INTO notifications");
        assertThat(SQL).doesNotContain("INSERT INTO notification_preferences");
    }

    @Test
    @DisplayName("CORREÇÃO (idempotência): a chave de dedup da ingestão existe — coluna source_message_id + índice único parcial")
    void dedupKeyExists() {
        assertThat(SQL).contains("source_message_id UUID");
        assertThat(SQL).contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_source_message");
        assertThat(SQL).contains("ON notifications (tenant_id, source_message_id) WHERE source_message_id IS NOT NULL");
    }

    @Test
    @DisplayName("CORREÇÃO (FKs): a guarda das FKs é a da P1-C — UNIQUE (tenant_id, id) via pg_constraint, não só to_regclass")
    void fkGuardChecksUniqueKey() {
        assertThat(SQL).contains("conrelid = to_regclass('users')");
        assertThat(SQL).contains("conrelid = to_regclass('customers')");
        assertThat(SQL).contains("pg_constraint");
        assertThat(SQL).contains("ARRAY['tenant_id', 'id']");
    }

    @Test
    @DisplayName("CORREÇÃO (sujeito): o banco também recusa os dois sujeitos juntos (exatamente um, como o subjectKind)")
    void subjectIsExactlyOneInDatabase() {
        assertThat(SQL).contains("CHECK (user_id IS NULL OR customer_id IS NULL)");
    }

    @Test
    @DisplayName("CORREÇÃO (FKs em tenant novo): a repetível R__ completa as FKs para customers, com duplicate_object e a guarda da P1-C")
    void repeatableCompletesCustomerFks() {
        String repeatable = readResource("/db/migration/tenant/R__notifications_customer_fk.sql");
        assertThat(repeatable).contains("fk_notifications_customer");
        assertThat(repeatable).contains("fk_notification_prefs_customer");
        assertThat(repeatable).contains("REFERENCES customers (tenant_id, id)");
        assertThat(repeatable).contains("duplicate_object");
        assertThat(repeatable).contains("pg_constraint");
        assertThat(repeatable).doesNotContain("CREATE TABLE");
    }

    private static String readResource(String path) {
        try (InputStream in = NotificationMigrationConsistencyTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
