package br.com.ziipsan.intelligence.gis.ogc;

import br.com.ziipsan.intelligence.gis.export.GisExportGuard;
import br.com.ziipsan.intelligence.gis.export.GisMgbMetadata;
import br.com.ziipsan.intelligence.gis.publication.GisTileLayers;

import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fachada WFS 2.0 na {@code api} (GIS-17, §7.2): GetCapabilities,
 * DescribeFeatureType e GetFeature sobre a mesma leitura do OGC API;
 * {@code Transaction} NUNCA grava na rede em operação — cada
 * operação vira item de rascunho no pacote do usuário (canal WFS),
 * pela porta {@link WfsPackagePort} que a integração liga ao
 * GisChangeSetService da GIS-04 (pacote DRAFT; submissão e revisão
 * por outra pessoa seguem o fluxo normal). XML de entrada é lido
 * com DOCTYPE proibido (sem XXE).
 *
 * <p>Correção ED-67 (revisão independente, D4b): o {@code GetFeature}
 * NÃO fixa mais a audiência em BACKOFFICE — ele propaga a audiência
 * real do chamador (contexto), os agregados e o parâmetro de
 * agregação ao {@link GisOgcService}, de modo que as guardas do §12
 * valem neste caminho como em qualquer outro.
 */
@Service
public class GisWfsService {

    public record TransactionDraft(String operation, String featureType, String featureId,
                                   Map<String, Object> properties) {}

    /** Porta para o pacote de edição (GIS-04): abre/obtém o DRAFT do usuário e anexa o item. */
    public interface WfsPackagePort {
        UUID openDraftPackage(UUID tenantId, UUID userId, String channel);
        void addDraftItem(UUID tenantId, UUID packageId, TransactionDraft draft);
    }

    private final GisOgcService ogc;
    private final WfsPackagePort packages;

    public GisWfsService(GisOgcService ogc, WfsPackagePort packages){ this.ogc = ogc; this.packages = packages; }

    public String getCapabilities(boolean canViewPersonal) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<wfs:WFS_Capabilities version=\"2.0.0\" xmlns:wfs=\"http://www.opengis.net/wfs/2.0\" xmlns:ows=\"http://www.opengis.net/ows/1.1\">");
        sb.append("<ows:OperationsMetadata>");
        for (String op : List.of("GetCapabilities","DescribeFeatureType","GetFeature","Transaction"))
            sb.append("<ows:Operation name=\"").append(op).append("\"/>");
        sb.append("</ows:OperationsMetadata><wfs:FeatureTypeList>");
        for (var c : ogc.collections(canViewPersonal))
            sb.append("<wfs:FeatureType><wfs:Name>").append(GisMgbMetadata.esc(String.valueOf(c.get("id"))))
              .append("</wfs:Name></wfs:FeatureType>");
        return sb.append("</wfs:FeatureTypeList></wfs:WFS_Capabilities>\n").toString();
    }

    public String describeFeatureType(String typeName) {
        GisTileLayers.byKey(typeName).orElseThrow(() -> new IllegalArgumentException("feature type desconhecido: " + typeName));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"urn:ziipsan:gis\">"
            + "<xsd:element name=\"" + GisMgbMetadata.esc(typeName) + "\"><xsd:complexType><xsd:sequence>"
            + "<xsd:element name=\"id\" type=\"xsd:string\"/>"
            + "<xsd:element name=\"typeCode\" type=\"xsd:string\"/>"
            + "<xsd:element name=\"lifecycleStatus\" type=\"xsd:string\"/>"
            + "<xsd:element name=\"geometry\" type=\"xsd:string\"/>"
            + "</xsd:sequence></xsd:complexType></xsd:element></xsd:schema>\n";
    }

    /**
     * GetFeature com a audiência REAL do chamador (D4b): terceiro
     * recebe o mesmo tratamento do OGC API — camada pessoal só
     * agregada e guardas do §12 sobre marcador sensível e
     * infraestrutura crítica.
     */
    public String getFeature(UUID tenant, UUID claimed, String typeName, GisOgcQuery q, boolean canViewPersonal,
                             GisExportGuard.Audience audience,
                             List<GisExportGuard.PersonalAggregate> aggregates, int aggregationParam) {
        return ogc.items(tenant, claimed, typeName, q, canViewPersonal, audience, aggregates, aggregationParam);
    }

    /** Transaction: devolve o resumo e o id do pacote DRAFT — a rede em operação não é tocada. */
    public Map<String,Object> transaction(UUID tenantId, UUID userId, String xml, boolean canViewPersonal) {
        if (packages == null) throw new IllegalStateException(
            "Transaction WFS sem a porta de pacotes (GIS-04) ligada — pendência de integração declarada");
        List<TransactionDraft> drafts = parseTransaction(xml, canViewPersonal);
        UUID pkg = packages.openDraftPackage(tenantId, userId, "WFS");
        for (TransactionDraft d : drafts) packages.addDraftItem(tenantId, pkg, d);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("packageId", pkg.toString()); out.put("packageStatus","DRAFT");
        out.put("operations", drafts.size()); out.put("appliedToOperationalNetwork", false);
        return out;
    }

    static List<TransactionDraft> parseTransaction(String xml, boolean canViewPersonal) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(true);
            var doc = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new StringReader(xml)));
            List<TransactionDraft> out = new ArrayList<>();
            for (String op : List.of("Insert","Update","Delete")) {
                NodeList nodes = doc.getElementsByTagNameNS("*", op);
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element el = (Element) nodes.item(i);
                    // Correção D15c: o primeiro filho pode ser nó de texto
                    // (whitespace de XML formatado) — procura-se o primeiro
                    // filho ELEMENTO; o elemento da feição não entra como
                    // propriedade de si mesmo.
                    Element featureEl = null;
                    String typeAttr = el.getAttribute("typeName");
                    if (typeAttr == null || typeAttr.isBlank()) {
                        NodeList children = el.getChildNodes();
                        for (int k = 0; k < children.getLength(); k++) {
                            if (children.item(k).getNodeType() == Node.ELEMENT_NODE) {
                                featureEl = (Element) children.item(k);
                                break;
                            }
                        }
                    }
                    final String type = (typeAttr != null && !typeAttr.isBlank()) ? typeAttr
                            : (featureEl == null ? null : featureEl.getLocalName());
                    // Correção D1: variável final capturável pela lambda.
                    final String resolvedType = type;
                    GisTileLayers layer = GisTileLayers.byKey(resolvedType)
                            .orElseThrow(() -> new IllegalArgumentException("Transaction para tipo desconhecido: " + resolvedType));
                    if (layer.personal() && !canViewPersonal)
                        throw new SecurityException("Transaction em camada pessoal exige gis.view-personal");
                    // Propriedades: filhos elemento DIRETOS do elemento da
                    // feição (Insert) ou da própria operação (Update/Delete).
                    Element propsSource = featureEl != null ? featureEl : el;
                    Map<String,Object> props = new LinkedHashMap<>();
                    NodeList kids = propsSource.getChildNodes();
                    for (int k = 0; k < kids.getLength(); k++) {
                        if (kids.item(k).getNodeType() != Node.ELEMENT_NODE) continue;
                        Element c = (Element) kids.item(k);
                        if (c.getTextContent() != null && !c.getTextContent().isBlank())
                            props.put(c.getLocalName(), c.getTextContent().trim());
                    }
                    String rid = el.getAttribute("rid");
                    out.add(new TransactionDraft(op.toUpperCase(), resolvedType,
                            rid == null || rid.isBlank() ? null : rid, props));
                }
            }
            if (out.isEmpty()) throw new IllegalArgumentException("Transaction sem Insert/Update/Delete");
            return out;
        } catch (IllegalArgumentException | SecurityException e) { throw e; }
          catch (Exception e) { throw new IllegalArgumentException("XML de Transaction inválido", e); }
    }
}
