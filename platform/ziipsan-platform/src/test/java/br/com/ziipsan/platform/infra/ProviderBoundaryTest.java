package br.com.ziipsan.platform.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.45: teste de fronteira — nenhum modulo de dominio (commercial, operations,
 * intelligence, grs, compliance, platform, shared) pode importar SDK de provedor
 * de nuvem. A infra de S3 desta frente usa HttpClient do JDK + SigV4 proprio, de
 * proposito: o SDK nao entra nem na infra, quanto mais no dominio. O teste varre o
 * fonte (nao o bytecode), para falhar tambem quando o SDK entra por import nao
 * usado. Pastas de teste e de frontend nao entram no escopo.
 */
class ProviderBoundaryTest {

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "software.amazon.awssdk", "com.amazonaws", "com.google.cloud", "com.azure",
            "com.amazonaws.services", "io.minio");

    @Test
    @DisplayName("nenhum fonte de dominio importa SDK de provedor de nuvem")
    void noProviderSdkInDomainSources() throws IOException {
        Path repo = repoRoot();
        List<String> violations = new ArrayList<>();
        List<String> moduleRoots = List.of("commercial", "operations", "intelligence", "grs",
                "compliance", "platform", "shared");
        for (String module : moduleRoots) {
            Path base = repo.resolve(module);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(base)) {
                files.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> p.toString().contains("/src/main/java/"))
                        .forEach(p -> {
                            try {
                                for (String line : Files.readAllLines(p)) {
                                    String trimmed = line.strip();
                                    if (trimmed.startsWith("import ")) {
                                        for (String forbidden : FORBIDDEN_IMPORTS) {
                                            if (trimmed.contains(forbidden)) {
                                                violations.add(p + ": " + trimmed);
                                            }
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        }
        assertThat(violations).as("imports de SDK de provedor em codigo de dominio").isEmpty();
    }

    static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            if (Files.exists(dir.resolve("docker-compose.yml")) && Files.exists(dir.resolve("pom.xml"))) {
                return dir;
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        throw new IllegalStateException("raiz do repositorio nao encontrada a partir de " + Path.of("").toAbsolutePath());
    }
}
