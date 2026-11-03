package br.com.ziipsan.platform.vertical;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Teste de fronteira da P0.47 (ArchUnit da P0.3 + varredura de termos):
 *
 * <ul>
 *   <li>O núcleo (core, platform, security) não depende de pacote: nenhuma classe do núcleo
 *       pode depender de {@code br.com.ziipsan.commercial.fiscal} (o pacote de saneamento
 *       em código hoje) nem de qualquer pacote {@code ..sanitation..} futuro.</li>
 *   <li>Termos de saneamento não aparecem no código do núcleo: {@code hidrômetro},
 *       {@code esgoto}, {@code fatura}, {@code saneamento}, {@code água} e
 *       {@code ligação de água}. A palavra isolada {@code ligação} NÃO é termo proibido:
 *       ela ocorre no núcleo com o sentido de vínculo (ex.: JwtTenantFilter, "sem ligação
 *       com o usuário autenticado") — só a expressão {@code ligação de água} é de pacote.</li>
 * </ul>
 *
 * <p>Lista de exceções: {@code docs/arquitetura/vertical-contract.md}. Hoje ela está vazia
 * (nenhuma ocorrência encontrada nos três módulos do núcleo); exceção nova entra lá com
 * justificativa, nunca com um termo acrescentado ao teste em silêncio.
 */
class VerticalBoundaryTest {

    private static final List<String> BANNED_TERMS = List.of(
            "hidrômetro", "hidrometro", "esgoto", "fatura", "saneamento", "água",
            "ligação de água", "ligacao de agua");

    @Test
    void coreDoesNotDependOnPackageCode() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.ziipsan.core", "br.com.ziipsan.platform", "br.com.ziipsan.security");
        for (String corePackage : List.of(
                "br.com.ziipsan.core..", "br.com.ziipsan.platform..", "br.com.ziipsan.security..")) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(corePackage)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "br.com.ziipsan.commercial.fiscal..", "..sanitation..")
                    .because("P0.47: o pacote depende do nucleo; o inverso e recusado (PLATAFORMA-E-VERTICAIS §6)");
            rule.check(classes);
        }
    }

    @Test
    void coreSourceHasNoSanitationTerms() throws IOException {
        Path root = VerticalManifestTest.repoRoot();
        List<Path> sourceRoots = List.of(
                root.resolve("platform/ziipsan-platform/src/main/java"),
                root.resolve("shared/ziipsan-core/src/main/java"),
                root.resolve("shared/ziipsan-security/src/main/java"));
        List<String> violations = new java.util.ArrayList<>();
        for (Path sourceRoot : sourceRoots) {
            if (!Files.exists(sourceRoot)) {
                fail("Raiz de codigo do nucleo nao encontrada: " + sourceRoot);
            }
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String content = Files.readString(file, StandardCharsets.UTF_8).toLowerCase();
                    for (String term : BANNED_TERMS) {
                        if (content.contains(term)) {
                            violations.add(file + ": termo proibido '" + term + "'");
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Termos de saneamento no nucleo (ver lista de excecoes em vertical-contract.md): " + violations);
    }
}
