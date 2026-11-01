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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de contrato da P0.33: varre o código-fonte e falha se algum problem+json
 * for emitido com código fora da tabela, fora do formato (inglês, minúsculas com
 * hífen) ou com o prefixo/código antigo.
 *
 * <p>Não depende de banco: lê arquivos do repositório. A raiz é encontrada subindo
 * a partir do diretório de execução até o pom raiz (artifactId {@code ziipsan}).
 * Escopo: todo o repositório exceto {@code docs/}, {@code infra/_arquivado/}
 * (arquivo congelado da P0.7), {@code target/} e {@code node_modules/}.
 */
class ProblemCodeContractTest {

    /** Tabela da P0.33 (decisão do dono do produto, 12/10/2026). */
    private static final Set<String> ALLOWED = Set.of(
            "unauthenticated", "access-denied", "mfa-required",
            "tenant-missing", "tenant-inactive", "tenant-mismatch",
            "not-found", "deletion-unavailable", "stale-version", "data-conflict",
            "record-deleted", "record-already-deleted",
            "customer-has-active-link", "customer-without-document",
            "invalid-request", "search-in-url",
            "filter-unavailable", "invalid-filter",
            "invalid-sort", "invalid-page", "invalid-inscription",
            // P0.16: novos códigos dos filtros de superfície (429 do rate limit,
            // 413 do limite de corpo). Não são mudança de mapeamento de status:
            // o 422 de validação continua 422; estes são códigos novos, não
            // re-mapeados.
            "too-many-requests", "content-too-large",
            "validation", "confirmation-required", "internal-error");

    /** Códigos antigos da tabela (português) — nenhum pode restar no código. */
    private static final List<String> OLD_CODES = List.of(
            "nao-autenticado", "acesso-negado", "mfa_obrigatorio",
            "tenant-ausente", "tenant-inativo", "tenant-divergente",
            "nao-encontrado", "exclusao-indisponivel", "versao-desatualizada",
            "conflito-de-dados", "registro-excluido", "registro-ja-excluido",
            "cliente-com-vinculo-vigente", "cliente-sem-documento",
            "requisicao-invalida", "busca-na-url",
            "filtro-indisponivel", "filtro-invalido",
            "ordenacao-invalida", "pagina-invalida", "inscricao-invalida",
            "confirmacao-necessaria", "erro-interno");

    private static final Pattern URN = Pattern.compile("urn:ziipsan:problem:([A-Za-z0-9_-]+)");
    private static final Pattern FORMAT = Pattern.compile("^[a-z]+(-[a-z]+)*$");

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path pom = dir.resolve("pom.xml");
            if (Files.exists(pom)) {
                try {
                    String content = Files.readString(pom, StandardCharsets.UTF_8);
                    // A raiz é o pom agregador: artifactId próprio ziipsan + packaging pom.
                    // Só o artifactId não basta: o pom do módulo app tem ziipsan como
                    // artifactId do PARENT, e repoRoot() parava no próprio módulo app,
                    // varrendo só platform/ziipsan-app/** em vez do repositório inteiro.
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

    private static List<Path> sourceFiles(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return !s.contains("/docs/") && !s.contains("/infra/_arquivado/")
                                && !s.contains("/target/") && !s.contains("/node_modules/")
                                && !s.contains("/.git/")
                                && !s.endsWith("ProblemCodeContractTest.java")
                        && (s.endsWith(".java") || s.endsWith(".ts") || s.endsWith(".tsx"));
                    })
                    .toList();
        }
    }

    @Test
    @DisplayName("todo type urn:ziipsan:problem:<codigo> usa código da tabela, em inglês com hífen")
    void everyEmittedProblemCodeIsInTheTable() throws IOException {
        Path root = repoRoot();
        Set<String> emitted = new HashSet<>();
        List<String> violations = new java.util.ArrayList<>();
        for (Path file : sourceFiles(root)) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = URN.matcher(content);
            while (m.find()) {
                String code = m.group(1);
                emitted.add(code);
                if (!ALLOWED.contains(code)) {
                    violations.add(file + ": código fora da tabela: " + code);
                }
                if (!FORMAT.matcher(code).matches()) {
                    violations.add(file + ": código fora do formato (inglês, minúsculas com hífen): " + code);
                }
            }
            if (content.contains("urn:ziipsan:problema:") || content.contains("urn:sigsan:")) {
                violations.add(file + ": prefixo antigo de URN");
            }
        }
        assertThat(violations).as("códigos problemáticos no fonte").isEmpty();
        assertThat(emitted).as("o teste precisa enxergar códigos emitidos, senão passa no vazio")
                .contains("validation", "internal-error", "unauthenticated");
    }

    @Test
    @DisplayName("nenhum código antigo da tabela (português) resta no código-fonte")
    void noOldProblemCodeRemains() throws IOException {
        Path root = repoRoot();
        List<String> violations = new java.util.ArrayList<>();
        for (Path file : sourceFiles(root)) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String old : OLD_CODES) {
                // "validacao" sem aspas é palavra portuguesa comum em comentários; o código
                // emitido é sempre "validacao" entre aspas ou em URN — e a URN antiga já é
                // coberta pelo teste de prefixo. Aqui só contam ocorrências citadas como código.
                if (old.equals("validacao")) {
                    continue;
                }
                if (content.contains(old)) {
                    violations.add(file + ": código antigo: " + old);
                }
            }
            if (content.contains("\"validacao\"") || content.contains("problem:validacao")) {
                violations.add(file + ": código antigo citado: validacao");
            }
        }
        assertThat(violations).as("códigos antigos no fonte").isEmpty();
    }

    @Test
    @DisplayName("nenhum 'sigsan' resta no código-fonte (fora de docs e do arquivado)")
    void noSigsanRemainsInSource() throws IOException {
        Path root = repoRoot();
        List<String> violations = new java.util.ArrayList<>();
        for (Path file : sourceFiles(root)) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.toLowerCase().contains("sigsan")) {
                violations.add(file.toString());
            }
        }
        assertThat(violations).as("arquivos com 'sigsan' fora de docs/_arquivado").isEmpty();
    }
}
