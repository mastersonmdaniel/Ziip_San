package br.com.ziipsan.platform.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.12 — o teste de CI do fim do {@code outOfOrder}. Roda no
 * {@code mvn verify} (logo, no {@code scripts/ci.sh}) e recusa o build quando:
 * <ol>
 *   <li>o {@code TenantMigrator} volta a ter {@code outOfOrder(true)} (ou perde
 *   o {@code outOfOrder(false)} explícito da virada);</li>
 *   <li>existe migration de tenant versionada fora da lista de publicadas
 *   ({@code migration-policy/published-tenant-migrations.txt}) com versão
 *   menor ou igual à maior publicada, abaixo de V100, ilegível ou duplicada
 *   — a regra vive em {@link TenantMigrationVersionPolicy};</li>
 *   <li>o marco {@code V100__global_sequence_start.sql} some do repo.</li>
 * </ol>
 *
 * <p>A varredura sobe a partir do diretório de trabalho até achar a raiz do
 * repo (o ancestral que contém algum {@code src/main/resources/db/migration/tenant}),
 * no mesmo espírito dos testes de consistência de migration das outras tarefas
 * (que leem o arquivo por caminho relativo com fallback). Se nenhuma migration
 * de tenant é encontrada, o teste FALHA: sem varredura, a guarda não existe —
 * passar em silêncio seria afrouxar a asserção.
 */
class TenantMigrationGlobalSequenceCiTest {

    private static Set<String> published() throws Exception {
        try (InputStream in = TenantMigrationGlobalSequenceCiTest.class
                .getResourceAsStream("/migration-policy/published-tenant-migrations.txt")) {
            assertThat(in).as("baseline de publicadas no classpath").isNotNull();
            Set<String> names = new TreeSet<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                String name = line.trim();
                if (!name.isEmpty() && !name.startsWith("#")) {
                    names.add(name);
                }
            }
            return names;
        }
    }

    private static List<Path> tenantMigrationDirs() throws Exception {
        // A varredura tem de cobrir TODOS os módulos, não só o da platform:
        // sobe até a raiz do repo (o ancestral com pom.xml E a pasta platform)
        // e varre a árvore inteira a partir dela. Sem raiz identificável, usa
        // os diretórios de tenant achados no caminho — e a asserção de
        // não-vazio em tenantMigrationFileNames impede passar sem varrer.
        Path start = Path.of("").toAbsolutePath();
        List<Path> fallback = new ArrayList<>();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("src/main/resources/db/migration/tenant"))) {
                fallback.add(dir.resolve("src/main/resources/db/migration/tenant"));
            }
            if (Files.isDirectory(dir.resolve("platform")) && Files.exists(dir.resolve("pom.xml"))) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    List<Path> all = walk
                            .filter(p -> p.endsWith("src/main/resources/db/migration/tenant")
                                    && Files.isDirectory(p))
                            .sorted()
                            .toList();
                    if (!all.isEmpty()) {
                        return all;
                    }
                }
            }
        }
        return fallback;
    }

    private static List<String> tenantMigrationFileNames() throws Exception {
        List<Path> dirs = tenantMigrationDirs();
        assertThat(dirs).as("diretórios db/migration/tenant encontrados a partir de %s",
                Path.of("").toAbsolutePath()).isNotEmpty();
        Set<String> names = new TreeSet<>();
        for (Path dir : dirs) {
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(name -> name.endsWith(".sql"))
                        .forEach(names::add);
            }
        }
        return List.copyOf(names);
    }

    @Test
    @DisplayName("TenantMigrator sem outOfOrder(true): a virada da P3.12 está no código")
    void tenantMigratorHasOutOfOrderOff() throws Exception {
        Path source = null;
        Path start = Path.of("").toAbsolutePath();
        for (Path dir = start; dir != null && source == null; dir = dir.getParent()) {
            Path candidate = dir.resolve(
                    "platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/tenant/TenantMigrator.java");
            if (Files.exists(candidate)) {
                source = candidate;
            }
            Path local = dir.resolve(
                    "src/main/java/br/com/ziipsan/platform/tenant/TenantMigrator.java");
            if (Files.exists(local)) {
                source = local;
            }
        }
        assertThat(source).as("fonte do TenantMigrator encontrado").isNotNull();
        String code = Files.readString(source);
        assertThat(code).doesNotContain(".outOfOrder(true)");
        assertThat(code).contains(".outOfOrder(false)");
    }

    @Test
    @DisplayName("CI recusa migration de tenant nova com versão <= maior publicada ou fora da sequência global")
    void ciRefusesNewMigrationOutsideGlobalSequence() throws Exception {
        Set<String> published = published();
        List<String> all = tenantMigrationFileNames();
        assertThat(all).as("migrations de tenant varridas").isNotEmpty();
        List<String> violations = TenantMigrationVersionPolicy.checkAll(all, published);
        assertThat(violations).as("violações da sequência global da P3.12").isEmpty();
    }

    @Test
    @DisplayName("marco V100 da sequência global existe entre as migrations de tenant")
    void globalSequenceMarkerExists() throws Exception {
        assertThat(tenantMigrationFileNames()).contains("V100__global_sequence_start.sql");
        // E ele é aceito pela política contra a baseline do corte (prova de que
        // a primeira versão da sequência global passa na própria guarda).
        TenantMigrationVersionPolicy.requireNewMigrationAllowed(
                "V100__global_sequence_start.sql", published(),
                TenantMigrationVersionPolicy.maxVersion(published()));
    }

    @Test
    @DisplayName("baseline do corte: as quatro últimas faixas do corte presentes na lista viva de publicadas")
    void baselineMatchesCutover() throws Exception {
        Set<String> published = published();
        assertThat(published).contains(
                "V15_1__reporting_core_and_views.sql",
                "V6_5__payment_refunds_and_unidentified_queue.sql",
                "V1_8__org_units_and_scopes.sql",
                "V21_3__certification_governance.sql");
    }
}
