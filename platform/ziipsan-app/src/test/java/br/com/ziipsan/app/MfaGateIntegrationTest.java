package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.15: o gate de MFA na cadeia real, antes do filtro de tenant. Não existe
 * rota de plataforma ainda (é P0.43): a prova usa um caminho de plataforma
 * inexistente — o gate barra antes do 404, e passa antes do 404 quando há amr.
 */
class MfaGateIntegrationTest extends AppIntegrationTestBase {

    @Test
    @DisplayName("plataforma sem amr: 403 mfa-required (o gate vem antes do tenant-missing)")
    void platformWithoutAmrIsMfaRequiredEvenWithoutTenant() throws Exception {
        // Sem tenant_id E sem amr: se o filtro de tenant viesse antes, seria tenant-missing.
        String token = TestTokens.token(null, "VIEWER");
        mvc.perform(get("/api/v1/platform/qualquer").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:mfa-required"));
    }

    @Test
    @DisplayName("plataforma sem amr, com tenant: 403 mfa-required do mesmo jeito")
    void platformWithTenantWithoutAmrIsMfaRequired() throws Exception {
        String token = TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER");
        mvc.perform(get("/api/v1/platform/qualquer").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:mfa-required"));
    }

    @Test
    @DisplayName("plataforma com amr otp e tenant: passa no gate (404, porque a rota ainda não existe)")
    void platformWithAmrPassesGate() throws Exception {
        String token = TestTokens.token(ProvisionedTenant.TENANT_ID, List.of("VIEWER"),
                b -> b.claim("amr", List.of("otp")));
        mvc.perform(get("/api/v1/platform/qualquer").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("plataforma com amr mas sem tenant: passa no gate e cai em tenant-missing (interação P0.43)")
    void platformWithAmrWithoutTenantFallsToTenantFilter() throws Exception {
        String token = TestTokens.token(null, List.of("VIEWER"), b -> b.claim("amr", List.of("hwk")));
        mvc.perform(get("/api/v1/platform/qualquer").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:tenant-missing"));
    }

    @Test
    @DisplayName("rota de negócio com token sem amr não passa pelo gate (gate só vale para plataforma)")
    void businessRouteUnaffectedByGate() throws Exception {
        String token = TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER");
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
