package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.10: nenhuma rota de negócio fora de /api/v1.
 *
 * <p>Varre os handler mappings em runtime (não regex sobre o código-fonte, que
 * aprovaria strings inocentes e reprovaria comentários). Allowlist explícita:
 * /error do Spring, /actuator/health e /v3/api-docs (permitidos sem token) e
 * /bff/** (P0.14: endpoints de INFRA do BFF — login, CSRF — deliberadamente
 * fora de /api/v1 para não caírem no RoutePermissionTest, que exige permissão
 * do catálogo para toda rota de negócio; não são rotas de negócio).
 * Qualquer controller novo fora de /api/v1 quebra este teste.
 */
class ApiVersionGuardTest extends AppIntegrationTestBase {

    @Autowired
    @Qualifier("requestMappingHandlerMapping") // sem isto, o actuator injeta controllerEndpointHandlerMapping e a injeção por tipo fica ambígua
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("toda rota mapeada é /api/v1/**, /bff/**, /error ou /actuator/health")
    void everyRouteIsVersioned() {
        List<String> offending = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            var condition = info.getPathPatternsCondition();
            if (condition == null) {
                return;
            }
            condition.getPatterns().forEach(pattern -> {
                String path = pattern.getPatternString();
                if (!path.startsWith("/api/v1") && !path.startsWith("/bff/")
                        && !path.equals("/error")
                        && !path.startsWith("/actuator/health")
                        && !path.startsWith("/v3/api-docs")) {
                    offending.add(path + " -> " + method);
                }
            });
        });
        assertThat(offending).as("rotas fora de /api/v1").isEmpty();
    }
}
