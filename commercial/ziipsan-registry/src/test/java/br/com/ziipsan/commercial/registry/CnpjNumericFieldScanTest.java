package br.com.ziipsan.commercial.registry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.39: nenhuma coluna, DTO ou esquema de API trata CNPJ como número. Um CNPJ alfanumérico
 * (12 posições alfanuméricas + 2 DVs) não cabe em tipo numérico: qualquer campo {@code cnpj}
 * declarado como número voltaria a recusar letras em alguma fronteira.
 *
 * <p>Varredura de código-fonte (sem banco): declarações Java em que o identificador contém
 * {@code cnpj} e o tipo é numérico, colunas SQL de migration com nome {@code cnpj} e tipo
 * numérico, e campos TypeScript {@code cnpj: number}. Comentários são removidos antes da
 * busca. Os pesos do cálculo do DV ({@code CNPJ_WEIGHTS_*}, arrays de {@code int}) não são
 * campos de CNPJ e não casam com o padrão de declaração.
 *
 * <p>Escopo: cadastro (registry), núcleo (core, onde vive {@code DocumentNumber}) e app — os
 * módulos desta frente — mais o frontend. Módulos de outras frentes (fiscal, cobrança) têm
 * seus próprios testes de fronteira (P2.6, P2.13, P3.1, P3.2) e não são varridos aqui: um
 * identificador de leiaute legado (ex.: raiz numérica do CNPJ no código de barras FEBRABAN)
 * não é um campo de CNPJ da API.
 */
class CnpjNumericFieldScanTest {

    /** Declaração Java: tipo numérico seguido de identificador que contém "cnpj" (não método: o nome não é seguido de parêntese). */
    private static final Pattern JAVA_NUMERIC_CNPJ =
            Pattern.compile("(?i)\\b(long|int|integer|double|bigdecimal)\\s+\\w*cnpj\\w*\\b(?!\\s*\\()");
    /** Coluna SQL: nome cnpj seguido de tipo numérico. */
    private static final Pattern SQL_NUMERIC_CNPJ =
            Pattern.compile("(?i)\\bcnpj\\w*\\s+(bigint|integer|int|smallint|numeric|decimal|double precision|real)\\b");
    /** Campo TypeScript: cnpj tipado como number. */
    private static final Pattern TS_NUMERIC_CNPJ =
            Pattern.compile("\\bcnpj\\s*\\?\\s*:\\s*number\\b");

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
        throw new IllegalStateException("raiz do repositório (pom ziipsan) não encontrada");
    }

    private static List<Path> files(Path root, String subdir, String suffix) throws IOException {
        Path base = root.resolve(subdir);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(base)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(suffix))
                    .filter(p -> !p.toString().contains("/target/"))
                    .toList();
        }
    }

    private static String code(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    @Test
    @DisplayName("a varredura enxerga arquivos (não passa por estar vazia)")
    void scansSomething() throws IOException {
        Path root = repoRoot();
        int java = files(root, "commercial/ziipsan-registry/src/main/java", ".java").size()
                + files(root, "shared/ziipsan-core/src/main/java", ".java").size()
                + files(root, "platform/ziipsan-app/src/main/java", ".java").size();
        int ts = files(root, "frontend/src", ".ts").size()
                + files(root, "frontend/src", ".tsx").size();
        assertThat(java).as("fontes Java no escopo").isGreaterThan(50);
        assertThat(ts).as("fontes TypeScript no escopo").isGreaterThan(50);
    }

    @Test
    @DisplayName("nenhum campo CNPJ declarado com tipo numérico em Java, SQL ou TypeScript")
    void noNumericCnpjField() throws IOException {
        Path root = repoRoot();
        List<String> violations = new ArrayList<>();
        for (Path f : files(root, "commercial/ziipsan-registry/src/main/java", ".java")) {
            if (JAVA_NUMERIC_CNPJ.matcher(code(f)).find()) {
                violations.add(f + ": campo CNPJ com tipo numérico (Java)");
            }
        }
        for (Path f : files(root, "shared/ziipsan-core/src/main/java", ".java")) {
            if (JAVA_NUMERIC_CNPJ.matcher(code(f)).find()) {
                violations.add(f + ": campo CNPJ com tipo numérico (Java)");
            }
        }
        for (Path f : files(root, "platform/ziipsan-app/src/main/java", ".java")) {
            if (JAVA_NUMERIC_CNPJ.matcher(code(f)).find()) {
                violations.add(f + ": campo CNPJ com tipo numérico (Java)");
            }
        }
        for (Path f : files(root, "commercial/ziipsan-registry/src/main/resources/db/migration", ".sql")) {
            if (SQL_NUMERIC_CNPJ.matcher(code(f)).find()) {
                violations.add(f + ": coluna CNPJ com tipo numérico (SQL)");
            }
        }
        List<Path> ts = new ArrayList<>();
        ts.addAll(files(root, "frontend/src", ".ts"));
        ts.addAll(files(root, "frontend/src", ".tsx"));
        for (Path f : ts) {
            if (TS_NUMERIC_CNPJ.matcher(code(f)).find()) {
                violations.add(f + ": campo cnpj tipado como number (TypeScript)");
            }
        }
        assertThat(violations).as("campos CNPJ com tipo numérico").isEmpty();
    }
}
