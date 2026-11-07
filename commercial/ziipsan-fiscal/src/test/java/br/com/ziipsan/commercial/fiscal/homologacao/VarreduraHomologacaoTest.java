package br.com.ziipsan.commercial.fiscal.homologacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Varredura do padrão P0.45 aplicada à homologação (teste exigido pelo aceite
 * da P3.7): XML com o certificado da Ziip/TGP fora da área separada é recusado
 * sempre; depois do fim do ciclo, é recusado em qualquer lugar. O thumbprint
 * aqui é marcador de teste — EXCETO nos casos da correção D1, em que o
 * certificado é DER fictício em base64 de verdade e o thumbprint esperado é o
 * hash (SHA-1/SHA-256) calculado NESTE teste com o MessageDigest da JDK,
 * nunca pela classe sob teste.
 */
class VarreduraHomologacaoTest {

    private static final String THUMB_ZIIP = "TESTE-THUMBPRINT-ZIIP-0001";
    private static final String THUMB_TESTE = "TESTE-THUMBPRINT-CERT-TESTE-0001";
    private static final String AREA = AreaSeparadaHomologacao.ESQUEMA_AREA;

    /** DER fictício do "certificado da Ziip/TGP" dos casos D1 (bytes arbitrários de teste). */
    private static final byte[] DER_ZIIP_FICTICIO =
            "DER-FICTICIO-CERTIFICADO-ZIIP-TGP-PARA-TESTE-D1".getBytes(StandardCharsets.UTF_8);
    /** DER fictício de um certificado DIVERSO (não é alvo da varredura). */
    private static final byte[] DER_OUTRO_FICTICIO =
            "DER-FICTICIO-CERTIFICADO-DIVERSO-PARA-TESTE-D1".getBytes(StandardCharsets.UTF_8);

    private static String xmlComCert(String thumbprint) {
        return "<NFAg-e><inf>doc sintetico</inf><Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">"
                + "<KeyInfo><X509Data><X509Certificate>" + thumbprint + "</X509Certificate></X509Data></KeyInfo>"
                + "</Signature></NFAg-e>";
    }

    /** XML assinado como o real: certificado DER em base64 (com quebras de linha, como no leiaute). */
    private static String xmlComCertificadoBase64(byte[] der) {
        String b64 = Base64.getMimeEncoder(76, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der);
        return "<NFAg-e><inf>doc sintetico</inf><Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">"
                + "<KeyInfo><X509Data><X509Certificate>" + b64 + "</X509Certificate></X509Data></KeyInfo>"
                + "</Signature></NFAg-e>";
    }

    private static String hashJdk(String algoritmo, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algoritmo).digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private final VarreduraHomologacao varredura = new VarreduraHomologacao(THUMB_ZIIP);

    @Test
    @DisplayName("XML com certificado Ziip/TGP fora da área separada é recusado")
    void foraDaAreaRecusado() {
        List<VarreduraHomologacao.Achado> achados = List.of(
                new VarreduraHomologacao.Achado("imutavel://fiscal/doc.xml", xmlComCert(THUMB_ZIIP)),
                new VarreduraHomologacao.Achado("/backups/locais/doc.xml", xmlComCert(THUMB_ZIIP)),
                new VarreduraHomologacao.Achado("src/test/resources/fiscal-contract-fixtures/doc.xml", xmlComCert(THUMB_ZIIP)));
        List<VarreduraHomologacao.Violacao> violacoes = varredura.varrer(achados, false);
        assertThat(violacoes).hasSize(3);
        assertThatThrownBy(() -> varredura.exigirLimpo(achados, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("dentro da área separada e com o ciclo aberto, o XML da homologação é admitido")
    void dentroDaAreaDuranteOCicloAdmitido() {
        List<VarreduraHomologacao.Achado> achados = List.of(
                new VarreduraHomologacao.Achado(AREA + "ciclo-01/doc.xml", xmlComCert(THUMB_ZIIP)));
        assertThat(varredura.varrer(achados, false)).isEmpty();
        assertThatCode(() -> varredura.exigirLimpo(achados, false)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("depois do fim do ciclo, XML com certificado Ziip/TGP é recusado até na área separada")
    void depoisDoFimDoCicloRecusadoEmQualquerLugar() {
        List<VarreduraHomologacao.Achado> achados = List.of(
                new VarreduraHomologacao.Achado(AREA + "ciclo-01/restante.xml", xmlComCert(THUMB_ZIIP)));
        List<VarreduraHomologacao.Violacao> violacoes = varredura.varrer(achados, true);
        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.get(0).motivo()).contains("após o fim do ciclo");
    }

    @Test
    @DisplayName("XML sintético de certificado de TESTE não é alvo desta varredura")
    void certificadoDeTesteNaoEAlvo() {
        List<VarreduraHomologacao.Achado> achados = List.of(
                new VarreduraHomologacao.Achado("imutavel://fiscal/sintetico.xml", xmlComCert(THUMB_TESTE)),
                new VarreduraHomologacao.Achado("/tmp/qualquer.xml", "<doc>sem certificado</doc>"));
        assertThat(varredura.varrer(achados, true)).isEmpty();
    }

    @Test
    @DisplayName("D1: certificado REAL em base64 (thumbprint SHA-1 do DER) fora da área é detectado e recusado")
    void certificadoRealEmBase64Sha1Detectado() {
        // O thumbprint esperado é o SHA-1 do DER — ele NÃO aparece como texto
        // em lugar nenhum do XML (o certificado viaja em base64). Antes da
        // correção D1, esta varredura declarava o arquivo "limpo".
        String thumbprintSha1 = hashJdk("SHA-1", DER_ZIIP_FICTICIO);
        VarreduraHomologacao varreduraReal = new VarreduraHomologacao(thumbprintSha1);
        String xml = xmlComCertificadoBase64(DER_ZIIP_FICTICIO);
        assertThat(xml).doesNotContain(thumbprintSha1);
        assertThat(varreduraReal.carregaCertificadoZiipTgp(xml)).isTrue();
        List<VarreduraHomologacao.Achado> achados = List.of(
                new VarreduraHomologacao.Achado("/backups/locais/doc.xml", xml));
        assertThat(varreduraReal.varrer(achados, false)).hasSize(1);
        assertThatThrownBy(() -> varreduraReal.exigirLimpo(achados, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("D1: certificado REAL em base64 (thumbprint SHA-256 do DER) pós-ciclo é detectado até na área")
    void certificadoRealEmBase64Sha256DetectadoPosCiclo() {
        String thumbprintSha256 = hashJdk("SHA-256", DER_ZIIP_FICTICIO);
        VarreduraHomologacao varreduraReal = new VarreduraHomologacao(thumbprintSha256);
        String xml = xmlComCertificadoBase64(DER_ZIIP_FICTICIO);
        assertThat(xml).doesNotContain(thumbprintSha256);
        List<VarreduraHomologacao.Achado> achados = List.of(
                new VarreduraHomologacao.Achado(AREA + "ciclo-01/restante.xml", xml));
        List<VarreduraHomologacao.Violacao> violacoes = varreduraReal.varrer(achados, true);
        assertThat(violacoes).hasSize(1);
        assertThat(violacoes.get(0).motivo()).contains("após o fim do ciclo");
    }

    @Test
    @DisplayName("D1: certificado DIVERSO em base64 não é alvo (o hash do DER não confere)")
    void certificadoDiversoEmBase64NaoEAlvo() {
        String thumbprintSha1 = hashJdk("SHA-1", DER_ZIIP_FICTICIO);
        VarreduraHomologacao varreduraReal = new VarreduraHomologacao(thumbprintSha1);
        List<VarreduraHomologacao.Achado> achados = List.of(
                new VarreduraHomologacao.Achado("/backups/locais/outro.xml",
                        xmlComCertificadoBase64(DER_OUTRO_FICTICIO)));
        assertThat(varreduraReal.varrer(achados, true)).isEmpty();
    }
}
