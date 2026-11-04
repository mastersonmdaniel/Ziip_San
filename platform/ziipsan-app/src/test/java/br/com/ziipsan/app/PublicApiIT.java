package br.com.ziipsan.app;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.integration.service.ApiClientService;
import br.com.ziipsan.integration.service.ApiTokenService;
import br.com.ziipsan.integration.service.TenantParameterReader;
import br.com.ziipsan.integration.service.WebhookService;
import br.com.ziipsan.integration.webhook.WebhookHttpSender;
import br.com.ziipsan.integration.webhook.WebhookSigner;
import br.com.ziipsan.platform.console.TenantParameterService;
import br.com.ziipsan.security.crypto.FieldCipher;
import br.com.ziipsan.security.crypto.TenantKeyProvider;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2.26 no PostgreSQL real + HTTP real (MockMvc, cadeia de segurança
 * própria da API pública): migration V7_1, token endpoint OAuth 2.0,
 * rotas de recurso com escopos, limite de taxa, tela do tenant e
 * webhooks de saída com assinatura e reenvio.
 *
 * <p>Os parâmetros {@code api.enabled} e {@code api.webhooks_enabled}
 * são aceitos pelo fluxo da P1.6 (propor → aprovar por outra pessoa →
 * aceite), como em produção — o padrão de fábrica é desligado, e sem
 * aceite o token endpoint responde 403.
 */
class PublicApiIT extends AppIntegrationTestBase {

    private static final String SCHEMA = ProvisionedTenant.SCHEMA;
    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;
    private static final AtomicBoolean SETUP = new AtomicBoolean(false);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ApiClientService apiClients;

    @Autowired
    private ApiTokenService apiTokens;

    @BeforeAll
    static void ensureTenant() {
        ProvisionedTenant.ensure();
    }

    /** Aceita api.enabled + api.webhooks_enabled uma vez por JVM. */
    private void acceptApiParameters() {
        if (!SETUP.compareAndSet(false, true)) {
            return;
        }
        var params = new TenantParameterService(dataSource, SCHEMA, TENANT);
        UUID proposer = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        for (String key : List.of("api.enabled", "api.webhooks_enabled")) {
            var version = params.propose(key, "true", LocalDate.now(), null, proposer);
            params.approve(version.id(), approver);
            params.accept(version.id(), UUID.randomUUID(), "Teste P2.26", "ADMIN", false);
        }
    }

    /** Roda o trabalho com o contexto do tenant, como o filtro faria. */
    private <T> T asTenant(TenantWork<T> work) throws Exception {
        TenantContextHolder.setTenant(SCHEMA);
        TenantContextHolder.setTenantId(TENANT);
        try {
            return work.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    private interface TenantWork<T> {
        T run() throws Exception;
    }

    private String uniqueClientId() {
        return "it-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ApiClientService.CreatedClient createClient(String scope, String environment) throws Exception {
        return asTenant(() -> apiClients.create(new ApiClientService.CreateInput(
                uniqueClientId(), "Cliente IT P2.26", "CLIENT_SECRET", List.of(scope),
                null, List.of(), environment, null)));
    }

    private String issueToken(String clientId, String secret, String scope) throws Exception {
        return asTenant(() -> {
            var issued = apiTokens.issue(new ApiTokenService.TokenRequest(
                    TENANT, clientId, "client_credentials", scope, secret, null, null), "127.0.0.1");
            return issued.accessToken();
        });
    }

    private String tokenForScope(String scope, String environment) throws Exception {
        var created = createClient(scope, environment);
        return issueToken(created.client().clientId(), created.secret(), scope);
    }

    private MvcResult postToken(String clientId, String secret, String scope) throws Exception {
        return mvc.perform(post("/api/v1/public-api/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("tenant_id", TENANT.toString())
                        .param("client_id", clientId)
                        .param("client_secret", secret)
                        .param("grant_type", "client_credentials")
                        .param("scope", scope))
                .andReturn();
    }

    // ------------------------------------------------------------- V7_1
    @Test
    @DisplayName("V7_1: tabelas, RLS forçada, parâmetros e regras de redação existem")
    void v71StructureExists() throws Exception {
        ProvisionedTenant.ensure();
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            for (String table : List.of("api_clients", "api_access_tokens", "api_rate_buckets",
                    "api_assertion_jtis", "webhook_endpoints", "webhook_deliveries", "webhook_alerts")) {
                assertThat(tableExists(c, table)).as("tabela %s", table).isTrue();
                assertThat(rlsForced(c, table)).as("RLS forçada em %s", table).isTrue();
            }
            for (String key : List.of("api.enabled", "api.rate_limit_per_minute", "api.ip_allowlist",
                    "api.client_max", "api.webhooks_enabled", "api.webhook_max_endpoints",
                    "api.webhook_event_types")) {
                assertThat(parameterExists(c, key)).as("parâmetro %s", key).isTrue();
            }
            assertThat(redactionRules(c)).isGreaterThanOrEqualTo(6);
        }
    }

    private static boolean tableExists(Connection c, String table) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, SCHEMA);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean rlsForced(Connection c, String table) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT relforcerowsecurity FROM pg_class cl JOIN pg_namespace n ON n.oid = cl.relnamespace"
                        + " WHERE n.nspname = ? AND cl.relname = ?")) {
            ps.setString(1, SCHEMA);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static boolean parameterExists(Connection c, String key) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM public.parameter_definitions WHERE definition_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int redactionRules(Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT count(*) FROM public.audit_redaction_rule"
                        + " WHERE table_name IN ('api_clients', 'api_access_tokens', 'api_assertion_jtis',"
                        + " 'webhook_endpoints')")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ------------------------------------------------------- token endpoint
    @Test
    @DisplayName("token endpoint emite token opaco; segredo errado e escopo fora do cliente são recusados")
    void tokenEndpointIssuesAndRefuses() throws Exception {
        acceptApiParameters();
        var created = createClient("properties:read", "PRODUCTION");
        MvcResult ok = postToken(created.client().clientId(), created.secret(), "properties:read");
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        String token = Json.readTree(ok.getResponse().getContentAsString()).get("access_token").asText();
        assertThat(token).startsWith("ziip_at_");
        assertThat(Json.readTree(ok.getResponse().getContentAsString()).get("scope").asText())
                .isEqualTo("properties:read");

        MvcResult wrongSecret = postToken(created.client().clientId(), "segredo-errado", "properties:read");
        assertThat(wrongSecret.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongSecret.getResponse().getContentAsString()).contains("invalid_client");

        MvcResult wrongScope = postToken(created.client().clientId(), created.secret(), "bills:read");
        assertThat(wrongScope.getResponse().getStatus()).isEqualTo(400);
        assertThat(wrongScope.getResponse().getContentAsString()).contains("invalid_scope");
    }

    // ---------------------------------------------------------- rotas HTTP
    @Test
    @DisplayName("rota de recurso exige Bearer, respeita o escopo e devolve versão; versão desconhecida é 404")
    void resourceRoutesEnforceScopesAndVersion() throws Exception {
        acceptApiParameters();
        String token = tokenForScope("properties:read", "PRODUCTION");
        mvc.perform(get("/api/v1/public-api/v1/properties")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Ziipsan-Public-Api-Version"))
                .andExpect(jsonPath("$.items").exists());
        mvc.perform(get("/api/v1/public-api/v1/bills")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/public-api/v1/properties"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/public-api/v99/properties")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("cliente SANDBOX recebe a massa sintética e o cabeçalho de sandbox")
    void sandboxClientReceivesFixtures() throws Exception {
        acceptApiParameters();
        String token = tokenForScope("properties:read", "SANDBOX");
        mvc.perform(get("/api/v1/public-api/v1/properties")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Ziipsan-Sandbox", "true"))
                .andExpect(jsonPath("$.items[0].id").value("10000000-0000-4000-8000-000000000001"));
    }

    @Test
    @DisplayName("limite do cliente: acima dele, 429 com Retry-After")
    void clientRateLimitReturns429() throws Exception {
        acceptApiParameters();
        var created = asTenant(() -> apiClients.create(new ApiClientService.CreateInput(
                uniqueClientId(), "Cliente IT taxa", "CLIENT_SECRET", List.of("properties:read"),
                2, List.of(), "PRODUCTION", null)));
        String token = issueToken(created.client().clientId(), created.secret(), "properties:read");
        mvc.perform(get("/api/v1/public-api/v1/properties").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public-api/v1/properties").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public-api/v1/properties").header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("POST service-orders cria OS em DRAFT com o tipo semeado e alvo unidade")
    void createServiceOrderReturnsDraft() throws Exception {
        acceptApiParameters();
        // A RLS FORÇADA de service_types exige app.current_tenant: sem a
        // GUC na mesma conexão, current_tenant_id() é NULL e o INSERT
        // viola a policy. (set_config session-level, false; a conexão
        // morre no fim do try, sem vazar para o pool.)
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement setTenant = c.prepareStatement(
                     "SELECT set_config('app.current_tenant', ?, false)")) {
            setTenant.setString(1, TENANT.toString());
            setTenant.executeQuery();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO \"" + SCHEMA + "\".service_types"
                            + " (tenant_id, code, name, operation, indicator, target_classes, effect_code)"
                            + " VALUES (?, 'IT-TEST', 'Tipo IT', 'OP-IT', 'INTERNAL', ARRAY['CONSUMER_UNIT'], 'EF-IT')"
                            + " ON CONFLICT DO NOTHING")) {
                ps.setObject(1, TENANT);
                ps.executeUpdate();
            }
        }
        String token = tokenForScope("service-orders:write", "PRODUCTION");
        MvcResult created = mvc.perform(post("/api/v1/public-api/v1/service-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceTypeCode\":\"IT-TEST\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.serviceTypeCode").value("IT-TEST"))
                .andReturn();
        String orderId = Json.readTree(created.getResponse().getContentAsString()).get("id").asText();
        String readToken = tokenForScope("service-orders:read", "PRODUCTION");
        mvc.perform(get("/api/v1/public-api/v1/service-orders/" + orderId)
                        .header("Authorization", "Bearer " + readToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // --------------------------------------------------------- tela do tenant
    @Test
    @DisplayName("tela do tenant: ADMIN cria cliente e vê o segredo uma vez; a listagem nunca o inclui")
    void adminScreenCreatesClientWithoutLeakingSecret() throws Exception {
        acceptApiParameters();
        String admin = "Bearer " + TestTokens.token(TENANT, "ADMIN");
        String viewer = "Bearer " + TestTokens.token(TENANT, "VIEWER");
        mvc.perform(get("/api/v1/tenant/integration/clients").header("Authorization", viewer))
                .andExpect(status().isForbidden());
        String clientId = uniqueClientId();
        MvcResult created = mvc.perform(post("/api/v1/tenant/integration/clients")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"name\":\"IT\",\"authMethod\":\"CLIENT_SECRET\","
                                + "\"scopes\":[\"properties:read\"],\"environment\":\"PRODUCTION\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String secret = Json.readTree(created.getResponse().getContentAsString()).get("secret").asText();
        assertThat(secret).isNotBlank();
        MvcResult list = mvc.perform(get("/api/v1/tenant/integration/clients").header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(list.getResponse().getContentAsString()).doesNotContain(secret);
        assertThat(list.getResponse().getContentAsString()).contains(clientId);
    }

    // -------------------------------------------------------------- webhooks
    @Test
    @DisplayName("webhook: entrega assinada com sucesso, falha com recuo e desativação após 5 falhas")
    void webhookDeliverySignedRetryAndAutoDisable() throws Exception {
        acceptApiParameters();
        var cipher = new FieldCipher(new TenantKeyProvider() {
            public byte[] encryptionKey(UUID tenantId) {
                return new byte[32];
            }

            public byte[] blindIndexKey(UUID tenantId) {
                return new byte[32];
            }
        });
        var captured = new ArrayList<String[]>();
        WebhookHttpSender okSender = (url, body, eventId, timestamp, signature) -> {
            captured.add(new String[]{body, eventId, String.valueOf(timestamp), signature});
            return new WebhookHttpSender.Result(200, null);
        };
        var webhooks = new WebhookService(dataSource, new TenantParameterReader(dataSource), okSender, cipher);
        var created = asTenant(() -> webhooks.createEndpoint("https://203.0.113.10/hook",
                List.of("integration.webhook.test")));
        UUID eventId = asTenant(() -> webhooks.sendTest(created.endpoint().id()));
        assertThat(eventId).isNotNull();
        int delivered = asTenant(() -> webhooks.deliverDue(10));
        assertThat(delivered).isEqualTo(1);
        assertThat(captured).hasSize(1);
        assertThat(WebhookSigner.verify(captured.get(0)[3], created.secret(), null,
                Long.parseLong(captured.get(0)[2]), Long.parseLong(captured.get(0)[2]),
                captured.get(0)[1], captured.get(0)[0])).isTrue();

        // Falha: o remetente quebra; a entrega fica FAILED com próxima
        // tentativa — e o lote automático a busca de novo (FAILED vencida).
        WebhookHttpSender failing = (url, body, eventId2, timestamp, signature) -> {
            throw new RuntimeException("receptor fora do ar");
        };
        var failingWebhooks = new WebhookService(dataSource, new TenantParameterReader(dataSource),
                failing, cipher);
        UUID failingEvent = asTenant(() -> failingWebhooks.sendTest(created.endpoint().id()));
        assertThat(failingEvent).isNotNull();
        for (int attempt = 1; attempt <= 5; attempt++) {
            int failed = asTenant(() -> failingWebhooks.deliverDue(10));
            assertThat(failed).isEqualTo(0);
            // A próxima tentativa é no futuro (recuo); o teste a vence
            // para provar a cadeia sem esperar o relógio. A conexão do
            // migratorDs é autoCommit: o UPDATE único já commita sozinho
            // (um c.commit() explícito aqui falha: "Cannot commit when
            // autoCommit is enabled"). E a RLS FORÇADA exige app.current_tenant
            // na mesma conexão: sem a GUC, current_tenant_id() é NULL e o
            // UPDATE casa 0 linhas (diagnóstico: expiryUpdatedRows=0 em
            // todas as tentativas, e o contador nunca avançava).
            try (Connection c = ProvisionedTenant.migratorDs.getConnection();
                 PreparedStatement setTenant = c.prepareStatement(
                         "SELECT set_config('app.current_tenant', ?, false)")) {
                setTenant.setString(1, TENANT.toString());
                setTenant.executeQuery();
                try (Statement st = c.createStatement()) {
                    st.execute("UPDATE \"" + SCHEMA + "\".webhook_deliveries SET next_attempt_at = NOW() - INTERVAL '1 second'"
                            + " WHERE tenant_id = '" + TENANT + "' AND status = 'FAILED'");
                }
            }
        }
        List<WebhookService.EndpointView> endpoints = asTenant(webhooks::listEndpoints);
        assertThat(endpoints.stream().filter(e -> e.id().equals(created.endpoint().id()))
                .findFirst().orElseThrow().status()).isEqualTo("DISABLED");
        // O SELECT de alertas usa a mesma conexão do migrator: também
        // precisa da GUC, senão a RLS devolve 0 mesmo com o alerta lá.
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             PreparedStatement setTenant = c.prepareStatement(
                     "SELECT set_config('app.current_tenant', ?, false)")) {
            setTenant.setString(1, TENANT.toString());
            setTenant.executeQuery();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT count(*) FROM \"" + SCHEMA + "\".webhook_alerts WHERE tenant_id = ? AND endpoint_id = ?")) {
                ps.setObject(1, TENANT);
                ps.setObject(2, created.endpoint().id());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
                }
            }
        }
        // Reenvio manual de entrega já concluída é recusado.
        List<WebhookService.DeliveryView> deliveries = asTenant(() -> webhooks.listDeliveries(
                created.endpoint().id(), "SENT"));
        assertThat(deliveries).isNotEmpty();
        assertThatThrownBy(() -> asTenant(() -> {
            webhooks.retryDelivery(deliveries.get(0).id());
            return null;
        })).isInstanceOf(Exception.class);
    }
}
