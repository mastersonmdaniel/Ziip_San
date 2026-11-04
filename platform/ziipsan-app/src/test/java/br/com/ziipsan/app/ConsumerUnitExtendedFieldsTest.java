package br.com.ziipsan.app;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.16 opcao 1: os campos que o PropertyWizard ja enviava em POST/PUT /consumer-units
 * e que eram descartados em silencio passam a ser modelados: criacao/edicao com todos
 * os campos novos devolve os mesmos valores no GET, e um payload sem eles continua
 * funcionando (e limpa os campos, como os demais opcionais ja faziam).
 *
 * <p>P1-C (integracao): a R2 trouxe os catalogos que este teste tratava como
 * "sem tabela no backend" — face de quadra, esgotamento e perfil passam a
 * existir de verdade (criados no setUp: block_faces por SQL, pois a rota de
 * catalogos e so leitura para ela; os demais pela API de catalogos), o
 * vinculo USER exige a linha relation_roles com system_role USER, e a face
 * de quadra exige registry.use_block_face=true. Modo de envio, fonte, poco,
 * pavimentos, ramos de atividade e rascunho continuam sem checagem de
 * existencia: aceitos e devolvidos como Ref(id, null, null). Rota de
 * entrega, rota alternativa e imovel principal tem tabela e FK, entao sao
 * criados de verdade e o id desconhecido e recusado.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsumerUnitExtendedFieldsTest extends AppIntegrationTestBase {

    private static final UUID TENANT_A = ProvisionedTenant.TENANT_ID;
    private static final String CPF = "111.444.777-35";
    private static final String TODAY = LocalDate.now().toString();
    private static final int CODE = ThreadLocalRandom.current().nextInt(100, 900);

    private static String localityId;
    private static String sectorId;
    private static String blockId;
    private static String routeId;
    private static String altRouteId;
    private static String subcategoryId;
    private static String waterId;
    private static String sewerId;
    private static String customerId;
    private static String parentUnitId;
    private static String unitId;
    private static String sewerDischargeId;
    private static String profileId;

    // Ids pendentes (sem catalogo no backend): aceitos e devolvidos como Ref(id, null, null).
    private static final String BLOCK_FACE_ID = UUID.randomUUID().toString();
    private static final String DELIVERY_MODE_ID = UUID.randomUUID().toString();
    private static final String WATER_SOURCE_ID = UUID.randomUUID().toString();
    private static final String WELL_TYPE_ID = UUID.randomUUID().toString();
    private static final String PAVEMENT_STREET_ID = UUID.randomUUID().toString();
    private static final String PAVEMENT_SIDEWALK_ID = UUID.randomUUID().toString();
    private static final String BRANCH_A = UUID.randomUUID().toString();
    private static final String BRANCH_B = UUID.randomUUID().toString();
    private static final String DRAFT_ID = UUID.randomUUID().toString();

    private final String operator = bearer(TestTokens.token(TENANT_A, "OPERATOR"));
    private final String viewer = bearer(TestTokens.token(TENANT_A, "VIEWER"));

    @Test
    @Order(1)
    @DisplayName("base: territorio, catalogos, cliente e imovel principal")
    void setUp() throws Exception {
        ProvisionedTenant.ensure();
        localityId = id(send(post("/api/v1/registry/localities"), operator, """
                {"code": %d, "name": "Localidade P016 %d"}""".formatted(CODE, CODE))
                .andExpect(status().isCreated()));
        sectorId = id(send(post("/api/v1/registry/commercial-sectors"), operator, """
                {"localityId": "%s", "code": 1, "name": "Setor P016"}""".formatted(localityId))
                .andExpect(status().isCreated()));
        routeId = id(send(post("/api/v1/registry/reading-routes"), operator, """
                {"code": %d, "name": "Rota P016"}""".formatted(CODE))
                .andExpect(status().isCreated()));
        altRouteId = id(send(post("/api/v1/registry/reading-routes"), operator, """
                {"code": %d, "name": "Rota alt P016"}""".formatted(CODE + 1))
                .andExpect(status().isCreated()));
        blockId = id(send(post("/api/v1/registry/blocks"), operator, """
                {"sectorId": "%s", "code": 3, "readingRouteId": "%s", "hasWaterNetwork": true, "hasSewerNetwork": true}"""
                .formatted(sectorId, routeId))
                .andExpect(status().isCreated()));
        String categoryId = id(send(post("/api/v1/registry/categories"), operator, """
                {"code": "P016%d", "name": "Residencial P016"}""".formatted(CODE))
                .andExpect(status().isCreated()));
        subcategoryId = id(send(post("/api/v1/registry/subcategories"), operator, """
                {"categoryId": "%s", "code": "P016%d.1", "name": "Residencial padrao P016"}""".formatted(categoryId, CODE))
                .andExpect(status().isCreated()));
        waterId = id(send(post("/api/v1/registry/water-statuses"), operator, """
                {"code": "LIGP%d", "name": "Ligado P016", "billable": true, "requiresNetwork": true, "connected": true, "supplies": true}"""
                .formatted(CODE))
                .andExpect(status().isCreated()));
        sewerId = id(send(post("/api/v1/registry/sewer-statuses"), operator, """
                {"code": "LIGP%d", "name": "Ligado P016", "billable": true, "requiresNetwork": true, "connected": true}"""
                .formatted(CODE))
                .andExpect(status().isCreated()));
        customerId = id(send(post("/api/v1/registry/customers"), operator, """
                {"personType": "PF", "name": "Cliente P016", "document": "%s", "billing": {"dueDay": 10}}""".formatted(CPF))
                .andExpect(status().isCreated()));
        // P1-C (integracao): catalogos da R2 que os corpos exigem — vinculo
        // USER (system_role), esgotamento e perfil pela API de catalogos;
        // face de quadra por SQL (a rota de catalogos e so leitura para ela)
        // e registry.use_block_face=true (sem ele, blockFaceId e DISABLED).
        // O tenant e compartilhado: outro teste pode ja ter semeado USER
        // (o POST volta 400 de clash de codigo) — nesse caso o 400 e
        // confirmado pelo tipo + detalhe do clash, nunca aceito as cegas.
        // (ApiException.BadRequest carrega so codigo + mensagem: o problem
        // sai sem errors[], por isso a confirmacao e por type/detail e nao
        // por errors[0].field.)
        var rolePost = send(post("/api/v1/registry/catalogs/relation_roles"), operator, """
                {"code": "USER", "name": "Usuário", "active": true, "extra": {"system_role": "USER"}}""");
        if (rolePost.andReturn().getResponse().getStatus() == 400) {
            rolePost.andExpect(jsonPath("$.type").value("urn:ziipsan:problem:code"))
                    .andExpect(jsonPath("$.detail").value("Já existe item com este código no catálogo."));
        } else {
            rolePost.andExpect(status().isCreated());
        }
        sewerDischargeId = id(send(post("/api/v1/registry/catalogs/sewer_discharges"), operator, """
                {"code": "P016", "name": "Tipo P016", "active": true}""")
                .andExpect(status().isCreated()));
        profileId = id(send(post("/api/v1/registry/catalogs/property_profiles"), operator, """
                {"code": "P016", "name": "Perfil P016", "active": true}""")
                .andExpect(status().isCreated()));
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
                st.execute("INSERT INTO " + ProvisionedTenant.SCHEMA + ".block_faces"
                        + " (id, tenant_id, block_id, code, name, active) VALUES ('"
                        + BLOCK_FACE_ID + "', '" + TENANT_A + "', '" + blockId
                        + "', 'P016', 'Face P016', true)");
                st.execute("INSERT INTO public.tenant_settings (tenant_id, setting_key, setting_value)"
                        + " VALUES ('" + TENANT_A + "', 'registry.use_block_face', 'true'::jsonb)"
                        + " ON CONFLICT (tenant_id, setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value");
            }
            c.commit();
        }
        parentUnitId = id(send(post("/api/v1/registry/consumer-units"), operator, legacyBody(5, 50))
                .andExpect(status().isCreated()));
    }

    @Test
    @Order(2)
    @DisplayName("criacao com todos os campos novos: GET devolve os mesmos valores")
    void createWithExtendedFields() throws Exception {
        MvcResult created = send(post("/api/v1/registry/consumer-units"), operator, fullBody(6, 60, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.blockFace.id").value(BLOCK_FACE_ID))
                .andExpect(jsonPath("$.lotFrontageM").value(12.5))
                .andExpect(jsonPath("$.deliveryMode.id").value(DELIVERY_MODE_ID))
                .andExpect(jsonPath("$.subcategories[0].privateUnits").value(2))
                .andExpect(jsonPath("$.subcategories[0].collectiveUnits").value(3))
                .andExpect(jsonPath("$.subcategories[0].activityBranchIds[0]").value(BRANCH_A))
                .andExpect(jsonPath("$.subcategories[0].activityBranchIds[1]").value(BRANCH_B))
                .andExpect(jsonPath("$.sewerDischarge.id").value(sewerDischargeId))
                .andExpect(jsonPath("$.profile.id").value(profileId))
                .andExpect(jsonPath("$.waterSource.id").value(WATER_SOURCE_ID))
                .andExpect(jsonPath("$.wellType.id").value(WELL_TYPE_ID))
                .andExpect(jsonPath("$.pavementStreet.id").value(PAVEMENT_STREET_ID))
                .andExpect(jsonPath("$.pavementSidewalk.id").value(PAVEMENT_SIDEWALK_ID))
                .andExpect(jsonPath("$.physical.garden").value(true))
                .andExpect(jsonPath("$.physical.lowerReservoirM3").value(5.0))
                .andExpect(jsonPath("$.physical.upperReservoirM3").value(1.5))
                .andExpect(jsonPath("$.physical.poolM3").value(20.0))
                .andExpect(jsonPath("$.usagePoints").value(7))
                .andExpect(jsonPath("$.residents").value(4))
                .andExpect(jsonPath("$.externalIds.iptu").value("123456"))
                .andExpect(jsonPath("$.deliveryRoute.id").value(routeId))
                .andExpect(jsonPath("$.deliverySequence").value(12))
                .andExpect(jsonPath("$.altRoute.id").value(altRouteId))
                .andExpect(jsonPath("$.parentUnit.id").value(parentUnitId))
                .andExpect(jsonPath("$.draftId").value(DRAFT_ID))
                .andExpect(jsonPath("$.address.coordinates.lat").value(closeTo(-23.5505, 0.000001)))
                .andExpect(jsonPath("$.address.coordinates.lng").value(closeTo(-46.6333, 0.000001)))
                .andReturn();
        unitId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        // GET separado confirma a persistencia (nao so o eco da criacao).
        mvc.perform(get("/api/v1/registry/consumer-units/" + unitId).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockFace.id").value(BLOCK_FACE_ID))
                .andExpect(jsonPath("$.lotFrontageM").value(12.5))
                .andExpect(jsonPath("$.subcategories[0].privateUnits").value(2))
                .andExpect(jsonPath("$.subcategories[0].activityBranchIds.length()").value(2))
                .andExpect(jsonPath("$.physical.garden").value(true))
                .andExpect(jsonPath("$.externalIds.iptu").value("123456"))
                .andExpect(jsonPath("$.parentUnit.id").value(parentUnitId))
                .andExpect(jsonPath("$.address.coordinates.lat").value(closeTo(-23.5505, 0.000001)));
    }

    @Test
    @Order(3)
    @DisplayName("payload antigo (sem os campos novos) continua funcionando e limpa os campos")
    void legacyPayloadStillWorksAndClears() throws Exception {
        MvcResult current = mvc.perform(get("/api/v1/registry/consumer-units/" + unitId).header("Authorization", viewer))
                .andReturn();
        Number version = JsonPath.read(current.getResponse().getContentAsString(), "$.version");
        String update = legacyBody(6, 61).replaceFirst("\\{", "{\"version\": " + version + ", ");
        send(put("/api/v1/registry/consumer-units/" + unitId), operator, update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockFace").value(nullValue()))
                .andExpect(jsonPath("$.lotFrontageM").value(nullValue()))
                .andExpect(jsonPath("$.deliveryMode").value(nullValue()))
                .andExpect(jsonPath("$.subcategories[0].privateUnits").value(nullValue()))
                .andExpect(jsonPath("$.subcategories[0].collectiveUnits").value(nullValue()))
                .andExpect(jsonPath("$.subcategories[0].activityBranchIds").isEmpty())
                .andExpect(jsonPath("$.sewerDischarge").value(nullValue()))
                .andExpect(jsonPath("$.profile").value(nullValue()))
                .andExpect(jsonPath("$.physical").value(nullValue()))
                .andExpect(jsonPath("$.usagePoints").value(nullValue()))
                .andExpect(jsonPath("$.residents").value(nullValue()))
                .andExpect(jsonPath("$.externalIds").value(nullValue()))
                .andExpect(jsonPath("$.deliveryRoute").value(nullValue()))
                .andExpect(jsonPath("$.deliverySequence").value(nullValue()))
                .andExpect(jsonPath("$.altRoute").value(nullValue()))
                .andExpect(jsonPath("$.parentUnit").value(nullValue()))
                .andExpect(jsonPath("$.draftId").value(nullValue()))
                .andExpect(jsonPath("$.address.coordinates").value(nullValue()))
                .andExpect(jsonPath("$.economies").value(2));
    }

    @Test
    @Order(4)
    @DisplayName("recusas: rota de entrega desconhecida e coordenada incompleta dao 422 no campo")
    void rejections() throws Exception {
        String unknownRoute = fullBody(7, 70, null).replace("\"deliveryRouteId\": \"" + routeId + "\"",
                "\"deliveryRouteId\": \"" + UUID.randomUUID() + "\"");
        send(post("/api/v1/registry/consumer-units"), operator, unknownRoute)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("deliveryRouteId"))
                .andExpect(jsonPath("$.errors[0].code").value("NOT_FOUND"));

        String incomplete = fullBody(8, 80, null).replace("\"coordinates\": {\"lat\": -23.5505, \"lng\": -46.6333}",
                "\"coordinates\": {\"lat\": -23.5505, \"lng\": null}");
        send(post("/api/v1/registry/consumer-units"), operator, incomplete)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[?(@.field == 'address.coordinates')].code").value("INCOMPLETE"));
    }

    // ------------------------------------------------------------------ corpos

    /** Corpo antigo: sem nenhum campo novo (o formato que ja existia antes de P0.16). */
    private String legacyBody(int lot, int readingSequence) {
        return """
                {"blockId": "%s", "localityId": "%s", "lot": %d, "sublot": 0,
                 "address": {"cep": "01001-000", "streetName": "Rua das Aguas P016", "number": "%d",
                             "neighborhood": "Centro", "city": "Sao Paulo", "state": "SP"},
                 "customers": [{"customerId": "%s", "relation": "USER", "startDate": "%s", "showOnBill": true}],
                 "subcategories": [{"subcategoryId": "%s", "economies": 2}],
                 "waterStatusId": "%s", "sewerStatusId": "%s", "readingSequence": %d}"""
                .formatted(blockId, localityId, lot, 100 + lot, customerId, TODAY, subcategoryId, waterId,
                        sewerId, readingSequence);
    }

    /** Corpo completo do wizard (todos os campos novos, nos nomes do frontend). */
    private String fullBody(int lot, int readingSequence, String confirmToken) {
        String tokens = confirmToken == null ? "" : ", \"confirmTokens\": [\"" + confirmToken + "\"]";
        return """
                {"blockId": "%s", "localityId": "%s", "blockFaceId": "%s", "lot": %d, "sublot": 0,
                 "lotFrontageM": 12.5,
                 "address": {"cep": "01001-000", "streetName": "Rua das Aguas P016", "number": "%d",
                             "neighborhood": "Centro", "city": "Sao Paulo", "state": "SP",
                             "coordinates": {"lat": -23.5505, "lng": -46.6333}},
                 "customers": [{"customerId": "%s", "relation": "USER", "startDate": "%s", "showOnBill": true}],
                 "deliveryModeId": "%s",
                 "subcategories": [{"subcategoryId": "%s", "economies": 2, "privateUnits": 2, "collectiveUnits": 3,
                                    "activityBranchIds": ["%s", "%s"]}],
                 "builtAreaM2": 85.5,
                 "waterStatusId": "%s", "sewerStatusId": "%s", "sewerDischargeId": "%s", "profileId": "%s",
                 "waterSourceId": "%s", "wellTypeId": "%s", "pavementStreetId": "%s", "pavementSidewalkId": "%s",
                 "physical": {"garden": true, "lowerReservoirM3": 5.0, "upperReservoirM3": 1.5, "poolM3": 20.0},
                 "usagePoints": 7, "residents": 4, "externalIds": {"iptu": "123456"},
                 "deliveryRouteId": "%s", "deliverySequence": 12, "altRouteId": "%s", "parentUnitId": "%s",
                 "parentRelationType": "CONDOMINIUM_MACRO",
                 "notes": "Imovel P016", "draftId": "%s",
                 "readingSequence": %d%s}"""
                .formatted(blockId, localityId, BLOCK_FACE_ID, lot, 100 + lot, customerId, TODAY,
                        DELIVERY_MODE_ID, subcategoryId, BRANCH_A, BRANCH_B, waterId, sewerId,
                        sewerDischargeId, profileId, WATER_SOURCE_ID, WELL_TYPE_ID, PAVEMENT_STREET_ID,
                        PAVEMENT_SIDEWALK_ID, routeId, altRouteId, parentUnitId, DRAFT_ID, readingSequence, tokens);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
