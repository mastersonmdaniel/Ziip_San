package br.com.ziipsan.commercial.registry.legacyxml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.SignatureMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Assinante XMLDSig das fixtures SINTÉTICAS da P2.19 (só testes): assina o
 * elemento de informações do documento com um par de chaves de TESTE gerado
 * no próprio teste, no desenho usual do documento fiscal (assinatura
 * enveloped sobre o {@code Id}, SHA-256). Duas formas de {@code KeyInfo}:
 * chave pública pura ({@code KeyValue}) e certificado X.509 autossinado de
 * teste ({@code X509Data}) — o "certificado de teste" do aceite. Nenhuma
 * chave ou certificado real é usado, e nenhum XML real é assinado aqui.
 */
final class SinteticoXmlSigner {

    private static final String RSA_SHA256 = "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256";

    private SinteticoXmlSigner() {
    }

    static byte[] signWithKeyValue(String xml, KeyPair keys) throws Exception {
        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        KeyInfoFactory kif = factory.getKeyInfoFactory();
        return sign(xml, keys.getPrivate(), kif.newKeyInfo(List.of(kif.newKeyValue(keys.getPublic()))));
    }

    static byte[] signWithCertificate(String xml, KeyPair keys, X509Certificate certificate) throws Exception {
        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        KeyInfoFactory kif = factory.getKeyInfoFactory();
        return sign(xml, keys.getPrivate(), kif.newKeyInfo(List.of(kif.newX509Data(List.of(certificate)))));
    }

    /**
     * Assina o documento com um {@code KeyInfo} AMBÍGUO (correção A12(a) da
     * revisão): certificado X.509 do par que assina + {@code KeyValue} de
     * OUTRO par de chaves. Serve para provar que o validador recusa a
     * ambiguidade em vez de deixar "o último item do KeyInfo vencer".
     */
    static byte[] signWithCertificateAndForeignKeyValue(String xml, KeyPair keys,
                                                        X509Certificate certificate,
                                                        PublicKey foreignPublic) throws Exception {
        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        KeyInfoFactory kif = factory.getKeyInfoFactory();
        KeyInfo keyInfo = kif.newKeyInfo(List.of(
                kif.newX509Data(List.of(certificate)),
                kif.newKeyValue(foreignPublic)));
        return sign(xml, keys.getPrivate(), keyInfo);
    }

    /**
     * Assina um FRAGMENTO IRRELEVANTE plantado no invólucro — nunca o
     * {@code infNFe} (correção A1 da revisão da P2.19): a assinatura
     * resultante é criptograficamente VÁLIDA, mas não cobre o documento
     * fiscal; serve para provar que o validador a recusa por falta de
     * amarração ao elemento que carrega a chave de acesso.
     */
    static byte[] signIrrelevantFragmentWithKeyValue(String xml, KeyPair keys) throws Exception {
        Document doc = parseSeguro(xml);
        Element root = doc.getDocumentElement();
        Element fragmento = doc.createElement("fragmentoIrrelevante");
        fragmento.setAttribute("Id", "FRAGMENTO-IRRELEVANTE");
        fragmento.setTextContent("conteudo sem relevancia fiscal");
        root.appendChild(fragmento);
        fragmento.setIdAttribute("Id", true);
        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        KeyInfoFactory kif = factory.getKeyInfoFactory();
        KeyInfo keyInfo = kif.newKeyInfo(List.of(kif.newKeyValue(keys.getPublic())));
        assinar(doc, root, "#FRAGMENTO-IRRELEVANTE", keys.getPrivate(), keyInfo);
        return serializar(doc);
    }

    private static byte[] sign(String xml, PrivateKey key, KeyInfo keyInfo) throws Exception {
        Document doc = parseSeguro(xml);
        NodeList infos = doc.getElementsByTagNameNS("*", "infNFe");
        if (infos.getLength() == 0) {
            infos = doc.getElementsByTagNameNS("*", "infNFAg-e");
        }
        Element info = (Element) infos.item(0);
        info.setIdAttribute("Id", true);
        Element parent = (Element) info.getParentNode();
        assinar(doc, parent, "#" + info.getAttribute("Id"), key, keyInfo);
        return serializar(doc);
    }

    private static Document parseSeguro(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static void assinar(Document doc, Element parent, String referenceUri,
                                PrivateKey key, KeyInfo keyInfo) throws Exception {
        XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
        Reference reference = factory.newReference(referenceUri,
                factory.newDigestMethod(DigestMethod.SHA256, null),
                List.of(factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                        factory.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null)),
                null, null);
        SignedInfo signedInfo = factory.newSignedInfo(
                factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                factory.newSignatureMethod(RSA_SHA256, (SignatureMethodParameterSpec) null),
                List.of(reference));
        XMLSignature signature = factory.newXMLSignature(signedInfo, keyInfo);
        signature.sign(new DOMSignContext(key, parent));
    }

    private static byte[] serializar(Document doc) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }

    /**
     * Certificado X.509 autossinado de TESTE, montado em DER neste helper
     * (sem API interna do JDK): TBSCertificate v3 mínimo (versão, serial,
     * algoritmo, emissor = assunto = CN informado, validade 2025–2030 e a
     * SubjectPublicKeyInfo do par gerado), assinado com SHA256withRSA.
     * Serve só para o {@code KeyInfo} das fixtures carregarem um certificado
     * de verdade — o validador o lê pelo {@code CertificateFactory} padrão.
     */
    static X509Certificate selfSignedTestCertificate(KeyPair keys, String commonName) throws Exception {
        byte[] algId = der(0x30, concat(
                new byte[]{0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B},
                new byte[]{0x05, 0x00}));
        byte[] name = der(0x30, der(0x31, der(0x30, concat(
                new byte[]{0x06, 0x03, 0x55, 0x04, 0x03},
                der(0x0C, commonName.getBytes(StandardCharsets.UTF_8))))));
        byte[] validity = der(0x30, concat(
                der(0x17, "250101000000Z".getBytes(StandardCharsets.US_ASCII)),
                der(0x17, "301231235959Z".getBytes(StandardCharsets.US_ASCII))));
        byte[] version = der(0xA0, der(0x02, new byte[]{2}));
        byte[] serial = der(0x02, new byte[]{1});
        byte[] tbs = der(0x30, concat(version, serial, algId, name, validity, name,
                keys.getPublic().getEncoded()));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keys.getPrivate());
        signer.update(tbs);
        byte[] signatureBytes = signer.sign();
        byte[] bitString = der(0x03, concat(new byte[]{0}, signatureBytes));
        byte[] certificate = der(0x30, concat(tbs, algId, bitString));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certificate));
    }

    private static byte[] der(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (content.length < 128) {
            out.write(content.length);
        } else if (content.length < 256) {
            out.write(0x81);
            out.write(content.length);
        } else {
            out.write(0x82);
            out.write((content.length >> 8) & 0xFF);
            out.write(content.length & 0xFF);
        }
        out.writeBytes(content);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
