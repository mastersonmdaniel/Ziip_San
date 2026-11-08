package br.com.ziipsan.integration.hub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.37: catálogo de conectores de parceiros (GES-11) e a matriz de PSP
 * da P3.2 lida pelo hub.
 */
class PartnerConnectorCatalogTest {

    private static PartnerConnectorCatalog.PspBehaviorView pspBehavior() {
        return new PartnerConnectorCatalog.PspBehaviorView(
                "PSP-EXEMPLO", "reenvia 3x em 1h", 120, "PORTAL_DO_PSP", true,
                Optional.empty(), true, false);
    }

    private static PartnerConnectorCatalog.CatalogEntry pspEntry() {
        return new PartnerConnectorCatalog.CatalogEntry("psp-pix", ExternalConnector.Kind.PSP,
                "PSP-EXEMPLO", "2.1", Set.of("PIX"), Optional.of(pspBehavior()), "layout-hash-2.1");
    }

    @Test
    @DisplayName("hub lê a matriz de PSP da P3.2 a partir do catálogo")
    void hubReadsPspMatrix() {
        PartnerConnectorCatalog catalog = new PartnerConnectorCatalog();
        catalog.register(pspEntry());
        catalog.register(new PartnerConnectorCatalog.CatalogEntry("sefaz-nfe", ExternalConnector.Kind.SEFAZ,
                "SEFAZ", "4.0", Set.of("NFE"), Optional.empty(), "layout-hash-4.0"));
        assertThat(catalog.pspMatrix()).hasSize(1);
        PartnerConnectorCatalog.PspBehaviorView behavior = catalog.pspMatrix().get(0);
        assertThat(behavior.psp()).isEqualTo("PSP-EXEMPLO");
        assertThat(behavior.callbackSlaSeconds()).isEqualTo(120);
        // Sem mecanismo equivalente declarado, a confirmação é por GET /pix/{e2eid} (regra da P3.2).
        assertThat(behavior.requiresGetPixConfirmation()).isTrue();
        assertThat(behavior.openFinanceJsr()).isTrue();
        assertThat(behavior.pixProximity()).isFalse();
    }

    @Test
    @DisplayName("entrada PSP sem matriz é recusada: comportamento de PSP nunca é presumido")
    void pspWithoutMatrixRefused() {
        assertThatThrownBy(() -> new PartnerConnectorCatalog.CatalogEntry("psp-pix",
                ExternalConnector.Kind.PSP, "PSP-EXEMPLO", "2.1", Set.of(), Optional.empty(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("matriz de comportamento");
        assertThatThrownBy(() -> new PartnerConnectorCatalog.CatalogEntry("sefaz-nfe",
                ExternalConnector.Kind.SEFAZ, "SEFAZ", "4.0", Set.of(), Optional.of(pspBehavior()), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("só cabe em conector PSP");
    }

    @Test
    @DisplayName("duplicado nome+versão é recusado; mesma integração em outra versão é outra entrada")
    void duplicateNameVersionRefused() {
        PartnerConnectorCatalog catalog = new PartnerConnectorCatalog();
        catalog.register(pspEntry());
        assertThatThrownBy(() -> catalog.register(pspEntry()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("já tem entrada");
        catalog.register(new PartnerConnectorCatalog.CatalogEntry("psp-pix", ExternalConnector.Kind.PSP,
                "PSP-EXEMPLO", "2.2", Set.of("PIX"), Optional.of(pspBehavior()), "layout-hash-2.2"));
        assertThat(catalog.entries()).hasSize(2);
        assertThat(catalog.require("psp-pix", "2.2").expectedLayoutFingerprint()).isEqualTo("layout-hash-2.2");
        assertThatThrownBy(() -> catalog.require("psp-pix", "9.9"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fora do catálogo");
    }

    @Test
    @DisplayName("matriz inválida é recusada campo a campo (SLA, reenvio, certificado)")
    void invalidMatrixRefused() {
        assertThatThrownBy(() -> new PartnerConnectorCatalog.PspBehaviorView(
                "PSP", "reenvia", 0, "PORTAL", false, Optional.empty(), false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SLA");
        assertThatThrownBy(() -> new PartnerConnectorCatalog.PspBehaviorView(
                "PSP", " ", 10, "PORTAL", false, Optional.empty(), false, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reenvio");
    }
}
