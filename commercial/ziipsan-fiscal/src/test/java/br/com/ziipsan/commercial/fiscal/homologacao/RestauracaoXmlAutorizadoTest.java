package br.com.ziipsan.commercial.fiscal.homologacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Restauração da P2.18 ensaiada só com sintéticos de certificado de TESTE
 * (aceite da P3.7). O hash esperado é calculado no dublê com MessageDigest da
 * JDK diretamente — nunca pela classe sob teste.
 */
class RestauracaoXmlAutorizadoTest {

    private static final String THUMB_ZIIP = "TESTE-THUMBPRINT-ZIIP-0001";
    private static final String THUMB_TESTE = "TESTE-THUMBPRINT-CERT-TESTE-0001";

    private static String xmlAssinadoCom(String thumbprint) {
        return "<NFAg-e><inf>sintetico</inf><Signature><KeyInfo><X509Certificate>"
                + thumbprint + "</X509Certificate></KeyInfo></Signature></NFAg-e>";
    }

    private static String sha256Jdk(String texto) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hashJdk(String algoritmo, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algoritmo).digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** XML assinado como o real: certificado DER fictício em base64 no X509Certificate. */
    private static String xmlComCertificadoBase64(byte[] der) {
        String b64 = Base64.getMimeEncoder(76, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der);
        return "<NFAg-e><inf>sintetico</inf><Signature><KeyInfo><X509Certificate>"
                + b64 + "</X509Certificate></KeyInfo></Signature></NFAg-e>";
    }

    /** Dublê do armazenamento imutável da P2.18, em memória. */
    private static final class ImutavelMemoria implements RestauracaoXmlAutorizado.ArmazenamentoImutavelPort {
        private final Map<String, String> conteudos;
        private final Map<String, String> hashes;

        ImutavelMemoria(Map<String, String> conteudos, Map<String, String> hashes) {
            this.conteudos = conteudos;
            this.hashes = hashes;
        }

        @Override
        public byte[] ler(String uri) {
            return conteudos.get(uri).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String hashRegistradoNaCadeia(String uri) {
            return hashes.get(uri);
        }
    }

    private static RestauracaoXmlAutorizado restaurador(RestauracaoXmlAutorizado.ArmazenamentoImutavelPort port) {
        return new RestauracaoXmlAutorizado(port, THUMB_ZIIP, Set.of(THUMB_TESTE));
    }

    @Test
    @DisplayName("restaura sintético de certificado de teste com o hash conferido na HashChain")
    void restauraSinteticoDeTeste() {
        String xml = xmlAssinadoCom(THUMB_TESTE);
        var port = new ImutavelMemoria(Map.of("imutavel://fiscal/s1.xml", xml),
                Map.of("imutavel://fiscal/s1.xml", sha256Jdk(xml)));
        RestauracaoXmlAutorizado.XmlRestaurado restaurado =
                restaurador(port).restaurar("imutavel://fiscal/s1.xml");
        assertThat(restaurado.xmlRestaurado()).isEqualTo(xml);
        assertThat(restaurado.xmlSha256()).isEqualTo(sha256Jdk(xml));
    }

    @Test
    @DisplayName("hash divergente da HashChain recusa a restauração")
    void hashDivergenteRecusado() {
        String xml = xmlAssinadoCom(THUMB_TESTE);
        var port = new ImutavelMemoria(Map.of("imutavel://fiscal/s1.xml", xml),
                Map.of("imutavel://fiscal/s1.xml", "0".repeat(64)));
        assertThatThrownBy(() -> restaurador(port).restaurar("imutavel://fiscal/s1.xml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HashChain");
    }

    @Test
    @DisplayName("XML com o certificado da Ziip/TGP no imutável é violação, não restauração")
    void xmlDaHomologacaoNoImutavelRecusado() {
        String xml = xmlAssinadoCom(THUMB_ZIIP);
        var port = new ImutavelMemoria(Map.of("imutavel://fiscal/h1.xml", xml),
                Map.of("imutavel://fiscal/h1.xml", sha256Jdk(xml)));
        assertThatThrownBy(() -> restaurador(port).restaurar("imutavel://fiscal/h1.xml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("área separada");
    }

    @Test
    @DisplayName("conteúdo que não é de certificado de teste conhecido é recusado")
    void conteudoEstranhoRecusado() {
        String xml = xmlAssinadoCom("THUMBPRINT-DESCONHECIDO");
        var port = new ImutavelMemoria(Map.of("imutavel://fiscal/x.xml", xml),
                Map.of("imutavel://fiscal/x.xml", sha256Jdk(xml)));
        assertThatThrownBy(() -> restaurador(port).restaurar("imutavel://fiscal/x.xml"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("o certificado da Ziip/TGP não pode ser cadastrado como certificado de teste")
    void ziipComoTesteRecusadoNaConstrucao() {
        var port = new ImutavelMemoria(Map.of(), Map.of());
        assertThatThrownBy(() -> new RestauracaoXmlAutorizado(port, THUMB_ZIIP, Set.of(THUMB_ZIIP)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("D1: restaura sintético com certificado de TESTE em base64 (thumbprint = hash do DER)")
    void restauraSinteticoComCertificadoBase64() {
        byte[] derTeste = "DER-FICTICIO-CERTIFICADO-DE-TESTE-D1".getBytes(StandardCharsets.UTF_8);
        byte[] derZiip = "DER-FICTICIO-CERTIFICADO-ZIIP-TGP-D1".getBytes(StandardCharsets.UTF_8);
        String thumbTeste = hashJdk("SHA-256", derTeste);
        String thumbZiip = hashJdk("SHA-1", derZiip);
        String xml = xmlComCertificadoBase64(derTeste);
        var port = new ImutavelMemoria(Map.of("imutavel://fiscal/s-b64.xml", xml),
                Map.of("imutavel://fiscal/s-b64.xml", sha256Jdk(xml)));
        var restauradorReal = new RestauracaoXmlAutorizado(port, thumbZiip, Set.of(thumbTeste));
        RestauracaoXmlAutorizado.XmlRestaurado restaurado = restauradorReal.restaurar("imutavel://fiscal/s-b64.xml");
        assertThat(restaurado.xmlRestaurado()).isEqualTo(xml);
        assertThat(restaurado.xmlSha256()).isEqualTo(sha256Jdk(xml));
    }

    @Test
    @DisplayName("D1: XML com o certificado da Ziip/TGP em base64 no imutável é violação, não restauração")
    void xmlDaHomologacaoEmBase64NoImutavelRecusado() {
        byte[] derTeste = "DER-FICTICIO-CERTIFICADO-DE-TESTE-D1".getBytes(StandardCharsets.UTF_8);
        byte[] derZiip = "DER-FICTICIO-CERTIFICADO-ZIIP-TGP-D1".getBytes(StandardCharsets.UTF_8);
        String thumbTeste = hashJdk("SHA-256", derTeste);
        String thumbZiip = hashJdk("SHA-1", derZiip);
        String xml = xmlComCertificadoBase64(derZiip);
        assertThat(xml).doesNotContain(thumbZiip);
        var port = new ImutavelMemoria(Map.of("imutavel://fiscal/h-b64.xml", xml),
                Map.of("imutavel://fiscal/h-b64.xml", sha256Jdk(xml)));
        var restauradorReal = new RestauracaoXmlAutorizado(port, thumbZiip, Set.of(thumbTeste));
        assertThatThrownBy(() -> restauradorReal.restaurar("imutavel://fiscal/h-b64.xml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("área separada");
    }
}
