package br.com.ziipsan.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * GIS-01 (P1.26): migrations V13_1 + V13_2 + core V9_6 no PostgreSQL real.
 *
 * <p>O teste roda o texto das próprias migrations (do classpath, com o placeholder
 * tenant_id substituído, como o TenantMigrator faz) contra um schema descartável,
 * numa transação desfeita no fim. A massa de referência é sintética (coordenadas
 * em graus, sem endereço real): um tenant com o modelo antigo (V2_2 + V2_3) é
 * migrado e o resultado é comparado, coluna a coluna, com o legado preservado.
 *
 * <p>Cada teste monta o seu schema do zero (legado → migração), para não
 * depender da ordem de execução nem do tenant provisionado compartilhado. O
 * tenant provisionado só aparece no teste dos cinco domínios, que prova o
 * caminho real de provisionamento (migrations + ativação do pacote).
 */
class Gis01MigrationTest {

    private static final List<String> LEGACY_MIGRATIONS = List.of(
            "db/migration/tenant/V1_1__create_tenant_schema.sql",
            "db/migration/tenant/V1_2__tenant_table_helper.sql",
            "db/migration/tenant/V1_3__harden_audit_function.sql",
            "db/migration/tenant/V2_2__create_losses_schema.sql",
            "db/migration/tenant/V2_3__create_gis_schema.sql");
    private static final List<String> GIS_MIGRATIONS = List.of(
            "db/migration/tenant/V13_1__gis_classification_catalog.sql",
            "db/migration/tenant/V13_2__gis_features_history_migration.sql");

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    // ------------------------------------------------------------------ setup

    /** Schema descartável com o modelo antigo (V2_2 + V2_3) e a massa de referência. */
    private static Scratch legacyScratch(Statement st) throws Exception {
        UUID tenant = UUID.randomUUID();
        String schema = "it_gis_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        st.execute("CREATE SCHEMA " + schema);
        st.execute("SET LOCAL search_path = " + schema + ", public");
        for (String m : LEGACY_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        asTenant(st, tenant);
        insertLegacyMass(st, tenant);
        return new Scratch(tenant, schema);
    }

    /** Aplica V13_1 + V13_2 no schema descartável e ativa o pacote sanitation para o tenant. */
    private static void migrate(Statement st, Scratch s) throws Exception {
        for (String m : GIS_MIGRATIONS) {
            st.execute(migrationSql(m, s.tenant()));
        }
        // platform.tenant_vertical.tenant_id referencia public.tenant_catalog(id):
        // o tenant descartável precisa da linha no catálogo (sem CNPJ, para não
        // esbarrar no UNIQUE entre schemas descartáveis), como o provisionamento
        // real faz antes de ativar o pacote.
        st.execute("INSERT INTO public.tenant_catalog (id, schema_name, name) VALUES ('"
                + s.tenant() + "', '" + s.schema() + "', 'Tenant descartável GIS-01')"
                + " ON CONFLICT (id) DO NOTHING");
        st.execute("INSERT INTO platform.tenant_vertical (tenant_id, vertical_id, state, activated_at)"
                + " VALUES ('" + s.tenant() + "', 'sanitation', 'ACTIVE', NOW())"
                + " ON CONFLICT (tenant_id, vertical_id) DO NOTHING");
        asTenant(st, s.tenant());
    }

    private static void asTenant(Statement st, UUID tenant) throws SQLException {
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
    }

    private static void useSchema(Statement st, String schema) throws SQLException {
        st.execute("SET LOCAL search_path = " + schema + ", public");
    }

    /**
     * Executa um SQL que deve falhar e volta ao savepoint: no PostgreSQL
     * qualquer erro aborta a transação inteira, então sem o savepoint o resto
     * do teste falharia com "current transaction is aborted" (padrão do teste
     * MTR). A GUC do tenant é definida antes do savepoint e sobrevive a ele.
     */
    private static void expectFailure(Connection c, Statement st, String sql) throws SQLException {
        var savepoint = c.setSavepoint();
        try {
            assertThatThrownBy(() -> st.execute(sql)).isInstanceOf(SQLException.class);
        } finally {
            c.rollback(savepoint);
        }
    }

    private static void expectFailure(Connection c, Statement st, String sql, String messagePart) throws SQLException {
        var savepoint = c.setSavepoint();
        try {
            assertThatThrownBy(() -> st.execute(sql)).isInstanceOf(SQLException.class)
                    .hasMessageContaining(messagePart);
        } finally {
            c.rollback(savepoint);
        }
    }

    /**
     * Violação de UNIQUE esperada: o SQLState é 23505, mas a mensagem do
     * PSQLException NÃO o contém (ela traz só constraint e chave) — verificar
     * a mensagem falha sempre, mesmo com a violação acontecendo.
     */
    private static void expectUniqueViolation(Connection c, Statement st, String sql) throws SQLException {
        var savepoint = c.setSavepoint();
        try {
            Throwable thrown = catchThrowable(() -> st.execute(sql));
            assertThat(thrown).isInstanceOf(SQLException.class);
            assertThat(((SQLException) thrown).getSQLState()).isEqualTo("23505");
        } finally {
            c.rollback(savepoint);
        }
    }

    private static String migrationSql(String path, UUID tenant) throws IOException {
        try (InputStream in = Gis01MigrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("migration %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("${tenant_id}", tenant.toString());
        }
    }

    /** Massa de referência sintética no modelo antigo: 15 linhas, 2 sem destino no novo modelo. */
    private static void insertLegacyMass(Statement st, UUID tenant) throws SQLException {
        String dma = "INSERT INTO dma_district (id, tenant_id, code, name) VALUES ('%s', '%s', 'DMA-1', 'DMA de teste')";
        UUID dmaId = UUID.randomUUID();
        st.execute(String.format(dma, dmaId, tenant));
        String el = "INSERT INTO gis_network_element (tenant_id, element_code, element_type, geometry, dma_id, material, diameter_mm, length_m, properties) VALUES ";
        // Água: tubo com comprimento declarado, válvula, junção, nó antigo, bomba, reservatório, tanque, poço, PRV.
        st.execute(el + String.format("('%s', 'P1', 'PIPE', ST_GeomFromText('LINESTRING(-46.0000 -23.0, -46.0010 -23.0)', 4674), '%s', 'PVC', 100, 110.50, '{}')", tenant, dmaId));
        st.execute(el + String.format("('%s', 'V1', 'VALVE', ST_GeomFromText('POINT(-46.0010 -23.0)', 4674), '%s', 'Ferro fundido', 150, NULL, '{}')", tenant, dmaId));
        st.execute(el + String.format("('%s', 'J1', 'JUNCTION', ST_GeomFromText('POINT(-46.0010 -23.0)', 4674), '%s', NULL, NULL, NULL, '{\"elevation_m\": 90.0, \"demand_lps\": 2.5}')", tenant, dmaId));
        st.execute(el + String.format("('%s', 'N1', 'NODE', ST_GeomFromText('POINT(-46.0020 -23.0)', 4674), '%s', NULL, NULL, NULL, '{}')", tenant, dmaId));
        st.execute(el + String.format("('%s', 'PS1', 'PUMP_STATION', ST_GeomFromText('POLYGON((-46.0000 -23.0, -46.0001 -23.0, -46.0001 -23.0001, -46.0000 -23.0001, -46.0000 -23.0))', 4674), '%s', NULL, NULL, NULL, '{\"power_kw\": 15.0, \"head_m\": 40.0}')", tenant, dmaId));
        st.execute(el + String.format("('%s', 'R1', 'RESERVOIR', ST_GeomFromText('POLYGON((-46.0020 -23.0, -46.0021 -23.0, -46.0021 -23.0001, -46.0020 -23.0001, -46.0020 -23.0))', 4674), '%s', NULL, NULL, NULL, '{}')", tenant, dmaId));
        st.execute(el + String.format("('%s', 'T1', 'TANK', ST_GeomFromText('POLYGON((-46.0030 -23.0, -46.0031 -23.0, -46.0031 -23.0001, -46.0030 -23.0001, -46.0030 -23.0))', 4674), '%s', NULL, NULL, NULL, '{}')", tenant, dmaId));
        st.execute(el + String.format("('%s', 'W1', 'WELL', ST_GeomFromText('POINT(-46.0040 -23.0)', 4674), '%s', NULL, NULL, NULL, '{}')", tenant, dmaId));
        st.execute(el + String.format("('%s', 'PRV1', 'PRESSURE_REDUCING_VALVE', ST_GeomFromText('POINT(-46.0015 -23.0)', 4674), '%s', NULL, 100, NULL, '{}')", tenant, dmaId));
        // Esgoto: coletor por gravidade, emissário pressurizado, poço de visita, ramal.
        st.execute(el + String.format("('%s', 'SP1', 'SEWER_PIPE', ST_GeomFromText('LINESTRING(-46.0010 -23.0, -46.0020 -23.0)', 4674), '%s', 'PVC', 200, 50.00, '{\"pressurized\": \"false\"}')", tenant, dmaId));
        st.execute(el + String.format("('%s', 'SPRESS', 'SEWER_PIPE', ST_GeomFromText('LINESTRING(-46.0020 -23.0, -46.0030 -23.0)', 4674), '%s', 'PEAD', 150, 75.00, '{\"pressurized\": \"true\"}')", tenant, dmaId));
        st.execute(el + String.format("('%s', 'MH1', 'MANHOLE', ST_GeomFromText('POINT(-46.0020 -23.0)', 4674), '%s', NULL, NULL, NULL, '{}')", tenant, dmaId));
        st.execute(el + String.format("('%s', 'C1', 'CONNECTION', ST_GeomFromText('LINESTRING(-46.0010 -23.0, -46.0011 -23.0)', 4674), '%s', 'PVC', 25, 12.00, '{}')", tenant, dmaId));
        // Sem destino no novo modelo: hidrômetro (é da ligação, não do GIS) e tubo com geometria de ponto.
        st.execute(el + String.format("('%s', 'M1', 'METER', ST_GeomFromText('POINT(-46.0050 -23.0)', 4674), '%s', NULL, NULL, NULL, '{}')", tenant, dmaId));
        st.execute(el + String.format("('%s', 'PBAD', 'PIPE', ST_GeomFromText('POINT(-46.0060 -23.0)', 4674), '%s', 'PVC', 100, NULL, '{}')", tenant, dmaId));
    }

    private record Scratch(UUID tenant, String schema) {
    }

    private static int count(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String queryString(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    // ------------------------------------------------------------------ testes

    @Test
    @DisplayName("legado migra sem perda: convertidos + nao mapeados = legado, e o legado fica intacto")
    void legacyMigratesWithoutLossAndReportsUnmapped() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                assertThat(count(st, "SELECT count(*) FROM gis_network_element")).isEqualTo(15);
                migrate(st, s);

                int converted = count(st, "SELECT count(DISTINCT legacy_object_id) FROM ("
                        + "SELECT legacy_object_id FROM gis_node WHERE legacy_object_id IS NOT NULL UNION ALL "
                        + "SELECT legacy_object_id FROM gis_arc WHERE legacy_object_id IS NOT NULL UNION ALL "
                        + "SELECT legacy_object_id FROM gis_connection WHERE legacy_object_id IS NOT NULL UNION ALL "
                        + "SELECT legacy_object_id FROM gis_structure WHERE legacy_object_id IS NOT NULL) f");
                int unmapped = count(st, "SELECT count(*) FROM gis_migration_unmapped");
                int legacy = count(st, "SELECT count(*) FROM gis_network_element_legacy");
                assertThat(converted + unmapped).isEqualTo(15);
                assertThat(legacy).isEqualTo(15);
                assertThat(unmapped).isEqualTo(2);

                // Relatório de não mapeadas: hidrômetro sem destino, tubo com geometria incompatível.
                try (ResultSet rs = st.executeQuery(
                        "SELECT element_code, reason FROM gis_migration_unmapped ORDER BY element_code")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("M1");
                    assertThat(rs.getString(2)).isEqualTo("NO_DESTINATION");
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("PBAD");
                    assertThat(rs.getString(2)).isEqualTo("GEOMETRY_TYPE_MISMATCH");
                    assertThat(rs.next()).isFalse();
                }
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("geometria tipada por papel: ponto em arco e linha em no sao recusados")
    void geometryRoleIsEnforced() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                String nodeType = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:JUNCTION'");
                String arcType = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:WATER_MAIN'");
                expectFailure(c, st,
                        "INSERT INTO gis_arc (tenant_id, code, feature_type_id, capture_method, geom) VALUES ('"
                                + s.tenant() + "', 'BAD-ARC', '" + arcType + "', 'DIGITIZED', "
                                + "ST_GeomFromText('POINTZ(-46.0 -23.0 0)', 4674))");
                expectFailure(c, st,
                        "INSERT INTO gis_node (tenant_id, code, feature_type_id, capture_method, geom) VALUES ('"
                                + s.tenant() + "', 'BAD-NODE', '" + nodeType + "', 'DIGITIZED', "
                                + "ST_GeomFromText('LINESTRINGZ(-46.0 -23.0 0, -46.001 -23.0 0)', 4674))");
                // A feição válida passa: o typmod recusa o tipo errado, não a tabela.
                // O ponto usa Z NaN (o caso de toda feição migrada do legado 2D): é
                // exatamente o caso que quebrava no trigger de auditoria antes da
                // decisão de auditar feições via histórico (ver o teste abaixo).
                st.execute("INSERT INTO gis_node (tenant_id, code, feature_type_id, capture_method, geom) VALUES ('"
                        + s.tenant() + "', 'OK-NODE', '" + nodeType + "', 'DIGITIZED', "
                        + "ST_GeomFromText('POINTZ(-46.0 -23.0 NaN)', 4674))");
                assertThat(count(st, "SELECT count(*) FROM gis_node WHERE code = 'OK-NODE'")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("code unico por tenant, nao global: mesmo code em dois tenants passa, duplicado no mesmo falha")
    void codeIsUniquePerTenantNotGlobal() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                // Dois schemas descartáveis, cada um com o seu tenant e o seu seed: o
                // segundo tenant precisa do próprio tipo FACTORY (a RLS esconde o do
                // primeiro), por isso dois schemas em vez de dois tenants num só.
                Scratch s1 = legacyScratch(st);
                migrate(st, s1);
                Scratch s2 = legacyScratch(st);
                migrate(st, s2);
                String insert = "INSERT INTO gis_node (tenant_id, code, feature_type_id, capture_method, geom) VALUES ('%s', 'DUP', "
                        + "(SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:JUNCTION'), "
                        + "'DIGITIZED', ST_GeomFromText('POINTZ(-46.0 -23.0 0)', 4674))";
                useSchema(st, s1.schema());
                asTenant(st, s1.tenant());
                st.execute(String.format(insert, s1.tenant()));
                useSchema(st, s2.schema());
                asTenant(st, s2.tenant());
                st.execute(String.format(insert, s2.tenant()));
                useSchema(st, s1.schema());
                asTenant(st, s1.tenant());
                expectUniqueViolation(c, st, String.format(insert, s1.tenant()));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("attributes fora do JSON Schema recusado por SQL direto; dentro, aceito")
    void attributesOutsideSchemaRejectedByDirectSql() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                // Tipo do tenant com schema exigente: campo obrigatório, enum e lista de valores.
                // gis_value_list tem uma linha por valor (list_code + code), não um JSON.
                st.execute("INSERT INTO gis_value_list (tenant_id, list_code, code, label, origin) VALUES ('"
                        + s.tenant() + "', 'cover_load_class', 'A15', 'A15', 'TENANT'), ('"
                        + s.tenant() + "', 'cover_load_class', 'D400', 'D400', 'TENANT')");
                st.execute("INSERT INTO gis_feature_class (tenant_id, code, domain, topology_role, geometry_type, origin) "
                        + "VALUES ('" + s.tenant() + "', 'VALVE_TEST', 'WATER', 'NODE', 'PointZ', 'TENANT')");
                String classId = queryString(st,
                        "SELECT id FROM gis_feature_class WHERE tenant_id = '" + s.tenant() + "' AND code = 'VALVE_TEST'");
                st.execute("INSERT INTO gis_feature_type (tenant_id, class_id, code, label, attributes_schema, origin) VALUES ('"
                        + s.tenant() + "', '" + classId + "', 'VALVE_TEST', 'Válvula de teste', '{\"required\": [\"turns\"], "
                        + "\"properties\": {\"turns\": {\"type\": \"integer\"}, "
                        + "\"actuator\": {\"type\": \"string\", \"enum\": [\"MANUAL\", \"ELECTRIC\"]}, "
                        + "\"cover\": {\"$ref\": \"gis_value_list:cover_load_class\"}}, \"additionalProperties\": false}', 'TENANT')");
                String typeId = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE tenant_id = '" + s.tenant() + "' AND code = 'VALVE_TEST'");
                String base = "INSERT INTO gis_node (tenant_id, code, feature_type_id, capture_method, geom, attributes) VALUES ('"
                        + s.tenant() + "', '%s', '" + typeId + "', 'DIGITIZED', "
                        + "ST_GeomFromText('POINTZ(-46.0 -23.0 0)', 4674), '%s'::jsonb)";
                expectFailure(c, st, String.format(base, "ATTR-1", "{\"actuator\": \"MANUAL\"}"), "turns");
                expectFailure(c, st, String.format(base, "ATTR-2",
                        "{\"turns\": 3, \"actuator\": \"HYDRAULIC\"}"));
                expectFailure(c, st, String.format(base, "ATTR-3",
                        "{\"turns\": 3, \"cover\": \"F900\"}"));
                expectFailure(c, st, String.format(base, "ATTR-4",
                        "{\"turns\": 3, \"surprise\": true}"));
                st.execute(String.format(base, "ATTR-OK",
                        "{\"turns\": 12, \"actuator\": \"MANUAL\", \"cover\": \"D400\"}"));
                assertThat(count(st, "SELECT count(*) FROM gis_node WHERE code = 'ATTR-OK'")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("metadado de captura obrigatorio: feicao sem capture_method e recusada")
    void captureMethodIsMandatory() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                String nodeType = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:JUNCTION'");
                assertThatThrownBy(() -> st.execute(
                        "INSERT INTO gis_node (tenant_id, code, feature_type_id, geom) VALUES ('"
                                + s.tenant() + "', 'NO-CAPTURE', '" + nodeType + "', "
                                + "ST_GeomFromText('POINTZ(-46.0 -23.0 0)', 4674))"))
                        .isInstanceOf(SQLException.class);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("historico sem sobreposicao e consulta por data devolve o estado antigo")
    void historyHasNoOverlapAndAsOfQueryWorks() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                String nodeType = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:JUNCTION'");
                st.execute("INSERT INTO gis_node (id, tenant_id, code, feature_type_id, capture_method, geom, name) VALUES ("
                        + "'11111111-1111-1111-1111-111111111111', '" + s.tenant() + "', 'HIST-1', '" + nodeType + "', "
                        + "'DIGITIZED', ST_GeomFromText('POINTZ(-46.0 -23.0 0)', 4674), 'nome v1')");
                st.execute("UPDATE gis_node SET name = 'nome v2' WHERE code = 'HIST-1'");
                st.execute("UPDATE gis_node SET name = 'nome v3' WHERE code = 'HIST-1'");
                assertThat(count(st, "SELECT count(*) FROM gis_node_history WHERE code = 'HIST-1'")).isEqualTo(2);
                assertThat(count(st, "SELECT count(*) FROM gis_node_history a JOIN gis_node_history b "
                        + "ON a.id = b.id AND a.ctid <> b.ctid AND a.valid_period && b.valid_period")).isZero();
                String mid = queryString(st, "SELECT (lower(valid_period) + (upper(valid_period) - lower(valid_period)) / 2)::text "
                        + "FROM gis_node_history WHERE code = 'HIST-1' AND name = 'nome v1'");
                String oldName = queryString(st, "SELECT gis_feature_at('gis_node', "
                        + "'11111111-1111-1111-1111-111111111111', '" + mid + "') ->> 'name'");
                assertThat(oldName).isEqualTo("nome v1");
                // Estado atual: clock_timestamp(), nao now() — now() e o inicio
                // desta transacao (o mesmo instante do created_at), e o periodo
                // [created_at, ...) da primeira versao o contem, entao gis_feature_at
                // devolveria 'nome v1' corretamente como "estado em T0". O relogio
                // real (depois da ultima alteracao) cai fora de todo periodo e
                // devolve a linha atual, 'nome v3'.
                String currentName = queryString(st, "SELECT gis_feature_at('gis_node', "
                        + "'11111111-1111-1111-1111-111111111111', clock_timestamp()) ->> 'name'");
                assertThat(currentName).isEqualTo("nome v3");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("view gis_network_element reproduz o legado nas colunas que alimentam o .inp")
    void compatibilityViewMatchesLegacyOnReferenceMass() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                // Comparação 1:1 para as linhas com correspondência direta (nó/arco).
                // O tipo antigo volta pela view (NODE->JUNCTION, emissário
                // pressurizado->SEWER_PIPE), a geometria 2D é igual (a view carrega Z
                // NaN, por isso ST_Force2D dos dois lados), o comprimento do arco é o
                // declarado, e o material/diâmetro são os do legado.
                int mismatches = count(st,
                        "SELECT count(*) FROM gis_network_element_legacy l "
                                + "JOIN gis_network_element v ON v.element_code = l.element_code AND v.tenant_id = l.tenant_id "
                                + "WHERE l.element_code IN ('P1', 'V1', 'J1', 'N1', 'PRV1', 'SP1', 'SPRESS', 'MH1') "
                                + "AND (CASE l.element_type WHEN 'NODE' THEN 'JUNCTION' ELSE l.element_type END <> v.element_type "
                                + "OR NOT ST_Equals(ST_Force2D(l.geometry), ST_Force2D(v.geometry)) "
                                + "OR l.dma_id IS DISTINCT FROM v.dma_id "
                                + "OR l.material IS DISTINCT FROM v.material "
                                + "OR l.diameter_mm IS DISTINCT FROM v.diameter_mm "
                                + "OR COALESCE(l.length_m, 0) <> COALESCE(v.length_m, 0))");
                assertThat(mismatches).isZero();
                // O ramal volta como SERVICE_LINE, com a mesma geometria 2D e diâmetro;
                // o comprimento NÃO é comparado: o ramal novo calcula o comprimento da
                // geometria, e o legado guardava 12,00 m declarados (o .inp usa o
                // comprimento cadastrado quando presente — ver GisCompatibilityViewInpTest).
                assertThat(count(st, "SELECT count(*) FROM gis_network_element_legacy l "
                        + "JOIN gis_network_element v ON v.element_code = l.element_code AND v.tenant_id = l.tenant_id "
                        + "WHERE l.element_code = 'C1' AND v.element_type = 'SERVICE_LINE' "
                        + "AND ST_Equals(ST_Force2D(l.geometry), ST_Force2D(v.geometry)) "
                        + "AND l.diameter_mm = v.diameter_mm")).isEqualTo(1);
                // Estruturas: a view emite o nó hidráulico contido com o tipo antigo da estrutura.
                assertThat(count(st, "SELECT count(*) FROM gis_network_element "
                        + "WHERE element_code IN ('PS1', 'R1', 'T1') AND element_type IN ('PUMP_STATION', 'RESERVOIR', 'TANK')"))
                        .isEqualTo(3);
                // Não mapeadas não aparecem na view.
                assertThat(count(st, "SELECT count(*) FROM gis_network_element WHERE element_code IN ('M1', 'PBAD')")).isZero();
                // Números que o builder lê das properties: elevação e demanda da junção sobrevivem.
                assertThat(queryString(st, "SELECT properties ->> 'elevation_m' FROM gis_network_element WHERE element_code = 'J1'"))
                        .isEqualTo("90.0");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("dma_district e leak_detection: mesmo code em dois tenants passa, duplicado no mesmo falha")
    void dmaAndLeakCodesUniquePerTenant() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                UUID otherTenant = UUID.randomUUID();
                // dma_district já tem DMA-1 no tenant do scratch; o outro tenant aceita o mesmo code.
                asTenant(st, otherTenant);
                st.execute("INSERT INTO dma_district (tenant_id, code, name) VALUES ('" + otherTenant + "', 'DMA-1', 'Outro')");
                asTenant(st, s.tenant());
                expectUniqueViolation(c, st,
                        "INSERT INTO dma_district (tenant_id, code, name) VALUES ('" + s.tenant() + "', 'DMA-1', 'Duplicado')");
                String leak = "INSERT INTO leak_detection (tenant_id, detection_code, dma_id, location, detection_method, detected_at) "
                        + "VALUES ('%s', 'LEAK-1', "
                        + "(SELECT id FROM dma_district WHERE tenant_id = '%s' AND code = 'DMA-1'), "
                        + "ST_GeomFromText('POINT(-46.0 -23.0)', 4674), 'VISUAL_INSPECTION', NOW())";
                st.execute(String.format(leak, s.tenant(), s.tenant()));
                asTenant(st, otherTenant);
                st.execute(String.format(leak, otherTenant, otherTenant));
                asTenant(st, s.tenant());
                expectUniqueViolation(c, st, String.format(leak, s.tenant(), s.tenant()));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("dominio inexistente recusado pelo banco; pacote ausente esconde o catalogo e trava classe do tenant")
    void unknownDomainRefusedAndPackageGateWorks() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                // Domínio inexistente: a FK de gis_feature_class.domain recusa.
                expectFailure(c, st,
                        "INSERT INTO gis_feature_class (tenant_id, code, domain, topology_role, geometry_type, origin) "
                                + "VALUES ('" + s.tenant() + "', 'BAD-DOMAIN', 'NOPE', 'NODE', 'PointZ', 'TENANT')");
                // Tenant sem sanitation, mas COM linha de domínio (para a FK não
                // mascarar o gate): a classe TENANT é recusada pelo gatilho de pacote.
                UUID bareTenant = UUID.randomUUID();
                asTenant(st, bareTenant);
                st.execute("INSERT INTO gis_domain (tenant_id, key, package, label, map_color_token, sort_order) "
                        + "VALUES ('" + bareTenant + "', 'WATER', 'sanitation', 'Água', 'map-water', 1)");
                expectFailure(c, st,
                        "INSERT INTO gis_feature_class (tenant_id, code, domain, topology_role, geometry_type, origin) "
                                + "VALUES ('" + bareTenant + "', 'NO-PACKAGE', 'WATER', 'NODE', 'PointZ', 'TENANT')");
                // O tenant com o pacote vê os cinco domínios; sem o pacote, a view esvazia
                // (os domínios continuam em gis_domain — é a visibilidade que some).
                asTenant(st, s.tenant());
                assertThat(count(st, "SELECT count(*) FROM gis_domain_visible")).isEqualTo(5);
                st.execute("DELETE FROM platform.tenant_vertical WHERE tenant_id = '" + s.tenant() + "'");
                assertThat(count(st, "SELECT count(*) FROM gis_domain_visible")).isZero();
                assertThat(count(st, "SELECT count(*) FROM gis_domain")).isEqualTo(5);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("catalogo core tem os parametros gis.*: work_srid com a lista do §3.9 e piso 5 na agregacao")
    void catalogHasGisParametersWithFloors() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                // A core V9_6 já rodou no provisionamento (ProvisionedTenant.ensure): o
                // catálogo é global (public), não por schema descartável. Cada chave
                // é verificada individualmente (outra frente pode somar chaves gis.*).
                for (String key : List.of("gis.work_srid", "gis.vertical_datum", "gis.snap_tolerance_m",
                        "gis.import_snap_tolerance_m", "gis.service_line_max_distance_m", "gis.accuracy_targets",
                        "gis.basemap_provider", "gis.style_profile", "gis.public_aggregation_min_units")) {
                    assertThat(count(st, "SELECT count(*) FROM public.parameter_definitions "
                            + "WHERE definition_key = '" + key + "'")).as(key).isEqualTo(1);
                }
                String allowed = queryString(st, "SELECT limit_value FROM public.parameter_legal_limits "
                        + "WHERE definition_key = 'gis.work_srid' AND limit_kind = 'ALLOWED_VALUES'");
                assertThat(allowed).contains("31972").contains("31976").contains("31978").contains("31985").contains("5396")
                        .doesNotContain("31977");
                String floor = queryString(st, "SELECT limit_value FROM public.parameter_legal_limits "
                        + "WHERE definition_key = 'gis.public_aggregation_min_units' AND limit_kind = 'FLOOR'");
                assertThat(floor).isEqualTo("5");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("nenhuma coluna de idade armazenada no novo modelo (o legado a preserva)")
    void noStoredAgeColumnInNewModel() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                assertThat(count(st, "SELECT count(*) FROM information_schema.columns WHERE table_schema = '" + s.schema() + "' "
                        + "AND table_name IN ('gis_node', 'gis_arc', 'gis_connection', 'gis_structure', 'gis_zone', 'gis_poi', 'gis_attachment') "
                        + "AND column_name = 'age_years'")).isZero();
                assertThat(count(st, "SELECT count(*) FROM information_schema.columns WHERE table_schema = '" + s.schema() + "' "
                        + "AND table_name = 'gis_network_element_legacy' AND column_name = 'age_years'")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("feicoes sao auditadas pelo historico, nao pelo audit_log (Z NaN quebra o to_jsonb do trigger)")
    void featuresAreAuditedViaHistoryNotAuditLog() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                // As seis tabelas de feição não têm audit_trigger (a trilha delas é o
                // histórico); anexo, contenção e não-mapeadas mantêm o trigger.
                assertThat(count(st, "SELECT count(*) FROM pg_trigger t JOIN pg_class cl ON cl.oid = t.tgrelid "
                        + "JOIN pg_namespace n ON n.oid = cl.relnamespace "
                        + "WHERE n.nspname = '" + s.schema() + "' AND t.tgname = 'audit_trigger' "
                        + "AND cl.relname IN ('gis_node', 'gis_arc', 'gis_connection', 'gis_structure', 'gis_zone', 'gis_poi')"))
                        .isZero();
                assertThat(count(st, "SELECT count(*) FROM pg_trigger t JOIN pg_class cl ON cl.oid = t.tgrelid "
                        + "JOIN pg_namespace n ON n.oid = cl.relnamespace "
                        + "WHERE n.nspname = '" + s.schema() + "' AND t.tgname = 'audit_trigger' "
                        + "AND cl.relname IN ('gis_attachment', 'gis_containment', 'gis_migration_unmapped')"))
                        .isEqualTo(3);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("tenant provisionado nasce com os cinco dominios do pacote sanitation")
    void provisionedTenantHasFiveDomains() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            // SET LOCAL e GUC local só valem dentro de uma transação.
            c.setAutoCommit(false);
            st.execute("SET LOCAL search_path = " + ProvisionedTenant.SCHEMA + ", public");
            asTenant(st, ProvisionedTenant.TENANT_ID);
            assertThat(count(st, "SELECT count(*) FROM gis_domain")).isEqualTo(5);
            assertThat(count(st, "SELECT count(*) FROM gis_domain_visible")).isEqualTo(5);
            try (ResultSet rs = st.executeQuery("SELECT key FROM gis_domain ORDER BY sort_order")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("WATER");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("SEWER");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("STORM");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("WASTE");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("CONTEXT");
                assertThat(rs.next()).isFalse();
            }
        }
    }
}
