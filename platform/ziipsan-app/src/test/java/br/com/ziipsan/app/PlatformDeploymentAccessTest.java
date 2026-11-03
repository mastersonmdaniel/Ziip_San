package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.43, na cadeia real: matriz por papel em {@code GET /api/v1/platform/deployments},
 * mais as duas invariantes do prefixo (MFA e ausência de tenant).
 *
 * <p>Tokens de plataforma: sem {@code tenant_id}, com {@code amr otp} (o gate já foi
 * provado em {@code MfaGateIntegrationTest}; aqui, um caso sem amr confirma que a
 * ordem gate → acesso se mantém na rota real).
 */
class PlatformDeploymentAccessTest extends AppIntegrationTestBase {

    private static String platformToken(String role) {
        return TestTokens.token(null, List.of(role), b -> b.claim("amr", List.of("otp")));
    }

    @Test
    @DisplayName("OWNER e OPS leem implantações; SUPPORT e FINANCE recebem 403 access-denied")
    void deploymentReadMatrix() throws Exception {
        String version = "p0r-f43-dep-" + UUID.randomUUID().toString().substring(0, 8);
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
        try {
            migrator.update(
                    "INSERT INTO platform.deployment (version, commit_hash, environment, started_at, finished_at)"
                            + " VALUES (?, ?, ?, NOW(), NOW())",
                    version, "dep1234", "local");
            for (String role : List.of("PLATFORM_OWNER", "PLATFORM_OPS")) {
                mvc.perform(get("/api/v1/platform/deployments")
                                .header("Authorization", bearer(platformToken(role))))
                        .andExpect(status().isOk())
                        .andExpect(content().string(org.hamcrest.Matchers.containsString(version)));
            }
            for (String role : List.of("PLATFORM_SUPPORT", "PLATFORM_FINANCE")) {
                mvc.perform(get("/api/v1/platform/deployments")
                                .header("Authorization", bearer(platformToken(role))))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:access-denied"));
            }
        } finally {
            migrator.update("DELETE FROM platform.deployment WHERE version = ?", version);
        }
    }

    @Test
    @DisplayName("token de tenant com amr em rota de plataforma: 403 tenant-not-allowed")
    void tenantTokenIsRefused() throws Exception {
        String token = TestTokens.token(ProvisionedTenant.TENANT_ID, List.of("VIEWER"),
                b -> b.claim("amr", List.of("otp")));
        mvc.perform(get("/api/v1/platform/deployments").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:tenant-not-allowed"));
    }

    @Test
    @DisplayName("papel de plataforma sem amr: 403 mfa-required (o gate vem antes do acesso)")
    void platformRoleWithoutAmrIsMfaRequired() throws Exception {
        String token = TestTokens.token(null, "PLATFORM_OWNER");
        mvc.perform(get("/api/v1/platform/deployments").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:mfa-required"));
    }

    @Test
    @DisplayName("sem token: 401")
    void withoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/platform/deployments")).andExpect(status().isUnauthorized());
    }
}
