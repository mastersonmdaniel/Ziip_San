package br.com.ziipsan.platform.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas da V1_15 sem banco, no padrão dos MigrationConsistencyTest do projeto:
 * os CHECKs da migration listam exatamente os enums do código, as duas tabelas são
 * registradas para RLS e a migration não semeia catálogo de outro dono.
 *
 * <p><b>CORREÇÃO PÓS-REVISÃO DA ED-41 (defeito D1 da revisão da ED-41):</b> a
 * ED-41 ampliou o enum {@link NotificationChannel} com {@code SMS} e
 * {@code WHATSAPP}, mas os CHECKs publicados da V1_15 NÃO são reescritos — quem
 * amplia os CHECKs é a migration da ED-41 (hoje pendente:
 * {@code db/pendente/ED-41__multichannel_notifications.sql}; no pouso ela recebe
 * número global e passa a viver em {@code db/migration/tenant/}). Por isso o
 * teste de consistência dos CANAIS passa a ler a CADEIA COMPOSTA (V1_15 +
 * migration de ampliação da ED-41), e não só a V1_15. A asserção NÃO foi
 * afrouxada: todo nome do enum continua exigido na cadeia, com a mesma
 * mensagem; sem a migration da ED-41 no classpath, {@code SMS}/{@code WHATSAPP}
 * não constam e este teste continua falhando — é o controle de que a guarda
 * segue viva. Categoria e digest continuam conferidos SÓ contra a V1_15 (a
 * ED-41 não os toca), e os demais testes deste arquivo seguem lendo só a
 * V1_15, como antes.
 */
class NotificationMigrationConsistencyTest {

    private static final String SQL = readSql();

    /** Cadeia composta para os CHECKs de canal: V1_15 + ampliação da ED-41 (vazia se ausente). */
    private static final String CHANNEL_CHAIN_SQL = SQL + "\n" + readChannelExtensionSql();

    private static String readSql() {
        try (InputStream in = NotificationMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V1_15__notifications_and_preferences.sql")) {
            assertThat(in).as("migration V1_15 no classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Lê a migration que amplia os CHECKs de canal (ED-41), onde ela estiver:
     * (a) no estado de entrega, o SQL pendente em {@code /db/pendente/}; (b) depois
     * do pouso, a migration numerada em {@code /db/migration/tenant/} — qualquer
     * arquivo daquele diretório, fora a própria V1_15, que declare os canais
     * novos nos CHECKs. Ausente nos dois lugares, devolve vazio e a guarda de
     * {@link #checksMatchEnums()} falha para {@code SMS}/{@code WHATSAPP} —
     * comportamento esperado: enum ampliado sem a migration é divergência real.
     */
    private static String readChannelExtensionSql() {
        String pending = readResourceOrNull("/db/pendente/ED-41__multichannel_notifications.sql");
        if (pending != null) {
            return pending;
        }
        try {
            URL dirUrl = NotificationMigrationConsistencyTest.class.getResource("/db/migration/tenant");
            if (dirUrl != null && "file".equals(dirUrl.getProtocol())) {
                File[] files = new File(dirUrl.toURI()).listFiles((dir, name) -> name.endsWith(".sql")
                        && !name.startsWith("V1_15__"));
                if (files != null) {
                    StringBuilder sb = new StringBuilder();
                    for (File file : files) {
                        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                        if (content.contains("'SMS'") && content.contains("'WHATSAPP'")) {
                            sb.append(content).append('\n');
                        }
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return "";
    }

    private static String readResourceOrNull(String path) {
        try (InputStream in = NotificationMigrationConsistencyTest.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("CHECKs de category e channel listam exatamente os enums do código "
            + "(canais: cadeia V1_15 + ampliação da ED-41)")
    void checksMatchEnums() {
        assertThat(SQL).contains("'SECURITY'", "'PENDING_APPROVAL'", "'LEGAL_NOTICE'");
        for (String name : enumNames(NotificationCategory.class)) {
            assertThat(SQL).as("categoria %s no CHECK", name).contains("'" + name + "'");
        }
        // Canais: a exigência é a MESMA (todo nome do enum, sem exceção), mas a
        // fonte é a cadeia composta — a V1_15 publicada lista IN_APP/EMAIL/PUSH
        // e a migration da ED-41 acrescenta SMS/WHATSAPP via ALTER dos CHECKs.
        // Sem a migration da ED-41, CHANNEL_CHAIN_SQL é só a V1_15 e este laço
        // falha em SMS/WHATSAPP, como falhava antes da correção (controle D1).
        for (String name : enumNames(NotificationChannel.class)) {
            assertThat(CHANNEL_CHAIN_SQL).as("canal %s no CHECK", name).contains("'" + name + "'");
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
