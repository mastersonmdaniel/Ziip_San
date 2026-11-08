package br.com.ziipsan.platform.accesscontrol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas do SQL PENDENTE da ED-06 (sem versão Flyway reservada no §3, o
 * arquivo vive em db/pendente/ até a integração reservar o número): as
 * tabelas existem e são registradas para RLS, os CHECKs fechados batem com
 * os enums do código e as chaves de parâmetro do SQL core são IGUAIS às do
 * PasswordPolicyRule. O teste localiza o arquivo subindo a árvore a partir
 * do diretório do módulo — quando a integração mover o SQL para
 * db/migration, este teste é substituído pelo de recurso no classpath
 * (padrão da P3.30), como o relatório declara.
 */
class AccessControlSqlConsistencyTest {

    private static final String TENANT_SQL = readPendente("V-pendente__ed06_tenant_access_control.sql");
    private static final String CORE_SQL = readPendente("V-pendente__ed06_core_parameters.sql");

    private static String readPendente(String fileName) {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("db/pendente").resolve(fileName);
            if (Files.exists(candidate)) {
                try {
                    return Files.readString(candidate, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("SQL pendente não localizado: " + fileName);
    }

    @Test
    @DisplayName("as tabelas da ED-06 existem e são registradas para RLS")
    void tablesAndRlsRegistration() {
        for (String table : Set.of("composite_profiles", "user_composite_profiles",
                "user_negative_restrictions", "access_delegations", "nist_policy_acceptances",
                "login_attempt_states", "active_sessions",
                "recertification_campaigns", "recertification_decisions")) {
            assertThat(TENANT_SQL).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(TENANT_SQL).contains("register_tenant_table('" + table + "')");
        }
    }

    @Test
    @DisplayName("CHECKs fechados do SQL batem com os enums do código")
    void closedDomainsMatchEnums() {
        assertThat(TENANT_SQL).contains("'KEEP', 'REVOKE'");
        assertThat(Stream.of(RecertificationService.DecisionKind.values())
                .map(Enum::name).toList()).containsExactly("KEEP", "REVOKE");
        assertThat(TENANT_SQL).contains("delegator_id <> delegate_id");
        assertThat(TENANT_SQL).contains("valid_to > valid_from");
        assertThat(TENANT_SQL).contains("option_key IN ('sec.password_max_age_days', 'sec.password_composition_required')");
    }

    @Test
    @DisplayName("as chaves do SQL core são exatamente as da política (dois sentidos)")
    void parameterKeysMatchPolicy() {
        for (String key : PasswordPolicyRule.PARAMETER_KEYS) {
            assertThat(CORE_SQL).contains("('" + key + "'");
        }
        assertThat(PasswordPolicyRule.PARAMETER_KEYS).hasSize(10);
        // Segundo sentido (correção D8): as chaves EXTRAÍDAS do SQL — cada
        // linha de parameter_definitions — são exatamente as da política;
        // uma chave inventada só no SQL reprova aqui (a versão anterior só
        // conferia Java → SQL e deixaria a chave extra passar).
        java.util.Set<String> sqlKeys = new java.util.TreeSet<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\('([a-z][a-z0-9_.]*)',\\s*'(INT|BOOLEAN|ENUM)'")
                .matcher(CORE_SQL);
        while (matcher.find()) {
            sqlKeys.add(matcher.group(1));
        }
        assertThat(sqlKeys).containsExactlyInAnyOrderElementsOf(PasswordPolicyRule.PARAMETER_KEYS);
    }
}
