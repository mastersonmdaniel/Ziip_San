package br.com.ziipsan.app;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.security.MfaPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.15: o realm JSON no nível estrutural (não há Keycloak aqui; a prova real de
 * login é a P0.27). Confere fluxo condicional, policies, required actions, mapper
 * AMR, client BFF e a divergência residual do client público — e que os valores
 * do JSON espelham as constantes de {@link MfaPolicy}.
 */
class RealmMfaStructureTest {

    private final JsonNode realm = readRealm();

    @Test
    @DisplayName("client BFF é confidencial, com o fluxo BFF e o mapper AMR; o público segue existindo (divergência registrada)")
    void bffClientAndAmrMapper() {
        JsonNode bff = client("ziipsan-bff");
        assertThat(bff).isNotNull();
        assertThat(bff.get("publicClient").asBoolean()).isFalse();
        assertThat(bff.get("standardFlowEnabled").asBoolean()).isTrue();
        assertThat(bff.get("authenticationFlowBindingOverrides").get("browser").asText())
                .isEqualTo("ziipsan-bff-browser");
        List<String> mappers = new ArrayList<>();
        for (JsonNode mapper : bff.get("protocolMappers")) {
            mappers.add(mapper.get("protocolMapper").asText());
            if (mapper.get("protocolMapper").asText().equals("oidc-amr-mapper")) {
                assertThat(mapper.get("config").get("access.token.claim").asText()).isEqualTo("true");
                assertThat(mapper.get("config").get("id.token.claim").asText()).isEqualTo("true");
            }
        }
        assertThat(mappers).contains("oidc-amr-mapper");
        // Sem segredo no JSON, nunca: ele vem do ambiente/cofre (BFF.md).
        assertThat(bff.get("secret")).isNull();

        // Divergência residual, explícita e não escondida: o público continua.
        JsonNode web = client("ziipsan-web");
        assertThat(web).isNotNull();
        assertThat(web.get("publicClient").asBoolean()).isTrue();
        assertThat(web.get("enabled").asBoolean()).isTrue();
        // Rotação de refresh token ligada no realm (vale para os dois clients).
        assertThat(realm.get("revokeRefreshToken").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("fluxo BFF: senha obrigatória + dois ramos condicionais (plataforma e tenant)")
    void conditionalFlows() {
        JsonNode top = flow("ziipsan-bff-browser");
        assertThat(top.get("topLevel").asBoolean()).isTrue();
        List<String> topAuthenticators = new ArrayList<>();
        List<String> topSubflows = new ArrayList<>();
        for (JsonNode execution : top.get("authenticationExecutions")) {
            if (execution.get("authenticatorFlow").asBoolean()) {
                topSubflows.add(execution.get("flowAlias").asText());
                assertThat(execution.get("requirement").asText()).isEqualTo("CONDITIONAL");
            } else {
                topAuthenticators.add(execution.get("authenticator").asText());
            }
        }
        assertThat(topAuthenticators).contains("auth-username-password-form");
        assertThat(topSubflows).containsExactlyInAnyOrder("ziipsan-platform-mfa", "ziipsan-tenant-mfa");

        assertSubflow("ziipsan-platform-mfa", "ziipsan-platform-condition",
                MfaPolicy.PLATFORM_USER_ATTRIBUTE, MfaPolicy.PLATFORM_USER_VALUE);
        assertSubflow("ziipsan-tenant-mfa", "ziipsan-tenant-condition",
                MfaPolicy.USER_ATTRIBUTE, "true");
    }

    private void assertSubflow(String alias, String conditionAlias, String attribute, String value) {
        JsonNode subflow = flow(alias);
        assertThat(subflow.get("topLevel").asBoolean()).isFalse();
        List<String> authenticators = new ArrayList<>();
        String conditionConfig = null;
        for (JsonNode execution : subflow.get("authenticationExecutions")) {
            authenticators.add(execution.get("authenticator").asText());
            if (execution.get("authenticator").asText().equals("conditional-user-attribute")) {
                assertThat(execution.get("requirement").asText()).isEqualTo("REQUIRED");
                conditionConfig = execution.get("authenticatorConfig").asText();
            } else {
                assertThat(execution.get("requirement").asText()).isEqualTo("ALTERNATIVE");
            }
        }
        assertThat(authenticators).containsExactlyInAnyOrder(
                "conditional-user-attribute", "auth-otp-form", "webauthn-authenticator");
        assertThat(conditionConfig).isEqualTo(conditionAlias);
        JsonNode config = authenticatorConfig(conditionAlias);
        assertThat(config.get("config").get("attribute").asText()).isEqualTo(attribute);
        assertThat(config.get("config").get("value").asText()).isEqualTo(value);
    }

    @Test
    @DisplayName("P0.43: os quatro papéis de plataforma do CENTRAL-ZIIP §2 existem no realm")
    void platformRolesExistInRealm() {
        List<String> roles = new ArrayList<>();
        for (JsonNode role : realm.get("roles").get("realm")) {
            roles.add(role.get("name").asText());
        }
        assertThat(roles).contains("PLATFORM_OWNER", "PLATFORM_OPS", "PLATFORM_SUPPORT", "PLATFORM_FINANCE");
    }

    @Test
    @DisplayName("políticas OTP/WebAuthn e required actions de enrolamento (definidas, não padrão)")
    void policiesAndRequiredActions() {
        assertThat(realm.get("otpPolicy").get("digits").asInt()).isEqualTo(6);
        assertThat(realm.get("otpPolicy").get("period").asInt()).isEqualTo(30);
        assertThat(realm.get("webAuthnPolicy").get("rpEntityName").asText()).isEqualTo("ZIIPSAN");
        List<String> actions = new ArrayList<>();
        for (JsonNode action : realm.get("requiredActions")) {
            actions.add(action.get("alias").asText());
            assertThat(action.get("enabled").asBoolean()).isTrue();
            assertThat(action.get("defaultAction").asBoolean())
                    .as("enrolamento não pode ser padrão para todo usuário novo: %s", action.get("alias").asText())
                    .isFalse();
        }
        assertThat(actions).contains("CONFIGURE_TOTP", "webauthn-register");
    }

    private JsonNode client(String clientId) {
        for (JsonNode client : realm.get("clients")) {
            if (client.get("clientId").asText().equals(clientId)) {
                return client;
            }
        }
        return null;
    }

    private JsonNode flow(String alias) {
        for (JsonNode flow : realm.get("authenticationFlows")) {
            if (flow.get("alias").asText().equals(alias)) {
                return flow;
            }
        }
        throw new IllegalStateException("fluxo ausente no realm: " + alias);
    }

    private JsonNode authenticatorConfig(String alias) {
        for (JsonNode config : realm.get("authenticatorConfig")) {
            if (config.get("alias").asText().equals(alias)) {
                return config;
            }
        }
        throw new IllegalStateException("authenticatorConfig ausente no realm: " + alias);
    }

    private static JsonNode readRealm() {
        try {
            Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            while (dir != null) {
                Path pom = dir.resolve("pom.xml");
                if (Files.exists(pom)) {
                    String content = Files.readString(pom, StandardCharsets.UTF_8);
                    if (content.contains("<artifactId>ziipsan</artifactId>")
                            && content.contains("<packaging>pom</packaging>")) {
                        Path realm = dir.resolve("infra/keycloak/realms/ziipsan-realm.json");
                        assertThat(realm).exists();
                        return Json.readTree(Files.readString(realm, StandardCharsets.UTF_8));
                    }
                }
                dir = dir.getParent();
            }
            throw new IllegalStateException("raiz do repositório não encontrada");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
