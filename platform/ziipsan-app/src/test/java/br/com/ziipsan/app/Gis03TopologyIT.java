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

/**
 * GIS-03 (P2.21): topologia persistida da V13_8 no PostgreSQL real — snap com
 * tolerancia do tenant, criacao de JUNCTION, obrigatoriedade de no em arco em
 * operacao, divisao de arco, arrasto de ponta, area suja e a view de
 * compatibilidade com node_1_code/node_2_code.
 *
 * <p>Segue EXATAMENTE o padrao do {@code Gis01MigrationTest}: o teste roda o
 * texto das proprias migrations (do classpath, com o placeholder tenant_id
 * substituido, como o TenantMigrator faz) contra um schema descartavel, numa
 * transacao desfeita no fim; cada teste monta o seu schema do zero. A
 * diferenca para a GIS-01 e a lista de migrations: aqui entram tambem a V1_5
 * (parameter_versions, de onde o snap le gis.snap_tolerance_m), as V13_3 a
 * V13_7 (a forma vigente de gis_check_feature/gis_write_history no HEAD) e a
 * V13_8. A massa e sintetica (coordenadas em graus em torno de -46, -23, sem
 * endereco real); a tolerancia padrao de fabrica (0,10 m) vale em todos os
 * testes — nenhum deles grava parameter_versions, entao o caminho do
 * factory_default e o exercitado.
 *
 * <p>Medidas usadas nos cenarios: 0,0000005 grau de longitude em -23 graus
 * vale cerca de 0,05 m (dentro da tolerancia); 0,001 grau vale cerca de
 * 100 m (longe de qualquer snap).
 */
class Gis03TopologyIT {

    private static final List<String> LEGACY_MIGRATIONS = List.of(
            "db/migration/tenant/V1_1__create_tenant_schema.sql",
            "db/migration/tenant/V1_2__tenant_table_helper.sql",
            "db/migration/tenant/V1_3__harden_audit_function.sql",
            "db/migration/tenant/V1_5__tenant_parameters.sql",
            "db/migration/tenant/V2_2__create_losses_schema.sql",
            "db/migration/tenant/V2_3__create_gis_schema.sql");
    private static final List<String> GIS_MIGRATIONS = List.of(
            "db/migration/tenant/V13_1__gis_classification_catalog.sql",
            "db/migration/tenant/V13_2__gis_features_history_migration.sql",
            "db/migration/tenant/V13_3__gis_responsible_party_redaction.sql",
            "db/migration/tenant/V13_4__gis_catalog_approval.sql",
            "db/migration/tenant/V13_5__gis_check_feature_qualified.sql",
            "db/migration/tenant/V13_6__gis_check_feature_search_path.sql",
            "db/migration/tenant/V13_7__gis_write_history_explicit_columns.sql",
            "db/migration/tenant/V13_8__gis_topology_validation.sql");
    private static final String FACTORY_CATALOG =
            "db/migration/tenant/R__gis_factory_catalog.sql";

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    // ------------------------------------------------------------------ setup

    /** Schema descartavel com o modelo antigo aplicado e nenhum dado legado. */
    private static Scratch legacyScratch(Statement st) throws Exception {
        UUID tenant = UUID.randomUUID();
        String schema = "it_gis_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        st.execute("CREATE SCHEMA " + schema);
        st.execute("SET LOCAL search_path = " + schema + ", public");
        for (String m : LEGACY_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        asTenant(st, tenant);
        return new Scratch(tenant, schema);
    }

    /** Aplica V13_1 a V13_8 no schema descartavel e ativa o pacote sanitation. */
    private static void migrate(Statement st, Scratch s) throws Exception {
        for (String m : GIS_MIGRATIONS) {
            st.execute(migrationSql(m, s.tenant()));
        }
        // Mesmo passo do Gis01MigrationTest: o tenant descartavel precisa da
        // linha no catalogo para ativar o pacote (sem CNPJ, para nao esbarrar
        // no UNIQUE entre schemas descartaveis).
        st.execute("INSERT INTO public.tenant_catalog (id, schema_name, name) VALUES ('"
                + s.tenant() + "', '" + s.schema() + "', 'Tenant descartável GIS-03')"
                + " ON CONFLICT (id) DO NOTHING");
        st.execute("INSERT INTO platform.tenant_vertical (tenant_id, vertical_id, state, activated_at)"
                + " VALUES ('" + s.tenant() + "', 'sanitation', 'ACTIVE', NOW())"
                + " ON CONFLICT (tenant_id, vertical_id) DO NOTHING");
        asTenant(st, s.tenant());
    }

    private static void asTenant(Statement st, UUID tenant) throws SQLException {
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
    }

    /**
     * Executa um SQL que deve falhar e volta ao savepoint: no PostgreSQL
     * qualquer erro aborta a transacao inteira, entao sem o savepoint o resto
     * do teste falharia com "current transaction is aborted" (padrao da
     * GIS-01/MTR).
     */
    private static void expectFailure(Connection c, Statement st, String sql, String messagePart) throws SQLException {
        var savepoint = c.setSavepoint();
        try {
            assertThatThrownBy(() -> st.execute(sql)).isInstanceOf(SQLException.class)
                    .hasMessageContaining(messagePart);
        } finally {
            c.rollback(savepoint);
        }
    }

    private static String migrationSql(String path, UUID tenant) throws IOException {
        try (InputStream in = Gis03TopologyIT.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("migration %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("${tenant_id}", tenant.toString());
        }
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

    // ------------------------------------------------------------- feicoes

    private static void insertNode(Statement st, UUID tenant, String code,
                                   String typeFactoryCode, String pointWkt) throws SQLException {
        String typeId = queryString(st,
                "SELECT id FROM gis_feature_type WHERE factory_code = '" + typeFactoryCode + "'");
        st.execute("INSERT INTO gis_node (tenant_id, code, feature_type_id, capture_method, geom) VALUES ('"
                + tenant + "', '" + code + "', '" + typeId + "', 'DIGITIZED', "
                + "ST_GeomFromText('" + pointWkt + "', 4674))");
    }

    private static void insertArc(Statement st, UUID tenant, String code, String lifecycle,
                                  String lineWkt) throws SQLException {
        String typeId = queryString(st,
                "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:WATER_MAIN'");
        st.execute("INSERT INTO gis_arc (tenant_id, code, feature_type_id, lifecycle_status, capture_method, geom) VALUES ('"
                + tenant + "', '" + code + "', '" + typeId + "', '" + lifecycle + "', 'DIGITIZED', "
                + "ST_GeomFromText('" + lineWkt + "', 4674))");
    }

    /** Code do no ligado a ponta 1 (qual = 1) ou 2 do arco. */
    private static String arcNodeCode(Statement st, String arcCode, int qual) throws SQLException {
        return queryString(st, "SELECT n.code FROM gis_arc a JOIN gis_node n ON n.id = a.node_" + qual + "_id "
                + "WHERE a.code = '" + arcCode + "'");
    }

    // ------------------------------------------------------------------ testes

    @Test
    @DisplayName("snap: arco com pontas perto de nos existentes recebe os node ids e o vertice movido")
    void snapMovesVertexAndFillsNodeIds() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                insertNode(st, s.tenant(), "N1", "gis01:JUNCTION", "POINTZ(-46.0000 -23.0 0)");
                insertNode(st, s.tenant(), "N2", "gis01:JUNCTION", "POINTZ(-46.0010 -23.0 0)");
                // Pontas a cerca de 0,05 m dos nos: dentro da tolerancia de 0,10 m.
                insertArc(st, s.tenant(), "ARC-1", "IN_SERVICE",
                        "LINESTRINGZ(-46.0000005 -23.0 0, -46.0009995 -23.0 0)");

                assertThat(arcNodeCode(st, "ARC-1", 1)).isEqualTo("N1");
                assertThat(arcNodeCode(st, "ARC-1", 2)).isEqualTo("N2");
                // O vertice foi MOVIDO para a posicao exata do no.
                assertThat(count(st, "SELECT count(*) FROM gis_arc a JOIN gis_node n ON n.code = 'N1' "
                        + "WHERE a.code = 'ARC-1' AND ST_Equals(ST_StartPoint(a.geom), n.geom)")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_arc a JOIN gis_node n ON n.code = 'N2' "
                        + "WHERE a.code = 'ARC-1' AND ST_Equals(ST_EndPoint(a.geom), n.geom)")).isEqualTo(1);
                // Nenhum JUNCTION sobrando: so os dois nos cadastrados.
                assertThat(count(st, "SELECT count(*) FROM gis_node")).isEqualTo(2);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("snap: ponta sem no cria JUNCTION no ponto da ponta")
    void endpointWithoutNodeCreatesJunction() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                insertNode(st, s.tenant(), "N1", "gis01:JUNCTION", "POINTZ(-46.0000 -23.0 0)");
                insertArc(st, s.tenant(), "ARC-2", "IN_SERVICE",
                        "LINESTRINGZ(-46.0000 -23.0 0, -46.0010 -23.0 0)");

                assertThat(arcNodeCode(st, "ARC-2", 1)).isEqualTo("N1");
                // A ponta final ganhou a juncao automatica derivada do code do arco.
                assertThat(arcNodeCode(st, "ARC-2", 2)).isEqualTo("ARC-2-J2");
                assertThat(queryString(st, "SELECT ft.code FROM gis_node n "
                        + "JOIN gis_feature_type ft ON ft.id = n.feature_type_id "
                        + "WHERE n.code = 'ARC-2-J2'")).isEqualTo("JUNCTION");
                assertThat(count(st, "SELECT count(*) FROM gis_arc a JOIN gis_node n ON n.code = 'ARC-2-J2' "
                        + "WHERE a.code = 'ARC-2' AND ST_Equals(ST_EndPoint(a.geom), n.geom)")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_node")).isEqualTo(2);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("arco IN_SERVICE sem no alcancavel e recusado; PLANNED sem no e aceito")
    void inServiceArcWithoutReachableNodeIsRefused() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                // Criterio documentado na V13_8: com o tipo JUNCTION desativado
                // pelo tenant, juncao automatica nao e admitida e toda ponta tem
                // de cair num no cadastrado. Sem nenhum no no tenant, o arco em
                // operacao nao tem como cumprir o §5.1.
                st.execute("UPDATE gis_feature_type SET active = false WHERE factory_code = 'gis01:JUNCTION'");
                String arcType = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:WATER_MAIN'");
                expectFailure(c, st,
                        "INSERT INTO gis_arc (tenant_id, code, feature_type_id, lifecycle_status, capture_method, geom) VALUES ('"
                                + s.tenant() + "', 'ARC-3', '" + arcType + "', 'IN_SERVICE', 'DIGITIZED', "
                                + "ST_GeomFromText('LINESTRINGZ(-46.0000 -23.0 0, -46.0010 -23.0 0)', 4674))",
                        "sem no nas duas pontas");
                // PLANNED pode existir sem no (a ponta pode apontar para no ainda
                // nao cadastrado): a mesma geometria entra, com as pontas nulas.
                insertArc(st, s.tenant(), "ARC-3P", "PLANNED",
                        "LINESTRINGZ(-46.0000 -23.0 0, -46.0010 -23.0 0)");
                assertThat(count(st, "SELECT count(*) FROM gis_arc WHERE code = 'ARC-3P' "
                        + "AND node_1_id IS NULL AND node_2_id IS NULL")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("no inserido sobre o arco divide o arco em dois, preservando atributos, pacote e historico")
    void nodeInsertedOnArcSplitsIt() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                insertNode(st, s.tenant(), "N1", "gis01:JUNCTION", "POINTZ(-46.0000 -23.0 0)");
                insertNode(st, s.tenant(), "N2", "gis01:JUNCTION", "POINTZ(-46.0020 -23.0 0)");
                UUID changeSet = UUID.randomUUID();
                String arcType = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:WATER_MAIN'");
                st.execute("INSERT INTO gis_arc (tenant_id, code, feature_type_id, lifecycle_status, "
                        + "capture_method, geom, name, network_function, change_set_id) VALUES ('"
                        + s.tenant() + "', 'ARC-4', '" + arcType + "', 'IN_SERVICE', 'DIGITIZED', "
                        + "ST_GeomFromText('LINESTRINGZ(-46.0000 -23.0 0, -46.0020 -23.0 0)', 4674), "
                        + "'Trecho original', 'WATER_DISTRIBUTION', '" + changeSet + "')");
                assertThat(count(st, "SELECT count(*) FROM gis_arc")).isEqualTo(1);

                // O divisor e um te cadastrado exatamente sobre o segmento.
                insertNode(st, s.tenant(), "M1", "gis01:TEE", "POINTZ(-46.0010 -23.0 0)");

                assertThat(count(st, "SELECT count(*) FROM gis_arc WHERE code IN ('ARC-4', 'ARC-4-2')"))
                        .isEqualTo(2);
                // Pontas: original (N1, M1), novo (M1, N2).
                assertThat(arcNodeCode(st, "ARC-4", 1)).isEqualTo("N1");
                assertThat(arcNodeCode(st, "ARC-4", 2)).isEqualTo("M1");
                assertThat(arcNodeCode(st, "ARC-4-2", 1)).isEqualTo("M1");
                assertThat(arcNodeCode(st, "ARC-4-2", 2)).isEqualTo("N2");
                // Atributos e pacote preservados no arco novo.
                assertThat(queryString(st, "SELECT name FROM gis_arc WHERE code = 'ARC-4-2'"))
                        .isEqualTo("Trecho original");
                assertThat(queryString(st, "SELECT network_function FROM gis_arc WHERE code = 'ARC-4-2'"))
                        .isEqualTo("WATER_DISTRIBUTION");
                assertThat(queryString(st, "SELECT change_set_id::text FROM gis_arc WHERE code = 'ARC-4-2'"))
                        .isEqualTo(changeSet.toString());
                assertThat(queryString(st, "SELECT feature_type_id::text FROM gis_arc WHERE code = 'ARC-4-2'"))
                        .isEqualTo(arcType);
                // O encurtamento do original passou pelo historico existente.
                assertThat(count(st, "SELECT count(*) FROM gis_arc_history WHERE code = 'ARC-4'"))
                        .isEqualTo(1);
                // A geometria do original termina exatamente no divisor.
                assertThat(count(st, "SELECT count(*) FROM gis_arc a JOIN gis_node n ON n.code = 'M1' "
                        + "WHERE a.code = 'ARC-4' AND ST_Equals(ST_EndPoint(a.geom), n.geom)")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("mover o no arrasta a ponta do arco ligado")
    void movingNodeDragsArcEndpoint() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                insertNode(st, s.tenant(), "N1", "gis01:JUNCTION", "POINTZ(-46.0000 -23.0 0)");
                insertNode(st, s.tenant(), "N2", "gis01:JUNCTION", "POINTZ(-46.0010 -23.0 0)");
                insertArc(st, s.tenant(), "ARC-5", "IN_SERVICE",
                        "LINESTRINGZ(-46.0000 -23.0 0, -46.0010 -23.0 0)");

                st.execute("UPDATE gis_node SET geom = ST_GeomFromText('POINTZ(-46.0001 -23.0002 0)', 4674) "
                        + "WHERE code = 'N1'");

                assertThat(count(st, "SELECT count(*) FROM gis_arc a JOIN gis_node n ON n.code = 'N1' "
                        + "WHERE a.code = 'ARC-5' AND ST_Equals(ST_StartPoint(a.geom), n.geom)")).isEqualTo(1);
                assertThat(arcNodeCode(st, "ARC-5", 1)).isEqualTo("N1");
                // Arrastar a ponta nao divide o arco nem cria outro.
                assertThat(count(st, "SELECT count(*) FROM gis_arc")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("gravacao em feicao gera gis_dirty_area PENDING com o envelope da feicao")
    void writesGenerateDirtyArea() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                // A conversao da V13_2 roda antes de os gatilhos da V13_8
                // existirem: nenhuma area suja nasce da migracao.
                assertThat(count(st, "SELECT count(*) FROM gis_dirty_area")).isZero();

                insertNode(st, s.tenant(), "D1", "gis01:JUNCTION", "POINTZ(-46.0000 -23.0 0)");
                int afterNode = count(st, "SELECT count(*) FROM gis_dirty_area");
                assertThat(afterNode).isGreaterThanOrEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_dirty_area d JOIN gis_node n ON n.code = 'D1' "
                        + "WHERE d.feature_table = 'gis_node' AND d.status = 'PENDING' "
                        + "AND ST_Covers(d.envelope, n.geom)")).isGreaterThanOrEqualTo(1);

                insertNode(st, s.tenant(), "D2", "gis01:JUNCTION", "POINTZ(-46.0010 -23.0 0)");
                insertArc(st, s.tenant(), "D-ARC", "PLANNED",
                        "LINESTRINGZ(-46.0000 -23.0 0, -46.0010 -23.0 0)");
                assertThat(count(st, "SELECT count(*) FROM gis_dirty_area"))
                        .isGreaterThan(afterNode);
                assertThat(count(st, "SELECT count(*) FROM gis_dirty_area "
                        + "WHERE feature_table = 'gis_arc' AND status = 'PENDING'"))
                        .isGreaterThanOrEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("view gis_network_element expoe node_1_code e node_2_code no ramo do arco")
    void viewExposesNodeCodes() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                insertNode(st, s.tenant(), "VN1", "gis01:JUNCTION", "POINTZ(-46.0000 -23.0 0)");
                insertNode(st, s.tenant(), "VN2", "gis01:JUNCTION", "POINTZ(-46.0010 -23.0 0)");
                insertArc(st, s.tenant(), "ARC-7", "IN_SERVICE",
                        "LINESTRINGZ(-46.0000 -23.0 0, -46.0010 -23.0 0)");

                assertThat(queryString(st, "SELECT node_1_code FROM gis_network_element "
                        + "WHERE element_code = 'ARC-7'")).isEqualTo("VN1");
                assertThat(queryString(st, "SELECT node_2_code FROM gis_network_element "
                        + "WHERE element_code = 'ARC-7'")).isEqualTo("VN2");
                // Nos demais ramos as duas colunas sao nulas.
                assertThat(count(st, "SELECT count(*) FROM gis_network_element "
                        + "WHERE element_code = 'VN1' AND node_1_code IS NULL AND node_2_code IS NULL"))
                        .isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("ramal: ponta inicial cola no no (tap_node_id e arc_id) e ponta final cria JUNCTION")
    void connectionSnapFillsTapNodeAndArc() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                insertNode(st, s.tenant(), "CN1", "gis01:JUNCTION", "POINTZ(-46.0000 -23.0 0)");
                insertNode(st, s.tenant(), "CN2", "gis01:JUNCTION", "POINTZ(-46.0010 -23.0 0)");
                insertArc(st, s.tenant(), "ARC-8", "IN_SERVICE",
                        "LINESTRINGZ(-46.0000 -23.0 0, -46.0010 -23.0 0)");
                String connType = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:WATER_SERVICE_LINE'");
                // Ponta inicial a cerca de 0,05 m de CN1; ponta final longe de tudo.
                st.execute("INSERT INTO gis_connection (tenant_id, code, feature_type_id, lifecycle_status, "
                        + "capture_method, geom) VALUES ('" + s.tenant() + "', 'CON-1', '" + connType + "', "
                        + "'IN_SERVICE', 'DIGITIZED', "
                        + "ST_GeomFromText('LINESTRINGZ(-46.0000005 -23.0 0, -46.0005 -23.0005 0)', 4674))");

                assertThat(queryString(st, "SELECT n.code FROM gis_connection cn "
                        + "JOIN gis_node n ON n.id = cn.tap_node_id WHERE cn.code = 'CON-1'")).isEqualTo("CN1");
                assertThat(queryString(st, "SELECT a.code FROM gis_connection cn "
                        + "JOIN gis_arc a ON a.id = cn.arc_id WHERE cn.code = 'CON-1'")).isEqualTo("ARC-8");
                assertThat(count(st, "SELECT count(*) FROM gis_connection cn JOIN gis_node n ON n.code = 'CN1' "
                        + "WHERE cn.code = 'CON-1' AND ST_Equals(ST_StartPoint(cn.geom), n.geom)")).isEqualTo(1);
                // A ponta final sem no virou JUNCTION (sem coluna de no no ramal,
                // a juncao existe no cadastro com o code derivado).
                assertThat(count(st, "SELECT count(*) FROM gis_node WHERE code = 'CON-1-J2'")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("seeds de conectividade da R__ sao idempotentes e seguem o §5.3")
    void connectivityRuleSeedsAreIdempotent() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                st.execute(migrationSql(FACTORY_CATALOG, s.tenant()));
                st.execute(migrationSql(FACTORY_CATALOG, s.tenant()));
                asTenant(st, s.tenant());

                // 17 regras CONNECTS: 4 terminais de 1 arco, 7 pecas/valvulas de
                // 2 arcos, TEE e WYE de 3, CROSS de 4, SPECIAL_PIECE de 2 a 4 e
                // as 2 do JUNCTION (grau minimo e aviso de mudanca sem peca).
                assertThat(count(st, "SELECT count(*) FROM gis_connectivity_rule WHERE relation = 'CONNECTS'"))
                        .isEqualTo(17);
                assertThat(count(st, "SELECT count(*) FROM gis_connectivity_rule r "
                        + "JOIN gis_feature_type ft ON ft.id = r.from_type_id "
                        + "WHERE ft.code = 'JUNCTION' AND r.min_arcs = 2 AND r.max_arcs IS NULL "
                        + "AND r.severity = 'ERROR'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_connectivity_rule r "
                        + "JOIN gis_feature_type ft ON ft.id = r.from_type_id "
                        + "WHERE ft.code = 'JUNCTION' AND r.min_arcs IS NULL AND r.max_arcs IS NULL "
                        + "AND r.severity = 'WARNING'")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_connectivity_rule r "
                        + "JOIN gis_feature_type ft ON ft.id = r.from_type_id "
                        + "WHERE ft.code = 'CAP' AND r.min_arcs = 1 AND r.max_arcs = 1")).isEqualTo(1);
                assertThat(count(st, "SELECT count(*) FROM gis_connectivity_rule r "
                        + "JOIN gis_feature_type ft ON ft.id = r.from_type_id "
                        + "WHERE ft.code = 'CROSS' AND r.min_arcs = 4 AND r.max_arcs = 4")).isEqualTo(1);
                assertThat(queryString(st, "SELECT r.condition::text FROM gis_connectivity_rule r "
                        + "JOIN gis_feature_type ft ON ft.id = r.from_type_id "
                        + "WHERE ft.code = 'REDUCER'")).contains("DIFFERENT");
                assertThat(queryString(st, "SELECT r.condition::text FROM gis_connectivity_rule r "
                        + "JOIN gis_feature_type ft ON ft.id = r.from_type_id "
                        + "WHERE ft.code = 'BEND'")).contains("SAME");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("B2: UPDATE PLANNED para IN_SERVICE em arco sem pontas e recusado")
    void updatePlannedToInServiceWithoutEndpointsIsRefused() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                // Sem juncao automatica (JUNCTION desativado), o arco PLANNED
                // entra sem pontas -- mas a TRANSICAO para IN_SERVICE tem de
                // recusar: o gatilho tambem dispara em UPDATE OF
                // lifecycle_status (correcao B2) e roda o snap sobre a
                // geometria existente.
                st.execute("UPDATE gis_feature_type SET active = false WHERE factory_code = 'gis01:JUNCTION'");
                insertArc(st, s.tenant(), "ARC-B2", "PLANNED",
                        "LINESTRINGZ(-46.0000 -23.0 0, -46.0010 -23.0 0)");
                assertThat(count(st, "SELECT count(*) FROM gis_arc WHERE code = 'ARC-B2' "
                        + "AND node_1_id IS NULL AND node_2_id IS NULL")).isEqualTo(1);
                expectFailure(c, st,
                        "UPDATE gis_arc SET lifecycle_status = 'IN_SERVICE' WHERE code = 'ARC-B2'",
                        "sem no nas duas pontas");
                assertThat(queryString(st, "SELECT lifecycle_status FROM gis_arc WHERE code = 'ARC-B2'"))
                        .isEqualTo("PLANNED");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("B2: INSERT UNDER_CONSTRUCTION sem no alcancavel e recusado")
    void insertUnderConstructionWithoutReachableNodeIsRefused() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                st.execute("UPDATE gis_feature_type SET active = false WHERE factory_code = 'gis01:JUNCTION'");
                String arcType = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:WATER_MAIN'");
                expectFailure(c, st,
                        "INSERT INTO gis_arc (tenant_id, code, feature_type_id, lifecycle_status, capture_method, geom) VALUES ('"
                                + s.tenant() + "', 'ARC-B2C', '" + arcType + "', 'UNDER_CONSTRUCTION', 'DIGITIZED', "
                                + "ST_GeomFromText('LINESTRINGZ(-46.0000 -23.0 0, -46.0010 -23.0 0)', 4674))",
                        "sem no nas duas pontas");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("B2: UPDATE de estado em arco com pontas ligadas passa")
    void updateStatusWithEndpointsPasses() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                insertNode(st, s.tenant(), "B2N1", "gis01:JUNCTION", "POINTZ(-46.0000 -23.0 0)");
                insertNode(st, s.tenant(), "B2N2", "gis01:JUNCTION", "POINTZ(-46.0010 -23.0 0)");
                insertArc(st, s.tenant(), "ARC-B2P", "PLANNED",
                        "LINESTRINGZ(-46.0000 -23.0 0, -46.0010 -23.0 0)");
                // O snap do INSERT ja ligou as pontas; a transicao redispara
                // o snap (idempotente, ST_SetPoint com o mesmo ponto) e passa.
                st.execute("UPDATE gis_arc SET lifecycle_status = 'IN_SERVICE' WHERE code = 'ARC-B2P'");
                assertThat(queryString(st, "SELECT lifecycle_status FROM gis_arc WHERE code = 'ARC-B2P'"))
                        .isEqualTo("IN_SERVICE");
                assertThat(arcNodeCode(st, "ARC-B2P", 1)).isEqualTo("B2N1");
                assertThat(arcNodeCode(st, "ARC-B2P", 2)).isEqualTo("B2N2");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("M2: tolerancia APPROVED de 0,50 m em parameter_versions faz snap que o padrao 0,10 nao faria")
    void approvedSnapToleranceIsHonored() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                insertNode(st, s.tenant(), "TOL1", "gis01:JUNCTION", "POINTZ(-46.0000 -23.0 0)");
                insertNode(st, s.tenant(), "TOL2", "gis01:JUNCTION", "POINTZ(-46.0010 -23.0 0)");
                // Versao APPROVED vigente de gis.snap_tolerance_m = 0,50
                // (proponente != aprovador e valid_from >= aprovacao, como a
                // V1_5 exige).
                st.execute("INSERT INTO parameter_versions (tenant_id, definition_key, value, valid_from, "
                        + "proposed_by, approved_by, approved_at, status, content_hash) VALUES ('"
                        + s.tenant() + "', 'gis.snap_tolerance_m', '0.50'::jsonb, CURRENT_DATE, '"
                        + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', now(), 'APPROVED', '"
                        + "a".repeat(64) + "')");
                // Ponta inicial a 0,000003 grau de TOL1: cerca de 0,31 m --
                // fora do padrao de fabrica (0,10), dentro do aprovado (0,50).
                insertArc(st, s.tenant(), "ARC-TOL", "IN_SERVICE",
                        "LINESTRINGZ(-46.000003 -23.0 0, -46.0010 -23.0 0)");
                assertThat(arcNodeCode(st, "ARC-TOL", 1)).isEqualTo("TOL1");
                assertThat(arcNodeCode(st, "ARC-TOL", 2)).isEqualTo("TOL2");
                assertThat(count(st, "SELECT count(*) FROM gis_arc a JOIN gis_node n ON n.code = 'TOL1' "
                        + "WHERE a.code = 'ARC-TOL' AND ST_Equals(ST_StartPoint(a.geom), n.geom)")).isEqualTo(1);
                // Snap no no existente: nenhum JUNCTION criado.
                assertThat(count(st, "SELECT count(*) FROM gis_node")).isEqualTo(2);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("M2: snap ignora no ABANDONED e cria JUNCTION na ponta")
    void snapIgnoresAbandonedNode() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Scratch s = legacyScratch(st);
                migrate(st, s);
                String junctionType = queryString(st,
                        "SELECT id FROM gis_feature_type WHERE factory_code = 'gis01:JUNCTION'");
                st.execute("INSERT INTO gis_node (tenant_id, code, feature_type_id, lifecycle_status, "
                        + "capture_method, geom) VALUES ('" + s.tenant() + "', 'AB1', '" + junctionType
                        + "', 'ABANDONED', 'DIGITIZED', ST_GeomFromText('POINTZ(-46.0000 -23.0 0)', 4674))");
                insertNode(st, s.tenant(), "AB2", "gis01:JUNCTION", "POINTZ(-46.0010 -23.0 0)");
                // Ponta inicial a cerca de 0,05 m do no abandonado: dentro da
                // tolerancia, mas no abandonado nao e ponta valida de rede.
                insertArc(st, s.tenant(), "ARC-AB", "IN_SERVICE",
                        "LINESTRINGZ(-46.0000005 -23.0 0, -46.0010 -23.0 0)");
                assertThat(arcNodeCode(st, "ARC-AB", 1)).isEqualTo("ARC-AB-J1");
                assertThat(arcNodeCode(st, "ARC-AB", 2)).isEqualTo("AB2");
            } finally {
                c.rollback();
            }
        }
    }
}
