package br.com.ziipsan.app;

import br.com.ziipsan.security.auth.PermissionCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.13: toda rota de negócio sob /api/v1 declara a permissão {@code recurso:ação} que
 * exige, e a permissão existe no catálogo. Varre os handler mappings em runtime (não regex
 * sobre o código-fonte). Allowlist explícita: {@code GET /api/v1/me} devolve só
 * dados do próprio usuário autenticado, e {@code GET /api/v1/about} (P0.42) devolve só
 * metadados da implantação, sem dado de tenant ou de usuário — nenhuma das duas acessa
 * dado de terceiros.
 *
 * <p>O documento do catálogo também é conferido contra o código: toda permissão do catálogo
 * aparece nele.
 */
class RoutePermissionTest extends AppIntegrationTestBase {

    private static final Pattern AUTHORITY = Pattern.compile("hasAuthority\\('([^']+)'\\)");

    @Autowired
    @Qualifier("requestMappingHandlerMapping") // sem isto, a injeção por tipo fica ambígua com o actuator
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("toda rota /api/v1 declara hasAuthority de uma permissão do catálogo")
    void everyRouteDeclaresCatalogPermission() {
        List<String> offending = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            var condition = info.getPathPatternsCondition();
            if (condition == null) {
                return;
            }
            condition.getPatterns().forEach(pattern -> {
                String path = pattern.getPatternString();
                if (!path.startsWith("/api/v1") || path.equals("/api/v1/me")
                        || path.equals("/api/v1/about")) {
                    return;
                }
                PreAuthorize auth = method.getMethodAnnotation(PreAuthorize.class);
                if (auth == null) {
                    auth = method.getBeanType().getAnnotation(PreAuthorize.class);
                }
                if (auth == null) {
                    offending.add(path + " -> sem @PreAuthorize: " + method);
                    return;
                }
                String expression = auth.value();
                if (expression.contains("hasAnyRole") || expression.contains("hasRole")) {
                    offending.add(path + " -> ainda usa papel fixo: " + expression);
                    return;
                }
                Matcher matcher = AUTHORITY.matcher(expression);
                boolean ok = false;
                while (matcher.find()) {
                    if (PermissionCatalog.isPermission(matcher.group(1))) {
                        ok = true;
                    } else {
                        offending.add(path + " -> permissão fora do catálogo: " + matcher.group(1));
                    }
                }
                if (!ok) {
                    offending.add(path + " -> sem hasAuthority de permissão do catálogo: " + expression);
                }
            });
        });
        assertThat(offending).as("rotas sem permissão declarada do catálogo").isEmpty();
    }

    @Test
    @DisplayName("documento do catálogo contém toda permissão do código")
    void catalogDocumentMatchesCode() throws Exception {
        Path root = repoRoot();
        Path doc = root.resolve("docs/seguranca/CATALOGO-PERMISSOES.md");
        assertThat(doc).exists();
        String content = Files.readString(doc, StandardCharsets.UTF_8);
        for (String permission : PermissionCatalog.allNames()) {
            assertThat(content).as("permissão ausente no documento: %s", permission).contains("`" + permission + "`");
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path pom = dir.resolve("pom.xml");
            if (Files.exists(pom)) {
                try {
                    String content = Files.readString(pom, StandardCharsets.UTF_8);
                    // A raiz é o pom agregador: artifactId próprio ziipsan + packaging pom.
                    if (content.contains("<artifactId>ziipsan</artifactId>")
                            && content.contains("<packaging>pom</packaging>")) {
                        return dir;
                    }
                } catch (Exception ignored) {
                    // continua subindo
                }
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("raiz do repositório (pom ziipsan) não encontrada");
    }
}
