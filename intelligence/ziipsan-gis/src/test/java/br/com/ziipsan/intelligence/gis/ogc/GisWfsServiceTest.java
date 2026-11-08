package br.com.ziipsan.intelligence.gis.ogc;

import br.com.ziipsan.intelligence.gis.export.GisExportService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GisWfsServiceTest {

    private final List<GisWfsService.TransactionDraft> drafts = new ArrayList<>();
    private final UUID pkg = UUID.randomUUID();
    private GisWfsService svc() {
        GisExportService.FeatureSource src = (t, layer) -> List.of();
        var packages = new GisWfsService.WfsPackagePort() {
            public UUID openDraftPackage(UUID tenantId, UUID userId, String channel) {
                assertEquals("WFS", channel); return pkg;
            }
            public void addDraftItem(UUID tenantId, UUID packageId, GisWfsService.TransactionDraft d) { drafts.add(d); }
        };
        return new GisWfsService(new GisOgcService(src), packages);
    }

    @Test void capabilitiesListaOperacoesETiposVisiveis() {
        String cap = svc().getCapabilities(false);
        assertTrue(cap.contains("Transaction")); assertTrue(cap.contains("water-arcs"));
        assertFalse(cap.contains("properties"));
    }

    @Test void transactionGravaNoPacoteENuncaNaRedeEmOperacao() {
        String xml = "<wfs:Transaction xmlns:wfs=\"http://www.opengis.net/wfs/2.0\">"
                + "<wfs:Insert><water-arcs><dn>150</dn></water-arcs></wfs:Insert></wfs:Transaction>";
        Map<String,Object> out = svc().transaction(UUID.randomUUID(), UUID.randomUUID(), xml, true);
        assertEquals(Boolean.FALSE, out.get("appliedToOperationalNetwork"));
        assertEquals("DRAFT", out.get("packageStatus"));
        assertEquals(1, drafts.size());
        assertEquals("INSERT", drafts.get(0).operation());
    }

    @Test void transactionEmCamadaPessoalSemPermissaoERecusada() {
        String xml = "<wfs:Transaction xmlns:wfs=\"http://www.opengis.net/wfs/2.0\">"
                + "<wfs:Delete typeName=\"properties\" rid=\"p1\"/></wfs:Transaction>";
        assertThrows(SecurityException.class, () -> svc().transaction(UUID.randomUUID(), UUID.randomUUID(), xml, false));
    }

    @Test void transactionComDoctypeEXxeERecusada() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE wfs:Transaction [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<wfs:Transaction xmlns:wfs=\"http://www.opengis.net/wfs/2.0\"><wfs:Insert><water-arcs/></wfs:Insert></wfs:Transaction>";
        assertThrows(IllegalArgumentException.class, () -> svc().transaction(UUID.randomUUID(), UUID.randomUUID(), xml, true));
    }

    // --- Correção D4b/D3: GetFeature com a audiência REAL do chamador ---

    private GisWfsService svcLeitura() {
        var prop = new br.com.ziipsan.intelligence.gis.export.GisExportFeature("p1","properties","PROPERTY","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of("code","p1"), true,false,false,"q1","IN_SERVICE");
        var sensitive = new br.com.ziipsan.intelligence.gis.export.GisExportFeature("s1","water-nodes","VALVE","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of(), false,false,true,null,"IN_SERVICE");
        var critical = new br.com.ziipsan.intelligence.gis.export.GisExportFeature("c1","water-nodes","INTAKE","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of(), false,true,false,null,"IN_SERVICE");
        GisExportService.FeatureSource src = (t, layer) -> switch (layer) {
            case "properties" -> List.of(prop);
            case "water-nodes" -> List.of(sensitive, critical);
            default -> List.of();
        };
        return new GisWfsService(new GisOgcService(src), null);
    }

    @Test void getFeatureParaTerceiroNaoDevolvePessoalIndividual() {
        // Sonda D4b da revisão: o GetFeature fixava BACKOFFICE e a
        // feição pessoal individual escapava pela fachada WFS.
        UUID t = UUID.randomUUID();
        String body = svcLeitura().getFeature(t, t, "properties", null, true,
                br.com.ziipsan.intelligence.gis.export.GisExportGuard.Audience.THIRD_PARTY, List.of(), 5);
        assertTrue(body.contains("aggregated"));
        assertFalse(body.contains("\"p1\""));
    }

    @Test void getFeatureParaTerceiroRecusaSensivelECritica() {
        UUID t = UUID.randomUUID();
        var prop = new br.com.ziipsan.intelligence.gis.export.GisExportFeature("p1","properties","PROPERTY","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of("code","p1"), true,false,false,"q1","IN_SERVICE");
        var sensitive = new br.com.ziipsan.intelligence.gis.export.GisExportFeature("s1","water-nodes","VALVE","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of(), false,false,true,null,"IN_SERVICE");
        GisExportService.FeatureSource src = (tenant, layer) ->
                layer.equals("water-nodes") ? List.of(sensitive) : List.of(prop);
        var wfs = new GisWfsService(new GisOgcService(src), null);
        assertThrows(IllegalArgumentException.class, () -> wfs.getFeature(t, t, "water-nodes", null, true,
                br.com.ziipsan.intelligence.gis.export.GisExportGuard.Audience.THIRD_PARTY, List.of(), 5));
        String agg = wfs.getFeature(t, t, "properties", null, true,
                br.com.ziipsan.intelligence.gis.export.GisExportGuard.Audience.THIRD_PARTY, List.of(), 5);
        assertTrue(agg.contains("aggregated")); assertFalse(agg.contains("\"p1\""));
    }

    @Test void transactionComXmlFormatadoLeTipoDaFeicao() {
        // Correção D15c: whitespace após <wfs:Insert> não pode quebrar
        // a extração do tipo (o primeiro filho é nó de texto), e o
        // elemento da feição não entra como propriedade de si mesmo.
        String xml = "<wfs:Transaction xmlns:wfs=\"http://www.opengis.net/wfs/2.0\">\n"
                + "  <wfs:Insert>\n"
                + "    <water-arcs><dn>150</dn></water-arcs>\n"
                + "  </wfs:Insert>\n"
                + "</wfs:Transaction>";
        Map<String,Object> out = svc().transaction(UUID.randomUUID(), UUID.randomUUID(), xml, true);
        assertEquals(1, drafts.size());
        assertEquals("water-arcs", drafts.get(0).featureType());
        assertEquals("150", drafts.get(0).properties().get("dn"));
        assertFalse(drafts.get(0).properties().containsKey("water-arcs"));
    }

    @Test void describeFeatureTypeDeTipoDesconhecidoRecusa() {
        assertThrows(IllegalArgumentException.class, () -> svc().describeFeatureType("nada"));
        assertTrue(svc().describeFeatureType("water-arcs").contains("xsd:schema"));
    }
}
