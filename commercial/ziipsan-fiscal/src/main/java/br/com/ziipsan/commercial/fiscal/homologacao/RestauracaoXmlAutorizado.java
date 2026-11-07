package br.com.ziipsan.commercial.fiscal.homologacao;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

/**
 * Restauração do XML autorizado a partir do armazenamento imutável da P2.18,
 * testada — como manda o aceite da P3.7 — SOMENTE com XMLs SINTÉTICOS assinados
 * com certificado de TESTE. XML da homologação com o e-CNPJ da Ziip/TGP nunca
 * entra no armazenamento imutável (ele é apagado ao fim do ciclo — P2.18/P3.7),
 * e esta classe recusa restaurá-lo se algum aparecer lá: presença dele no
 * imutável é violação da separação, não caso de restauração.
 *
 * <p>A restauração confere o SHA-256 do conteúdo restaurado contra o hash
 * registrado na {@code HashChain} (P2.18) antes de devolver qualquer byte como
 * "restaurado": hash divergente é erro explícito, nunca restauração parcial.
 *
 * <p>Portas sem implementação neste pacote: a leitura do emulador de S3 local
 * pela interface da P0.45 é da P2.18; aqui só a regra de restauração é testada,
 * com dublês em memória nos testes.
 */
public final class RestauracaoXmlAutorizado {

    /** Porta de leitura do armazenamento imutável (implementação: P2.18/P0.45). */
    public interface ArmazenamentoImutavelPort {

        /** Conteúdo gravado sob a URI (XML autorizado sintético, neste ensaio). */
        byte[] ler(String uri);

        /** SHA-256 registrado na HashChain para a URI (64 hex minúsculos). */
        String hashRegistradoNaCadeia(String uri);
    }

    /**
     * @param uri           URI do XML no armazenamento imutável
     * @param xmlRestaurado conteúdo restaurado e conferido (texto do XML)
     * @param xmlSha256     SHA-256 conferido contra a HashChain
     */
    public record XmlRestaurado(String uri, String xmlRestaurado, String xmlSha256) {
    }

    private final ArmazenamentoImutavelPort armazenamento;
    private final VarreduraHomologacao varredura;
    private final Set<String> thumbprintsCertificadosDeTeste;

    /**
     * @param thumbprintsCertificadosDeTeste thumbprints dos certificados de TESTE
     *                                       admitidos no ensaio (configuração do
     *                                       ambiente local; nenhum é o da Ziip/TGP)
     */
    public RestauracaoXmlAutorizado(ArmazenamentoImutavelPort armazenamento,
                                    String thumbprintZiipTgp,
                                    Set<String> thumbprintsCertificadosDeTeste) {
        this.armazenamento = Objects.requireNonNull(armazenamento, "Porta do armazenamento imutável é obrigatória");
        this.varredura = new VarreduraHomologacao(thumbprintZiipTgp);
        this.thumbprintsCertificadosDeTeste = Set.copyOf(
                Objects.requireNonNull(thumbprintsCertificadosDeTeste,
                        "Conjunto de certificados de teste admitidos é obrigatório"));
        if (thumbprintsCertificadosDeTeste.stream().anyMatch(
                t -> varredura.carregaCertificadoZiipTgp(t))) {
            throw new IllegalArgumentException(
                    "O certificado da Ziip/TGP não pode constar como certificado de teste do ensaio de restauração");
        }
    }

    /**
     * Restaura e confere um XML sintético do armazenamento imutável.
     *
     * @throws IllegalStateException se o conteúdo carregar o certificado da
     *                               Ziip/TGP, se não for de certificado de teste
     *                               conhecido, ou se o SHA-256 divergir da HashChain
     */
    public XmlRestaurado restaurar(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("URI do XML a restaurar é obrigatória");
        }
        byte[] bytes = Objects.requireNonNull(armazenamento.ler(uri),
                "Armazenamento imutável devolveu conteúdo nulo para " + uri);
        String xml = new String(bytes, StandardCharsets.UTF_8);

        // XML da homologação real NÃO vive no imutável (P2.18): se aparecer, é violação.
        if (varredura.carregaCertificadoZiipTgp(xml)) {
            throw new IllegalStateException(
                    "XML com o certificado da Ziip/TGP encontrado no armazenamento imutável (" + uri
                            + "): ele pertence só à área separada e ao expurgo do ciclo (P3.7)");
        }
        // O ensaio é com certificado de TESTE conhecido: conteúdo sem nenhum dos
        // thumbprints de teste admitidos não é o ensaio — é conteúdo estranho.
        // A identificação usa o MESMO algoritmo da varredura (correção D1):
        // extrai o X509Certificate em base64, decodifica o DER e compara o
        // thumbprint calculado (SHA-1/SHA-256) — não é busca textual.
        boolean deTeste = thumbprintsCertificadosDeTeste.stream()
                .anyMatch(t -> VarreduraHomologacao.conteudoCarregaCertificado(xml, t));
        if (!deTeste) {
            throw new IllegalStateException(
                    "Conteúdo de " + uri + " não é XML sintético de certificado de teste conhecido: "
                            + "o ensaio de restauração da P3.7 só admite sintéticos de teste");
        }

        String hashReal = Sha256.deBytes(bytes);
        String hashCadeia = armazenamento.hashRegistradoNaCadeia(uri);
        if (!hashReal.equals(hashCadeia)) {
            throw new IllegalStateException(
                    "SHA-256 do XML restaurado diverge do registrado na HashChain (" + uri + ")");
        }
        return new XmlRestaurado(uri, xml, hashReal);
    }
}
