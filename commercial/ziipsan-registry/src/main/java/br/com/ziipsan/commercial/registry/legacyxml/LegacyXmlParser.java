package br.com.ziipsan.commercial.registry.legacyxml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Leitura estrutural (sem assinatura) do XML autorizado de legado (P2.19):
 * extrai chave de acesso, emitente, modelo, competência e protocolo de
 * autorização de qualquer invólucro usual do documento autorizado
 * ({@code nfeProc} com {@code protNFe}, ou o documento puro), procurando os
 * elementos pelo nome local — o parser não presume namespace nem leiaute
 * de um emissor específico, porque a origem é "sistema antigo ou emissor
 * usado no intervalo", declarado no mapeamento.
 *
 * <p>Parser seguro: namespace-aware, DOCTYPE proibido e acesso externo
 * desligado (XXE nunca resolve nada). O documento do DESTINATÁRIO nunca é
 * extraído nem devolvido: ele é dado pessoal, não entra no registro da
 * importação (V14_3 não tem coluna para ele) e o aceite manda importar os
 * XML reais só na LC.10 justamente por causa dele.
 *
 * <p>A conferência criptográfica da assinatura NÃO é feita aqui: é da
 * porta {@link XmlSignatureValidatorPort}. Aqui só se lê o que está
 * declarado no arquivo; divergência entre o declarado e o esperado é
 * decisão do serviço, com o motivo de {@link LegacyXmlRejection}.
 */
public final class LegacyXmlParser {

    /** Falha estrutural de leitura, com o motivo de recusa correspondente. */
    public static final class LegacyXmlParseException extends RuntimeException {
        private final LegacyXmlRejection rejection;

        public LegacyXmlParseException(LegacyXmlRejection rejection, String message) {
            super(message);
            this.rejection = rejection;
        }

        public LegacyXmlRejection rejection() {
            return rejection;
        }
    }

    /**
     * @param accessKey        chave de 44 dígitos (do {@code Id} do elemento de
     *                         informações ou do elemento {@code chNFe}/{@code chNFAge})
     * @param model            modelo (posições 21–22 da chave)
     * @param competence       competência AAMM embutida na chave
     * @param issuerFromKey    CNPJ do emitente embutido na chave (posições 7–20)
     * @param issuerFromBody   CNPJ do elemento {@code emit/CNPJ}, se presente
     * @param protocol         {@code infProt/nProt}, se presente
     * @param protocolStatus   {@code infProt/cStat}, se presente
     */
    public record ParsedXml(String accessKey, String model, YearMonth competence,
                            String issuerFromKey, String issuerFromBody,
                            String protocol, String protocolStatus) {
    }

    public ParsedXml parse(byte[] xmlBytes) {
        if (xmlBytes == null || xmlBytes.length == 0) {
            throw new LegacyXmlParseException(LegacyXmlRejection.XML_INVALID,
                    "Arquivo vazio: nada a importar.");
        }
        Document doc = parseDom(xmlBytes);
        String accessKey = extrairChave(doc);
        if (accessKey == null) {
            throw new LegacyXmlParseException(LegacyXmlRejection.ACCESS_KEY_INVALID,
                    "Nenhuma chave de acesso de 44 dígitos localizável no XML.");
        }
        if (digitoVerificador(accessKey) != accessKey.charAt(43) - '0') {
            throw new LegacyXmlParseException(LegacyXmlRejection.ACCESS_KEY_INVALID,
                    "Dígito verificador (módulo 11) da chave de acesso incorreto.");
        }
        YearMonth competence;
        try {
            int month = Integer.parseInt(accessKey.substring(4, 6));
            int year = 2000 + Integer.parseInt(accessKey.substring(2, 4));
            competence = YearMonth.of(year, month);
        } catch (RuntimeException e) {
            throw new LegacyXmlParseException(LegacyXmlRejection.ACCESS_KEY_INVALID,
                    "Competência (AAMM) da chave de acesso impossível.");
        }
        Element emit = primeiroPorNomeLocal(doc, "emit");
        String issuerFromBody = emit == null ? null : textoDoFilho(emit, "CNPJ");
        if (issuerFromBody != null) {
            issuerFromBody = issuerFromBody.replaceAll("\\D", "");
        }
        String protocol = textoDoPrimeiro(doc, "nProt");
        String protocolStatus = textoDoPrimeiro(doc, "cStat");
        return new ParsedXml(accessKey, accessKey.substring(20, 22), competence,
                accessKey.substring(6, 20), issuerFromBody,
                protocol == null || protocol.isBlank() ? null : protocol.strip(),
                protocolStatus == null || protocolStatus.isBlank() ? null : protocolStatus.strip());
    }

    /** DV módulo 11 das 43 primeiras posições: pesos 2..9, da direita para a esquerda (mesma regra da chave da P2.6). */
    static int digitoVerificador(String chave44) {
        int sum = 0;
        int weight = 2;
        for (int i = 42; i >= 0; i--) {
            sum += (chave44.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
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
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(
                    new String(xmlBytes, StandardCharsets.UTF_8))));
        } catch (Exception e) {
            throw new LegacyXmlParseException(LegacyXmlRejection.XML_INVALID,
                    "Conteúdo não é XML bem-formado aceito pelo parser seguro (" + e.getClass().getSimpleName() + ").");
        }
    }

    /**
     * Chave de acesso das DUAS fontes possíveis, cruzadas (correção A8 da
     * revisão): o {@code Id} do elemento de informações (que é o que a
     * assinatura cobre) e o elemento {@code chNFe}/{@code chNFAge} do
     * protocolo (que a assinatura pode NÃO cobrir, porque o {@code protNFe}
     * é anexado depois). Se as duas fontes existem e divergem, o arquivo é
     * internamente inconsistente — recusa {@code ACCESS_KEY_INVALID},
     * nunca "a primeira que aparecer".
     */
    private static String extrairChave(Document doc) {
        List<Element> todos = new ArrayList<>();
        coletar(doc.getDocumentElement(), todos);
        String chaveDoId = null;
        String chaveDoElemento = null;
        for (Element el : todos) {
            if (chaveDoId == null) {
                String id = el.getAttribute("Id");
                if (id != null) {
                    String digitos = id.replaceAll("\\D", "");
                    if (digitos.length() == 44) {
                        chaveDoId = digitos;
                    }
                }
            }
            if (chaveDoElemento == null) {
                String local = nomeLocal(el);
                if (("chNFe".equals(local) || "chNFAge".equals(local) || "chave".equals(local))
                        && el.getTextContent() != null) {
                    String digitos = el.getTextContent().replaceAll("\\D", "");
                    if (digitos.length() == 44) {
                        chaveDoElemento = digitos;
                    }
                }
            }
        }
        if (chaveDoId != null && chaveDoElemento != null && !chaveDoId.equals(chaveDoElemento)) {
            throw new LegacyXmlParseException(LegacyXmlRejection.ACCESS_KEY_INVALID,
                    "Chave do Id do elemento de informações diverge da chave declarada"
                            + " no elemento chNFe/chNFAge do documento.");
        }
        return chaveDoId != null ? chaveDoId : chaveDoElemento;
    }

    private static String textoDoPrimeiro(Document doc, String nomeLocal) {
        Element el = primeiroPorNomeLocal(doc, nomeLocal);
        return el == null ? null : el.getTextContent();
    }

    private static Element primeiroPorNomeLocal(Document doc, String nomeLocal) {
        List<Element> todos = new ArrayList<>();
        coletar(doc.getDocumentElement(), todos);
        for (Element el : todos) {
            if (nomeLocal.equals(nomeLocal(el))) {
                return el;
            }
        }
        return null;
    }

    private static String textoDoFilho(Element pai, String nomeLocal) {
        NodeList filhos = pai.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            Node n = filhos.item(i);
            if (n instanceof Element el && nomeLocal.equals(nomeLocal(el))) {
                return el.getTextContent();
            }
        }
        return null;
    }

    private static void coletar(Element el, List<Element> out) {
        if (el == null) {
            return;
        }
        out.add(el);
        NodeList filhos = el.getChildNodes();
        for (int i = 0; i < filhos.getLength(); i++) {
            if (filhos.item(i) instanceof Element filho) {
                coletar(filho, out);
            }
        }
    }

    private static String nomeLocal(Element el) {
        String local = el.getLocalName();
        if (local != null) {
            return local;
        }
        String nome = el.getNodeName();
        int colon = nome.indexOf(':');
        return colon >= 0 ? nome.substring(colon + 1) : nome;
    }
}
