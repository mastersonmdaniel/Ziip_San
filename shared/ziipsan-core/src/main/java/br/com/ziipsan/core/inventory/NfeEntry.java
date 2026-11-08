package br.com.ziipsan.core.inventory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.xml.sax.InputSource;

/**
 * Entrada por NF-e (ED-58): leitura do XML da nota do fornecedor e
 * CONFERENCIA item a item contra o esperado (o que foi pedido/recebido
 * fisicamente, informado pelo chamador — compras sao fora do produto,
 * ADR-0028 (i), logo o "esperado" NAO e um pedido de compra do sistema).
 * Parser do JDK com XXE desligado (DOCTYPE recusado).
 *
 * <p>Divergencia (item faltando, a mais ou quantidade diferente) NAO
 * posta entrada: devolve o relatorio de divergencias para o operador
 * decidir (ver WarehouseService.receiveFromNfe).
 */
public final class NfeEntry {
    private NfeEntry() {}

    public record NfeItem(String productCode, String description, BigDecimal qty,
                          BigDecimal unitValue, String lotCode, String lotExpiry) {}
    public record ParsedNfe(String accessKey, String supplierDocument, List<NfeItem> items) {}

    public enum LineStatus { MATCHED, QTY_DIVERGENT, UNEXPECTED_ITEM, MISSING_ITEM }
    public record ConferenceLine(String productCode, LineStatus status,
                                 BigDecimal expectedQty, BigDecimal invoicedQty) {}
    public record Conference(List<ConferenceLine> lines) {
        public boolean confered() { return lines.stream().allMatch(l -> l.status() == LineStatus.MATCHED); }
        public List<ConferenceLine> divergences() { return lines.stream().filter(l -> l.status() != LineStatus.MATCHED).toList(); }
    }

    public static ParsedNfe parse(String xml) {
        if (xml == null || xml.isBlank()) throw new IllegalArgumentException("XML da NF-e e obrigatorio");
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element inf = (Element) doc.getElementsByTagName("infNFe").item(0);
            if (inf == null) throw new IllegalArgumentException("XML sem infNFe: nao e uma NF-e");
            String key = inf.getAttribute("Id");
            String cnpj = text(doc, "emit", "CNPJ");
            List<NfeItem> items = new ArrayList<>();
            NodeList dets = doc.getElementsByTagName("det");
            for (int i = 0; i < dets.getLength(); i++) {
                Element det = (Element) dets.item(i);
                Element prod = (Element) det.getElementsByTagName("prod").item(0);
                String lot = null, expiry = null;
                NodeList rastro = det.getElementsByTagName("rastro");
                if (rastro.getLength() > 0) {
                    Element r = (Element) rastro.item(0);
                    lot = childText(r, "nLote"); expiry = childText(r, "dVal");
                }
                items.add(new NfeItem(childText(prod, "cProd"), childText(prod, "xProd"),
                        decimal(childText(prod, "qCom")), decimal(childText(prod, "vUnCom")), lot, expiry));
            }
            if (items.isEmpty()) throw new IllegalArgumentException("NF-e sem itens (det/prod)");
            return new ParsedNfe(key, cnpj, List.copyOf(items));
        } catch (IllegalArgumentException e) { throw e; }
          catch (Exception e) { throw new IllegalArgumentException("XML da NF-e ilegivel: " + e.getMessage(), e); }
    }

    /** Conferencia contra o esperado (codigo -> quantidade esperada). */
    public static Conference check(ParsedNfe nfe, Map<String, BigDecimal> expectedByCode) {
        Map<String, BigDecimal> expected = expectedByCode == null ? Map.of() : expectedByCode;
        List<ConferenceLine> lines = new ArrayList<>();
        Map<String, BigDecimal> seen = new LinkedHashMap<>();
        for (NfeItem it : nfe.items()) {
            seen.merge(it.productCode(), it.qty(), BigDecimal::add);
        }
        for (Map.Entry<String, BigDecimal> e : seen.entrySet()) {
            BigDecimal exp = expected.get(e.getKey());
            if (exp == null) lines.add(new ConferenceLine(e.getKey(), LineStatus.UNEXPECTED_ITEM, null, e.getValue()));
            else if (exp.compareTo(e.getValue()) != 0) lines.add(new ConferenceLine(e.getKey(), LineStatus.QTY_DIVERGENT, exp, e.getValue()));
            else lines.add(new ConferenceLine(e.getKey(), LineStatus.MATCHED, exp, e.getValue()));
        }
        for (Map.Entry<String, BigDecimal> e : expected.entrySet()) {
            if (!seen.containsKey(e.getKey())) lines.add(new ConferenceLine(e.getKey(), LineStatus.MISSING_ITEM, e.getValue(), null));
        }
        return new Conference(List.copyOf(lines));
    }

    private static String text(Document doc, String parent, String tag) {
        NodeList p = doc.getElementsByTagName(parent);
        if (p.getLength() == 0) return null;
        return childText((Element) p.item(0), tag);
    }
    private static String childText(Element parent, String tag) {
        NodeList n = parent.getElementsByTagName(tag);
        return n.getLength() == 0 ? null : n.item(0).getTextContent().trim();
    }
    private static BigDecimal decimal(String s) { return s == null ? null : new BigDecimal(s); }
}
