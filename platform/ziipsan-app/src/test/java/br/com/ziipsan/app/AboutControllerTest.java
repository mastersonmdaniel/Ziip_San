package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.42: {@code GET /api/v1/about} apresenta o registro gravado mais recente.
 *
 * <p>A prova do fallback (sem registro: dev/unknown) é unitária, em
 * {@code AboutInfoTest}: o banco de integração é compartilhado pela JVM e outra
 * classe (ex.: o teste do perfil migrate) pode já ter gravado uma linha, então
 * "tabela vazia" não é um estado que este teste possa prometer. Aqui, a linha
 * inserida é a mais recente no instante da leitura, e a asserção é sobre ela.
 */
class AboutControllerTest extends AppIntegrationTestBase {

    @Test
    @DisplayName("about devolve a implantação gravada mais recente (versão, commit, ambiente, data)")
    void aboutReturnsLatestRecordedDeployment() throws Exception {
        String version = "p0r-f43-about-" + UUID.randomUUID().toString().substring(0, 8);
        JdbcTemplate migrator = new JdbcTemplate(ProvisionedTenant.migratorDs);
        try {
            migrator.update(
                    "INSERT INTO platform.deployment (version, commit_hash, environment, started_at, finished_at)"
                            + " VALUES (?, ?, ?, NOW(), NOW())",
                    version, "about1234", "local");
            String token = TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER");
            mvc.perform(get("/api/v1/about").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(version))
                    .andExpect(jsonPath("$.commit").value("about1234"))
                    .andExpect(jsonPath("$.environment").value("local"))
                    .andExpect(jsonPath("$.deployedAt").exists())
                    .andExpect(jsonPath("$.deployedAt").isNotEmpty());
        } finally {
            migrator.update("DELETE FROM platform.deployment WHERE version = ?", version);
        }
    }

    @Test
    @DisplayName("about sem token é 401 (rota autenticada, como /api/v1/**)")
    void aboutWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/about")).andExpect(status().isUnauthorized());
    }
}
