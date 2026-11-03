package br.com.ziipsan.app;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suíte de fronteira de módulos da P1.13 (ArchUnit da P0.3, no CI via Surefire).
 *
 * <p>Estende a suíte de fronteira do pacote de vertical da P0.47
 * ({@code VerticalBoundaryTest}: o núcleo não depende de pacote nem carrega termo
 * de saneamento): aqui a regra é entre <b>módulos</b>, não só núcleo × pacote.
 * Camadas congeladas pelo estado atual do código, verificado por varredura de
 * imports antes de escrever as regras:
 *
 * <ul>
 *   <li>núcleo ({@code core}, {@code security}): só depende de si mesmo;</li>
 *   <li>{@code platform}: só de núcleo e de si mesmo;</li>
 *   <li>módulo de domínio: só de núcleo, de si mesmo e da própria família
 *       ({@code commercial}, {@code intelligence}, {@code operations}, {@code grs},
 *       {@code compliance}) — a dependência {@code gis → losses} e
 *       {@code losses → quality} existe hoje e é da família, não um vazamento;</li>
 *   <li>ninguém depende de {@code app}: ela é a raiz de composição, não uma
 *       biblioteca;</li>
 *   <li>nenhum nome de classe simples se repete entre {@code billing} e
 *       {@code metering}: as duplicatas citadas na P1.13 (ver P0.25) não existem
 *       hoje, e este teste falha se voltarem. A duplicata conhecida
 *       {@code DmaDistrictRepository} (gis × losses) NÃO entra aqui: ela é da
 *       P1.26, que a remove explicitamente.</li>
 * </ul>
 *
 * <p>Violação proposital: o teste {@code forbiddenDependencyFailsTheBuild}
 * aplica a mesma forma de regra a um par que existe de fato (gis → losses) e
 * exige que ela falhe com {@code AssertionError} — é a prova de que a regra
 * morde, não só de que o código atual passa. O par de controle
 * (billing → metering) passa, como deve.
 */
class ModuleBoundaryTest {

    private static final String CORE = "br.com.ziipsan.core..";
    private static final String SECURITY = "br.com.ziipsan.security..";
    private static final String PLATFORM = "br.com.ziipsan.platform..";
    private static final String APP = "br.com.ziipsan.app..";
    private static final String COMMERCIAL = "br.com.ziipsan.commercial..";
    private static final String INTELLIGENCE = "br.com.ziipsan.intelligence..";
    private static final String OPERATIONS = "br.com.ziipsan.operations..";
    private static final String GRS = "br.com.ziipsan.grs..";
    private static final String COMPLIANCE = "br.com.ziipsan.compliance..";

    private static final List<String> ALL_FAMILIES = List.of(
            CORE, SECURITY, PLATFORM, APP, COMMERCIAL, INTELLIGENCE, OPERATIONS, GRS, COMPLIANCE);

    /** Módulo de domínio → família a que pertence (regras de fronteira por família). */
    private static final Map<String, String> DOMAIN_MODULES = Map.of(
            "br.com.ziipsan.commercial.registry..", COMMERCIAL,
            "br.com.ziipsan.commercial.billing..", COMMERCIAL,
            "br.com.ziipsan.commercial.collection..", COMMERCIAL,
            "br.com.ziipsan.commercial.fiscal..", COMMERCIAL,
            "br.com.ziipsan.operations.metering..", OPERATIONS,
            "br.com.ziipsan.intelligence.gis..", INTELLIGENCE,
            "br.com.ziipsan.intelligence.quality..", INTELLIGENCE,
            "br.com.ziipsan.intelligence.losses..", INTELLIGENCE,
            "br.com.ziipsan.grs.mtr..", GRS,
            "br.com.ziipsan.compliance.lgpd..", COMPLIANCE);

    /** Import único por JVM de teste: reimportar em cada teste estourou a memória do fork. */
    private static final JavaClasses CLASSES = allClasses();

    private static JavaClasses allClasses() {
        return new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("br.com.ziipsan.core", "br.com.ziipsan.security",
                        "br.com.ziipsan.platform", "br.com.ziipsan.app",
                        "br.com.ziipsan.commercial", "br.com.ziipsan.operations",
                        "br.com.ziipsan.intelligence", "br.com.ziipsan.grs",
                        "br.com.ziipsan.compliance");
    }

    @Test
    @DisplayName("núcleo (core, security) só depende de si mesmo")
    void coreDependsOnlyOnItself() {
        JavaClasses classes = CLASSES;
        noClasses().that().resideInAPackage(CORE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        SECURITY, PLATFORM, APP, COMMERCIAL, INTELLIGENCE, OPERATIONS, GRS, COMPLIANCE)
                .because("P1.13: o nucleo nao depende de plataforma, app ou dominio")
                .check(classes);
        noClasses().that().resideInAPackage(SECURITY)
                .should().dependOnClassesThat().resideInAnyPackage(
                        CORE, PLATFORM, APP, COMMERCIAL, INTELLIGENCE, OPERATIONS, GRS, COMPLIANCE)
                .because("P1.13: security nao depende de core, plataforma, app ou dominio")
                .check(classes);
    }

    @Test
    @DisplayName("platform só depende do núcleo e de si mesma")
    void platformDependsOnlyOnCoreAndItself() {
        noClasses().that().resideInAPackage(PLATFORM)
                .should().dependOnClassesThat().resideInAnyPackage(
                        APP, COMMERCIAL, INTELLIGENCE, OPERATIONS, GRS, COMPLIANCE)
                .because("P1.13: platform nao depende de app nem de dominio (P0.47, estendida)")
                .check(CLASSES);
    }

    @Test
    @DisplayName("módulo de domínio só depende do núcleo, de si e da própria família")
    void domainModulesRespectFamilyBoundaries() {
        JavaClasses classes = CLASSES;
        for (Map.Entry<String, String> entry : DOMAIN_MODULES.entrySet()) {
            List<String> forbidden = ALL_FAMILIES.stream()
                    .filter(f -> !f.equals(entry.getValue()) && !f.equals(CORE) && !f.equals(SECURITY))
                    .toList();
            // A família do próprio módulo é permitida, mas o pacote exato do módulo
            // não pode ser proibido por acidente: a lista acima já exclui a família.
            noClasses().that().resideInAPackage(entry.getKey())
                    .should().dependOnClassesThat().resideInAnyPackage(forbidden.toArray(new String[0]))
                    .because("P1.13: " + entry.getKey() + " fora da familia " + entry.getValue())
                    .check(classes);
        }
    }

    @Test
    @DisplayName("nenhum módulo depende da app (raiz de composição)")
    void nobodyDependsOnApp() {
        JavaClasses classes = CLASSES;
        for (String root : List.of(CORE, SECURITY, PLATFORM, COMMERCIAL, INTELLIGENCE, OPERATIONS, GRS, COMPLIANCE)) {
            noClasses().that().resideInAPackage(root)
                    .should().dependOnClassesThat().resideInAPackage(APP)
                    .because("P1.13: a app compoe os modulos; nenhum modulo a importa")
                    .check(classes);
        }
    }

    @Test
    @DisplayName("violação proposital: dependência proibida falha o build; par limpo passa")
    void forbiddenDependencyFailsTheBuild() {
        JavaClasses classes = CLASSES;
        // gis importa losses de fato (varredura de imports): a regra morde.
        assertThatThrownBy(() -> noClasses().that().resideInAPackage("br.com.ziipsan.intelligence.gis..")
                .should().dependOnClassesThat().resideInAPackage("br.com.ziipsan.intelligence.losses..")
                .check(classes))
                .isInstanceOf(AssertionError.class);
        // Controle: billing não importa metering — a mesma forma de regra passa.
        noClasses().that().resideInAPackage("br.com.ziipsan.commercial.billing..")
                .should().dependOnClassesThat().resideInAPackage("br.com.ziipsan.operations.metering..")
                .check(classes);
    }

    @Test
    @DisplayName("nenhum nome de classe simples se repete entre billing e metering")
    void noDuplicateSimpleNamesBetweenBillingAndMetering() throws IOException {
        Path root = repoRoot();
        Set<String> billing = simpleNames(root.resolve("commercial/ziipsan-billing/src/main/java"));
        Set<String> metering = simpleNames(root.resolve("operations/ziipsan-metering/src/main/java"));
        Set<String> duplicates = new HashSet<>(billing);
        duplicates.retainAll(metering);
        assertThat(duplicates).as("duplicatas billing x metering (P1.13, ver P0.25)").isEmpty();
    }

    private static Set<String> simpleNames(Path moduleRoot) throws IOException {
        Set<String> names = new HashSet<>();
        try (Stream<Path> files = Files.walk(moduleRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                names.add(file.getFileName().toString());
            }
        }
        return names;
    }

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
        throw new IllegalStateException("raiz do repositorio (pom ziipsan) nao encontrada");
    }
}
