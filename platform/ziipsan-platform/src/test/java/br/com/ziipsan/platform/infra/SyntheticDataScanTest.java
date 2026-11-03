package br.com.ziipsan.platform.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.45: teste de varredura — dado pessoal real nao entra na base local. Regra:
 * qualquer documento com formato de CPF/CNPJ num arquivo que alimenta o banco local
 * (migrations e init do PostgreSQL) precisa estar na mesma linha que a marca
 * {@code SINTETICO} (ou o arquivo inteiro marcado no cabecalho). Sem a marca, a
 * carga e recusada aqui, antes de qualquer teste de integracao. Documentos de
 * orgaos publicos em docs/ (ex.: catalogo de normas) nao alimentam o banco e nao
 * entram neste escopo — o roteiro de anonimizacao (docs/seguranca/...) trata deles
 * separadamente, e nenhum deles e dado de cliente.
 */
class SyntheticDataScanTest {

    /** CPF formatado, CNPJ formatado, ou documento so com digitos em literal SQL. */
    static final Pattern DOCUMENT = Pattern.compile(
            "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}");

    static boolean lineIsClean(String line, boolean fileMarked) {
        if (fileMarked || line.contains("SINTETICO")) {
            return true;
        }
        return !DOCUMENT.matcher(line).find();
    }

    @Test
    @DisplayName("a regra aceita documento marcado SINTETICO e recusa sem marca")
    void ruleAcceptsMarkedRejectsUnmarked() {
        assertThat(lineIsClean("'111.222.333-44' -- SINTETICO", false)).isTrue();
        assertThat(lineIsClean("-- SINTETICO: carga de teste", true)).isTrue();
        assertThat(lineIsClean("'111.222.333-44'", false)).isFalse();
        assertThat(lineIsClean("'11.222.333/0001-44'", false)).isFalse();
        assertThat(lineIsClean("nenhum documento aqui", false)).isTrue();
    }

    @Test
    @DisplayName("nenhuma migration ou init do banco local traz documento sem a marca SINTETICO")
    void noUnmarkedDocumentInDbSources() throws IOException {
        Path repo = ProviderBoundaryTest.repoRoot();
        List<String> violations = new ArrayList<>();
        List<Path> roots = new ArrayList<>();
        // Migrations vivem por modulo (ex.: commercial/ziipsan-registry/src/main/...),
        // entao procura-se o diretorio db/migration dentro de cada modulo conhecido.
        for (String module : List.of("commercial", "operations", "intelligence", "grs",
                "compliance", "platform", "shared")) {
            Path base = repo.resolve(module);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(base)) {
                walk.filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().equals("migration")
                                && p.getParent() != null
                                && p.getParent().getFileName().toString().equals("db"))
                        .forEach(roots::add);
            }
        }
        Path init = repo.resolve("infra/postgres/init");
        if (Files.isDirectory(init)) {
            roots.add(init);
        }
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".sql")).forEach(p -> {
                    try {
                        List<String> lines = Files.readAllLines(p);
                        boolean fileMarked = lines.stream().limit(10).anyMatch(l -> l.contains("SINTETICO"));
                        for (int i = 0; i < lines.size(); i++) {
                            if (!lineIsClean(lines.get(i), fileMarked)) {
                                violations.add(p + ":" + (i + 1) + ": " + lines.get(i).strip());
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        assertThat(violations).as("documentos sem marca SINTETICO em fonte de banco local").isEmpty();
    }
}
