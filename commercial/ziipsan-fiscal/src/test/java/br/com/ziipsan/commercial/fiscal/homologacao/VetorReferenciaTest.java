package br.com.ziipsan.commercial.fiscal.homologacao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vetores de referência de fiscal-contract-fixtures (§5): sem assinatura, sem
 * elemento Signature, sem certificado. Thumbprints são marcadores de teste.
 */
class VetorReferenciaTest {

    private static final String THUMB_ZIIP = "TESTE-THUMBPRINT-ZIIP-0001";

    private static final String XML_ASSINADO = """
            <NFAg-e xmlns="urn:sintetico-teste"><infNFAg-e><emit><CNPJ>00000000000000</CNPJ></emit><tpAmb>2</tpAmb></infNFAg-e><Signature xmlns="http://www.w3.org/2000/09/xmldsig#"><SignedInfo><CanonicalizationMethod Algorithm="urn:teste"/></SignedInfo><KeyInfo><X509Data><X509Certificate>TESTE-THUMBPRINT-ZIIP-0001</X509Certificate></X509Data></KeyInfo></Signature></NFAg-e>
            """;

    private static final String XML_LIMPO = """
            <NFAg-e xmlns="urn:sintetico-teste"><infNFAg-e><emit><CNPJ>00000000000000</CNPJ></emit><tpAmb>2</tpAmb></infNFAg-e></NFAg-e>
            """;

    @Test
    @DisplayName("saneamento remove Signature e certificado e o resultado é fixture segura")
    void saneamentoRemoveAssinaturaECertificado() {
        String saneado = VetorReferencia.sanearParaFixture(XML_ASSINADO, THUMB_ZIIP);
        assertThat(saneado).doesNotContain("Signature").doesNotContain("X509Certificate")
                .doesNotContain(THUMB_ZIIP);
        assertThat(saneado).contains("infNFAg-e").contains("<tpAmb>2</tpAmb>");
        assertThatCode(() -> VetorReferencia.exigirSeguroParaFixture(saneado, THUMB_ZIIP))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("fixture com Signature, com certificado ou com o certificado Ziip/TGP é recusada")
    void fixtureContaminadaRecusada() {
        assertThatThrownBy(() -> VetorReferencia.exigirSeguroParaFixture(XML_ASSINADO, THUMB_ZIIP))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> VetorReferencia.exigirSeguroParaFixture(
                "<doc><X509Certificate>abc</X509Certificate></doc>", THUMB_ZIIP))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> VetorReferencia.exigirSeguroParaFixture(
                "<doc><!-- " + THUMB_ZIIP + " --></doc>", THUMB_ZIIP))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("XML limpo é fixture segura; XML malformado não é saneado em silêncio")
    void xmlLimpoPassaEMalformadoFalha() {
        assertThatCode(() -> VetorReferencia.exigirSeguroParaFixture(XML_LIMPO, THUMB_ZIIP))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> VetorReferencia.sanearParaFixture("<doc><aberto>", THUMB_ZIIP))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
