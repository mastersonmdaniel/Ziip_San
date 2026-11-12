package br.com.ziipsan.intelligence.gis.hydraulic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M5/M6: o serviço novo não referencia a view/entidade antigas e o SQL
 * pendente remove as duas. Lê as fontes da entrega (caminho por
 * propriedade ziipsan.entrega.dir; sem a propriedade, localiza a raiz
 * do repo subindo a partir do directório de trabalho).
 */
class RemovalArchitectureTest {

    private Path entrega() {
        String d = System.getProperty("ziipsan.entrega.dir");
        if (d != null) return Path.of(d);
        // Padrao: a raiz do repo — sobe a partir do directorio de trabalho
        // (sob Maven, o do modulo) ate encontrar intelligence/ziipsan-gis.
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("intelligence/ziipsan-gis"))) return dir;
            dir = dir.getParent();
        }
        return Path.of("entrega");
    }

    @Test
    void servicoNovoNaoUsaViewNemEntidadeAntiga() throws Exception {
        Path svc = entrega().resolve("intelligence/ziipsan-gis/src/main/java/br/com/ziipsan/intelligence/gis/epanet/EpanetSimulationService.java");
        String src = Files.readString(svc);
        assertFalse(src.contains("GisNetworkElementRepository") || src.contains("domain.GisNetworkElement"), "servico ainda usa a entidade/repositorio antigos");
        assertFalse(src.contains("FROM gis_network_element"), "servico ainda le a view");
        assertTrue(src.contains("FROM gis_node") || src.contains("gis_node"), "servico deve ler gis_node");
    }

    @Test
    void sqlPendenteRemoveViewELegadoECriaMapeamento() throws Exception {
        Path sql = entrega().resolve("intelligence/ziipsan-gis/src/main/resources/db/pendente/ED-63__gis_hydraulic.sql");
        String src = Files.readString(sql);
        assertTrue(src.contains("DROP VIEW IF EXISTS gis_network_element"));
        assertTrue(src.contains("DROP TABLE IF EXISTS gis_network_element_legacy"));
        assertTrue(src.contains("gis_hydraulic_mapping"));
        assertTrue(src.contains("gis_hydraulic_run"));
    }
}
