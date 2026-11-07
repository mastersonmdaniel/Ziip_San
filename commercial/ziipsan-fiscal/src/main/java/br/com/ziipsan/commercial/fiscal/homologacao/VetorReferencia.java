package br.com.ziipsan.commercial.fiscal.homologacao;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Vetor de referência de {@code fiscal-contract-fixtures} (FISCAL-PORTE-JAVA §5;
 * aceite da P3.7): o que fica guardado de um ciclo de homologação é SÓ o vetor
 * SEM assinatura, SEM o elemento {@code Signature} e SEM certificado — o XML
 * assinado com o e-CNPJ da Ziip/TGP é apagado no expurgo e nunca vira fixture.
 *
 * <p>{@link #sanearParaFixture(String, String)} remove do XML o elemento
 * {@code Signature} (com tudo dentro dele, inclusive {@code X509Certificate}) e
 * confere o resultado antes de devolver: se restar assinatura, certificado ou o
 * thumbprint do certificado da Ziip/TGP, é erro explícito — fixture contaminada
 * não é gravada "quase limpa".
 *
 * <p>{@link #exigirSeguroParaFixture(String, String)} é a conferência usada
 * sobre fixtures já existentes (teste de CI lê o diretório de fixtures e exige
 * esta forma de cada arquivo).
 */
public final class VetorReferencia {

    private VetorReferencia() {
    }

    /** Verdadeiro se o XML contém elemento de assinatura XML-DSig (qualquer namespace). */
    public static boolean contemAssinatura(String xml) {
        return xml != null && (xml.contains("<Signature") || xml.contains(":Signature"));
    }

    /** Verdadeiro se o XML contém certificado X.509 embutido. */
    public static boolean contemCertificado(String xml) {
        return xml != null && xml.contains("X509Certificate");
    }

    /**
     * Confere que um XML pode ficar em {@code fiscal-contract-fixtures}.
     *
     * @param thumbprintZiipTgp thumbprint do certificado da Ziip/TGP (configuração);
     *                          pode ser nulo quando a conferência é só estrutural
     * @throws IllegalStateException se houver assinatura, certificado ou o certificado da Ziip/TGP
     */
    public static void exigirSeguroParaFixture(String xml, String thumbprintZiipTgp) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("Vetor de referência vazio não é fixture");
        }
        if (contemAssinatura(xml)) {
            throw new IllegalStateException(
                    "Fixture recusada: vetor de referência não pode conter o elemento Signature (§5)");
        }
        if (contemCertificado(xml)) {
            throw new IllegalStateException(
                    "Fixture recusada: vetor de referência não pode conter certificado (§5)");
        }
        if (thumbprintZiipTgp != null && !thumbprintZiipTgp.isBlank()
                && xml.replaceAll("\\s+", "").toLowerCase()
                        .contains(thumbprintZiipTgp.replaceAll("\\s+", "").toLowerCase())) {
            throw new IllegalStateException(
                    "Fixture recusada: vetor de referência contém o certificado da Ziip/TGP");
        }
    }

    /**
     * Saneia um XML assinado para vetor de referência: remove todo elemento
     * {@code Signature} (qualquer namespace) e confere o resultado com
     * {@link #exigirSeguroParaFixture(String, String)} antes de devolvê-lo.
     */
    public static String sanearParaFixture(String xmlAssinado, String thumbprintZiipTgp) {
        if (xmlAssinado == null || xmlAssinado.isBlank()) {
            throw new IllegalArgumentException("XML de origem do vetor é obrigatório");
        }
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = fabrica.newDocumentBuilder().parse(new InputSource(new StringReader(xmlAssinado)));

            List<Element> assinaturas = new ArrayList<>();
            coletar(doc.getDocumentElement(), "Signature", assinaturas);
            for (Element assinatura : assinaturas) {
                Node pai = assinatura.getParentNode();
                if (pai != null) {
                    pai.removeChild(assinatura);
                }
            }

            StringWriter saida = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.transform(new DOMSource(doc), new StreamResult(saida));
            String saneado = saida.toString();
            exigirSeguroParaFixture(saneado, thumbprintZiipTgp);
            return saneado;
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("XML de origem não é XML bem-formado para saneamento de fixture", e);
        }
    }

    private static void coletar(Element elemento, String nomeLocal, List<Element> destino) {
        String local = elemento.getLocalName() != null ? elemento.getLocalName() : elemento.getTagName();
        if (nomeLocal.equals(local)) {
            destino.add(elemento);
            return; // não desce: a subárvore inteira sai com o elemento
        }
        NodeList filhos = elemento.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            if (filhos.item(i) instanceof Element filho) {
                coletar(filho, nomeLocal, destino);
            }
        }
    }
}
