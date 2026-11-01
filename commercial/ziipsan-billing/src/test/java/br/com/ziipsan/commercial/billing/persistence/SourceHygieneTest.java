package br.com.ziipsan.commercial.billing.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas de código-fonte contra as armadilhas da seção 7 do relatório 02 que são de estilo, não de
 * cálculo: o código de produção do módulo não usa ponto flutuante, não constrói {@code BigDecimal}
 * a partir de {@code double}, não imprime dado em log de console e não engole exceção.
 *
 * <p>Os comentários são removidos antes da busca (eles falam dessas coisas justamente para avisar).
 */
class SourceHygieneTest {

    private static final Path MAIN = Paths.get("src/main/java");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> walk = Files.walk(MAIN)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String code(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static List<String> offenders(String regex) throws IOException {
        List<String> found = new ArrayList<>();
        for (Path p : sources()) {
            if (java.util.regex.Pattern.compile(regex).matcher(code(p)).find()) {
                found.add(p.getFileName().toString());
            }
        }
        return found;
    }

    @Test
    @DisplayName("há fontes para varrer (o teste não passa por estar vazio)")
    void scansSomething() throws IOException {
        assertThat(sources().size()).isGreaterThan(30);
    }

    @Test
    @DisplayName("armadilha 1: nenhum new BigDecimal(<double>) nem BigDecimal.valueOf(<double>) no código de produção")
    void noBigDecimalFromDouble() throws IOException {
        assertThat(offenders("new\\s+BigDecimal\\(\\s*-?\\d*\\.\\d")).as("new BigDecimal(literal decimal)").isEmpty();
        assertThat(offenders("BigDecimal\\.valueOf\\(\\s*-?\\d*\\.\\d")).as("BigDecimal.valueOf(literal decimal)").isEmpty();
    }

    @Test
    @DisplayName("nenhum tipo double/float nem Math.round no código de produção (dinheiro e percentual são BigDecimal)")
    void noFloatingPoint() throws IOException {
        assertThat(offenders("\\b(double|float|Double|Float)\\b")).isEmpty();
        assertThat(offenders("Math\\.round\\(")).isEmpty();
    }

    @Test
    @DisplayName("armadilha 10: nada de System.out/err nem printStackTrace (o legado vazava dado de cliente em log de cálculo)")
    void noConsoleOutput() throws IOException {
        assertThat(offenders("System\\.(out|err)")).isEmpty();
        assertThat(offenders("printStackTrace")).isEmpty();
    }

    @Test
    @DisplayName("armadilha 11: nenhum catch vazio (o legado engolia exceção e seguia com consumo mínimo errado)")
    void noSwallowedExceptions() throws IOException {
        assertThat(offenders("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}")).isEmpty();
    }

    @Test
    @DisplayName("todo arredondamento escolhe o RoundingMode (nenhum setScale/divide de escala sem modo)")
    void noImplicitRounding() throws IOException {
        // setScale(n) sem modo e divide(x) sem escala/modo lançariam ArithmeticException ou arredondariam por padrão
        assertThat(offenders("setScale\\(\\s*\\d+\\s*\\)")).isEmpty();
        assertThat(offenders("\\.divide\\([^,()]+\\)")).isEmpty();
    }
}
