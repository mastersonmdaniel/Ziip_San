package br.com.ziipsan.commercial.fiscal.errors;

import br.com.ziipsan.commercial.fiscal.nfage.NfageRejectionCode;
import br.com.ziipsan.commercial.fiscal.nfse.NfseRejectionCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobertura do aceite da P2.16 (ADR-0020): todo código de rejeição da NFAg-e
 * ({@link NfageRejectionCode}) e da NFS-e ({@link NfseRejectionCode}) tem
 * explicação no catálogo de erros da P1.22 — os arquivos de
 * {@code content/help/errors} que o gerador da P1.21 publica em
 * {@code support.error_explanation} (chaves {@code nfage:<cStat>} e
 * {@code nfse:<código>}, formato da V9_3).
 *
 * <p>O teste lê os arquivos reais do repositório: código novo que entre na
 * fonte versionada (os enums) sem arquivo de explicação reprova aqui — não é
 * uma varredura vazia: ele também reprova explicação órfã (arquivo fiscal sem
 * código na fonte), arquivo com {@code code} divergente do nome, e explicação
 * sem título, sem texto ou sem seção de causas/ações.
 *
 * <p><b>Fechamento pós-revisão (M3):</b> a primeira versão deste teste não
 * olhava a {@code officialMessage} — foi por essa brecha que a mensagem
 * errada do código 215 (duplicada do 214) passou. Agora o teste, para cada
 * código, (a) exige {@code officialMessage} não vazia e coerente com a
 * explicação (algum termo significativo da mensagem oficial aparece no
 * arquivo), (b) reprova código duplicado e mensagem oficial duplicada
 * dentro do mesmo enum, e (c) confere o {@code title:} do frontmatter de
 * cada arquivo (presente, não vazio e igual ao título de nível 1).
 */
class FiscalRejectionCatalogCoverageTest {

    /** Localiza {@code content/help/errors} subindo a partir do diretório de trabalho do teste. */
    private static Path errorsDir() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("content/help/errors");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new AssertionError("content/help/errors não encontrado a partir de "
                + Path.of("").toAbsolutePath()
                + " — o teste de cobertura da P2.16 exige o repositório completo (ausência = erro explícito)");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new AssertionError("Falha ao ler " + file + ": " + e.getMessage(), e);
        }
    }

    /** Minúsculas sem diacríticos, para comparar a mensagem oficial com o texto da explicação. */
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /** Termos significativos (≥ 4 letras) da mensagem oficial. */
    private static List<String> significantTerms(String officialMessage) {
        List<String> terms = new ArrayList<>();
        for (String word : normalize(officialMessage).split("[^a-z0-9]+")) {
            if (word.length() >= 4) {
                terms.add(word);
            }
        }
        return terms;
    }

    /** Valor de um campo simples do frontmatter YAML ({@code chave: "valor"}), ou {@code null}. */
    private static String frontmatterField(String content, String field) {
        String[] lines = content.split("\n");
        if (lines.length == 0 || !lines[0].strip().equals("---")) {
            return null;
        }
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.equals("---")) {
                return null;
            }
            if (line.startsWith(field + ": ")) {
                String value = line.substring(field.length() + 2).strip();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    /** Título de nível 1 ({@code # ...}) do corpo, ou {@code null}. */
    private static String h1Title(String content) {
        for (String line : content.split("\n")) {
            if (line.startsWith("# ")) {
                return line.substring(2).strip();
            }
        }
        return null;
    }

    private static void assertExplanation(Path errors, String prefix, String code, String catalogCode,
                                          String officialMessage) {
        Path file = errors.resolve(prefix + "-" + code + ".md");
        assertThat(file)
                .as("explicação do catálogo para %s (arquivo %s)", catalogCode, file.getFileName())
                .exists();
        String content = read(file);
        assertThat(content)
                .as("arquivo %s declara o code exato do catálogo", file.getFileName())
                .contains("code: \"" + catalogCode + "\"");
        assertThat(content)
                .as("arquivo %s tem título (cabeçalho de nível 1)", file.getFileName())
                .contains("\n# ");
        assertThat(content)
                .as("arquivo %s explica causas e ações (padrão do catálogo da P1.22)", file.getFileName())
                .contains("## Causas prováveis")
                .contains("## O que fazer");
        // Texto explicativo não vazio entre o título e as causas.
        String body = content.substring(content.indexOf("\n# ") + 3, content.indexOf("## Causas prováveis"));
        assertThat(body.replaceAll("[#\\s]", "").length())
                .as("arquivo %s tem explicação com texto próprio", file.getFileName())
                .isGreaterThan(40);

        // (c) title: do frontmatter presente, não vazio e igual ao H1.
        String frontmatterTitle = frontmatterField(content, "title");
        assertThat(frontmatterTitle)
                .as("arquivo %s tem title: não vazio no frontmatter", file.getFileName())
                .isNotNull()
                .isNotBlank();
        assertThat(h1Title(content))
                .as("arquivo %s tem H1 igual ao title: do frontmatter", file.getFileName())
                .isEqualTo(frontmatterTitle);

        // (a) officialMessage da fonte: não vazia e coerente com a explicação.
        assertThat(officialMessage)
                .as("código %s tem mensagem oficial não vazia na fonte versionada", catalogCode)
                .isNotNull()
                .isNotBlank();
        assertThat(officialMessage.strip().length())
                .as("mensagem oficial de %s é uma frase, não um rótulo curto", catalogCode)
                .isGreaterThanOrEqualTo(10);
        assertThat(officialMessage.strip())
                .as("mensagem oficial de %s não é o próprio código", catalogCode)
                .isNotEqualTo(code);
        List<String> terms = significantTerms(officialMessage);
        assertThat(terms)
                .as("mensagem oficial de %s tem termos significativos para conferir coerência", catalogCode)
                .isNotEmpty();
        String normalizedContent = normalize(content);
        assertThat(terms.stream().anyMatch(normalizedContent::contains))
                .as("explicação de %s é coerente com a mensagem oficial \"%s\" "
                        + "(algum termo significativo da mensagem aparece no arquivo)",
                        catalogCode, officialMessage)
                .isTrue();
    }

    @Test
    @DisplayName("toda rejeição da NFAg-e da fonte versionada tem explicação no catálogo (P1.22)")
    void nfageCoverage() {
        assertThat(NfageRejectionCode.values())
                .as("a fonte de rejeições da NFAg-e não pode ser vazia (varredura vazia não prova nada)")
                .isNotEmpty();
        Path errors = errorsDir();
        for (NfageRejectionCode rejection : NfageRejectionCode.values()) {
            assertExplanation(errors, "nfage", rejection.code(), rejection.catalogCode(),
                    rejection.officialMessage());
        }
    }

    @Test
    @DisplayName("toda rejeição da NFS-e da fonte versionada tem explicação no catálogo (P1.22)")
    void nfseCoverage() {
        assertThat(NfseRejectionCode.values())
                .as("a fonte de rejeições da NFS-e não pode ser vazia (varredura vazia não prova nada)")
                .isNotEmpty();
        Path errors = errorsDir();
        for (NfseRejectionCode rejection : NfseRejectionCode.values()) {
            assertExplanation(errors, "nfse", rejection.code(), rejection.catalogCode(),
                    rejection.officialMessage());
        }
    }

    @Test
    @DisplayName("nenhum código nem mensagem oficial se repete dentro do mesmo enum (regressão do 215 duplicado do 214)")
    void noDuplicateCodeOrOfficialMessageWithinAnEnum() {
        Set<String> nfageCodes = new HashSet<>();
        Set<String> nfageMessages = new HashSet<>();
        for (NfageRejectionCode rejection : NfageRejectionCode.values()) {
            assertThat(nfageCodes.add(rejection.code()))
                    .as("código duplicado no enum da NFAg-e: %s", rejection.code())
                    .isTrue();
            assertThat(nfageMessages.add(rejection.officialMessage()))
                    .as("mensagem oficial duplicada no enum da NFAg-e (o erro do 215): \"%s\"",
                            rejection.officialMessage())
                    .isTrue();
        }
        Set<String> nfseCodes = new HashSet<>();
        Set<String> nfseMessages = new HashSet<>();
        for (NfseRejectionCode rejection : NfseRejectionCode.values()) {
            assertThat(nfseCodes.add(rejection.code()))
                    .as("código duplicado no enum da NFS-e: %s", rejection.code())
                    .isTrue();
            assertThat(nfseMessages.add(rejection.officialMessage()))
                    .as("mensagem oficial duplicada no enum da NFS-e: \"%s\"", rejection.officialMessage())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("nenhum arquivo fiscal do catálogo fica órfão: todo nfage-*/nfse-* corresponde a um código da fonte")
    void noOrphanFiscalExplanations() throws IOException {
        Set<String> expected = new TreeSet<>();
        for (NfageRejectionCode r : NfageRejectionCode.values()) {
            expected.add("nfage-" + r.code() + ".md");
        }
        for (NfseRejectionCode r : NfseRejectionCode.values()) {
            expected.add("nfse-" + r.code() + ".md");
        }
        List<String> fiscalFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.list(errorsDir())) {
            stream.map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("nfage-") || name.startsWith("nfse-"))
                    .forEach(fiscalFiles::add);
        }
        assertThat(fiscalFiles)
                .as("arquivos fiscais do catálogo devem ser exatamente os códigos das fontes versionadas")
                .containsExactlyInAnyOrderElementsOf(expected);
    }
}
