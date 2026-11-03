package br.com.ziipsan.commercial.fiscal.persistence;

import br.com.ziipsan.commercial.fiscal.port.HomologationXmlArea.PurgeManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Área separada da homologação (ADR-0033, ajuste v2.9): grava, purga com manifesto, sem sobrescrita. */
class FilesystemHomologationXmlAreaTest {

    @Test
    void writeAndPurgeCycle(@TempDir Path root) {
        var area = new FilesystemHomologationXmlArea(root, Clock.systemUTC());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Path written = area.write("ciclo-1", first, "<xml/>".getBytes(StandardCharsets.UTF_8));
        area.write("ciclo-1", second, "<xml/>".getBytes(StandardCharsets.UTF_8));

        assertTrue(Files.exists(written));
        assertThrows(UncheckedIOException.class,
                () -> area.write("ciclo-1", first, "<xml/>".getBytes(StandardCharsets.UTF_8)));

        PurgeManifest manifest = area.purgeCycle("ciclo-1");
        assertEquals(2, manifest.count());
        assertEquals("ciclo-1", manifest.cycle());
        assertFalse(manifest.sha256().isBlank());
        assertFalse(Files.exists(root.resolve("ciclo-1")));

        PurgeManifest again = area.purgeCycle("ciclo-1");
        assertEquals(0, again.count());
    }
}
