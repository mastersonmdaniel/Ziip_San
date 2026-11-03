package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.8 + P0.39, ponta a ponta no contexto real (filtros de segurança reais, banco de IT).
 *
 * <p>Prova: cabeçalhos Ziip-Version/Ziip-Min-Client-Version em resposta pública (health)
 * e em resposta recusada (401 sem token); métricas HTTP + Hikari + contador de negócio
 * expostos no Prometheus (autenticado); latência sem rótulo de tenant é garantida em
 * código pelo TenantTagGuardTest (aqui só se prova a exposição, não a ausência de tag
 * em cada série — o scrape completo é papel da Central, P1.17).
 */
class VersionAndObservabilityIntegrationTest extends AppIntegrationTestBase {

    private String token() {
        ProvisionedTenant.ensure();
        return bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER"));
    }

    @Test
    @DisplayName("P0.39: health publica traz Ziip-Version e Ziip-Min-Client-Version")
    void healthHasVersionHeaders() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Ziip-Version"))
                .andExpect(header().exists("Ziip-Min-Client-Version"));
    }

    @Test
    @DisplayName("P0.39: resposta recusada (401) tambem traz os cabeçalhos de versao")
    void unauthenticatedHasVersionHeaders() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("Ziip-Version"))
                .andExpect(header().exists("Ziip-Min-Client-Version"));
    }

    @Test
    @DisplayName("P0.8: Prometheus expoe http.server.requests, hikaricp e o contador de negocio")
    void prometheusExposesHttpHikariAndBusinessCounter() throws Exception {
        // Uma requisição de negócio primeiro: sem ela, http.server.requests e o contador
        // de negócio podem nem existir no registry.
        mvc.perform(get("/api/v1/me").header("Authorization", token()))
                .andExpect(status().isOk());
        MvcResult result = mvc.perform(get("/actuator/prometheus").header("Authorization", token()))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("http_server_requests");
        assertThat(body).contains("hikaricp");
        assertThat(body).contains("ziipsan_business_requests_total");
        assertThat(body).contains("route_group=\"me\"");
    }

    @Test
    @DisplayName("P0.8: /actuator/metrics exige autenticacao (nao e publico como o health)")
    void metricsRequiresAuthentication() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }
}
