package br.com.ziipsan.core.certification;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.32 boundary + pure-logic tests (no database: the live cycle is proven
 * by CertificationCoreIT in the app module).
 *
 * <p>Boundary, in the P0.47 sense (VerticalBoundaryTest): the certification
 * core must not depend on any vertical package and must not carry a sector
 * term. The scan below walks the core certification sources themselves;
 * the negative control proves the predicate bites by feeding it a string
 * that carries a banned term and requiring a hit.
 */
class CertificationBoundaryTest {

    private static final List<String> BANNED_TERMS = List.of(
            "hidrômetro", "hidrometro", "esgoto", "fatura", "saneamento", "água",
            "ligação de água", "ligacao de agua");

    private static boolean carriesBannedTerm(String content) {
        String lower = content.toLowerCase();
        return BANNED_TERMS.stream().anyMatch(lower::contains);
    }

    @Test
    @DisplayName("o núcleo de certificação não carrega termo de pacote nem importa pacote")
    void coreCertificationSourcesCarryNoPackageTerms() throws IOException {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path pkg = root.resolve("src/main/java/br/com/ziipsan/core/certification");
        assertThat(pkg).as("pacote do núcleo de certificação existe").isDirectory();
        List<String> violations = new java.util.ArrayList<>();
        try (Stream<Path> files = Files.walk(pkg)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (carriesBannedTerm(content)) {
                    violations.add(file + ": termo proibido");
                }
                if (content.contains("br.com.ziipsan.commercial")
                        || content.contains("..sanitation..")
                        || content.contains("packages/sanitation")) {
                    violations.add(file + ": dependência de pacote");
                }
            }
        }
        assertThat(violations).as("termos de pacote ou dependências no núcleo").isEmpty();
    }

    @Test
    @DisplayName("o predicado de termo proibido morde (controle negativo)")
    void bannedTermPredicateBites() {
        assertThat(carriesBannedTerm("relatório de esgoto do mês")).isTrue();
        assertThat(carriesBannedTerm("mapa de cláusulas em palavras próprias")).isFalse();
    }

    @Test
    @DisplayName("as quatro classes do catálogo são as únicas aceitas")
    void standardClassesAreExactlyFour() {
        assertThat(CertificationService.StandardClass.from("REGULATORY"))
                .isEqualTo(CertificationService.StandardClass.REGULATORY);
        assertThat(CertificationService.StandardClass.from("VOLUNTARY"))
                .isEqualTo(CertificationService.StandardClass.VOLUNTARY);
        assertThat(CertificationService.StandardClass.from("REFERENCE"))
                .isEqualTo(CertificationService.StandardClass.REFERENCE);
        assertThat(CertificationService.StandardClass.from("EXPORT_PROFILE"))
                .isEqualTo(CertificationService.StandardClass.EXPORT_PROFILE);
        assertThatThrownBy(() -> CertificationService.StandardClass.from("OBRIGATORIA"))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> CertificationService.StandardClass.from(null))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("os tipos de evidência são fechados")
    void evidenceKindsAreClosed() {
        assertThat(CertificationService.EvidenceKind.from("DOCUMENT"))
                .isEqualTo(CertificationService.EvidenceKind.DOCUMENT);
        assertThatThrownBy(() -> CertificationService.EvidenceKind.from("PLANILHA"))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("o hash de aceite é determinístico e sensível ao conteúdo aceito")
    void acceptanceHashIsDeterministicAndContentSensitive() {
        UUID tenant = UUID.randomUUID();
        UUID activation = UUID.randomUUID();
        UUID accepter = UUID.randomUUID();
        String hash = CertificationService.acceptanceHash(tenant, activation, "SYN-1", "TESTE-1", "Escopo X", accepter);
        assertThat(CertificationService.acceptanceHash(tenant, activation, "SYN-1", "TESTE-1", "Escopo X", accepter))
                .isEqualTo(hash);
        assertThat(CertificationService.acceptanceHash(tenant, activation, "SYN-1", "TESTE-1", "Escopo Y", accepter))
                .isNotEqualTo(hash);
        assertThat(CertificationService.acceptanceHash(tenant, activation, "SYN-2", "TESTE-1", "Escopo X", accepter))
                .isNotEqualTo(hash);
        assertThat(hash).hasSize(64);
    }

    @Test
    @DisplayName("o aviso fixo diz que o módulo não certifica")
    void disclaimerStatesTheModuleDoesNotCertify() {
        assertThat(CertificationService.DISCLAIMER).contains("não certifica");
        assertThat(CertificationService.DISCLAIMER).contains("organismo");
        assertThat(CertificationService.DISCLAIMER).contains("autoavaliação");
    }
}
