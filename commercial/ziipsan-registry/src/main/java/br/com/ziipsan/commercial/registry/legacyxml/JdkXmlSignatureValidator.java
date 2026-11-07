package br.com.ziipsan.commercial.registry.legacyxml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.KeySelectorResult;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyValue;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Optional;

/**
 * Validador XMLDSig do JDK (P2.19) — ver o contrato em
 * {@link XmlSignatureValidatorPort}.
 *
 * <p>Regras: exatamente UM elemento {@code Signature}; o atributo
 * {@code Id} do elemento de informações do documento é registrado como ID
 * antes de validar (sem isso a referência {@code #Id} não resolve); a
 * validação exige o núcleo válido e TODAS as referências válidas (a
 * {@code validate} do JDK já devolve falso se alguma referência falhar —
 * a conferência explícita abaixo só produz o motivo). A chave é a do
 * {@code KeyInfo} do próprio documento: certificado X.509 quando presente,
 * senão a chave pública de um {@code KeyValue}.
 *
 * <p><b>Amarração ao documento (correção A1 da revisão):</b> assinatura
 * válida sobre um fragmento qualquer NÃO basta — é exigida uma referência
 * cuja URI seja exatamente {@code #<Id>} do elemento de informações que
 * carrega a chave de acesso ({@code infNFe}/{@code infNFAge}), e essa
 * referência precisa validar. Sem isso, o conteúdo fiscal (emitente,
 * valores) ficaria fora do conteúdo assinado e uma adulteração dele não
 * seria detectada por ninguém.
 */
public final class JdkXmlSignatureValidator implements XmlSignatureValidatorPort {

    @Override
    public SignatureResult validate(byte[] xmlBytes) {
        Document doc;
        try {
            doc = parseDom(xmlBytes);
        } catch (RuntimeException e) {
            return SignatureResult.invalid("XML não pôde ser relido para validar a assinatura");
        }
        NodeList signatures = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        if (signatures.getLength() == 0) {
            return SignatureResult.invalid("XML sem elemento Signature");
        }
        if (signatures.getLength() > 1) {
            return SignatureResult.invalid("XML com mais de um elemento Signature");
        }
        String uriDoDocumento = uriDoElementoDoDocumento(doc);
        if (uriDoDocumento == null) {
            return SignatureResult.invalid("XML sem elemento de informações (infNFe/infNFAge)"
                    + " com Id: não há elemento do documento ao qual amarrar a assinatura");
        }
        registrarIds(doc);
        try {
            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");
            SeletorChave seletor = new SeletorChave();
            DOMValidateContext context = new DOMValidateContext(seletor, signatures.item(0));
            XMLSignature signature = factory.unmarshalXMLSignature(context);
            Reference referenciaDoDocumento = null;
            for (Reference reference : signature.getSignedInfo().getReferences()) {
                if (uriDoDocumento.equals(reference.getURI())) {
                    referenciaDoDocumento = reference;
                    break;
                }
            }
            if (referenciaDoDocumento == null) {
                return SignatureResult.invalid("assinatura não cobre o documento fiscal:"
                        + " nenhuma referência aponta para " + uriDoDocumento
                        + " (assinatura sobre fragmento irrelevante não é assinatura do documento)");
            }
            boolean coreValid = signature.validate(context);
            if (!coreValid) {
                boolean refsOk = true;
                for (Reference reference : signature.getSignedInfo().getReferences()) {
                    try {
                        if (!reference.validate(context)) {
                            refsOk = false;
                            break;
                        }
                    } catch (Exception e) {
                        refsOk = false;
                        break;
                    }
                }
                return SignatureResult.invalid(refsOk
                        ? "valor da assinatura não confere com o conteúdo assinado"
                        : "resumo de referência da assinatura não confere (conteúdo alterado após a assinatura)");
            }
            try {
                if (!referenciaDoDocumento.validate(context)) {
                    return SignatureResult.invalid("resumo da referência do documento fiscal não confere"
                            + " (conteúdo do documento alterado após a assinatura)");
                }
            } catch (Exception e) {
                return SignatureResult.invalid("referência do documento fiscal não pôde ser validada ("
                        + e.getClass().getSimpleName() + ")");
            }
            return SignatureResult.ok(seletor.signerDocument());
        } catch (Exception e) {
            return SignatureResult.invalid("assinatura não pôde ser validada ("
                    + e.getClass().getSimpleName() + ")");
        }
    }

    /**
     * URI ({@code #<Id>}) do elemento de informações que carrega a chave de
     * acesso — {@code infNFe}/{@code infNFAge} com atributo {@code Id}. Sem
     * esse elemento não há documento ao qual amarrar a assinatura (A1).
     * Reserva: o primeiro elemento cujo {@code Id} contenha os 44 dígitos de
     * uma chave de acesso, para invólucros com nome de elemento diverso.
     */
    private static String uriDoElementoDoDocumento(Document doc) {
        for (String nomeLocal : new String[]{"infNFe", "infNFAge"}) {
            NodeList infos = doc.getElementsByTagNameNS("*", nomeLocal);
            for (int i = 0; i < infos.getLength(); i++) {
                if (infos.item(i) instanceof Element el) {
                    String id = el.getAttribute("Id");
                    if (id != null && !id.isBlank()) {
                        return "#" + id;
                    }
                }
            }
        }
        NodeList todos = doc.getElementsByTagName("*");
        for (int i = 0; i < todos.getLength(); i++) {
            if (todos.item(i) instanceof Element el && el.hasAttribute("Id")) {
                String id = el.getAttribute("Id");
                if (id != null && id.replaceAll("\\D", "").length() == 44) {
                    return "#" + id;
                }
            }
        }
        return null;
    }

    /** Registra como atributo ID o {@code Id} de qualquer elemento que o tenha (ex.: infNFe/infNFAg-e). */
    private static void registrarIds(Document doc) {
        NodeList todos = doc.getElementsByTagName("*");
        for (int i = 0; i < todos.getLength(); i++) {
            if (todos.item(i) instanceof Element el && el.hasAttribute("Id")) {
                try {
                    el.setIdAttribute("Id", true);
                } catch (RuntimeException ignored) {
                    // Sem DTD/schema o JDK pode recusar; a resolução da referência
                    // então falha na validação e o documento é recusado — nunca aceito.
                }
            }
        }
    }

    private static Document parseDom(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(
                    new StringReader(new String(xmlBytes, StandardCharsets.UTF_8))));
        } catch (Exception e) {
            throw new IllegalStateException("parse para validação de assinatura falhou", e);
        }
    }

    /** Chave do KeyInfo: prefere o certificado X.509; aceita KeyValue (fixtures de teste). */
    private static final class SeletorChave extends KeySelector {
        private X509Certificate certificate;

        Optional<String> signerDocument() {
            if (certificate == null) {
                return Optional.empty();
            }
            String dn = certificate.getSubjectX500Principal().getName();
            // CN de certificado de documento fiscal: "NOME...:CNPJ". Só o sufixo
            // numérico de 14 dígitos depois do ':' do RDN CN é o documento;
            // qualquer outro formato devolve vazio (ausência de dado, nunca um chute).
            for (String rdn : dn.split(",")) {
                String parte = rdn.strip();
                if (parte.startsWith("CN=")) {
                    String cn = parte.substring(3);
                    int colon = cn.lastIndexOf(':');
                    if (colon < 0) {
                        return Optional.empty();
                    }
                    String suffix = cn.substring(colon + 1).replaceAll("\\D", "");
                    return suffix.length() == 14 ? Optional.of(suffix) : Optional.empty();
                }
            }
            return Optional.empty();
        }

        @Override
        public KeySelectorResult select(KeyInfo keyInfo, Purpose purpose, javax.xml.crypto.AlgorithmMethod method,
                                        XMLCryptoContext context) throws KeySelectorException {
            if (keyInfo == null) {
                throw new KeySelectorException("Signature sem KeyInfo: sem chave declarada, não há o que validar");
            }
            // Correção A12(a) da revisão: o certificado tem precedência e a
            // ambiguidade é recusada — com X509Data + KeyValue de chaves
            // DIFERENTES, não se sabe qual chave valida nem a qual documento
            // de assinante ela corresponde; sem esta trava, o cruzamento
            // assinante × emitente do serviço conferiria o certificado de
            // uma chave que não foi a que validou.
            PublicKey keyDoCertificado = null;
            PublicKey keyDoKeyValue = null;
            for (Object item : keyInfo.getContent()) {
                if (item instanceof X509Data x509) {
                    for (Object entry : x509.getContent()) {
                        if (entry instanceof X509Certificate cert) {
                            certificate = cert;
                            keyDoCertificado = cert.getPublicKey();
                        }
                    }
                } else if (item instanceof KeyValue kv) {
                    try {
                        keyDoKeyValue = kv.getPublicKey();
                    } catch (Exception e) {
                        throw new KeySelectorException("KeyValue ilegível no KeyInfo", e);
                    }
                }
            }
            if (keyDoCertificado != null && keyDoKeyValue != null
                    && !Arrays.equals(keyDoCertificado.getEncoded(), keyDoKeyValue.getEncoded())) {
                throw new KeySelectorException("KeyInfo ambíguo: certificado X.509 e KeyValue"
                        + " com chaves públicas diferentes");
            }
            PublicKey key = keyDoCertificado != null ? keyDoCertificado : keyDoKeyValue;
            if (key == null) {
                throw new KeySelectorException("KeyInfo sem certificado X.509 nem chave pública utilizável");
            }
            Key chave = key;
            return () -> chave;
        }
    }
}
