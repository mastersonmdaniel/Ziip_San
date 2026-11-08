package br.com.ziipsan.intelligence.gis.ogc;

import br.com.ziipsan.intelligence.gis.export.GisExportFeature;
import br.com.ziipsan.intelligence.gis.export.GisExportGuard;
import br.com.ziipsan.intelligence.gis.export.GisExportService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GisOgcServiceTest {

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final GisExportFeature arc = new GisExportFeature("a1","water-arcs","PIPE","LINESTRING",
            List.of(List.of(-48.0,-15.0), List.of(-48.1,-15.1)), Map.of("dn",100), false,false,false,null,"IN_SERVICE");
    private final GisExportFeature prop = new GisExportFeature("p1","properties","PROPERTY","POINT",
            List.of(List.of(-48.0,-15.0)), Map.of("code","p1"), true,false,false,"q1","IN_SERVICE");

    private GisOgcService svc() {
        GisExportService.FeatureSource src = (t, layer) -> {
            // A fonte só devolve dado do tenant A: pedir como B não vaza a feição.
            if (!t.equals(tenantA)) return List.of();
            return layer.equals("properties") ? List.of(prop) : List.of(arc);
        };
        return new GisOgcService(src);
    }

    @Test void conformanceDeclaraSomenteOImplementado() {
        // Correção D6: a declaração anterior (features-3/queryables,
        // features-3/filtering, cql2-text, oas30) excedia o implementado —
        // não há endpoint /queryables, o CQL2 é o subconjunto de igualdades
        // com AND e não há descrição OpenAPI da API. Declarar só o real:
        // Core + GeoJSON (parte 1) e CRS (parte 2).
        String c = svc().conformance();
        assertTrue(c.contains("ogcapi-features-1/1.0/conf/core"));
        assertTrue(c.contains("ogcapi-features-1/1.0/conf/geojson"));
        assertTrue(c.contains("ogcapi-features-2/1.0/conf/crs"));
        assertFalse(c.contains("features-3"));
        assertFalse(c.contains("queryables"));
        assertFalse(c.contains("cql2"));
        assertFalse(c.contains("oas30"));
    }

    @Test void itemsTrazCamposDoCore() {
        String body = svc().items(tenantA, tenantA, "water-arcs", null, true,
                GisExportGuard.Audience.BACKOFFICE, List.of(), 5);
        assertTrue(body.contains("numberMatched"));
        assertTrue(body.contains("numberReturned"));
        assertTrue(body.contains("timeStamp"));
    }

    // --- Correção D3/D4: sondas da revisão independente viram testes ---

    private GisOgcService svcCom(java.util.Map<String, List<GisExportFeature>> porCamada) {
        GisExportService.FeatureSource src = (t, layer) ->
                t.equals(tenantA) ? porCamada.getOrDefault(layer, List.of()) : List.of();
        return new GisOgcService(src);
    }

    @Test void itemsParaTerceiroRecusaMarcadorSensivel() {
        var sensitive = new GisExportFeature("s1","water-nodes","VALVE","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of(), false, false, true, null, "IN_SERVICE");
        var s = svcCom(Map.of("water-nodes", List.of(sensitive)));
        assertThrows(IllegalArgumentException.class, () -> s.items(tenantA, tenantA, "water-nodes",
                null, true, GisExportGuard.Audience.THIRD_PARTY, List.of(), 5));
    }

    @Test void itemsParaTerceiroRecusaInfraestruturaCritica() {
        var critical = new GisExportFeature("c1","water-nodes","INTAKE","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of(), false, true, false, null, "IN_SERVICE");
        var s = svcCom(Map.of("water-nodes", List.of(critical)));
        assertThrows(IllegalArgumentException.class, () -> s.items(tenantA, tenantA, "water-nodes",
                null, true, GisExportGuard.Audience.THIRD_PARTY, List.of(), 5));
    }

    @Test void itemParaTerceiroNaoDevolvePessoalIndividual() {
        // Sonda D4a da revisão: item() não recebia audiência e devolvia
        // a feição pessoal individual a credencial de terceiro.
        assertThrows(IllegalArgumentException.class, () -> svc().item(tenantA, tenantA, "properties", "p1",
                true, GisExportGuard.Audience.THIRD_PARTY));
    }

    @Test void itemParaTerceiroRecusaSensivelECriticaEmCamadaNaoPessoal() {
        var sensitive = new GisExportFeature("s1","water-nodes","VALVE","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of(), false, false, true, null, "IN_SERVICE");
        var critical = new GisExportFeature("c1","water-nodes","INTAKE","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of(), false, true, false, null, "IN_SERVICE");
        var s = svcCom(Map.of("water-nodes", List.of(sensitive, critical)));
        assertThrows(IllegalArgumentException.class, () -> s.item(tenantA, tenantA, "water-nodes", "s1",
                true, GisExportGuard.Audience.THIRD_PARTY));
        assertThrows(IllegalArgumentException.class, () -> s.item(tenantA, tenantA, "water-nodes", "c1",
                true, GisExportGuard.Audience.THIRD_PARTY));
    }

    @Test void itemNoBackofficeComPermissaoDevolvePessoal() {
        String body = svc().item(tenantA, tenantA, "properties", "p1",
                true, GisExportGuard.Audience.BACKOFFICE);
        assertTrue(body.contains("p1"));
    }

    @Test void itemsParaTerceiroDerivaAgregadosDaFonteQuandoNaoFornecidos() {
        // Correção D7: sem lista de agregados do chamador, o serviço
        // deriva da fonte (célula → contagem) em vez de devolver vazio.
        java.util.List<GisExportFeature> seis = new java.util.ArrayList<>();
        for (int i = 1; i <= 6; i++) seis.add(new GisExportFeature("p" + i, "properties", "PROPERTY", "POINT",
                List.of(List.of(-48.0,-15.0)), Map.of("code","p" + i), true, false, false, "q1", "IN_SERVICE"));
        var s = svcCom(Map.of("properties", seis));
        String body = s.items(tenantA, tenantA, "properties", null, false,
                GisExportGuard.Audience.THIRD_PARTY, List.of(), 5);
        assertTrue(body.contains("q1")); assertTrue(body.contains("aggregated"));
        assertFalse(body.contains("\"p1\""));
    }

    @Test void parametroDeAgregacaoDoTenantPrevaleceSobreOPisoDoChamador() {
        // Correção D7: tenant configurou 10 — célula com 6 não publica,
        // embora o chamador tenha passado o piso 5.
        java.util.List<GisExportFeature> seis = new java.util.ArrayList<>();
        for (int i = 1; i <= 6; i++) seis.add(new GisExportFeature("p" + i, "properties", "PROPERTY", "POINT",
                List.of(List.of(-48.0,-15.0)), Map.of("code","p" + i), true, false, false, "q1", "IN_SERVICE"));
        var s = svcCom(Map.of("properties", seis));
        s.setTenantParameterPort(tenant -> 10);
        String body = s.items(tenantA, tenantA, "properties", null, false,
                GisExportGuard.Audience.THIRD_PARTY, List.of(), 5);
        assertFalse(body.contains("q1"));
    }

    @Test void colecoesPessoaisSomeSemPermissao() {
        assertTrue(svc().collections(false).stream().noneMatch(c -> Boolean.TRUE.equals(c.get("personal"))));
        assertTrue(svc().collections(true).stream().anyMatch(c -> Boolean.TRUE.equals(c.get("personal"))));
    }

    @Test void tenantDeclaradoDiversoDoContextoERecusado() {
        assertThrows(SecurityException.class, () -> svc().items(tenantA, tenantB, "water-arcs",
                null, true, GisExportGuard.Audience.BACKOFFICE, List.of(), 5));
    }

    @Test void tenantBNaoLeFeicaoDoTenantA() {
        String body = svc().items(tenantB, tenantB, "water-arcs", null, true,
                GisExportGuard.Audience.BACKOFFICE, List.of(), 5);
        assertFalse(body.contains("a1"));
    }

    @Test void pessoalParaTerceiroViraAgregadoSemIndividuo() {
        String body = svc().items(tenantA, tenantA, "properties", null, false,
                GisExportGuard.Audience.THIRD_PARTY,
                List.of(new GisExportGuard.PersonalAggregate("q1", 3),
                        new GisExportGuard.PersonalAggregate("q2", 6)), 5);
        assertFalse(body.contains("p1")); assertTrue(body.contains("q2")); assertTrue(body.contains("aggregated"));
    }

    @Test void pessoalNoBackofficeSemPermissaoERecusado() {
        assertThrows(SecurityException.class, () -> svc().items(tenantA, tenantA, "properties",
                null, false, GisExportGuard.Audience.BACKOFFICE, List.of(), 5));
    }

    @Test void bboxELimitEFiltroCqlFuncionam() {
        var q = GisOgcQuery.parse("-49,-16,-47,-14", null, "10", "dn = 100");
        String body = svc().items(tenantA, tenantA, "water-arcs", q, true,
                GisExportGuard.Audience.BACKOFFICE, List.of(), 5);
        assertTrue(body.contains("a1"));
        var q2 = GisOgcQuery.parse("0,0,1,1", null, null, null);
        assertFalse(svc().items(tenantA, tenantA, "water-arcs", q2, true,
                GisExportGuard.Audience.BACKOFFICE, List.of(), 5).contains("a1"));
        var q3 = GisOgcQuery.parse(null, null, null, "dn = 200");
        assertFalse(svc().items(tenantA, tenantA, "water-arcs", q3, true,
                GisExportGuard.Audience.BACKOFFICE, List.of(), 5).contains("a1"));
    }

    @Test void datetimeFiltraPelaPropriedadeDatetimeENuncaIgnora() {
        var comData = new GisExportFeature("a9","water-arcs","PIPE","LINESTRING",
                List.of(List.of(-48.0,-15.0), List.of(-48.1,-15.1)), Map.of("datetime","2026-06-01T00:00:00Z"), false,false,false,null,"IN_SERVICE");
        var q = GisOgcQuery.parse(null, "2026-01-01T00:00:00Z/2026-12-31T23:59:59Z", null, null);
        assertEquals(1, q.apply(List.of(comData)).size());
        // Sem a propriedade datetime, a feição é excluída quando datetime é pedido.
        assertEquals(0, q.apply(List.of(arc)).size());
        var fora = GisOgcQuery.parse(null, "2027-01-01T00:00:00Z/2027-12-31T23:59:59Z", null, null);
        assertEquals(0, fora.apply(List.of(comData)).size());
    }

    @Test void filtroForaDoSubconjuntoERecusadoNuncaIgnorado() {
        assertThrows(IllegalArgumentException.class, () -> GisOgcQuery.parse(null, null, null, "dn > 100").apply(List.of(arc)));
        assertThrows(IllegalArgumentException.class, () -> GisOgcQuery.parse(null, null, "99999", null));
        assertThrows(IllegalArgumentException.class, () -> GisOgcQuery.parse("1,2,3", null, null, null));
    }
}
