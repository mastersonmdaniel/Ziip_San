package br.com.ziipsan.app;

import br.com.ziipsan.security.auth.PermissionCatalog;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1.37, E2E por perfil: para cada perfil de fábrica, um token com só aquele papel
 * devolve em GET /api/v1/me exatamente as permissões efetivas do perfil — e nada mais.
 * Perfil desligado (auditor externo, regulador) devolve papéis, mas zero permissões.
 * O endpoint de perfis e a mudança de comportamento de parameters:manage (ADMIN e
 * GESTOR sem concessão direta) também são provados aqui.
 *
 * <p>Não testa a gravação da atribuição em user_profiles: o CHECK da V1_9 ainda só
 * aceita os quatro mínimos, e a migration que o alarga é da integração (versão a
 * alocar pelo coordenador). A validação do serviço (aceita os 26, recusa os
 * desligados) está em TenantUserServiceTest, sem banco.
 */
class FactoryProfileMeTest extends AppIntegrationTestBase {

    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;

    @Test
    @DisplayName("cada perfil de fábrica expande em /me para exatamente suas permissões efetivas")
    void meExpandsEveryFactoryProfile() throws Exception {
        for (PermissionCatalog.FactoryProfile profile : PermissionCatalog.factoryProfiles()) {
            String token = bearer(TestTokens.token(TENANT, profile.name()));
            MvcResult result = mvc.perform(get("/api/v1/me")
                            .header("Authorization", token)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = result.getResponse().getContentAsString();
            List<String> roles = JsonPath.read(body, "$.roles");
            assertThat(roles).as("papéis de %s", profile.name()).contains(profile.name());
            List<String> permissions = JsonPath.read(body, "$.permissions");
            List<String> expected = profile.enabled()
                    ? profile.permissions().stream().sorted().toList()
                    : List.of();
            // /me devolve tudo que não é ROLE_* como "permissions", incluindo a
            // autoridade de fator do token Bearer (FACTOR_BEARER, pré-existente à
            // P1.37, fora do catálogo recurso:ação): o que se confere aqui são as
            // permissões do catálogo, e que nada fora do catálogo além do fator aparece.
            List<String> catalogPermissions = permissions.stream()
                    .filter(PermissionCatalog::isPermission).sorted().toList();
            assertThat(catalogPermissions).as("permissões efetivas de %s", profile.name())
                    .containsExactlyElementsOf(expected);
            assertThat(permissions.stream().filter(p -> !PermissionCatalog.isPermission(p)).toList())
                    .as("autoridades fora do catálogo em /me para %s", profile.name())
                    .containsExactly("FACTOR_BEARER");
        }
    }

    @Test
    @DisplayName("GET /api/v1/profiles lista os 26 perfis versionados; sem user:read é 403")
    void profilesEndpointListsFactoryProfiles() throws Exception {
        String admin = bearer(TestTokens.token(TENANT, "ADMIN"));
        MvcResult result = mvc.perform(get("/api/v1/profiles")
                        .header("Authorization", admin)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(PermissionCatalog.VERSION))
                .andExpect(jsonPath("$.profiles.length()").value(26))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        List<Object> profiles = JsonPath.read(body, "$.profiles[?(@.code == 'AUDITOR_EXTERNO')]");
        assertThat(profiles).hasSize(1);
        // O filtro JsonPath devolve lista mesmo com [0]: lê-se a lista e confere-se o elemento.
        List<Boolean> externoEnabled = JsonPath.read(body, "$.profiles[?(@.code == 'AUDITOR_EXTERNO')].enabled");
        assertThat(externoEnabled).containsExactly(false);
        List<Boolean> adminEnabled = JsonPath.read(body, "$.profiles[?(@.code == 'ADMIN')].enabled");
        assertThat(adminEnabled).containsExactly(true);

        String viewer = bearer(TestTokens.token(TENANT, "VIEWER"));
        mvc.perform(get("/api/v1/profiles")
                        .header("Authorization", viewer)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("parameters:manage: ADMIN e GESTOR passam sem concessão direta; ATENDENTE leva 403")
    void parametersManageIsGrantedToAdminAndGestorOnly() throws Exception {
        String path = "/api/v1/tenant/parameters/p137-inexistente/current";
        mvc.perform(get(path)
                        .header("Authorization", bearer(TestTokens.token(TENANT, "ADMIN")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mvc.perform(get(path)
                        .header("Authorization", bearer(TestTokens.token(TENANT, "GESTOR")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        mvc.perform(get(path)
                        .header("Authorization", bearer(TestTokens.token(TENANT, "ATENDENTE")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
