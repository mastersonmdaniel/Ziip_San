package br.com.ziipsan.intelligence.gis.ogc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GisGeoServerProvisioningTest {

    @Test void politicaRecusaPortaPublicaConsoleWfsTeSemVersao() {
        assertThrows(IllegalArgumentException.class, () -> new GisGeoServerProvisioning.GeoServerPolicy(null, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new GisGeoServerProvisioning.GeoServerPolicy("FIXADA-NA-IMPLANTACAO", true, false, false));
        assertThrows(IllegalArgumentException.class, () -> new GisGeoServerProvisioning.GeoServerPolicy("FIXADA-NA-IMPLANTACAO", false, true, false));
        assertThrows(IllegalArgumentException.class, () -> new GisGeoServerProvisioning.GeoServerPolicy("FIXADA-NA-IMPLANTACAO", false, false, true));
        assertDoesNotThrow(() -> new GisGeoServerProvisioning.GeoServerPolicy("FIXADA-NA-IMPLANTACAO", false, false, false));
    }

    @Test void workspaceERoleSaoPorTenantESomenteLeituraPorNome() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertNotEquals(GisGeoServerProvisioning.workspaceName(a), GisGeoServerProvisioning.workspaceName(b));
        assertTrue(GisGeoServerProvisioning.readOnlyRole(a).startsWith("gis_ro_"));
        // Correção D11b: o payload do store traz host e port da conexão.
        String store = GisGeoServerProvisioning.dataStoreJson(a, "db.interno", 5432, "ziipsan", "tenant_schema", "vault:gis-ro-a");
        assertTrue(store.contains(GisGeoServerProvisioning.readOnlyRole(a)));
        assertTrue(store.contains("secret:vault:gis-ro-a"));
        assertTrue(store.contains("db.interno"));
        assertTrue(store.contains("5432"));
        assertFalse(store.contains("password"));
    }

    @Test void provisionamentoSoUsaRestEGeraWorkspaceStoreCamadasEEstilos() {
        List<String> calls = new ArrayList<>();
        var rest = new GisGeoServerProvisioning.GeoServerRestPort() {
            public void post(String path, String body) { calls.add("POST " + path); }
            public void put(String path, String body, String ct) { calls.add("PUT " + path); }
        };
        UUID t = UUID.randomUUID();
        var policy = new GisGeoServerProvisioning.GeoServerPolicy("FIXADA-NA-IMPLANTACAO", false, false, false);
        GisGeoServerProvisioning.provision(rest, policy, t, "db.interno", 5432, "ziipsan", "s1", "vault:x",
                List.of("water-arcs"), Map.of("water-arcs", "<StyledLayerDescriptor/>"));
        assertTrue(calls.get(0).contains("/rest/workspaces"));
        assertTrue(calls.stream().anyMatch(c -> c.contains("/datastores")));
        assertTrue(calls.stream().anyMatch(c -> c.contains("/featuretypes")));
        assertTrue(calls.stream().anyMatch(c -> c.startsWith("PUT") && c.contains("/styles/")));
        // Correção D11c: o SLD é associado como estilo padrão da camada.
        assertTrue(calls.stream().anyMatch(c -> c.startsWith("PUT") && c.contains("/layers/water-arcs")));
    }

    @Test void provisionamentoExigePolicyEAtualizacaoUsaPut() {
        // Correção D11a/D11d: sem policy não provisiona; a atualização
        // (§7.2 item 5) vai por PUT em store/featuretype/estilo/camada.
        List<String> calls = new ArrayList<>();
        var rest = new GisGeoServerProvisioning.GeoServerRestPort() {
            public void post(String path, String body) { calls.add("POST " + path); }
            public void put(String path, String body, String ct) { calls.add("PUT " + path); }
        };
        UUID t = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> GisGeoServerProvisioning.provision(
                rest, null, t, "db.interno", 5432, "ziipsan", "s1", "vault:x", List.of("water-arcs"), Map.of()));
        var policy = new GisGeoServerProvisioning.GeoServerPolicy("FIXADA-NA-IMPLANTACAO", false, false, false);
        GisGeoServerProvisioning.refresh(rest, policy, t, "db.interno", 5432, "ziipsan", "s1", "vault:x",
                List.of("water-arcs"), Map.of("water-arcs", "<StyledLayerDescriptor/>"));
        assertTrue(calls.stream().allMatch(c -> c.startsWith("PUT")));
        assertTrue(calls.stream().anyMatch(c -> c.contains("/featuretypes/water-arcs")));
        assertTrue(calls.stream().anyMatch(c -> c.contains("/styles/")));
        assertTrue(calls.stream().anyMatch(c -> c.contains("/layers/water-arcs")));
    }

    @Test void convencaoUnicaDeNomesDeViewAgregada() {
        // Correção D15a: guarda WMS e provisionamento usam a mesma convenção.
        assertEquals("pub_properties", GisGeoServerProvisioning.viewName("properties"));
        assertEquals("pub_properties_agg", GisGeoServerProvisioning.aggregatedViewName("properties"));
        assertEquals("properties_agg", GisGeoServerProvisioning.aggregatedLayerKey("properties"));
    }

    @Test void guardaWmsSoLeituraWorkspaceDoTenantEAgregadoParaTerceiro() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertThrows(SecurityException.class, () -> GisWmsProxyGuard.forwardLayer(null, a, "GetMap", "water-arcs", false, false, true));
        assertThrows(SecurityException.class, () -> GisWmsProxyGuard.forwardLayer(a, b, "GetMap", "water-arcs", false, false, true));
        assertThrows(SecurityException.class, () -> GisWmsProxyGuard.forwardLayer(a, a, "Transaction", "water-arcs", false, false, true));
        assertEquals("water-arcs", GisWmsProxyGuard.forwardLayer(a, a, "GetMap", "water-arcs", false, false, true));
        assertEquals("properties_agg", GisWmsProxyGuard.forwardLayer(a, a, "GetMap", "properties", true, true, false));
    }

    @Test void guardaWmsPessoalSemPermissaoParaNaoTerceiroERecusada() {
        // Correção D14: camada pessoal, credencial de backoffice sem
        // gis.view-personal → recusa (antes devolvia o nome cru).
        UUID a = UUID.randomUUID();
        assertThrows(SecurityException.class, () -> GisWmsProxyGuard.forwardLayer(a, a, "GetMap", "properties", true, false, false));
        assertEquals("properties", GisWmsProxyGuard.forwardLayer(a, a, "GetMap", "properties", true, false, true));
    }
}
