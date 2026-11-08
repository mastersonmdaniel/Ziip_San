package br.com.ziipsan.platform.orgscope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas das migrations da P3.30 sem banco, no padrão dos
 * MigrationConsistencyTest do projeto: as tabelas da reserva V1_8 existem
 * e são registradas para RLS, os CHECKs de area_type são IGUAIS ao enum
 * OrgAreaType (nos dois sentidos), a concessão tem exatamente uma forma
 * (CHECK) e a repetível completa as FKs que a V1_8 não pode criar fresca.
 */
class OrgScopeMigrationConsistencyTest {

    private static final String TENANT_SQL =
            readResource("/db/migration/tenant/V1_8__org_units_and_scopes.sql");
    private static final String REPEATABLE_SQL =
            readResource("/db/migration/tenant/R__org_scope_fks.sql");

    private static String readResource(String path) {
        try (InputStream in = OrgScopeMigrationConsistencyTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Os literais de TODAS as ocorrências do marcador no SQL (correção D5
     * da revisão independente: a versão anterior lia só a PRIMEIRA
     * ocorrência de {@code area_type IN} — a de org_unit_areas — e o CHECK
     * de user_org_scopes nunca era conferido; uma divergência futura entre
     * os dois CHECKs passaria pela guarda).
     */
    private static java.util.List<Set<String>> allCheckLiterals(String sql, String marker) {
        java.util.List<Set<String>> occurrences = new java.util.ArrayList<>();
        int from = 0;
        while (true) {
            int markerAt = sql.indexOf(marker, from);
            if (markerAt < 0) {
                break;
            }
            int open = sql.indexOf('(', markerAt);
            int close = sql.indexOf(')', open);
            Set<String> literals = new HashSet<>();
            Matcher matcher = Pattern.compile("'([A-Z][A-Z0-9_]*)'").matcher(sql.substring(open, close));
            while (matcher.find()) {
                literals.add(matcher.group(1));
            }
            occurrences.add(literals);
            from = close + 1;
        }
        assertThat(occurrences).as("ocorrências de %s", marker).isNotEmpty();
        return occurrences;
    }

    @Test
    @DisplayName("as tabelas da reserva V1_8 existem e são registradas para RLS")
    void tablesAndRlsRegistration() {
        for (String table : Set.of("org_units", "org_unit_areas", "user_org_scopes",
                "protocol_org_assignments", "protocol_transfers")) {
            assertThat(TENANT_SQL).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(TENANT_SQL).contains("register_tenant_table('" + table + "')");
        }
    }

    @Test
    @DisplayName("CHECKs de area_type são IGUAIS ao enum OrgAreaType (dois sentidos) — TODAS as ocorrências")
    void areaTypeChecksMatchEnum() {
        Set<String> enumNames = Arrays.stream(OrgAreaType.values())
                .map(Enum::name).collect(Collectors.toSet());
        java.util.List<Set<String>> occurrences = allCheckLiterals(TENANT_SQL, "area_type IN");
        // A V1_8 tem exatamente dois CHECKs de area_type: org_unit_areas e
        // user_org_scopes. Os DOIS são conferidos, um a um (correção D5).
        assertThat(occurrences).hasSize(2);
        for (Set<String> literals : occurrences) {
            assertThat(literals).isEqualTo(enumNames);
        }
    }

    @Test
    @DisplayName("a concessão tem exatamente uma forma no banco e o auto-pai é vedado")
    void grantShapeAndSelfParentChecks() {
        assertThat(TENANT_SQL).contains("unrestricted AND org_unit_id IS NULL");
        assertThat(TENANT_SQL).contains("NOT unrestricted AND org_unit_id IS NOT NULL");
        assertThat(TENANT_SQL).contains("area_type IS NOT NULL AND area_id IS NOT NULL");
        assertThat(TENANT_SQL).contains("parent_id IS NULL OR parent_id <> id");
        assertThat(TENANT_SQL).contains("uq_user_scopes_unrestricted");
    }

    @Test
    @DisplayName("a V1_8 não semeia unidade, área nem escopo (estrutura é fato do tenant)")
    void noSeedData() {
        assertThat(TENANT_SQL).doesNotContain("INSERT INTO org_units");
        assertThat(TENANT_SQL).doesNotContain("INSERT INTO org_unit_areas");
        assertThat(TENANT_SQL).doesNotContain("INSERT INTO user_org_scopes");
    }

    @Test
    @DisplayName("a repetível completa as FKs de users e de service_protocols com a guarda P1-C")
    void repeatableCompletesForeignKeys() {
        assertThat(REPEATABLE_SQL).contains("fk_user_org_scopes_user");
        assertThat(REPEATABLE_SQL).contains("fk_protocol_assignments_protocol");
        assertThat(REPEATABLE_SQL).contains("fk_protocol_transfers_protocol");
        assertThat(REPEATABLE_SQL).contains("pg_constraint");
        assertThat(REPEATABLE_SQL).contains("ARRAY['tenant_id', 'id']");
    }

    @Test
    @DisplayName("as permissões nomeadas da P3.30 são literais recurso:ação válidos")
    void namedPermissionsAreWellFormed() {
        Pattern name = Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");
        assertThat(OrgScopePermissions.ALL).containsExactlyInAnyOrder(
                "protocol:transfer-any-unit", "so:close-retroactive", "so:close-without-activities");
        for (String permission : OrgScopePermissions.ALL) {
            assertThat(name.matcher(permission).matches()).as(permission).isTrue();
        }
    }
}
