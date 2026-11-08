package br.com.ziipsan.intelligence.gis.export;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class GisExportServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final GisExportFeature arc = new GisExportFeature("a1","water-arcs","PIPE","LINESTRING",
            List.of(List.of(-48.0,-15.0), List.of(-48.1,-15.1)), Map.of("dn",100,"material","PVC"), false,false,false,null,"IN_SERVICE");

    private GisMgbMetadata.Metadata meta() {
        return new GisMgbMetadata.Metadata("Rede de água","Prestadora", List.of(-48.2,-15.2,-47.9,-14.9),
                4674, "Imbituba", "±0,10 m", Instant.parse("2026-01-01T00:00:00Z"), "cadastro GIS", "restrito", "mensal");
    }
    private GisExportService service(List<GisExportFeature> feats, GisExportService.BinaryExportPort bin,
                                     GisExportService.PersonalAccessRegistrar reg) {
        return new GisExportService((t, layer) -> feats, bin, reg);
    }
    private GisExportService.ExportRequest req(GisExportFormats fmt) {
        return new GisExportService.ExportRequest(fmt, "water-arcs", GisExportProfiles.Profile.GENERIC,
                GisExportGuard.Audience.BACKOFFICE, true, 5, List.of(), meta(), "rotina");
    }

    @Test void geoJsonValidoComGeometriaEPropriedades() {
        var r = service(List.of(arc), null, null).export(tenant, user, req(GisExportFormats.GEOJSON));
        String body = new String(r.content(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"FeatureCollection\""));
        assertTrue(body.contains("\"LineString\""));
        assertTrue(body.contains("\"dn\":100") || body.contains("\"dn\": 100") || body.contains("100"));
        assertTrue(r.metadataXml().contains("MD_Metadata"));
    }

    @Test void csvEDxfEKmlCarregamAFeicao() {
        var svc = service(List.of(arc), null, null);
        assertTrue(new String(svc.export(tenant,user,req(GisExportFormats.CSV)).content(), StandardCharsets.UTF_8).contains("a1"));
        String dxf = new String(svc.export(tenant,user,req(GisExportFormats.DXF)).content(), StandardCharsets.UTF_8);
        assertTrue(dxf.contains("LWPOLYLINE")); assertTrue(dxf.contains("water-arcs"));
        assertTrue(new String(svc.export(tenant,user,req(GisExportFormats.KML)).content(), StandardCharsets.UTF_8).contains("Placemark"));
    }

    @Test void binarioSemPortaGdalFalhaDeclarandoPendencia() {
        var ex = assertThrows(UnsupportedOperationException.class,
                () -> service(List.of(arc), null, null).export(tenant, user, req(GisExportFormats.GPKG)));
        assertTrue(ex.getMessage().contains("GDAL"));
    }

    @Test void semMetadadoMgbRecusa() {
        var bad = new GisExportService.ExportRequest(GisExportFormats.GEOJSON,"water-arcs",
                GisExportProfiles.Profile.GENERIC, GisExportGuard.Audience.BACKOFFICE, true, 5, List.of(), null, "x");
        assertThrows(IllegalArgumentException.class, () -> service(List.of(arc), null, null).export(tenant, user, bad));
    }

    @Test void pessoalRegistraAcessoAntesDeExportar() {
        var personal = new GisExportFeature("p1","properties","PROPERTY","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of("code","p1"), true,false,false,"q1","IN_SERVICE");
        AtomicBoolean registered = new AtomicBoolean(false);
        var svc = service(List.of(personal), null, (t,u,l,p) -> registered.set(true));
        var reqP = new GisExportService.ExportRequest(GisExportFormats.GEOJSON,"properties",
                GisExportProfiles.Profile.GENERIC, GisExportGuard.Audience.BACKOFFICE, true, 5, List.of(), meta(), "ficha");
        var r = svc.export(tenant, user, reqP);
        assertTrue(registered.get()); assertTrue(r.personalIncluded());
    }

    @Test void pessoalSemPermissaoNaoRegistraAcessoNemExporta() {
        var personal = new GisExportFeature("p1","properties","PROPERTY","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of("code","p1"), true,false,false,"q1","IN_SERVICE");
        AtomicBoolean registered = new AtomicBoolean(false);
        var svc = service(List.of(personal), null, (t,u,l,p) -> registered.set(true));
        var reqSem = new GisExportService.ExportRequest(GisExportFormats.GEOJSON,"properties",
                GisExportProfiles.Profile.GENERIC, GisExportGuard.Audience.BACKOFFICE, false, 5, List.of(), meta(), "ficha");
        assertThrows(IllegalArgumentException.class, () -> svc.export(tenant, user, reqSem));
        assertFalse(registered.get());
    }

    @Test void terceiroSoRecebeAgregadosAcimaDoPiso() {
        var personal = new GisExportFeature("p1","properties","PROPERTY","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of("code","p1"), true,false,false,"q1","IN_SERVICE");
        var reqT = new GisExportService.ExportRequest(GisExportFormats.GEOJSON,"properties",
                GisExportProfiles.Profile.GENERIC, GisExportGuard.Audience.THIRD_PARTY, false, 5,
                List.of(new GisExportGuard.PersonalAggregate("q1", 4),
                        new GisExportGuard.PersonalAggregate("q2", 7)), meta(), "prefeitura");
        var r = service(List.of(personal), null, null).export(tenant, user, reqT);
        String body = new String(r.content(), StandardCharsets.UTF_8);
        assertFalse(body.contains("p1")); assertTrue(body.contains("q2")); assertFalse(body.contains("\"q1\""));
        assertEquals(1, r.publishedAggregates().size());
    }

    @Test void perfilSaneagoDeclaradoNaoValidadoECorsanValidado() {
        assertFalse(GisExportProfiles.validated(GisExportProfiles.Profile.SANEAGO_GISWATER_PROPOSTA));
        assertTrue(GisExportProfiles.validated(GisExportProfiles.Profile.CORSAN_ANEXO_A));
        assertEquals(28, GisExportProfiles.CORSAN_LAYERS.size());
        assertEquals("arc", GisExportProfiles.layerFor(GisExportProfiles.Profile.SANEAGO_GISWATER_PROPOSTA, arc));
    }

    @Test void nomesShapefileComNoMaximoDezCaracteresEUnicos() {
        var map = GisExportProfiles.shapefileNameMap(List.of("identificador_longo", "identificador_curto", "dn"));
        assertTrue(map.values().stream().allMatch(v -> v.length() <= 10));
        assertEquals(3, new java.util.HashSet<>(map.values()).size());
    }

    @Test void geoJsonDePontoSegueRfc7946ComPosicaoUnica() {
        // Correção D5: POINT é UMA posição [lon,lat], nunca [[lon,lat]].
        var pt = new GisExportFeature("pt1","water-nodes","VALVE","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of(), false,false,false,null,"IN_SERVICE");
        String gj = GisExportService.geoJson(List.of(pt), GisExportProfiles.Profile.GENERIC);
        assertTrue(gj.contains("\"coordinates\":[-48.0,-15.0]") || gj.contains("\"coordinates\": [-48.0, -15.0]"));
        assertFalse(gj.contains("[[-48.0"));
        var r = service(List.of(pt), null, null).export(tenant, user, new GisExportService.ExportRequest(
                GisExportFormats.GEOJSON,"water-nodes", GisExportProfiles.Profile.GENERIC,
                GisExportGuard.Audience.BACKOFFICE, true, 5, List.of(), meta(), "rotina"));
        assertFalse(new String(r.content(), StandardCharsets.UTF_8).contains("[[-48.0"));
    }

    @Test void terceiroDerivaAgregadosDaFonteEFormatoDeclaraOConteudo() {
        // Correção D7 + D15d: sem agregados no pedido, eles são
        // derivados das feições pessoais; e o formato do resultado é
        // o do CONTEÚDO (JSON de agregados), não o formato pedido.
        java.util.List<GisExportFeature> seis = new java.util.ArrayList<>();
        for (int i = 1; i <= 6; i++) seis.add(new GisExportFeature("p" + i,"properties","PROPERTY","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of("code","p" + i), true,false,false,"q1","IN_SERVICE"));
        var reqT = new GisExportService.ExportRequest(GisExportFormats.GPKG,"properties",
                GisExportProfiles.Profile.GENERIC, GisExportGuard.Audience.THIRD_PARTY, false, 5,
                List.of(), meta(), "prefeitura");
        var r = service(seis, null, null).export(tenant, user, reqT);
        assertEquals(1, r.publishedAggregates().size());
        assertEquals("q1", r.publishedAggregates().get(0).cellKey());
        assertEquals(6, r.publishedAggregates().get(0).unitCount());
        assertEquals(GisExportFormats.GEOJSON, r.format());
        assertFalse(new String(r.content(), StandardCharsets.UTF_8).contains("\"p1\""));
    }

    @Test void filtroDeExportacaoAreaDominioCicloDeVidaERedeNaData() {
        // Correção D9: os filtros do §9.3 são expressos no pedido.
        var antiga = new GisExportFeature("a0","water-arcs","PIPE","LINESTRING",
                List.of(List.of(-48.0,-15.0), List.of(-48.1,-15.1)),
                Map.of("datetime","2025-01-01T00:00:00Z"), false,false,false,null,"PLANNED");
        var svc = service(List.of(arc, antiga), null, null);
        var filtro = new GisExportFilter(List.of(-49.0,-16.0,-47.0,-14.0), "water", "IN_SERVICE",
                Instant.parse("2026-06-01T00:00:00Z"));
        var reqF = new GisExportService.ExportRequest(GisExportFormats.GEOJSON,"water-arcs",
                GisExportProfiles.Profile.GENERIC, GisExportGuard.Audience.BACKOFFICE, true, 5,
                List.of(), meta(), "rotina", filtro);
        String body = new String(svc.export(tenant, user, reqF).content(), StandardCharsets.UTF_8);
        // arc não tem a propriedade datetime → fora da "rede na data";
        // antiga é PLANNED → fora do ciclo pedido. Nada vaza em silêncio.
        assertFalse(body.contains("a1")); assertFalse(body.contains("a0"));
        var soCiclo = new GisExportFilter(null, null, "PLANNED", null);
        var reqC = new GisExportService.ExportRequest(GisExportFormats.GEOJSON,"water-arcs",
                GisExportProfiles.Profile.GENERIC, GisExportGuard.Audience.BACKOFFICE, true, 5,
                List.of(), meta(), "rotina", soCiclo);
        String bodyC = new String(svc.export(tenant, user, reqC).content(), StandardCharsets.UTF_8);
        assertTrue(bodyC.contains("a0")); assertFalse(bodyC.contains("\"a1\""));
    }

    @Test void sensivelComPessoalRecusaAntesDeRegistrarAcesso() {
        // Correção D15e: marcador sensível recusa ANTES do registro no
        // personal_data_access_log.
        var personal = new GisExportFeature("p1","properties","PROPERTY","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of("code","p1"), true,false,false,"q1","IN_SERVICE");
        var sensitive = new GisExportFeature("s1","properties","ELECTRODEPENDENT","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of(), false,false,true,"q1","IN_SERVICE");
        AtomicBoolean registered = new AtomicBoolean(false);
        var svc = service(List.of(personal, sensitive), null, (t,u,l,p) -> registered.set(true));
        var reqP = new GisExportService.ExportRequest(GisExportFormats.GEOJSON,"properties",
                GisExportProfiles.Profile.GENERIC, GisExportGuard.Audience.BACKOFFICE, true, 5, List.of(), meta(), "ficha");
        assertThrows(IllegalArgumentException.class, () -> svc.export(tenant, user, reqP));
        assertFalse(registered.get());
    }

    @Test void devolucaoExigeTodosOsItensEPrazoDeDezDiasUteis() {
        assertFalse(GisReturnOfBase.complete(java.util.EnumSet.of(GisReturnOfBase.Item.GPKG)));
        assertTrue(GisReturnOfBase.complete(GisReturnOfBase.required()));
        // 02/11/2026 é segunda-feira; +10 dias úteis = 16/11/2026.
        assertEquals(java.time.LocalDate.of(2026,11,16),
                GisReturnOfBase.deadline(java.time.LocalDate.of(2026,11,2)));
        assertTrue(GisReturnOfBase.withinDeadline(java.time.LocalDate.of(2026,11,2), java.time.LocalDate.of(2026,11,16)));
        assertFalse(GisReturnOfBase.withinDeadline(java.time.LocalDate.of(2026,11,2), java.time.LocalDate.of(2026,11,17)));
    }
}
