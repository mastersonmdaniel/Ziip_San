package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.15: o gate de MFA na cadeia real, antes do filtro de tenant. A prova usa
 * um caminho de plataforma inexistente — o gate barra antes do 404, e passa
 * antes do 404 quando há amr. P0.43: depois do gate, o PlatformAccessFilter
 * fiscaliza o prefixo (token com tenant é recusado) e o filtro de tenant
 * isenta rotas de plataforma; a primeira rota real é
 * GET /api/v1/platform/deployments (ver PlatformDeploymentAccessTest).
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
    @DisplayName("P0.43: plataforma com amr mas COM tenant: passa no gate e cai em tenant-not-allowed (token de tenant não abre rota de plataforma)")
    void platformWithAmrAndTenantIsTenantNotAllowed() throws Exception {
        String token = TestTokens.token(ProvisionedTenant.TENANT_ID, List.of("VIEWER"),
                b -> b.claim("amr", List.of("otp")));
        mvc.perform(get("/api/v1/platform/qualquer").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:tenant-not-allowed"));
    }

    @Test
    @DisplayName("P0.43: plataforma com amr, sem tenant e sem papel PLATFORM_*: passa no gate e cai em access-denied (não mais tenant-missing)")
    void platformWithAmrWithoutTenantWithoutPlatformRoleIsAccessDenied() throws Exception {
        String token = TestTokens.token(null, List.of("VIEWER"), b -> b.claim("amr", List.of("hwk")));
        mvc.perform(get("/api/v1/platform/qualquer").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:access-denied"));
    }

    @Test
    @DisplayName("P0.43: plataforma com amr, sem tenant e com papel PLATFORM_OPS: passa no gate, no acesso e no filtro de tenant (404, rota inexistente)")
    void platformWithAmrWithoutTenantWithPlatformRolePassesTenantFilter() throws Exception {
        String token = TestTokens.token(null, List.of("PLATFORM_OPS"), b -> b.claim("amr", List.of("hwk")));
        mvc.perform(get("/api/v1/platform/qualquer").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("rota de negócio com token sem amr não passa pelo gate (gate só vale para plataforma)")
    void businessRouteUnaffectedByGate() throws Exception {
        String token = TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER");
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
