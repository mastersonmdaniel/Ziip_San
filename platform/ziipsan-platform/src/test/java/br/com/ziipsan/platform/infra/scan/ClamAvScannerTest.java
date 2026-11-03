package br.com.ziipsan.platform.infra.scan;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1.20: protocolo INSTREAM do clamd exercitado contra um stub em processo.
 * Isto prova o cliente (framing, chunking, parse de OK / FOUND / ERROR), NAO
 * um clamd real: nao ha daemon ClamAV neste sandbox, e o teste nao finge que
 * ha. O que falta para provar contra o real esta no javadoc de ClamAvScanner.
 */
class ClamAvScannerTest {

    /** Stub de clamd: le zINSTREAM + blocos, guarda os bytes, responde a linha. */
    private static final class FakeClamd implements AutoCloseable {
        private final ServerSocket server;
        private final String response;
        private final AtomicReference<byte[]> received = new AtomicReference<>();
        private final CountDownLatch done = new CountDownLatch(1);
        private final Thread thread;

        FakeClamd(String response) throws IOException {
            this.server = new ServerSocket(0);
            this.response = response;
            this.thread = new Thread(() -> {
                try (Socket socket = server.accept();
                     DataInputStream in = new DataInputStream(socket.getInputStream());
                     DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                    byte[] magic = new byte[10];
                    in.readFully(magic);
                    if (!Arrays.equals(magic, "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII))) {
                        throw new IOException("magic INSTREAM invalido");
                    }
                    ByteArrayOutputStream body = new ByteArrayOutputStream();
                    while (true) {
                        int len = in.readInt();
                        if (len == 0) {
                            break;
                        }
                        body.write(in.readNBytes(len));
                    }
                    received.set(body.toByteArray());
                    out.write((response + "\n").getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (IOException ignored) {
                    // Fechamento do stub no fim do teste.
                } finally {
                    done.countDown();
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        byte[] awaitReceived() throws InterruptedException {
            if (!done.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("stub de clamd nao respondeu a tempo");
            }
            return received.get();
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    @Test
    @DisplayName("stream: OK vira CLEAN e o conteudo inteiro chega (chunking acima de 8 KiB)")
    void okIsCleanAndChunkingDeliversWholeContent() throws Exception {
        byte[] content = new byte[20_000];
        Arrays.fill(content, (byte) 'A');
        try (FakeClamd clamd = new FakeClamd("stream: OK")) {
            ClamAvScanner scanner = new ClamAvScanner("127.0.0.1", clamd.port(), 5_000, null);
            FileScanner.ScanResult result = scanner.scan(new ByteArrayInputStream(content));
            assertThat(result.verdict()).isEqualTo(FileScanner.ScanVerdict.CLEAN);
            assertThat(result.threat()).isEmpty();
            assertThat(result.scanner()).isEqualTo(ClamAvScanner.SCANNER_NAME);
            assertThat(clamd.awaitReceived()).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("stream: <ameaca> FOUND vira INFECTED com o nome da ameaca")
    void foundIsInfectedWithThreatName() throws Exception {
        try (FakeClamd clamd = new FakeClamd("stream: EICAR-Test-Signature FOUND")) {
            ClamAvScanner scanner = new ClamAvScanner("127.0.0.1", clamd.port(), 5_000, null);
            FileScanner.ScanResult result = scanner.scan(
                    new ByteArrayInputStream("X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
                            .getBytes(StandardCharsets.US_ASCII)));
            assertThat(result.verdict()).isEqualTo(FileScanner.ScanVerdict.INFECTED);
            assertThat(result.threat()).contains("EICAR-Test-Signature");
        }
    }

    @Test
    @DisplayName("stream: ERROR e conexao recusada viram ScannerUnavailableException, nunca CLEAN")
    void errorAndRefusedConnectionAreUnavailable() throws Exception {
        try (FakeClamd clamd = new FakeClamd("stream: ERROR broken pipe")) {
            ClamAvScanner scanner = new ClamAvScanner("127.0.0.1", clamd.port(), 5_000, null);
            assertThatThrownBy(() -> scanner.scan(new ByteArrayInputStream("x".getBytes())))
                    .isInstanceOf(ScannerUnavailableException.class);
        }
        ClamAvScanner down = new ClamAvScanner("127.0.0.1", 1, 500, null);
        assertThatThrownBy(() -> down.scan(new ByteArrayInputStream("x".getBytes())))
                .isInstanceOf(ScannerUnavailableException.class);
    }

    @Test
    @DisplayName("idade das assinaturas: mtime mais recente do diretorio; sem diretorio, desconhecida")
    void signatureAgeFromDatabaseDir(@TempDir Path dir) throws Exception {
        Path daily = dir.resolve("daily.cvd");
        Path main = dir.resolve("main.cvd");
        Files.writeString(daily, "x");
        Files.writeString(main, "x");
        Instant dailyTime = Instant.parse("2026-11-01T10:00:00Z");
        Instant mainTime = Instant.parse("2026-11-02T10:00:00Z");
        Files.setLastModifiedTime(daily, FileTime.from(dailyTime));
        Files.setLastModifiedTime(main, FileTime.from(mainTime));
        ClamAvScanner scanner = new ClamAvScanner("127.0.0.1", 3310, 5_000, dir);
        assertThat(scanner.signatureUpdatedAt()).contains(mainTime);
        assertThat(new ClamAvScanner("127.0.0.1", 3310, 5_000, null).signatureUpdatedAt()).isEmpty();
        assertThat(new ClamAvScanner("127.0.0.1", 3310, 5_000, dir.resolve("inexistente"))
                .signatureUpdatedAt()).isEmpty();
    }

    @Test
    @DisplayName("InputStream que falha na leitura nao vira veredicto")
    void unreadableContentFails() {
        ClamAvScanner scanner = new ClamAvScanner("127.0.0.1", 3310, 5_000, null);
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("leitura falhou");
            }
        };
        assertThatThrownBy(() -> scanner.scan(failing)).isInstanceOf(IOException.class);
    }
}
