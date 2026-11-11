package br.com.ziipsan.app.vertical;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Teste de fronteira da P0.47 (ArchUnit da P0.3 + varredura de termos), na casa e com a
 * regra das decisoes 66 + 71 do dono (10/11/2026):
 *
 * <ul>
 *   <li><b>Casa (decisao 66):</b> o teste vive no modulo {@code app}
 *       ({@code br.com.ziipsan.app.vertical}), nao no {@code platform}. E aqui que ele
 *       pode importar o nucleo inteiro sem a aresta de teste platform -&gt; fiscal que
 *       fechava o ciclo do reactor; essa aresta saiu do pom do platform com esta
 *       mudanca (o unico consumidor real dela era este teste).</li>
 *   <li><b>Regra redefinida (decisao 71):</b> nucleo puro + excecoes listadas e
 *       justificadas uma a uma em {@code docs/arquitetura/vertical-contract.md} secao 3,
 *       que este teste le mecanicamente em cada corrida (entrada malformada = falha).
 *       O casamento e exacto por par — (ficheiro, termo) na varredura e
 *       (classe origem, classe destino) no ArchUnit — sem contagio: um ficheiro ou
 *       classe exceptuado para um par continua a falhar por qualquer outro. Fora da
 *       lista, a regra mantem a forca original integral: os mesmos pacotes, os mesmos
 *       8 termos, os mesmos 3 source roots e a mesma mensagem {@code because}.</li>
 * </ul>
 *
 * <p>Verificacoes, como no teste original da P0.47:
 * <ul>
 *   <li>O nucleo (core, platform, security) nao depende de pacote: nenhuma classe do
 *       nucleo pode depender de {@code br.com.ziipsan.commercial.fiscal} nem de qualquer
 *       pacote {@code ..sanitation..} — excepto os pares (origem, destino) da tabela
 *       3.2 do contrato, que no baseline sao todos internos ao proprio core (o catalogo
 *       de fabrica da ED-70 vive no core num pacote chamado
 *       {@code br.com.ziipsan.core.sanitation}). Qualquer linha de violacao
 *       irreconhecivel falha o teste (fail-closed por linha, R27-3A-3).</li>
 *   <li>Termos de saneamento nao aparecem no codigo do nucleo: {@code hidrometro},
 *       {@code esgoto}, {@code fatura}, {@code saneamento}, {@code agua} e
 *       {@code ligacao de agua} (e as variantes acentuadas) — excepto os pares
 *       (ficheiro, termo) da tabela 3.1 do contrato. A palavra isolada {@code ligacao}
 *       NAO e termo proibido: ela ocorre no nucleo com o sentido de vinculo — so a
 *       expressao {@code ligacao de agua} e de pacote.</li>
 * </ul>
 */
class VerticalBoundaryTest {

    private static final List<String> BANNED_TERMS = List.of(
            "hidrômetro", "hidrometro", "esgoto", "fatura", "saneamento", "água",
            "ligação de água", "ligacao de agua");

    private static final Pattern CLASS_NAME = Pattern.compile(
            "(?:[a-z_]\\w*\\.)+[A-Z]\\w*(?:\\$[A-Za-z]\\w*)*");

    private static final Pattern FQCN = Pattern.compile(
            "(?:[a-z_]\\w*\\.)+[A-Z]\\w*(?:\\$[A-Za-z]\\w*)*");

    /** Raiz do repo: mesma ancora do VerticalManifestTest do platform (que nao se move). */
    static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("packages/sanitation/vertical.json"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Raiz do repositorio nao encontrada a partir de user.dir");
    }

    @Test
    void coreDoesNotDependOnPackageCode() throws IOException {
        Set<List<String>> exceptions = archExceptions();
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.ziipsan.core", "br.com.ziipsan.platform", "br.com.ziipsan.security");
        List<String> uncovered = new ArrayList<>();
        for (String corePackage : List.of(
                "br.com.ziipsan.core..", "br.com.ziipsan.platform..", "br.com.ziipsan.security..")) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(corePackage)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "br.com.ziipsan.commercial.fiscal..", "..sanitation..")
                    .because("P0.47: o pacote depende do nucleo; o inverso e recusado (PLATAFORMA-E-VERTICAIS §6)");
            EvaluationResult result = rule.evaluate(classes);
            if (!result.hasViolation()) {
                continue;
            }
            List<String> details = new ArrayList<>();
            for (String detail : result.getFailureReport().getDetails()) {
                for (String line : detail.split("\\R")) {
                    if (!line.isBlank()) {
                        details.add(line.trim());
                    }
                }
            }
            if (details.isEmpty()) {
                fail("Violacao ArchUnit sem detalhes legiveis para a regra de " + corePackage
                        + ": nada e exceptuado sem par (origem, destino) identificavel");
            }
            for (String line : details) {
                if (!isViolationLine(line)) {
                    // R27-3A-3 (fail-closed por linha): getDetails() so devolve
                    // linhas de violacao; qualquer linha nao-vazia irreconhecivel
                    // e formato desconhecido e falha — nunca e ignorada em silencio.
                    fail("Linha de violacao ArchUnit irreconhecivel para a regra de "
                            + corePackage + " (formato inesperado; nada e exceptuado sem "
                            + "par (origem, destino) identificavel): " + line);
                }
                if (!coveredByArchException(line, exceptions)) {
                    uncovered.add(line);
                }
            }
            // Falha fechada: se a regra tem violacao mas nenhuma linha de violacao foi
            // reconhecida, o formato mudou e nada pode ser dado como exceptuado.
            boolean anyViolationLine = details.stream().anyMatch(VerticalBoundaryTest::isViolationLine);
            if (!anyViolationLine) {
                fail("Violacao ArchUnit sem nenhuma linha de violacao reconhecivel para a regra de "
                        + corePackage + ": detalhes=" + details);
            }
        }
        assertTrue(uncovered.isEmpty(),
                "Dependencias do nucleo em codigo de pacote fora das excecoes de "
                        + "vertical-contract.md secao 3.2: " + uncovered);
    }

    @Test
    void coreSourceHasNoSanitationTerms() throws IOException {
        Set<List<String>> exceptions = termExceptions();
        Path root = repoRoot();
        List<Path> sourceRoots = List.of(
                root.resolve("platform/ziipsan-platform/src/main/java"),
                root.resolve("shared/ziipsan-core/src/main/java"),
                root.resolve("shared/ziipsan-security/src/main/java"));
        List<String> violations = new ArrayList<>();
        for (Path sourceRoot : sourceRoots) {
            if (!Files.exists(sourceRoot)) {
                fail("Raiz de codigo do nucleo nao encontrada: " + sourceRoot);
            }
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String content = Files.readString(file, StandardCharsets.UTF_8).toLowerCase();
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    for (String term : BANNED_TERMS) {
                        if (content.contains(term)
                                && !exceptions.contains(List.of(relative, term))) {
                            violations.add(relative + ": termo proibido '" + term + "'");
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Termos de saneamento no nucleo fora das excecoes de vertical-contract.md secao 3.1: "
                        + violations);
    }

    // ------------------------------------------------------------------
    // Leitura mecanica das excecoes (vertical-contract.md, secao 3)
    // ------------------------------------------------------------------

    private static Set<List<String>> termExceptions() throws IOException {
        List<List<String>> rows = tableRows(contractSection(), "### 3.1", "Ficheiro");
        Set<List<String>> exceptions = new LinkedHashSet<>();
        for (List<String> row : rows) {
            String file = row.get(0);
            String term = row.get(1);
            if (!file.endsWith(".java") || file.isBlank()) {
                fail("Excecao de termos malformada em vertical-contract.md 3.1: ficheiro '" + file + "'");
            }
            if (!BANNED_TERMS.contains(term)) {
                fail("Excecao de termos malformada em vertical-contract.md 3.1: termo '" + term
                        + "' fora da lista de termos proibidos");
            }
            if (!exceptions.add(List.of(file, term))) {
                fail("Excecao de termos duplicada em vertical-contract.md 3.1: " + file + " / " + term);
            }
        }
        return exceptions;
    }

    private static Set<List<String>> archExceptions() throws IOException {
        List<List<String>> rows = tableRows(contractSection(), "### 3.2", "Classe origem");
        Set<List<String>> exceptions = new LinkedHashSet<>();
        for (List<String> row : rows) {
            String origin = row.get(0);
            String target = row.get(1);
            if (!FQCN.matcher(origin).matches() || !FQCN.matcher(target).matches()) {
                fail("Excecao ArchUnit malformada em vertical-contract.md 3.2: '" + origin
                        + "' -> '" + target + "'");
            }
            if (!exceptions.add(List.of(origin, target))) {
                fail("Excecao ArchUnit duplicada em vertical-contract.md 3.2: " + origin + " -> " + target);
            }
        }
        return exceptions;
    }

    /** Texto da secao 3 do contrato (de "## 3." ate "## 4."). */
    private static String contractSection() throws IOException {
        String contract = Files.readString(
                repoRoot().resolve("docs/arquitetura/vertical-contract.md"), StandardCharsets.UTF_8);
        int start = contract.indexOf("\n## 3.");
        int end = contract.indexOf("\n## 4.", start < 0 ? 0 : start);
        if (start < 0 || end < 0) {
            fail("vertical-contract.md sem secao 3 ou sem secao 4: as excecoes da fronteira sao ilegíveis");
        }
        return contract.substring(start, end);
    }

    /**
     * Linhas de dados da tabela Markdown sob o marcador dado, dentro da secao 3.
     * Cabecalho e separador sao validados pela primeira celula; qualquer linha de
     * dados sem exactamente 3 colunas nao vazias falha o teste (entrada malformada).
     */
    private static List<List<String>> tableRows(String section, String marker, String firstHeader) {
        int from = section.indexOf(marker);
        if (from < 0) {
            fail("vertical-contract.md sem a subsecao " + marker + ": excecoes ilegíveis");
        }
        int next = section.indexOf("\n### ", from + marker.length());
        String sub = next < 0 ? section.substring(from) : section.substring(from, next);
        List<List<String>> rows = new ArrayList<>();
        for (String line : sub.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("|")) {
                continue;
            }
            String[] parts = trimmed.split("\\|", -1);
            // Linha de tabela bem formada: "" | c1 | c2 | c3 | ""
            if (parts.length != 5) {
                fail("Linha de tabela malformada em vertical-contract.md " + marker
                        + " (esperadas 3 colunas): " + trimmed);
            }
            List<String> cells = List.of(parts[1].trim(), parts[2].trim(), parts[3].trim());
            if (cells.get(0).equals(firstHeader)) {
                continue;
            }
            if (cells.stream().allMatch(c -> c.matches(":?-{3,}:?"))) {
                continue;
            }
            if (cells.stream().anyMatch(String::isBlank)) {
                fail("Excecao com celula vazia em vertical-contract.md " + marker + ": " + trimmed);
            }
            rows.add(cells);
        }
        if (rows.isEmpty()) {
            fail("Tabela de excecoes vazia em vertical-contract.md " + marker);
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Cobertura ArchUnit por par exacto (origem, destino)
    // ------------------------------------------------------------------

    private static boolean isViolationLine(String line) {
        return line.startsWith("Constructor <") || line.startsWith("Field <")
                || line.startsWith("Method <") || line.startsWith("Static Initializer <")
                || line.startsWith("Class <") || line.startsWith("Interface <");
    }

    /**
     * Uma linha de violacao esta coberta se, e somente se, TODAS as classes de pacote
     * (fiscal ou sanitation) nomeadas depois da classe origem tiverem par exacto
     * (origem, destino) na tabela 3.2. A primeira classe nomeada na linha e a dona do
     * membro origem; um destino fora da lista — mesmo a partir de uma origem
     * exceptuada para outro destino — deixa a linha descoberta (sem contagio).
     */
    private static boolean coveredByArchException(String line, Set<List<String>> exceptions) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = CLASS_NAME.matcher(line);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        if (tokens.isEmpty()) {
            return false;
        }
        String origin = tokens.get(0);
        for (int i = 1; i < tokens.size(); i++) {
            String target = tokens.get(i);
            if (isPackageClass(target) && !exceptions.contains(List.of(origin, target))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPackageClass(String className) {
        int dollar = className.indexOf('$');
        String outer = dollar < 0 ? className : className.substring(0, dollar);
        int lastDot = outer.lastIndexOf('.');
        if (lastDot < 0) {
            return false;
        }
        String pkg = outer.substring(0, lastDot);
        return pkg.equals("br.com.ziipsan.commercial.fiscal")
                || pkg.startsWith("br.com.ziipsan.commercial.fiscal.")
                || List.of(pkg.split("\\.")).contains("sanitation");
    }
}
