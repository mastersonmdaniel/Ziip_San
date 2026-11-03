package br.com.ziipsan.platform.infra.scan;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * P1.20: implementacao real da {@link FileScanner} sobre o daemon ClamAV
 * (clamd), via protocolo INSTREAM sobre TCP — sem SDK, sem dependencia externa,
 * so JDK. E a implementacao de referencia; o servico da hospedagem a substitui
 * implementando a interface, sem tocar o pipeline.
 *
 * <p>Protocolo: envia {@code zINSTREAM\0}, depois blocos (4 bytes big-endian
 * com o tamanho + dados) terminados por um bloco de tamanho zero, e le a
 * resposta: {@code stream: OK} (limpo), {@code stream: <ameaca> FOUND}
 * (infectado) ou {@code stream: ERROR} (falha do daemon — tratada como
 * varredor indisponivel, nunca como limpo).
 *
 * <p>Idade das assinaturas: lida do diretorio das definicoes do freshclam
 * (tipicamente /var/lib/clamav), como o mtime mais recente entre daily.cld /
 * daily.cvd / main.cld / main.cvd, quando o diretorio for informado e legivel.
 * Sem diretorio, ou sem arquivo legivel, a idade e DESCONHECIDA
 * ({@link Optional#empty()}), nunca "atual".
 *
 * <p>Ressalva honesta: neste sandbox nao ha daemon ClamAV (sem binarios clamd /
 * clamdscan / freshclam e sem /var/lib/clamav), de modo que esta classe nao foi
 * exercitada contra um clamd real aqui — apenas contra um stub de protocolo em
 * teste (ver ClamAvScannerTest) e, no fluxo de documentos, por uma implementacao
 * falsa da interface. O que falta para provar: um clamd acessivel com definicoes
 * do freshclam, um EICAR real devolvendo FOUND e um PDF limpo devolvendo OK.
 */
public final class ClamAvScanner implements FileScanner {

    /** Nome registrado em documents.scanner e nos eventos de varredura. */
    public static final String SCANNER_NAME = "clamav";

    private static final int MAX_CHUNK = 8192;

    private final String host;
    private final int port;
    private final int timeoutMillis;
    private final Path databaseDir;

    /**
     * @param host endereco do clamd (tipicamente 127.0.0.1)
     * @param port porta do clamd (padrao 3310)
     * @param timeoutMillis timeout de socket (conexao e leitura)
     * @param databaseDir diretorio das definicoes do freshclam, ou null quando
     *                    a idade das assinaturas nao pode ser lida localmente
     *                    (varredor remoto, por exemplo): idade desconhecida
     */
    public ClamAvScanner(String host, int port, int timeoutMillis, Path databaseDir) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host do clamd e obrigatorio");
        }
        if (port <= 0 || port > 65535 || timeoutMillis <= 0) {
            throw new IllegalArgumentException("porta e timeout devem ser positivos");
        }
        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
        this.databaseDir = databaseDir;
    }

    @Override
    public ScanResult scan(InputStream content) throws IOException {
        byte[] bytes;
        try {
            bytes = content.readAllBytes();
        } catch (IOException e) {
            throw new IOException("falha ao ler o conteudo para varredura", e);
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {
                out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                for (int offset = 0; offset < bytes.length; offset += MAX_CHUNK) {
                    int len = Math.min(MAX_CHUNK, bytes.length - offset);
                    out.writeInt(len);
                    out.write(bytes, offset, len);
                }
                out.writeInt(0);
                out.flush();
                String response = readResponse(in);
                return parseResponse(response);
            }
        } catch (ScannerUnavailableException e) {
            throw e;
        } catch (IOException e) {
            throw new ScannerUnavailableException("clamd inacessivel em " + host + ":" + port, e);
        }
    }

    @Override
    public Optional<Instant> signatureUpdatedAt() {
        if (databaseDir == null || !Files.isDirectory(databaseDir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(databaseDir)) {
            return files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.equals("daily.cld") || name.equals("daily.cvd")
                                || name.equals("main.cld") || name.equals("main.cvd");
                    })
                    .map(p -> {
                        try {
                            FileTime mtime = Files.getLastModifiedTime(p);
                            return Optional.of(mtime.toInstant());
                        } catch (IOException e) {
                            return Optional.<Instant>empty();
                        }
                    })
                    .flatMap(Optional::stream)
                    .max(Comparator.naturalOrder());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String readResponse(DataInputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            buffer.write(b);
        }
        if (buffer.size() == 0 && b == -1) {
            throw new ScannerUnavailableException("clamd fechou a conexao sem resposta");
        }
        return buffer.toString(StandardCharsets.US_ASCII).strip();
    }

    private ScanResult parseResponse(String response) throws ScannerUnavailableException {
        if (!response.startsWith("stream:")) {
            throw new ScannerUnavailableException("resposta inesperada do clamd: " + response);
        }
        String body = response.substring("stream:".length()).strip();
        if (body.equals("OK")) {
            return ScanResult.clean(SCANNER_NAME, signatureUpdatedAt());
        }
        if (body.endsWith("FOUND")) {
            String threat = body.substring(0, body.length() - "FOUND".length()).strip();
            if (threat.isEmpty()) {
                throw new ScannerUnavailableException("clamd devolveu FOUND sem nome de ameaca");
            }
            return ScanResult.infected(threat, SCANNER_NAME, signatureUpdatedAt());
        }
        // "ERROR ..." do daemon: falha do varredor, nunca veredicto.
        throw new ScannerUnavailableException("clamd devolveu erro: " + body);
    }
}
