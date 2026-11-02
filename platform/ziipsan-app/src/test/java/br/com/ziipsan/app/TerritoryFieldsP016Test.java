package br.com.ziipsan.app;

import br.com.ziipsan.core.audit.AuditLogRepository;
import br.com.ziipsan.platform.tenant.TenantProvisioningService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.hamcrest.Matchers;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.16, opção 1 (V3_6): os campos de território que a tela já enviava e o backend descartava
 * em silêncio passam a ser persistidos e devolvidos. Criação/edição de cada entidade com os
 * novos campos → GET devolve os mesmos valores; payload sem os novos campos continua funcionando.
 *
 * <p>Tenant próprio (não compartilha o de {@link ProvisionedTenant} com o resto da suíte), com
 * schema descartado no fim. Não toca nos casos {@code campoDesconhecido} de RegistryApiE2ETest.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TerritoryFieldsP016Test extends AppIntegrationTestBase {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SCHEMA = "it_p016_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    private static String localityId;
    private static String localityPlainId;
    private static String sectorId;
    private static String sectorPlainId;
    private static String routeId;
    private static String routePlainId;
    private static String blockId;
    private static String blockLegacyId;
    private static String blockPlainId;

    private final String operator = bearer(TestTokens.token(TENANT, "OPERATOR"));
    private final String viewer = bearer(TestTokens.token(TENANT, "VIEWER"));

    @BeforeAll
    static void setUp() throws SQLException {
        ProvisionedTenant.ensure();
        var result = new TenantProvisioningService(
                ProvisionedTenant.appDs,
                new JdbcTemplate(ProvisionedTenant.appDs),
                Mockito.mock(AuditLogRepository.class),
                ProvisionedTenant.migratorDs)
                .provisionTenant(TenantProvisioningService.TenantProvisioningRequest.builder()
                        .tenantId(TENANT)
                        .schemaName(SCHEMA)
                        .displayName("Tenant P0.16 territorio")
                        // CNPJ distinto do de RegistryApiE2ETest (11222333000181): os dois testes
                        // provisionam tenants no mesmo banco de integracao e o catalogo (public)
                        // tem unicidade de CNPJ; o teardown so descarta o schema, nao o catalogo.
                        .cnpj("12345678000195")
                        .email("p016@teste.com.br")
                        .plan("PRO")
                        .build());
        if (!result.success()) {
            throw new IllegalStateException("provisionamento do tenant P0.16: " + result.error());
        }
    }

    @AfterAll
    static void tearDown() throws SQLException {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection(); Statement st = c.createStatement()) {
            if (SCHEMA.startsWith("it_p016_")) {
                st.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("localidade: headquarters e computerized persistem; sem eles, o default é false")
    void locality() throws Exception {
        localityId = id(send(post("/api/v1/registry/localities"), operator, """
                {"code": 11, "name": "Localidade P016", "headquarters": true, "computerized": true}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.headquarters").value(true))
                .andExpect(jsonPath("$.computerized").value(true)));
        mvc.perform(get("/api/v1/registry/localities/" + localityId).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headquarters").value(true))
                .andExpect(jsonPath("$.computerized").value(true));

        localityPlainId = id(send(post("/api/v1/registry/localities"), operator, """
                {"code": 12, "name": "Localidade P016 sem campos novos"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.headquarters").value(false))
                .andExpect(jsonPath("$.computerized").value(false)));
    }

    @Test
    @Order(2)
    @DisplayName("setor: alternative persiste; sem ele, o default é false")
    void sector() throws Exception {
        sectorId = id(send(post("/api/v1/registry/commercial-sectors"), operator, """
                {"localityId": "%s", "code": 21, "name": "Setor P016", "alternative": true}""".formatted(localityId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alternative").value(true)));
        mvc.perform(get("/api/v1/registry/commercial-sectors/" + sectorId).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternative").value(true));

        sectorPlainId = id(send(post("/api/v1/registry/commercial-sectors"), operator, """
                {"localityId": "%s", "code": 22, "name": "Setor P016 sem campo novo"}""".formatted(localityId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alternative").value(false)));
    }

    @Test
    @Order(3)
    @DisplayName("rota: localityId, sectorId e maxUnits persistem; sem eles, ficam nulos; referência inexistente é 422")
    void route() throws Exception {
        routeId = id(send(post("/api/v1/registry/reading-routes"), operator, """
                {"code": 31, "name": "Rota P016", "localityId": "%s", "sectorId": "%s", "maxUnits": 150}"""
                .formatted(localityId, sectorId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.localityId").value(localityId))
                .andExpect(jsonPath("$.locality.id").value(localityId))
                .andExpect(jsonPath("$.sectorId").value(sectorId))
                .andExpect(jsonPath("$.sector.id").value(sectorId))
                .andExpect(jsonPath("$.maxUnits").value(150)));
        mvc.perform(get("/api/v1/registry/reading-routes/" + routeId).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localityId").value(localityId))
                .andExpect(jsonPath("$.sectorId").value(sectorId))
                .andExpect(jsonPath("$.maxUnits").value(150));

        routePlainId = id(send(post("/api/v1/registry/reading-routes"), operator, """
                {"code": 32, "name": "Rota P016 sem campos novos"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.localityId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.sectorId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.maxUnits").value(Matchers.nullValue())));

        send(post("/api/v1/registry/reading-routes"), operator, """
                {"code": 33, "name": "Rota P016 localidade inexistente", "localityId": "%s"}"""
                .formatted(UUID.randomUUID()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("localityId"))
                .andExpect(jsonPath("$.errors[0].code").value("NOT_FOUND"));
        send(post("/api/v1/registry/reading-routes"), operator, """
                {"code": 34, "name": "Rota P016 limite invalido", "maxUnits": 0}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("maxUnits"));
    }

    @Test
    @Order(4)
    @DisplayName("quadra: enum de rede, setor censitário e incremento de lote persistem; PARTIAL conta como tem rede")
    void block() throws Exception {
        blockId = id(send(post("/api/v1/registry/blocks"), operator, """
                {"sectorId": "%s", "code": 41, "readingRouteId": "%s", "waterNetwork": "PARTIAL", \
                "sewerNetwork": "FULL", "censusSector": "355030800000123", "autoIncrementLot": true}"""
                .formatted(sectorId, routeId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waterNetwork").value("PARTIAL"))
                .andExpect(jsonPath("$.sewerNetwork").value("FULL"))
                .andExpect(jsonPath("$.hasWaterNetwork").value(true))
                .andExpect(jsonPath("$.hasSewerNetwork").value(true))
                .andExpect(jsonPath("$.censusSector").value("355030800000123"))
                .andExpect(jsonPath("$.autoIncrementLot").value(true)));
        mvc.perform(get("/api/v1/registry/blocks/" + blockId).header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waterNetwork").value("PARTIAL"))
                .andExpect(jsonPath("$.sewerNetwork").value("FULL"))
                .andExpect(jsonPath("$.censusSector").value("355030800000123"))
                .andExpect(jsonPath("$.autoIncrementLot").value(true));

        // Payload legado (só booleanos): true vira FULL, false vira NONE.
        blockLegacyId = id(send(post("/api/v1/registry/blocks"), operator, """
                {"sectorId": "%s", "code": 42, "hasWaterNetwork": true, "hasSewerNetwork": false}"""
                .formatted(sectorId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waterNetwork").value("FULL"))
                .andExpect(jsonPath("$.sewerNetwork").value("NONE"))
                .andExpect(jsonPath("$.hasWaterNetwork").value(true))
                .andExpect(jsonPath("$.hasSewerNetwork").value(false)));

        blockPlainId = id(send(post("/api/v1/registry/blocks"), operator, """
                {"sectorId": "%s", "code": 43}""".formatted(sectorId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.waterNetwork").value("NONE"))
                .andExpect(jsonPath("$.sewerNetwork").value("NONE"))
                .andExpect(jsonPath("$.hasWaterNetwork").value(false))
                .andExpect(jsonPath("$.censusSector").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.autoIncrementLot").value(false)));

        send(post("/api/v1/registry/blocks"), operator, """
                {"sectorId": "%s", "code": 44, "censusSector": "123456789012345678901"}""".formatted(sectorId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("censusSector"));
    }

    @Test
    @Order(5)
    @DisplayName("edição: PUT troca os novos campos e o GET reflete; pai da rota, depois de atribuído, não muda")
    void update() throws Exception {
        send(put("/api/v1/registry/localities/" + localityId), operator, """
                {"code": 11, "name": "Localidade P016", "headquarters": false, "computerized": true, "version": 0}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headquarters").value(false))
                .andExpect(jsonPath("$.computerized").value(true));

        send(put("/api/v1/registry/commercial-sectors/" + sectorId), operator, """
                {"localityId": "%s", "code": 21, "name": "Setor P016", "alternative": false, "version": 0}"""
                .formatted(localityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternative").value(false));

        send(put("/api/v1/registry/blocks/" + blockId), operator, """
                {"sectorId": "%s", "code": 41, "waterNetwork": "NONE", "sewerNetwork": "PARTIAL", \
                "censusSector": null, "autoIncrementLot": false, "version": 0}""".formatted(sectorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waterNetwork").value("NONE"))
                .andExpect(jsonPath("$.sewerNetwork").value("PARTIAL"))
                .andExpect(jsonPath("$.hasWaterNetwork").value(false))
                .andExpect(jsonPath("$.hasSewerNetwork").value(true))
                .andExpect(jsonPath("$.censusSector").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.autoIncrementLot").value(false));

        send(put("/api/v1/registry/reading-routes/" + routeId), operator, """
                {"code": 31, "name": "Rota P016", "localityId": "%s", "sectorId": "%s", "maxUnits": 200, \
                "version": 0}""".formatted(localityPlainId, sectorPlainId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("localityId"))
                .andExpect(jsonPath("$.errors[0].code").value("IMMUTABLE"));

        // Rota sem pai aceita a primeira atribuição.
        send(put("/api/v1/registry/reading-routes/" + routePlainId), operator, """
                {"code": 32, "name": "Rota P016 sem campos novos", "localityId": "%s", "sectorId": "%s", \
                "maxUnits": 50, "version": 0}""".formatted(localityId, sectorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localityId").value(localityId))
                .andExpect(jsonPath("$.sectorId").value(sectorId))
                .andExpect(jsonPath("$.maxUnits").value(50));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
