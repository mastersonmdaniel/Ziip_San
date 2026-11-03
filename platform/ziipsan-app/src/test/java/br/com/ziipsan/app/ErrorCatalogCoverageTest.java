package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de cobertura da P1.22 (plano v3.0, ADR-0020 decisão 2): falha se algum
 * código {@code urn:ziipsan:problem:*} emitido pelo código não tiver explicação
 * no catálogo de erros, cuja única fonte é {@code content/help/errors/*.yaml}
 * (o SQL de publicação e o bundle do frontend são gerados a partir dele).
 *
 * <p>Universo emitido, sem depender da tabela do {@link ProblemCodeContractTest}
 * (que o teste não duplica): (a) sufixos de URN literais em {@code .java} /
 * {@code .ts} / {@code .tsx} do repositório; (b) códigos passados como string
 * nos pontos de emissão do problem+json — {@code ProblemResponses.write},
 * {@code new ApiException.Conflict/BadRequest/MethodNotAllowed}, o helper
 * {@code problem(...)} do tratador global e os literais de
 * {@code ApiException.java} (subclasses de código fixo e o ternário
 * validation/confirmation-required). Só fontes {@code main} entram em (b):
 * tabelas de teste não são emissão.
 *
 * <p>Excluídos da varredura de (a): o próprio catálogo
 * ({@code content/help/errors/}), os gerados a partir dele
 * ({@code R__help_content.sql}, {@code helpBundle.generated.ts}) — incluí-los
 * tornaria o teste vazio, pois o catálogo cobriria a si mesmo — e este
 * arquivo de teste. Não depende de banco: lê arquivos do repositório.
 */
class ErrorCatalogCoverageTest {

    private static final Pattern URN = Pattern.compile("urn:ziipsan:problem:([a-z][a-z0-9-]*)");
    private static final Pattern CATALOG_CODE =
            Pattern.compile("(?m)^code:\\s*urn:ziipsan:problem:([a-z][a-z0-9-]*)\\s*$");
    private static final Pattern CODE_FORMAT = Pattern.compile("^[a-z]+(-[a-z]+)*$");

    /** problem+json escrito por filtros de segurança/tenant (o código é o 3º argumento). */
    private static final Pattern PROBLEM_RESPONSES_WRITE = Pattern.compile(
            "ProblemResponses\\.write\\(\\s*[^,]+,\\s*\\d+\\s*,\\s*\"([a-z][a-z0-9-]*)\"",
            Pattern.DOTALL);
    /** ApiException com código variável (registry): Conflict, BadRequest, MethodNotAllowed. */
    private static final Pattern VARIABLE_CODE_EXCEPTION = Pattern.compile(
            "new ApiException\\.(?:Conflict|BadRequest|MethodNotAllowed)\\(\\s*\"([a-z][a-z0-9-]*)\"");
    /** Helper problem(...) do GlobalExceptionHandler (status, código, título, detalhe).
     * O status pode ser literal (409) ou variável (handleExceptionInternal repassa
     * o status do Spring: problem(status, "invalid-request", ...)); o segundo
     * argumento precisa ser literal, o que exclui o repasse problem(ex.status(),
     * ex.code(), ...) do tratador de ApiException. */
    private static final Pattern HANDLER_PROBLEM = Pattern.compile(
            "problem\\(\\s*[^,]+,\\s*\"([a-z][a-z0-9-]*)\"");
    /** Qualquer literal de string no formato de código (só usado em ApiException.java). */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path pom = dir.resolve("pom.xml");
            if (Files.exists(pom)) {
                try {
                    String content = Files.readString(pom, StandardCharsets.UTF_8);
                    if (content.contains("<artifactId>ziipsan</artifactId>")
                            && content.contains("<packaging>pom</packaging>")) {
                        return dir;
                    }
                } catch (IOException ignored) {
                    // continua subindo
                }
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("raiz do repositório (pom ziipsan) não encontrada a partir de "
                + System.getProperty("user.dir"));
    }

    private static boolean isExcluded(Path p, String fileName) {
        String s = p.toString().replace('\\', '/');
        return s.contains("/docs/") || s.contains("/infra/_arquivado/")
                || s.contains("/target/") || s.contains("/node_modules/")
                || s.contains("/.git/")
                || s.contains("/content/help/")
                || s.endsWith("R__help_content.sql")
                || s.endsWith("helpBundle.generated.ts")
                || fileName.equals("ErrorCatalogCoverageTest.java");
    }

    private static List<Path> codeFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return !isExcluded(p, p.getFileName().toString())
                                && (s.endsWith(".java") || s.endsWith(".ts") || s.endsWith(".tsx"));
                    })
                    .toList();
        }
    }

    private static boolean isMainSource(Path p) {
        String s = p.toString().replace('\\', '/');
        return s.contains("/src/main/") && !s.contains("/src/test/");
    }

    private static Set<String> emittedCodes(Path root) throws IOException {
        Set<String> emitted = new HashSet<>();
        for (Path file : codeFiles(root)) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Matcher urn = URN.matcher(content);
            while (urn.find()) {
                emitted.add(urn.group(1));
            }
            if (isMainSource(file)) {
                Matcher w = PROBLEM_RESPONSES_WRITE.matcher(content);
                while (w.find()) {
                    emitted.add(w.group(1));
                }
                Matcher v = VARIABLE_CODE_EXCEPTION.matcher(content);
                while (v.find()) {
                    emitted.add(v.group(1));
                }
                Matcher h = HANDLER_PROBLEM.matcher(content);
                while (h.find()) {
                    emitted.add(h.group(1));
                }
                if (file.getFileName().toString().equals("ApiException.java")) {
                    Matcher lit = STRING_LITERAL.matcher(content);
                    while (lit.find()) {
                        if (CODE_FORMAT.matcher(lit.group(1)).matches()) {
                            emitted.add(lit.group(1));
                        }
                    }
                }
            }
        }
        return emitted;
    }

    private static Set<String> catalogCodes(Path root) throws IOException {
        Path errorsDir = root.resolve("content/help/errors");
        Set<String> catalog = new HashSet<>();
        try (Stream<Path> walk = Files.walk(errorsDir)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".yaml")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = CATALOG_CODE.matcher(content);
                if (m.find()) {
                    catalog.add(m.group(1));
                }
            }
        }
        return catalog;
    }

    @Test
    @DisplayName("todo código problem emitido tem explicação no catálogo de erros")
    void everyEmittedProblemCodeHasAnExplanation() throws IOException {
        Path root = repoRoot();
        Set<String> emitted = emittedCodes(root);
        Set<String> catalog = catalogCodes(root);
        assertThat(emitted).as("o teste precisa enxergar códigos emitidos, senão passa no vazio")
                .contains("validation", "internal-error", "unauthenticated");
        assertThat(catalog).as("o catálogo precisa ter explicações, senão passa no vazio")
                .contains("validation", "not-found");
        Set<String> missing = new TreeSet<>(emitted);
        missing.removeAll(catalog);
        assertThat(missing).as("códigos emitidos sem explicação em content/help/errors/").isEmpty();
    }
}
