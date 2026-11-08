package br.com.ziipsan.intelligence.gis.webedit;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.JoinInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.JoinResultView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.OpenForServiceOrderInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.PlantStructureInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.ProjectInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.ProjectView;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.SplitInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.TransformInput;
import br.com.ziipsan.intelligence.gis.webedit.GisWebEditDtos.UserFeatureInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fiação do serviço da GIS-09 (correções D1, D2, D4 e D5 da
 * revisão ED-53): a guarda camada×operação é CHAMADA pelas
 * escritas, o cadastro de unidades valida contra o esquema REAL
 * do tipo (porta), o sync respeita shouldSync/ensureTransition
 * e o projeto nasce com os dois pacotes vinculados. Sem banco:
 * um JdbcTemplate falso registra as escritas e devolve as
 * linhas-fixture das leituras — o que se prova é a FIAÇÃO (quem
 * chama a guarda, em que ordem, e o que NÃO é escrito quando a
 * guarda recusa). O comportamento em PostgreSQL real é da IT
 * Gis09WebEditIT (CI do dono).
 *
 * <p>Correção N1 (2ª revisão): a seção N1 ao final prova a
 * guarda VIEW nas LEITURAS escopadas a camada (permitida com
 * VIEW; recusada sem VIEW com zero dados — a consulta às
 * feições nem é executada) e que a leitura de projeto segue
 * por tenant, sem guarda por camada (gis_project não tem
 * coluna de camada). Correção N2 (informativo): o FakeJdbc
 * chaveia as concessões por camada E usuário.
 */
class GisWebEditServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID LAYER_A = UUID.randomUUID();
    private static final UUID LAYER_B = UUID.randomUUID();
    private static final UUID TYPE_ID = UUID.randomUUID();

    /** JdbcTemplate falso: leituras por fixture, escritas gravadas. */
    static final class FakeJdbc extends JdbcTemplate {
        // Correção N2 (2ª revisão, informativo): as concessões são
        // chaveadas por camada E por usuário (layerId|userId),
        // como a consulta real do serviço (tenant, usuário,
        // camada) — conceder a OUTRO usuário não autoriza o
        // chamador, e a suíte prova isso (ver seção N1).
        final Map<String, List<String>> grantsByLayerAndUser = new HashMap<>();
        final List<String> updateSqls = new ArrayList<>();
        final List<Object[]> updateArgs = new ArrayList<>();
        List<Map<String, Object>> eligibility = List.of();
        List<Map<String, Object>> syncCurrent = List.of();
        List<Map<String, Object>> joinDef = List.of();
        List<Map<String, Object>> joinRows = List.of();
        List<Map<String, Object>> userLayer = List.of();
        List<Map<String, Object>> userFeatures = List.of();
        int userFeatureQueries;
        Object[] lastProjectInsert;
        String lastJoinSql;

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> elementType,
                                        Object... args) {
            if (sql.contains("gis_layer_permission")) {
                UUID userId = (UUID) args[1];
                UUID layerId = (UUID) args[2];
                return (List<T>) grantsByLayerAndUser.getOrDefault(
                        layerId + "|" + userId, List.of());
            }
            return List.of();
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            if (sql.contains("FROM gis_change_set_item")) {
                return eligibility;
            }
            if (sql.contains("FROM gis_drawn_connection_sync")) {
                return syncCurrent;
            }
            if (sql.contains("FROM gis_table_join")) {
                return joinDef;
            }
            if (sql.contains("left_id")) {
                lastJoinSql = sql;
                return joinRows;
            }
            if (sql.contains("FROM gis_user_feature")) {
                userFeatureQueries++;
                return userFeatures;
            }
            if (sql.contains("FROM gis_user_layer")) {
                return userLayer;
            }
            if (sql.contains("FROM gis_project")) {
                if (lastProjectInsert == null) {
                    return List.of();
                }
                Map<String, Object> row = new HashMap<>();
                row.put("id", lastProjectInsert[0]);
                row.put("name", lastProjectInsert[2]);
                row.put("status", "DRAFT");
                // Indices do INSERT: id, tenant, name, description,
                // work_ref, projectSetId, asBuiltSetId, user, user
                // (o status 'DRAFT' e literal no SQL, nao parametro).
                row.put("project_change_set_id", lastProjectInsert[5]);
                row.put("as_built_change_set_id", lastProjectInsert[6]);
                row.put("work_ref", lastProjectInsert[4]);
                return List.of(row);
            }
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            updateSqls.add(sql);
            updateArgs.add(args);
            if (sql.contains("INSERT INTO gis_project")) {
                lastProjectInsert = args;
            }
            return 1;
        }

        long insertsInto(String table) {
            return updateSqls.stream()
                    .filter(s -> s.contains("INSERT INTO " + table)).count();
        }

        List<Object[]> insertArgsOf(String table) {
            List<Object[]> out = new ArrayList<>();
            for (int i = 0; i < updateSqls.size(); i++) {
                if (updateSqls.get(i).contains("INSERT INTO " + table)) {
                    out.add(updateArgs.get(i));
                }
            }
            return out;
        }
    }

    static final class FakePort implements GisDrawnConnectionSync.DrawnConnectionPort {
        final UUID result = UUID.randomUUID();
        int calls;

        @Override
        public UUID ensureServiceConnection(UUID tenantId, UUID gisConnectionId,
                                            UUID consumerUnitId, String domainKey) {
            calls++;
            return result;
        }

        @Override
        public void retireServiceConnection(UUID tenantId, UUID serviceConnectionId) {
        }
    }

    private FakeJdbc jdbc;
    private AtomicInteger schemaCalls;
    private Optional<Map<String, Object>> schemaResult;
    private GisFeatureTypeSchemaPort schemaPort;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(USER);
        jdbc = new FakeJdbc();
        schemaCalls = new AtomicInteger();
        schemaResult = Optional.of(realSchema());
        schemaPort = (tenantId, featureTypeId) -> {
            schemaCalls.incrementAndGet();
            return schemaResult;
        };
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private GisWebEditService service() {
        return new GisWebEditService(jdbc, Optional.empty(), schemaPort);
    }

    private GisWebEditService serviceWithPort(FakePort port) {
        return new GisWebEditService(jdbc, Optional.of(port), schemaPort);
    }

    private void grant(UUID layer, String... operations) {
        grantTo(USER, layer, operations);
    }

    /** Concede a um usuário específico (fidelidade N2 do dublê). */
    private void grantTo(UUID user, UUID layer, String... operations) {
        jdbc.grantsByLayerAndUser.put(layer + "|" + user, List.of(operations));
    }

    /** Esquema REAL de fixture (o mesmo formato da V13_1). */
    private static Map<String, Object> realSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("diameter_mm"),
                "properties", Map.of(
                        "diameter_mm", Map.of(
                                "type", "integer", "title", "Diametro (mm)",
                                "minimum", 20, "maximum", 2000),
                        "material", Map.of(
                                "type", "string", "title", "Material",
                                "enum", List.of("PVC", "PEAD", "FOFO"))));
    }

    private static PlantStructureInput plantInput(Map<String, Object> attributes) {
        return new PlantStructureInput("ETA", "ETA-01", "ETA Central",
                "POLYGON((0 0, 10 0, 10 10, 0 10, 0 0))", null,
                attributes, TYPE_ID, LAYER_A);
    }

    // ------------------------------------------------------------- D1

    @Test
    void joinWriteWithoutLayerPermissionIsForbiddenAndWritesNothing() {
        JoinInput in = new JoinInput("gis_arc", "consumer_units", "LEFT",
                "service_connection_id", LAYER_A);
        ApiException ex = assertThrows(ApiException.Forbidden.class,
                () -> service().createJoin(in));
        assertEquals(403, ex.status());
        assertEquals(0, jdbc.insertsInto("gis_table_join"));
        assertTrue(jdbc.updateSqls.isEmpty());
    }

    @Test
    void permissionIsPerLayerNotGlobal() {
        grant(LAYER_A, "MANAGE_LAYER");
        JoinInput onB = new JoinInput("gis_arc", "gis_node", "INNER", "code", LAYER_B);
        assertThrows(ApiException.Forbidden.class, () -> service().createJoin(onB));
        JoinInput onA = new JoinInput("gis_arc", "gis_node", "INNER", "code", LAYER_A);
        assertNotNull(service().createJoin(onA));
        assertEquals(1, jdbc.insertsInto("gis_table_join"));
    }

    @Test
    void permissionIsPerOperation() {
        grant(LAYER_A, "VIEW");
        SplitInput split = new SplitInput("LINESTRING(0 0, 10 0)", 5.0, 0.0,
                0.01, LAYER_A);
        assertThrows(ApiException.Forbidden.class,
                () -> service().split(split, false));
        TransformInput move = new TransformInput("LINESTRING(0 0, 10 0)", "MOVE",
                1.0, 1.0, null, null, null, null, null, null, null, LAYER_A);
        assertThrows(ApiException.Forbidden.class, () -> service().transform(move));
        grant(LAYER_A, "SPLIT", "EDIT_GEOMETRY");
        assertNotNull(service().split(split, false));
        assertTrue(service().transform(move).geomWkt().contains("11"));
    }

    @Test
    void serviceOrderOpeningChecksPermissionBeforeInsert() {
        OpenForServiceOrderInput affects = new OpenForServiceOrderInput(
                "OS-1", true, LAYER_A);
        assertThrows(ApiException.Forbidden.class,
                () -> service().openChangeSetForServiceOrder(affects));
        assertEquals(0, jdbc.insertsInto("gis_change_set"));
        // OS sem efeito de rede: não abre e nem pede permissão.
        OpenForServiceOrderInput noEffect = new OpenForServiceOrderInput(
                "OS-2", false, LAYER_A);
        assertTrue(service().openChangeSetForServiceOrder(noEffect).isEmpty());
        assertEquals(0, jdbc.insertsInto("gis_change_set"));
        grant(LAYER_A, "CREATE");
        assertTrue(service().openChangeSetForServiceOrder(affects).isPresent());
        assertEquals(1, jdbc.insertsInto("gis_change_set"));
    }

    // ------------------------------------------------------------- D2

    @Test
    void plantStructureValidatesAgainstRealTypeSchema() {
        grant(LAYER_A, "CREATE");
        ApiException missing = assertThrows(ApiException.BadRequest.class,
                () -> service().draftPlantStructure(plantInput(Map.of())));
        assertEquals("atributos-invalidos", missing.code());
        ApiException belowMin = assertThrows(ApiException.BadRequest.class,
                () -> service().draftPlantStructure(
                        plantInput(Map.of("diameter_mm", 10))));
        assertEquals("atributos-invalidos", belowMin.code());
        ApiException badEnum = assertThrows(ApiException.BadRequest.class,
                () -> service().draftPlantStructure(plantInput(
                        Map.of("diameter_mm", 100, "material", "COBRE"))));
        assertEquals("atributos-invalidos", badEnum.code());
        assertNotNull(service().draftPlantStructure(
                plantInput(Map.of("diameter_mm", 100, "material", "PVC"))));
        assertTrue(schemaCalls.get() >= 4);
    }

    @Test
    void plantStructureTypeWithoutSchemaIsExplicitlyRefused() {
        grant(LAYER_A, "CREATE");
        schemaResult = Optional.of(Map.of());
        ApiException ex = assertThrows(ApiException.BadRequest.class,
                () -> service().draftPlantStructure(
                        plantInput(Map.of("diameter_mm", 100))));
        assertEquals("esquema-nao-cadastrado", ex.code());
    }

    @Test
    void plantStructureUnknownTypeInTenantIsNotFound() {
        grant(LAYER_A, "CREATE");
        schemaResult = Optional.empty();
        assertThrows(ApiException.NotFound.class,
                () -> service().draftPlantStructure(
                        plantInput(Map.of("diameter_mm", 100))));
    }

    @Test
    void plantStructureGuardRunsBeforeSchemaLookup() {
        // Sem CREATE na camada: 403 e a porta nem é consultada.
        assertThrows(ApiException.Forbidden.class,
                () -> service().draftPlantStructure(
                        plantInput(Map.of("diameter_mm", 100))));
        assertEquals(0, schemaCalls.get());
    }

    // ------------------------------------------------------------- D5

    @Test
    void createProjectCreatesAndLinksBothChangeSets() {
        grant(LAYER_A, "CREATE");
        ProjectView view = service().createProject(
                new ProjectInput("Obra X", "desc", "OBRA-1", LAYER_A));
        List<Object[]> sets = jdbc.insertArgsOf("gis_change_set");
        assertEquals(2, sets.size());
        assertEquals("PROJECT", sets.get(0)[2]);
        assertEquals("AS_BUILT", sets.get(1)[2]);
        assertNotEquals(sets.get(0)[0], sets.get(1)[0]);
        // O projeto referencia os DOIS pacotes criados — nada NULL.
        assertEquals(sets.get(0)[0], view.projectChangeSetId());
        assertEquals(sets.get(1)[0], view.asBuiltChangeSetId());
        assertNotNull(view.projectChangeSetId());
        assertNotNull(view.asBuiltChangeSetId());
        // project_ref dos pacotes aponta para o projeto.
        assertEquals(view.id().toString(), sets.get(0)[4]);
        assertEquals(view.id().toString(), sets.get(1)[4]);
    }

    @Test
    void createProjectWithoutPermissionWritesNothing() {
        assertThrows(ApiException.Forbidden.class, () -> service().createProject(
                new ProjectInput("Obra X", null, null, LAYER_A)));
        assertTrue(jdbc.updateSqls.isEmpty());
    }

    @Test
    void executeJoinRunsTheDefinedJoinWithinTenant() {
        UUID joinId = UUID.randomUUID();
        grant(LAYER_A, "VIEW");
        jdbc.joinDef = List.of(Map.of(
                "id", joinId, "left_table", "gis_arc",
                "right_table", "consumer_units", "join_type", "LEFT",
                "join_key", "service_connection_id", "layer_id", LAYER_A));
        UUID left = UUID.randomUUID();
        UUID right = UUID.randomUUID();
        jdbc.joinRows = List.of(
                Map.of("left_id", left, "right_id", right, "key_value", "svc-1"),
                Map.of("left_id", UUID.randomUUID(), "key_value", "svc-2"));
        JoinResultView result = service().executeJoin(joinId);
        assertEquals(2, result.rows().size());
        assertEquals(left, result.rows().get(0).leftId());
        assertEquals(right, result.rows().get(0).rightId());
        assertTrue(jdbc.lastJoinSql.contains("LEFT JOIN"));
        assertTrue(jdbc.lastJoinSql.contains("consumer_units"));
        assertTrue(jdbc.lastJoinSql.contains("l.tenant_id"));
    }

    @Test
    void executeJoinWithoutViewPermissionIsForbidden() {
        UUID joinId = UUID.randomUUID();
        jdbc.joinDef = List.of(Map.of(
                "id", joinId, "left_table", "gis_arc",
                "right_table", "gis_node", "join_type", "INNER",
                "join_key", "code", "layer_id", LAYER_A));
        assertThrows(ApiException.Forbidden.class,
                () -> service().executeJoin(joinId));
    }

    @Test
    void addUserFeatureWritesIntoOwnedLayerWithCreatePermission() {
        grant(LAYER_A, "CREATE");
        jdbc.userLayer = List.of(Map.of(
                "id", LAYER_A, "name", "Minha camada",
                "geometry_type", "POINT", "owner_id", USER));
        var view = service().addUserFeature(LAYER_A,
                new UserFeatureInput("POINT(1 2)", Map.of("note", "x")));
        assertNotNull(view.id());
        assertEquals(1, jdbc.insertsInto("gis_user_feature"));
        // Geometria de outro tipo que o da camada: recusada.
        ApiException ex = assertThrows(ApiException.BadRequest.class,
                () -> service().addUserFeature(LAYER_A,
                        new UserFeatureInput("LINESTRING(0 0, 1 1)", Map.of())));
        assertEquals("geometria-incompativel-camada", ex.code());
        assertEquals(1, jdbc.insertsInto("gis_user_feature"));
    }

    @Test
    void addUserFeatureOfAnotherOwnerIsForbidden() {
        grant(LAYER_A, "CREATE");
        jdbc.userLayer = List.of(Map.of(
                "id", LAYER_A, "name", "De outro",
                "geometry_type", "POINT", "owner_id", UUID.randomUUID()));
        assertThrows(ApiException.Forbidden.class,
                () -> service().addUserFeature(LAYER_A,
                        new UserFeatureInput("POINT(1 2)", Map.of())));
        assertEquals(0, jdbc.insertsInto("gis_user_feature"));
    }

    // ------------------------------------------------------------- D4

    @Test
    void syncRefusesWhenPackageNotApplied() {
        grant(LAYER_A, "CREATE");
        jdbc.eligibility = List.of(Map.of(
                "change_set_status", "DRAFT", "operation", "INSERT"));
        ApiException ex = assertThrows(ApiException.Conflict.class,
                () -> service().syncDrawnConnection(
                        LAYER_A, UUID.randomUUID(), UUID.randomUUID(), "WATER"));
        assertEquals("sincronizacao-nao-elegivel", ex.code());
        assertEquals(0, jdbc.insertsInto("gis_drawn_connection_sync"));
    }

    @Test
    void syncEligibleWithoutPortStaysPendingAndIsIdempotent() {
        grant(LAYER_A, "CREATE");
        UUID conn = UUID.randomUUID();
        jdbc.eligibility = List.of(Map.of(
                "change_set_status", "APPLIED", "operation", "INSERT"));
        var first = service().syncDrawnConnection(
                LAYER_A, conn, UUID.randomUUID(), "WATER");
        assertEquals("PENDING", first.status());
        assertEquals(1, jdbc.insertsInto("gis_drawn_connection_sync"));
        // Segunda chamada com o PENDING já registrado: mesmo
        // estado, sem regravar (idempotência honesta).
        jdbc.syncCurrent = List.of(Map.of("status", "PENDING"));
        var second = service().syncDrawnConnection(
                LAYER_A, conn, UUID.randomUUID(), "WATER");
        assertEquals("PENDING", second.status());
        assertEquals(1, jdbc.insertsInto("gis_drawn_connection_sync"));
    }

    @Test
    void syncWithPortTransitionsPendingToSynced() {
        grant(LAYER_A, "CREATE");
        FakePort port = new FakePort();
        jdbc.eligibility = List.of(Map.of(
                "change_set_status", "APPLIED", "operation", "UPDATE"));
        var view = serviceWithPort(port).syncDrawnConnection(
                LAYER_A, UUID.randomUUID(), UUID.randomUUID(), "SEWER");
        assertEquals("SYNCED", view.status());
        assertEquals(port.result, view.serviceConnectionId());
        assertEquals(1, port.calls);
        assertEquals(1, jdbc.insertsInto("gis_drawn_connection_sync"));
    }

    @Test
    void syncFromRetiredIsRefusedByStateMachine() {
        grant(LAYER_A, "CREATE");
        FakePort port = new FakePort();
        jdbc.eligibility = List.of(Map.of(
                "change_set_status", "APPLIED", "operation", "INSERT"));
        jdbc.syncCurrent = List.of(Map.of("status", "RETIRED"));
        ApiException ex = assertThrows(ApiException.Conflict.class,
                () -> serviceWithPort(port).syncDrawnConnection(
                        LAYER_A, UUID.randomUUID(), UUID.randomUUID(), "WATER"));
        assertEquals("transicao-de-sincronizacao-invalida", ex.code());
        assertEquals(0, port.calls);
        assertEquals(0, jdbc.insertsInto("gis_drawn_connection_sync"));
    }

    // ------------------------------------------------------------- N1

    private void ownedLayerFixture() {
        jdbc.userLayer = List.of(Map.of(
                "id", LAYER_A, "name", "Minha camada",
                "geometry_type", "POINT", "owner_id", USER));
    }

    @Test
    void listUserFeaturesWithViewReturnsTheFeatures() {
        grant(LAYER_A, "VIEW");
        ownedLayerFixture();
        UUID featureId = UUID.randomUUID();
        jdbc.userFeatures = List.of(Map.of(
                "id", featureId, "geom_wkt", "POINT(1 2)",
                "attributes_json", "{\"note\":\"x\"}"));
        var features = service().listUserFeatures(LAYER_A);
        assertEquals(1, features.size());
        assertEquals(featureId, features.get(0).id());
        assertEquals("POINT(1 2)", features.get(0).geomWkt());
        assertEquals(1, jdbc.userFeatureQueries);
    }

    @Test
    void listUserFeaturesWithoutViewIsForbiddenAndReadsNoFeatureData() {
        // VIEW concedida só a OUTRO usuário (N2: o dublê chaveia
        // por camada E usuário) — para o chamador é como não ter.
        grantTo(UUID.randomUUID(), LAYER_A, "VIEW");
        ownedLayerFixture();
        jdbc.userFeatures = List.of(Map.of(
                "id", UUID.randomUUID(), "geom_wkt", "POINT(1 2)",
                "attributes_json", "{}"));
        ApiException ex = assertThrows(ApiException.Forbidden.class,
                () -> service().listUserFeatures(LAYER_A));
        assertEquals(403, ex.status());
        // Zero dados: a consulta às feições nem foi executada.
        assertEquals(0, jdbc.userFeatureQueries);
    }

    @Test
    void listUserLayersOnlyReturnsLayersWithView() {
        grant(LAYER_A, "VIEW");
        // LAYER_B é do mesmo dono, mas a concessão VIEW dela foi
        // revogada: a camada é retida — zero dados dela na lista.
        jdbc.userLayer = List.of(
                Map.of("id", LAYER_A, "name", "Camada A",
                        "geometry_type", "POINT", "owner_id", USER),
                Map.of("id", LAYER_B, "name", "Camada B",
                        "geometry_type", "LINESTRING", "owner_id", USER));
        var layers = service().listUserLayers();
        assertEquals(1, layers.size());
        assertEquals(LAYER_A, layers.get(0).id());
    }

    @Test
    void getProjectFollowsTenantScopeWithoutLayerGuard() {
        // gis_project não tem coluna de camada: a leitura segue
        // por tenant (N1, exceção declarada no serviço). Prova:
        // com a concessão CREATE usada na escrita e NENHUMA
        // concessão restante depois, o projeto do tenant segue
        // legível — nenhuma guarda VIEW é exigida dele.
        grant(LAYER_A, "CREATE");
        ProjectView created = service().createProject(
                new ProjectInput("Obra Y", null, "OBRA-2", LAYER_A));
        jdbc.grantsByLayerAndUser.clear();
        ProjectView read = service().getProject(created.id());
        assertEquals(created.id(), read.id());
        assertEquals("Obra Y", read.name());
        assertEquals(created.projectChangeSetId(), read.projectChangeSetId());
        assertEquals(created.asBuiltChangeSetId(), read.asBuiltChangeSetId());
    }

    @Test
    void getProjectUnknownInTenantIsNotFound() {
        assertThrows(ApiException.NotFound.class,
                () -> service().getProject(UUID.randomUUID()));
    }
}
