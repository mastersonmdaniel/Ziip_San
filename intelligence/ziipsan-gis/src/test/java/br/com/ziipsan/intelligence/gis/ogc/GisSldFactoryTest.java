package br.com.ziipsan.intelligence.gis.ogc;

import br.com.ziipsan.intelligence.gis.publication.GisStyleRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GisSldFactoryTest {

    @Test void sldDerivaDaMesmaResolucaoDaFonteUnica() {
        var inService = GisStyleRules.resolve(List.of(), "WATER", null, "IN_SERVICE", "WITHIN_TARGET", "map-water");
        var planned = GisStyleRules.resolve(List.of(), "WATER", null, "PLANNED", "WITHIN_TARGET", "map-water");
        // A paleta (hex dos tokens) é do produto; o teste a fornece como o chamador fará.
        Map<String,String> palette = Map.of("map-water", "#00548F");
        String sld = GisSldFactory.generate("water-arcs",
                List.of(Map.entry("IN_SERVICE", inService), Map.entry("PLANNED", planned)), palette);
        var sigs = GisSldFactory.signatures(sld);
        assertEquals(2, sigs.size());
        // Regras e cores do SLD == estilo resolvido da fonte única.
        assertEquals("#00548F", sigs.get(0).colorHex());
        assertEquals(inService.lineWidth(), sigs.get(0).lineWidth());
        assertEquals(inService.lineDash(), sigs.get(0).dash());
        assertEquals(planned.lineDash(), sigs.get(1).dash());
        assertEquals(List.of(3.0, 2.0), sigs.get(1).dash());
    }

    @Test void sldDePontoEDePoligonoTemSimbolizadorProprio() {
        // Correção D15b: ponto/polígono não ficam sem simbologia.
        var s = GisStyleRules.resolve(List.of(), "WATER", null, "IN_SERVICE", "WITHIN_TARGET", "map-water");
        Map<String,String> palette = Map.of("map-water", "#00548F");
        String sldPonto = GisSldFactory.generate("water-nodes",
                List.of(Map.entry("IN_SERVICE", s)), palette, "POINT");
        assertTrue(sldPonto.contains("PointSymbolizer"));
        var sigs = GisSldFactory.signatures(sldPonto);
        assertEquals(1, sigs.size());
        assertEquals("#00548F", sigs.get(0).colorHex());
        String sldPoligono = GisSldFactory.generate("zones",
                List.of(Map.entry("IN_SERVICE", s)), palette, "POLYGON");
        assertTrue(sldPoligono.contains("PolygonSymbolizer"));
        assertEquals(1, GisSldFactory.signatures(sldPoligono).size());
    }

    @Test void tokenSemHexNaPaletaEerroNuncaCorInventada() {
        var s = GisStyleRules.resolve(List.of(), "WATER", null, "IN_SERVICE", "WITHIN_TARGET", "map-water");
        assertThrows(IllegalArgumentException.class, () -> GisSldFactory.generate("water-arcs",
                List.of(Map.entry("IN_SERVICE", s)), Map.of()));
    }
}
