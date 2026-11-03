package br.com.ziipsan.platform.infra.scan;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

/**
 * P1.20: varredor de arquivos. A interface e o contrato substituivel: o ClamAV
 * ({@link ClamAvScanner}) e UMA implementacao, e o servico da hospedagem (ADR-0011)
 * entra como outra implementacao, sem tocar o pipeline de documentos.
 *
 * <p>Regra dura, sem excecao: um arquivo so e considerado limpo quando um varredor
 * realmente o examinou e devolveu {@link ScanVerdict#CLEAN}. Varredor indisponivel
 * nao e "limpo": e {@link ScannerUnavailableException}, e o documento fica em
 * quarentena com scan_status NOT_SCANNED. Nenhuma implementacao desta interface
 * pode devolver CLEAN sem varrer.
 */
public interface FileScanner {

    /** Veredicto de uma varredura efetivamente realizada. */
    enum ScanVerdict {
        CLEAN,
        INFECTED
    }

    /**
     * Resultado de uma varredura realizada.
     *
     * @param verdict veredicto (nunca "nao varrido": a ausencia de varredura e excecao)
     * @param threat nome da ameaca detectada, ou vazio quando limpo
     * @param scanner identificacao do varredor (ex.: "clamav")
     * @param avDbUpdatedAt data das definicoes que varreram o arquivo, ou vazio
     *                      quando o varredor nao a informa
     */
    record ScanResult(ScanVerdict verdict, Optional<String> threat, String scanner,
                      Optional<Instant> avDbUpdatedAt) {
        public ScanResult {
            if (verdict == null || scanner == null || scanner.isBlank()) {
                throw new IllegalArgumentException("verdict e scanner sao obrigatorios");
            }
            if (threat == null || avDbUpdatedAt == null) {
                throw new IllegalArgumentException("threat e avDbUpdatedAt nao podem ser nulos (use Optional.empty())");
            }
            if (verdict == ScanVerdict.INFECTED && threat.isEmpty()) {
                throw new IllegalArgumentException("INFECTED exige o nome da ameaca");
            }
        }

        public static ScanResult clean(String scanner, Optional<Instant> avDbUpdatedAt) {
            return new ScanResult(ScanVerdict.CLEAN, Optional.empty(), scanner, avDbUpdatedAt);
        }

        public static ScanResult infected(String threat, String scanner, Optional<Instant> avDbUpdatedAt) {
            return new ScanResult(ScanVerdict.INFECTED, Optional.of(threat), scanner, avDbUpdatedAt);
        }
    }

    /**
     * Varre o conteudo. O chamador entrega o stream; a implementacao o consome.
     *
     * @throws ScannerUnavailableException quando o varredor nao esta acessivel
     *         (daemon fora do ar, rede, timeout) — o chamador trata como "nao
     *         varrido", nunca como limpo
     * @throws IOException falha de leitura do conteudo ou de protocolo
     */
    ScanResult scan(InputStream content) throws IOException;

    /**
     * Data das definicoes (assinaturas) em uso pelo varredor, ou vazio quando
     * desconhecida. Vazio significa DESCONHECIDA, nunca "atual": o monitor de
     * idade ({@code DocumentService.checkSignatureFreshness}) a trata como
     * UNKNOWN, nao como FRESH.
     */
    Optional<Instant> signatureUpdatedAt();
}
