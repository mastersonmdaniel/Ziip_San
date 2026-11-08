package br.com.ziipsan.integration.erp;

import br.com.ziipsan.integration.hub.ExternalConnector;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ErpConnectorAndParametersTest {

    @Test
    void connectorLivesOnHubPortWithErpKindAndDeclaredVersion() {
        FakeErpTransport fake = new FakeErpTransport();
        ErpConnector c = new ErpConnector(ErpProvider.IPM, "declared-v9", fake);
        assertEquals("erp-ipm", c.name());
        assertEquals(ExternalConnector.Kind.ERP, c.kind());
        assertEquals("declared-v9", c.partnerVersion());
        var ctx = new ExternalConnector.ConnectorContext(ErpFixtures.TENANT,
                br.com.ziipsan.integration.hub.IntegrationMode.SANDBOX, "vault:erp-ipm", "corr-1");
        assertTrue(c.testConnection(ctx).reachable());
        assertEquals(1, fake.pingCalls);
        assertEquals("declared-test-fingerprint", c.homologationLayoutFingerprint(ctx));
    }

    @Test
    void connectorRefusesUndeclaredPartnerVersion() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ErpConnector(ErpProvider.FIORILLI, " ", new FakeErpTransport()));
        assertTrue(e.getMessage().contains("DECLARADA"));
    }

    @Test
    void missingParametersRefuseByName() {
        ErpParameters empty = ErpParameters.fromValues(Map.of());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> empty.stockOfficial(ErpProvider.BETHA));
        assertTrue(e.getMessage().contains("erp.betha.stock_official"));
        assertTrue(e.getMessage().contains("[A DEFINIR]"));
    }

    @Test
    void mappingRefusesUndeclaredFieldByName() {
        ErpLayoutMapping partial = ErpLayoutMapping.declared(ErpProvider.BETHA, "v1", Map.of("data", "F_DATA"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> partial.requireField("conta"));
        assertTrue(e.getMessage().contains("conta"));
        assertTrue(e.getMessage().contains("[A DEFINIR]"));
    }

    @Test
    void serviceRefusesMappingOfAnotherProviderOrVersion() {
        FakeErpTransport fake = new FakeErpTransport();
        ErpConnector c = new ErpConnector(ErpProvider.BETHA, "declared-v1", fake);
        ErpLayoutMapping otherVersion = ErpLayoutMapping.declared(ErpProvider.BETHA, "other-v", Map.of("data", "F_DATA"));
        assertThrows(IllegalArgumentException.class,
                () -> new ErpExportService(c, ErpFixtures.params("ERP"), otherVersion,
                        br.com.ziipsan.integration.hub.IntegrationMode.SANDBOX));
    }

    @Test
    void providersHaveStableHubNames() {
        assertEquals("erp-betha", ErpProvider.BETHA.connectorName());
        assertEquals("erp-ipm", ErpProvider.IPM.connectorName());
        assertEquals("erp-fiorilli", ErpProvider.FIORILLI.connectorName());
        UUID.fromString("11111111-1111-1111-1111-111111111111"); // fixtures sao UUID validos
    }
}
