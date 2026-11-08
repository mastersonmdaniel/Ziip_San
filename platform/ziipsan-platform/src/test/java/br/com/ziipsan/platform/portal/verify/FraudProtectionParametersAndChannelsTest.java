package br.com.ziipsan.platform.portal.verify;

import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FraudProtectionParametersAndChannelsTest {

    private static final OfficialChannels.BrandingContact CONTACT =
            new OfficialChannels.BrandingContact("0800 000 0000", "contato@tenant.exemplo", "https://tenant.exemplo", null);

    @Test
    void verifyEnabledPadraoDeFabricaELigado() {
        assertThat(FraudProtectionParameters.fromValues(Map.of()).verifyEnabled()).isTrue();
        assertThat(FraudProtectionParameters.fromValues(
                Map.of(FraudProtectionParameters.VERIFY_ENABLED, "true")).verifyEnabled()).isTrue();
        assertThat(FraudProtectionParameters.fromValues(
                Map.of(FraudProtectionParameters.VERIFY_ENABLED, "false")).verifyEnabled()).isFalse();
        assertThatThrownBy(() -> FraudProtectionParameters.fromValues(
                Map.of(FraudProtectionParameters.VERIFY_ENABLED, "\"sim\"")).verifyEnabled())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void canaisSaoLidosViaJsonDoCore() {
        String json = "[{\"type\":\"EMAIL\",\"label\":\"E-mail oficial\",\"value\":\"contato@tenant.exemplo\"},"
                + "{\"type\":\"PHONE\",\"label\":\"Telefone\",\"value\":\"0800 000 0000\"}]";
        var params = FraudProtectionParameters.fromValues(Map.of(FraudProtectionParameters.OFFICIAL_CHANNELS, json));
        assertThat(params.declaredOfficialChannels()).hasSize(2);
        assertThat(FraudProtectionParameters.fromValues(Map.of()).declaredOfficialChannels()).isEmpty();
        // Prova de que a leitura e JSON de verdade (Json do core), nao toString de mapa:
        assertThat(Json.readTree(json).isArray()).isTrue();
    }

    @Test
    void canalQueConfereComBrandingContactEPublicado() {
        var channels = OfficialChannels.of(List.of(
                new OfficialChannel(OfficialChannel.ChannelType.EMAIL, "E-mail oficial", "contato@tenant.exemplo"),
                new OfficialChannel(OfficialChannel.ChannelType.WHATSAPP, "WhatsApp", "0800 000 0000"),
                new OfficialChannel(OfficialChannel.ChannelType.WEBSITE, "Site", "https://tenant.exemplo"),
                new OfficialChannel(OfficialChannel.ChannelType.PORTAL, "Portal", "https://portal.tenant.exemplo")),
                CONTACT, "https://portal.tenant.exemplo", null);
        assertThat(channels.channels()).hasSize(4);
        assertThat(FraudProtectionTexts.officialChannelLines(channels)).hasSize(4);
    }

    @Test
    void canalDivergenteDaFonteERecusado() {
        assertThatThrownBy(() -> OfficialChannels.of(List.of(
                new OfficialChannel(OfficialChannel.ChannelType.EMAIL, "E-mail", "outro@fraude.exemplo")),
                CONTACT, null, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diverge");
        assertThatThrownBy(() -> OfficialChannels.of(List.of(
                new OfficialChannel(OfficialChannel.ChannelType.PHONE, "Telefone", "0800 111 1111")),
                CONTACT, null, null)).isInstanceOf(IllegalArgumentException.class);
        // Portal sem dominio proprio conferido nao se publica sem lastro:
        assertThatThrownBy(() -> OfficialChannels.of(List.of(
                new OfficialChannel(OfficialChannel.ChannelType.PORTAL, "Portal", "https://qualquer.exemplo")),
                CONTACT, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alertaDeGolpeConfiguravel() {
        var off = FraudProtectionParameters.fromValues(Map.of());
        assertThat(off.scamAlert().enabled()).isFalse();
        var on = FraudProtectionParameters.fromValues(Map.of(FraudProtectionParameters.SCAM_ALERT,
                "{\"enabled\":true,\"text\":\"Cuidado: golpe do boleto falso na regiao.\"}"));
        assertThat(on.scamAlert().enabled()).isTrue();
        assertThat(on.scamAlert().text()).contains("boleto falso");
        assertThatThrownBy(() -> FraudProtectionParameters.fromValues(Map.of(
                FraudProtectionParameters.SCAM_ALERT, "{\"enabled\":true}")).scamAlert())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textosPadraoNaoContemCanalInventado() {
        assertThat(FraudProtectionTexts.NEVER_LOOSE_LINK_TEXT).contains("nunca pedimos pagamento por link avulso");
        assertThat(FraudProtectionTexts.NEVER_LOOSE_LINK_TEXT).doesNotContain("@").doesNotContain("0800")
                .doesNotContain("http");
        String guidance = FraudProtectionTexts.pixReceiverGuidance("SAAE EXEMPLO", "12.345.678/0001-90");
        assertThat(guidance).contains("SAAE EXEMPLO").contains("12.345.678/0001-90");
        assertThatThrownBy(() -> FraudProtectionTexts.pixReceiverGuidance(null, "12"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
