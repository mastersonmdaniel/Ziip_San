package br.com.ziipsan.app;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.JoinInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.OpenForServiceOrderInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.PlantStructureInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.ProjectInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.ProjectView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.UserFeatureInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.UserLayerInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.UserLayerView;
import br.com.ziipsan.intelligence.gis.webedit.JdbcGisFeatureTypeSchemaPort;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIS-09 (ED-53): edição web no PostgreSQL+PostGIS real — SQL em
 * db/pendente/ED-53__gis_web_edit.sql sobre as migrations GIS
 * (V13_1..V13_9), exercitando o SERVIÇO GisWebEditService de
 * verdade (não só o SQL): a guarda camada×operação aplicada
 * (correção D1), a validação de atributos contra o
 * attributes_schema gravado no banco (D2), o par de pacotes do
 * projeto criado e vinculado (D5), a feição da camada do
 * usuário (D5), a junção executada (D5) e o sync do ramal
 * desenhado com elegibilidade e PENDING idempotente (D4).
 *
 * <p>Correção D3 da revisão ED-53: esta IT SUBSTITUI a versão
 * anterior, que era vacuosa (um único teste afirmando que a
 * própria classe tinha métodos — passava em qualquer ambiente
 * sem provar cenário nenhum). A declaração é registrada aqui e
 * no relatório: nenhuma asserção da suíte foi afrouxada; a IT
 * vacuosa foi removida e trocada por cenários de comportamento.
 *
 * <p>ESTADO HONESTO: escrita e NÃO executada na VM da frente de
 * correção (não há PostgreSQL/PostGIS disponível — verificado:
 * sem psql, sem servidor, sem socket). Ela roda na CI do dono,
 * no padrão das ITs vizinhas (ProvisionedTenant, schema
 * descartável, migrations do classpath, transação desfeita no
 * fim). O serviço roda sobre a MESMA conexão do teste
 * (SingleConnectionDataSource) para enxergar o schema
 * descartável ainda não confirmado; as anotações transacionais
 * do serviço ficam inertes fora do contêiner — quem manda é a
 * transação do teste. Assume o módulo ziipsan-gis no classpath
 * da app (é onde o controller/serviço moram na aplicação real).
 *
 * <p>Limite declarado (o mesmo da Gis10QgisIT): o setup roda
 * com a GUC app.current_tenant setada (exigência da RLS para os
 * INSERTs de seed/migração); a pendência 12 da ED-55 (GUC a
 * partir do mapeamento de papéis) segue NÃO resolvida e fora
 * desta IT.
 */
class Gis09WebEditIT {

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
            "db/migration/tenant/V13_8__gis_topology_validation.sql",
            "db/migration/tenant/V13_9__gis_change_sets.sql",
            "db/pendente/ED-53__gis_web_edit.sql");

    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID LAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeAll
    static void setUp() {
        ProvisionedTenant.ensure();
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    private record Scratch(UUID tenant, String schema) {
    }

    private static Scratch scratch(Statement st) throws Exception {
        UUID tenant = UUID.randomUUID();
        String schema = "it_gis_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        st.execute("CREATE SCHEMA " + schema);
        st.execute("SET LOCAL search_path = " + schema + ", public");
        for (String m : LEGACY_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        for (String m : GIS_MIGRATIONS) {
            st.execute(migrationSql(m, tenant));
        }
        st.execute("INSERT INTO public.tenant_catalog (id, schema_name, name) VALUES ('"
                + tenant + "', '" + schema + "', 'Tenant descartavel GIS-09')"
                + " ON CONFLICT (id) DO NOTHING");
        asTenant(st, tenant);
        return new Scratch(tenant, schema);
    }

    private static void asTenant(Statement st, UUID tenant) throws SQLException {
        st.execute("SELECT set_config('app.current_tenant', '" + tenant + "', true)");
    }

    private static String migrationSql(String path, UUID tenant) throws IOException {
        try (InputStream in = Gis09WebEditIT.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("migration %s no classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("${tenant_id}", tenant.toString());
        }
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

    /** Serviço real sobre a conexão do teste (mesmo schema descartável). */
    private static GisWebEditService service(Connection c, Scratch s, UUID user) {
        TenantContextHolder.setTenantId(s.tenant());
        TenantContextHolder.setUserId(user);
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(c, true));
        return new GisWebEditService(jdbc, Optional.empty(),
                new JdbcGisFeatureTypeSchemaPort(jdbc));
    }

    private static void grant(Statement st, Scratch s, UUID user, UUID layer,
                              String operation) throws SQLException {
        st.execute("INSERT INTO gis_layer_permission (tenant_id, layer_id,"
                + " layer_key, user_id, operation, created_by) VALUES ('"
                + s.tenant() + "', '" + layer + "', 'camada-it', '" + user
                + "', '" + operation + "', '" + user + "')");
    }

    private static String factoryTypeId(Statement st, String factoryCode)
            throws SQLException {
        return queryString(st, "SELECT id FROM gis_feature_type"
                + " WHERE factory_code = '" + factoryCode + "'");
    }

    // ------------------------------------------------------------ D1

    @Test
    @DisplayName("D1: servico recusa escrita sem permissao na camada e aceita com ela; permissao nao vaza entre tenants")
    void layerPermissionGuardIsEnforcedByService() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch a = scratch(st);
            grant(st, a, USER_A, LAYER, "CREATE");
            GisWebEditService svc = service(c, a, USER_A);
            ProjectView view = svc.createProject(
                    new ProjectInput("Obra IT", null, null, LAYER));
            assertThat(view.projectChangeSetId()).isNotNull();
            // Outra camada, sem concessao: 403 e nada e gravado.
            UUID otherLayer = UUID.randomUUID();
            assertThatThrownBy(() -> svc.createProject(
                    new ProjectInput("Obra IT 2", null, null, otherLayer)))
                    .isInstanceOf(ApiException.Forbidden.class);
            assertThat(count(st, "SELECT count(*) FROM gis_project")).isEqualTo(1);
            // Tenant B nao enxerga a permissao de A nem escreve com ela.
            Scratch b = scratch(st);
            assertThat(count(st, "SELECT count(*) FROM gis_layer_permission")).isZero();
            GisWebEditService svcB = service(c, b, USER_A);
            assertThatThrownBy(() -> svcB.createProject(
                    new ProjectInput("Obra IT B", null, null, LAYER)))
                    .isInstanceOf(ApiException.Forbidden.class);
            c.rollback();
        }
    }

    // ------------------------------------------------------------ D2

    @Test
    @DisplayName("D2: cadastro de unidade valida contra o attributes_schema gravado em gis_feature_type")
    void plantStructureAttributesValidatedAgainstDatabaseSchema() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch a = scratch(st);
            String typeId = factoryTypeId(st, "gis01:GATE_VALVE");
            assertThat(typeId).isNotNull();
            st.execute("UPDATE gis_feature_type SET attributes_schema = '"
                    + "{\"type\":\"object\",\"required\":[\"diameter_mm\"],"
                    + "\"properties\":{\"diameter_mm\":{\"type\":\"integer\","
                    + "\"minimum\":20,\"maximum\":2000},\"material\":{\"type\":"
                    + "\"string\",\"enum\":[\"PVC\",\"PEAD\",\"FOFO\"]}}}'::jsonb"
                    + " WHERE id = '" + typeId + "'");
            grant(st, a, USER_A, LAYER, "CREATE");
            GisWebEditService svc = service(c, a, USER_A);
            String polygon = "POLYGON((0 0, 10 0, 10 10, 0 10, 0 0))";
            // Obrigatorio ausente: recusado pelo esquema REAL do banco.
            ApiException missing = org.junit.jupiter.api.Assertions.assertThrows(
                    ApiException.BadRequest.class, () -> svc.draftPlantStructure(
                            new PlantStructureInput("ETA", "ETA-01", "ETA", polygon,
                                    null, Map.of(), UUID.fromString(typeId), LAYER)));
            assertThat(missing.code()).isEqualTo("atributos-invalidos");
            // Valido contra o esquema: aceito.
            assertThat(svc.draftPlantStructure(new PlantStructureInput(
                    "ETA", "ETA-01", "ETA", polygon, null,
                    Map.of("diameter_mm", 100, "material", "PVC"),
                    UUID.fromString(typeId), LAYER))).isNotNull();
            // Tipo sem esquema cadastrado: recusa explicita, nunca vazia.
            st.execute("UPDATE gis_feature_type SET attributes_schema = '{}'::jsonb"
                    + " WHERE id = '" + typeId + "'");
            ApiException noSchema = org.junit.jupiter.api.Assertions.assertThrows(
                    ApiException.BadRequest.class, () -> svc.draftPlantStructure(
                            new PlantStructureInput("ETA", "ETA-01", "ETA", polygon,
                                    null, Map.of("diameter_mm", 100),
                                    UUID.fromString(typeId), LAYER)));
            assertThat(noSchema.code()).isEqualTo("esquema-nao-cadastrado");
            // Tipo inexistente neste tenant: 404.
            assertThatThrownBy(() -> svc.draftPlantStructure(
                    new PlantStructureInput("ETA", "ETA-01", "ETA", polygon, null,
                            Map.of("diameter_mm", 100), UUID.randomUUID(), LAYER)))
                    .isInstanceOf(ApiException.NotFound.class);
            c.rollback();
        }
    }

    // ------------------------------------------------------------ D5

    @Test
    @DisplayName("D5: camada do usuario grava e le feicao; outro usuario nao acessa")
    void userLayerFeatureRoundTripAndOwnership() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch a = scratch(st);
            GisWebEditService svc = service(c, a, USER_A);
            UserLayerView layer = svc.createUserLayer(
                    new UserLayerInput("Anotacoes", "POINT"));
            // A criacao registrou a concessao do dono (guarda D1).
            assertThat(count(st, "SELECT count(*) FROM gis_layer_permission"
                    + " WHERE layer_id = '" + layer.id() + "'")).isEqualTo(9);
            var feature = svc.addUserFeature(layer.id(), new UserFeatureInput(
                    "POINT(-48.5 -26.9)", Map.of("note", "it")));
            assertThat(feature.id()).isNotNull();
            var listed = svc.listUserFeatures(layer.id());
            assertThat(listed).hasSize(1);
            assertThat(listed.get(0).geomWkt()).contains("-48.5");
            // Outro usuario do mesmo tenant: nem le nem escreve.
            GisWebEditService svcB = service(c, a, USER_B);
            assertThatThrownBy(() -> svcB.listUserFeatures(layer.id()))
                    .isInstanceOf(ApiException.Forbidden.class);
            assertThatThrownBy(() -> svcB.addUserFeature(layer.id(),
                    new UserFeatureInput("POINT(-48.5 -26.9)", Map.of())))
                    .isInstanceOf(ApiException.Forbidden.class);
            assertThat(count(st, "SELECT count(*) FROM gis_user_feature")).isEqualTo(1);
            c.rollback();
        }
    }

    @Test
    @DisplayName("D5: projeto nasce com os dois pacotes vinculados; FK recusa pacote de outro tenant")
    void projectPairCreatedLinkedAndCrossTenantFkRefused() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch a = scratch(st);
            grant(st, a, USER_A, LAYER, "CREATE");
            GisWebEditService svc = service(c, a, USER_A);
            ProjectView view = svc.createProject(
                    new ProjectInput("Obra IT", "desc", "OBRA-IT", LAYER));
            assertThat(view.projectChangeSetId()).isNotNull();
            assertThat(view.asBuiltChangeSetId()).isNotNull();
            assertThat(view.projectChangeSetId())
                    .isNotEqualTo(view.asBuiltChangeSetId());
            assertThat(count(st, "SELECT count(*) FROM gis_change_set"
                    + " WHERE project_ref = '" + view.id() + "'"
                    + " AND type IN ('PROJECT', 'AS_BUILT')")).isEqualTo(2);
            assertThat(queryString(st, "SELECT project_change_set_id::text"
                    + " FROM gis_project WHERE id = '" + view.id() + "'"))
                    .isEqualTo(view.projectChangeSetId().toString());
            // FK composta: o tenant B nao referencia o pacote de A.
            Scratch b = scratch(st);
            var sp = c.setSavepoint();
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO gis_project (id, tenant_id, name,"
                            + " project_change_set_id) VALUES ('"
                            + UUID.randomUUID() + "', '" + b.tenant()
                            + "', 'Invasor', '" + view.projectChangeSetId() + "')"))
                    .isInstanceOf(SQLException.class);
            c.rollback(sp);
            c.rollback();
        }
    }

    @Test
    @DisplayName("D5/SG-841: CHECK recusa tabela fora da lista e a juncao definida EXECUTA")
    void joinChecksAndExecution() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch a = scratch(st);
            var sp = c.setSavepoint();
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO gis_table_join (tenant_id, left_table,"
                            + " right_table, join_type, join_key) VALUES ('"
                            + a.tenant() + "', 'pg_shadow', 'gis_node',"
                            + " 'INNER', 'code')"))
                    .isInstanceOf(SQLException.class);
            c.rollback(sp);
            asTenant(st, a.tenant());
            grant(st, a, USER_A, LAYER, "MANAGE_LAYER");
            grant(st, a, USER_A, LAYER, "VIEW");
            GisWebEditService svc = service(c, a, USER_A);
            var join = svc.createJoin(new JoinInput(
                    "gis_node", "gis_arc", "INNER", "code", LAYER));
            // Massa: no e arco com o mesmo code.
            String nodeType = factoryTypeId(st, "gis01:GATE_VALVE");
            String arcType = queryString(st,
                    "SELECT id FROM gis_feature_type WHERE code = 'WATER_MAIN' LIMIT 1");
            UUID setId = UUID.randomUUID();
            st.execute("INSERT INTO gis_change_set (id, tenant_id, type, channel,"
                    + " author_id) VALUES ('" + setId + "', '" + a.tenant()
                    + "', 'CORRECTION', 'WEB', '" + USER_A + "')");
            st.execute("INSERT INTO gis_node (id, tenant_id, code, feature_type_id,"
                    + " lifecycle_status, capture_method, geom) VALUES ('"
                    + UUID.randomUUID() + "', '" + a.tenant() + "', 'X-1', '"
                    + nodeType + "', 'PLANNED', 'DIGITIZED',"
                    + " ST_GeomFromText('POINTZ(-48.5 -26.9 0)', 4674))");
            st.execute("INSERT INTO gis_arc (id, tenant_id, code, feature_type_id,"
                    + " lifecycle_status, change_set_id, capture_method,"
                    + " network_function, geom) VALUES ('" + UUID.randomUUID()
                    + "', '" + a.tenant() + "', 'X-1', '" + arcType
                    + "', 'PLANNED', '" + setId + "', 'DIGITIZED',"
                    + " 'WATER_DISTRIBUTION',"
                    + " ST_GeomFromText('LINESTRINGZ(-48.5 -26.9 0, -48.4 -26.9 0)', 4674))");
            var result = svc.executeJoin(join.id());
            assertThat(result.rows()).hasSize(1);
            assertThat(result.rows().get(0).keyValue()).isEqualTo("X-1");
            c.rollback();
        }
    }

    // ------------------------------------------------------------ D4

    @Test
    @DisplayName("D4: sync exige pacote APLICADO e grava PENDING idempotente sem a porta")
    void drawnSyncRequiresAppliedPackageAndIsIdempotentPending() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch a = scratch(st);
            String typeId = factoryTypeId(st, "gis01:GATE_VALVE");
            UUID conn = UUID.randomUUID();
            st.execute("INSERT INTO gis_connection (id, tenant_id, code,"
                    + " feature_type_id, lifecycle_status, capture_method, geom)"
                    + " VALUES ('" + conn + "', '" + a.tenant() + "', 'RAMAL-IT', '"
                    + typeId + "', 'PLANNED', 'DIGITIZED',"
                    + " ST_GeomFromText('LINESTRINGZ(-48.5 -26.9 0, -48.49 -26.9 0)', 4674))");
            UUID draftSet = UUID.randomUUID();
            st.execute("INSERT INTO gis_change_set (id, tenant_id, type, channel,"
                    + " status, author_id) VALUES ('" + draftSet + "', '"
                    + a.tenant() + "', 'PROJECT', 'WEB', 'DRAFT', '" + USER_A + "')");
            st.execute("INSERT INTO gis_change_set_item (tenant_id, change_set_id,"
                    + " operation, feature_table, feature_id, proposed_state)"
                    + " VALUES ('" + a.tenant() + "', '" + draftSet
                    + "', 'INSERT', 'gis_connection', '" + conn + "', '{}'::jsonb)");
            grant(st, a, USER_A, LAYER, "CREATE");
            GisWebEditService svc = service(c, a, USER_A);
            // Pacote em DRAFT: nao sincroniza (shouldSync) e nada grava.
            ApiException ex = org.junit.jupiter.api.Assertions.assertThrows(
                    ApiException.Conflict.class, () -> svc.syncDrawnConnection(
                            LAYER, conn, UUID.randomUUID(), "WATER"));
            assertThat(ex.code()).isEqualTo("sincronizacao-nao-elegivel");
            assertThat(count(st, "SELECT count(*) FROM gis_drawn_connection_sync"))
                    .isZero();
            // Pacote APLICADO com item da feicao: sincroniza -> PENDING
            // (sem a porta do cadastro, nunca SYNCED fingido).
            UUID appliedSet = UUID.randomUUID();
            st.execute("INSERT INTO gis_change_set (id, tenant_id, type, channel,"
                    + " status, author_id) VALUES ('" + appliedSet + "', '"
                    + a.tenant() + "', 'PROJECT', 'WEB', 'APPLIED', '" + USER_A + "')");
            st.execute("INSERT INTO gis_change_set_item (tenant_id, change_set_id,"
                    + " operation, feature_table, feature_id, proposed_state)"
                    + " VALUES ('" + a.tenant() + "', '" + appliedSet
                    + "', 'INSERT', 'gis_connection', '" + conn + "', '{}'::jsonb)");
            var first = svc.syncDrawnConnection(LAYER, conn, UUID.randomUUID(), "WATER");
            assertThat(first.status()).isEqualTo("PENDING");
            var second = svc.syncDrawnConnection(LAYER, conn, UUID.randomUUID(), "WATER");
            assertThat(second.status()).isEqualTo("PENDING");
            assertThat(count(st, "SELECT count(*) FROM gis_drawn_connection_sync"))
                    .isEqualTo(1);
            c.rollback();
        }
    }

    // ---------------------------------------------------------- TR-626

    @Test
    @DisplayName("TR-626: OS que altera a rede abre pacote SERVICE_ORDER; sem efeito de rede, nao abre")
    void serviceOrderOpensChangeSetOnlyWhenAffectsNetwork() throws Exception {
        try (Connection c = ProvisionedTenant.connect(); Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            Scratch a = scratch(st);
            grant(st, a, USER_A, LAYER, "CREATE");
            GisWebEditService svc = service(c, a, USER_A);
            assertThat(svc.openChangeSetForServiceOrder(
                    new OpenForServiceOrderInput("OS-IT-1", false, LAYER))).isEmpty();
            assertThat(count(st, "SELECT count(*) FROM gis_change_set")).isZero();
            assertThat(svc.openChangeSetForServiceOrder(
                    new OpenForServiceOrderInput("OS-IT-2", true, LAYER))).isPresent();
            assertThat(queryString(st, "SELECT type FROM gis_change_set"
                    + " WHERE service_order_ref = 'OS-IT-2'"))
                    .isEqualTo("SERVICE_ORDER");
            c.rollback();
        }
    }
}
