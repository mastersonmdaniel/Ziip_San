package br.com.ziipsan.operations.asset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda de arquitetura da P1.35 (plano v3.0, linha P1.35: "inclusao e
 * alteracao so por OS interna da P1.31 (teste de arquitetura recusa
 * escrita direta)").
 *
 * <p>A escrita na tabela {@code asset} vive num unico lugar,
 * {@link AssetService}, e os dois caminhos de escrita dele
 * ({@code createAsset}/{@code updateAsset}) passam pela exigencia de OS
 * interna ({@code requireInternalOrder}). Nenhuma outra classe do modulo
 * — nem o controlador, nem os verificadores de alvo da P1.31, que so
 * leem — pode inserir, atualizar ou apagar na tabela. A recusa em tempo
 * de execucao (servico) e no banco (proveniencia NOT NULL com FK para
 * {@code service_orders}, na V27_1) e provada pelo AssetRegistryIT; esta
 * guarda impede que um caminho novo de escrita apareca sem passar pelo
 * servico.
 *
 * <p>Varredura de fontes, no mesmo desenho do ModuleBoundaryTest da app:
 * o SQL e montado por concatenacao de String, e o ArchUnit nao enxerga
 * literais SQL — por isso a guarda le o fonte, e nao o bytecode.
 */
class AssetArchitectureTest {

    @Test
    @DisplayName("So o AssetService escreve na tabela asset; nenhum outro fonte do modulo escreve")
    void onlyAssetServiceWritesToAssetTable() throws IOException {
        Path main = moduleMainSources();
        List<String> writers = new ArrayList<>();
        try (Stream<Path> files = Files.walk(main)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (file.getFileName().toString().equals("AssetService.java")) {
                    continue;
                }
                if (writesToAssetTable(source)) {
                    writers.add(file.getFileName().toString());
                }
            }
        }
        assertThat(writers)
                .as("fontes fora do AssetService que escrevem na tabela asset (P1.35: escrita so por OS interna, via AssetService)")
                .isEmpty();
    }

    @Test
    @DisplayName("Os dois caminhos de escrita do AssetService exigem OS interna")
    void bothWritePathsRequireInternalOrder() throws IOException {
        String service = Files.readString(
                moduleMainSources().resolve("br/com/ziipsan/operations/asset/AssetService.java"),
                StandardCharsets.UTF_8);
        long gates = service.lines()
                .filter(line -> line.contains("requireInternalOrder(connection, orderId)"))
                .count();
        assertThat(gates)
                .as("chamadas a requireInternalOrder nos caminhos de escrita (create + update)")
                .isEqualTo(2);
    }

    /** INSERT/UPDATE/DELETE dirigidos a tabela asset (o SELECT dos verificadores nao conta). */
    private static boolean writesToAssetTable(String source) {
        boolean mentionsAssetTable = source.contains("t(\"asset\")")
                || source.contains(".asset\"")
                || source.contains(".asset ");
        if (!mentionsAssetTable) {
            return false;
        }
        return source.contains("INSERT INTO") || source.contains("UPDATE ")
                || source.contains("DELETE FROM");
    }

    private static Path moduleMainSources() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path main = dir.resolve("operations/ziipsan-asset/src/main/java");
            if (Files.isDirectory(main)) {
                return main;
            }
            Path direct = dir.resolve("src/main/java");
            if (Files.isDirectory(direct)
                    && Files.exists(dir.resolve("pom.xml"))
                    && readQuietly(dir.resolve("pom.xml")).contains("<artifactId>ziipsan-asset</artifactId>")) {
                return direct;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("fontes do modulo ziipsan-asset nao encontradas a partir de user.dir");
    }

    private static String readQuietly(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
