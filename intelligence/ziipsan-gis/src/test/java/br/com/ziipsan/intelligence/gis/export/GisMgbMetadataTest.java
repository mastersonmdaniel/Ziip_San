package br.com.ziipsan.intelligence.gis.export;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GisMgbMetadataTest {
    @Test void xmlComCamposDoPerfilMgbEEscape() {
        String xml = GisMgbMetadata.toXml(new GisMgbMetadata.Metadata(
                "Rede <Água> & Esgoto","Prestadora & Filhos", List.of(-48.2,-15.2,-47.9,-14.9),
                4674,"Imbituba","±0,10 m", Instant.parse("2026-01-01T00:00:00Z"),"linhagem","LGPD","mensal"));
        assertTrue(xml.contains("MD_Metadata"));
        assertTrue(xml.contains("&lt;Água&gt;"));
        assertTrue(xml.contains("4674"));
        assertFalse(xml.contains("<Água>"));
    }
    @Test void xmlEstruturalIso19115ComHierarquiaGmd() {
        // Correção D10: hierarquia gmd: real — nada de elementos planos
        // inventados (<EPSG>, <bbox>, <title> solto).
        String xml = GisMgbMetadata.toXml(new GisMgbMetadata.Metadata(
                "Rede de Água","Prestadora", List.of(-48.2,-15.2,-47.9,-14.9),
                4674,"Imbituba","±0,10 m", Instant.parse("2026-01-01T00:00:00Z"),"linhagem","LGPD","mensal"));
        assertTrue(xml.contains("<gmd:CI_Citation>"));
        assertTrue(xml.contains("<gmd:EX_GeographicBoundingBox>"));
        assertTrue(xml.contains("<gmd:westBoundLongitude><gco:Decimal>-48.2</gco:Decimal>"));
        assertTrue(xml.contains("<gmd:RS_Identifier>"));
        assertTrue(xml.contains("EPSG:4674"));
        assertTrue(xml.contains("<gmd:LI_Lineage>"));
        assertFalse(xml.contains("<EPSG>"));
        assertFalse(xml.contains("<bbox>"));
    }
    @Test void semTituloOuSemBboxRecusa() {
        assertThrows(IllegalArgumentException.class, () -> GisMgbMetadata.toXml(new GisMgbMetadata.Metadata(
                " ", "x", List.of(-48.2,-15.2,-47.9,-14.9), 4674,"Imbituba",null,null,null,null,null)));
    }
}
