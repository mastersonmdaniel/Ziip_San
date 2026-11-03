package br.com.ziipsan.commercial.fiscal.persistence;

import br.com.ziipsan.commercial.fiscal.application.FiscalIssuanceService;
import br.com.ziipsan.commercial.fiscal.port.HomologationXmlArea;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Área separada da homologação fiscal local, em filesystem (ADR-0033, ajuste v2.9).
 *
 * <p>O diretório raiz é configurado pelo operador, fora do repositório, fora das tabelas do
 * produto, fora do armazenamento imutável e fora dos backups locais. Cada ciclo tem seu
 * subdiretório; um documento nunca sobrescreve outro (CREATE_NEW) e a purga apaga o ciclo
 * inteiro, devolvendo o manifesto — sem manifesto, não há prova da exclusão.
 */
public final class FilesystemHomologationXmlArea implements HomologationXmlArea {

    private final Path root;
    private final Clock clock;

    public FilesystemHomologationXmlArea(Path root, Clock clock) {
        this.root = root;
        this.clock = clock;
    }

    @Override
    public Path write(String cycle, UUID documentId, byte[] signedXml) {
        Path directory = cycleDirectory(cycle);
        Path file = directory.resolve(documentId + ".xml");
        try {
            Files.createDirectories(directory);
            return Files.write(file, signedXml, java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public PurgeManifest purgeCycle(String cycle) {
        Path directory = cycleDirectory(cycle);
        try {
            if (!Files.isDirectory(directory)) {
                // Purga idempotente: ciclo inexistente não é erro, e o manifesto registra zero.
                return new PurgeManifest(cycle, clock.instant().atOffset(java.time.ZoneOffset.UTC), 0,
                        FiscalIssuanceService.sha256Hex(new byte[0]));
            }
            List<Path> files;
            try (Stream<Path> stream = Files.list(directory)) {
                files = stream.sorted().collect(Collectors.toList());
            }
            StringBuilder manifest = new StringBuilder();
            for (Path file : files) {
                byte[] content = Files.readAllBytes(file);
                manifest.append(file.getFileName()).append(':')
                        .append(FiscalIssuanceService.sha256Hex(content)).append('\n');
                Files.delete(file);
            }
            Files.delete(directory);
            return new PurgeManifest(cycle, clock.instant().atOffset(java.time.ZoneOffset.UTC), files.size(),
                    FiscalIssuanceService.sha256Hex(manifest.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path cycleDirectory(String cycle) {
        if (cycle == null || !cycle.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("O nome do ciclo e invalido: " + cycle);
        }
        return root.resolve(cycle);
    }
}
