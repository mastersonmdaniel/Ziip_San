package br.com.ziipsan.integration.hub;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.37: parâmetros integration.<name>.* — ausência é erro, produção só com aceite. */
class IntegrationParametersTest {

    private static IntegrationParameters params(Map<String, String> values) {
        return IntegrationParameters.fromValues(values);
    }

    @Test
    @DisplayName("chaves seguem o padrão do aceite: integration.<name>.mode e integration.<name>.credential_ref")
    void keyPattern() {
        assertThat(IntegrationParameters.modeKey("psp-pix")).isEqualTo("integration.psp-pix.mode");
        assertThat(IntegrationParameters.credentialRefKey("sefaz-nfe"))
                .isEqualTo("integration.sefaz-nfe.credential_ref");
    }

    @Test
    @DisplayName("modo lido do JSON textual vigente e aceito")
    void modeRead() {
        var p = params(Map.of("integration.psp-pix.mode", "\"SANDBOX\""));
        assertThat(p.mode("psp-pix")).isEqualTo(IntegrationMode.SANDBOX);
        assertThat(IntegrationMode.SANDBOX.callsPartner()).isTrue();
        assertThat(IntegrationMode.SIMULATED.callsPartner()).isFalse();
    }

    @Test
    @DisplayName("modo ausente é MissingParameterException nomeando a chave — nunca presumido")
    void modeMissing() {
        var p = params(Map.of());
        assertThatThrownBy(() -> p.mode("psp-pix"))
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("integration.psp-pix.mode");
    }

    @Test
    @DisplayName("modo fora do catálogo é recusado")
    void modeOutsideCatalog() {
        var p = params(Map.of("integration.psp-pix.mode", "\"REAL\""));
        assertThatThrownBy(() -> p.mode("psp-pix"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fora do catálogo");
    }

    @Test
    @DisplayName("PRODUCTION sem aceite é recusado; com aceite, opera; SANDBOX não exige aceite")
    void productionOnlyWithAcceptance() {
        var prod = params(Map.of("integration.psp-pix.mode", "\"PRODUCTION\""));
        assertThatThrownBy(() -> prod.operableMode("psp-pix", false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("só com aceite");
        assertThat(prod.operableMode("psp-pix", true)).isEqualTo(IntegrationMode.PRODUCTION);
        var sandbox = params(Map.of("integration.psp-pix.mode", "\"SANDBOX\""));
        assertThat(sandbox.operableMode("psp-pix", false)).isEqualTo(IntegrationMode.SANDBOX);
    }

    @Test
    @DisplayName("credential_ref devolve a REFERÊNCIA do cofre; ausente é erro explícito")
    void credentialRefReadAndMissing() {
        var p = params(Map.of("integration.psp-pix.credential_ref", "\"vault:tenants/t1/psp-pix/v3\""));
        assertThat(p.credentialRef("psp-pix")).isEqualTo("vault:tenants/t1/psp-pix/v3");
        assertThatThrownBy(() -> params(Map.of()).credentialRef("psp-pix"))
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("integration.psp-pix.credential_ref");
    }

    @Test
    @DisplayName("nome de integração inválido é recusado (ele entra na chave do parâmetro)")
    void invalidName() {
        assertThatThrownBy(() -> IntegrationParameters.modeKey("PSP Pix"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntegrationParameters.modeKey(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IntegrationParameters.modeKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
