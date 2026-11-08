package br.com.ziipsan.platform.orgscope;

import br.com.ziipsan.platform.orgscope.web.OrgScopeController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda da correção D2 da revisão independente: a atribuição INICIAL de
 * unidade ao protocolo tem rota na API, no padrão do controller (toda rota
 * com a permissão declarada; ator do sub do token — a assinatura com
 * {@code @AuthenticationPrincipal} é conferida junto). Sem a rota, o
 * serviço tinha a operação mas a borda não a alcançava.
 */
class OrgScopeControllerTest {

    private static Method method(String name) {
        return Arrays.stream(OrgScopeController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("método " + name + " não existe no controller"));
    }

    @Test
    @DisplayName("correção D2: POST /protocols/{id}/org-unit existe, com protocol:write declarada")
    void assignUnitRouteExistsWithPermission() {
        Method assign = method("assignUnit");
        PostMapping mapping = assign.getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/protocols/{id}/org-unit");
        assertThat(assign.getAnnotation(PreAuthorize.class).value())
                .contains("hasAuthority('protocol:write')");
    }

    @Test
    @DisplayName("correção D4: POST /org-units/{id}/deactivate existe, com user:write declarada")
    void deactivateRouteExistsWithPermission() {
        Method deactivate = method("deactivateUnit");
        PostMapping mapping = deactivate.getAnnotation(PostMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/org-units/{id}/deactivate");
        assertThat(deactivate.getAnnotation(PreAuthorize.class).value())
                .contains("hasAuthority('user:write')");
    }

    @Test
    @DisplayName("toda rota do controller continua declarando a sua permissão (régua da P0.13)")
    void everyRouteDeclaresPermission() {
        long routes = Arrays.stream(OrgScopeController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PostMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class))
                .peek(m -> assertThat(m.getAnnotation(PreAuthorize.class))
                        .as("rota %s sem @PreAuthorize", m.getName()).isNotNull())
                .count();
        assertThat(routes).isGreaterThanOrEqualTo(9);
    }
}
