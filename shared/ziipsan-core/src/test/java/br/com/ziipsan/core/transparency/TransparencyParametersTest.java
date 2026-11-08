package br.com.ziipsan.core.transparency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import br.com.ziipsan.core.parameters.MissingParameterException;
import br.com.ziipsan.core.transparency.TransparencyParameters.LaiMode;
class TransparencyParametersTest {
    @Test @DisplayName("padrao por natureza: AUTARCHY/PUBLIC_COMPANY ligam (EXTERNAL_LINK); PRIVATE/SEM desligam (NONE)")
    void defaultByNature() {
        assertThat(TransparencyParameters.defaultLaiMode("AUTARCHY")).isEqualTo(LaiMode.EXTERNAL_LINK);
        assertThat(TransparencyParameters.defaultLaiMode("PUBLIC_COMPANY")).isEqualTo(LaiMode.EXTERNAL_LINK);
        assertThat(TransparencyParameters.defaultLaiMode("PRIVATE")).isEqualTo(LaiMode.NONE);
        assertThat(TransparencyParameters.defaultLaiMode("SEM")).isEqualTo(LaiMode.NONE);
        assertThat(TransparencyParameters.moduleEnabledByDefault("AUTARCHY")).isTrue();
        assertThat(TransparencyParameters.moduleEnabledByDefault("PUBLIC_COMPANY")).isTrue();
        assertThat(TransparencyParameters.moduleEnabledByDefault("PRIVATE")).isFalse();
        assertThat(TransparencyParameters.moduleEnabledByDefault("SEM")).isFalse();
    }
    @Test @DisplayName("natureza ausente ou fora do dominio real = desligado (falha fechada)")
    void unknownNatureOff() {
        assertThat(TransparencyParameters.defaultLaiMode(null)).isEqualTo(LaiMode.NONE);
        assertThat(TransparencyParameters.defaultLaiMode("FUNDACAO")).isEqualTo(LaiMode.NONE);
        assertThat(TransparencyParameters.defaultLaiMode("")).isEqualTo(LaiMode.NONE);
        // literais reais do dominio aceitam caixa/espaco da fiacao
        assertThat(TransparencyParameters.defaultLaiMode(" autarchy ")).isEqualTo(LaiMode.EXTERNAL_LINK);
    }
    @Test @DisplayName("pub.lai_mode explicito vale sobre o padrao; desconhecido recusado; NONE desliga ate autarquia")
    void explicitMode() {
        assertThat(TransparencyParameters.laiMode("PRIVATE", "NATIVE_SIC")).isEqualTo(LaiMode.NATIVE_SIC);
        assertThat(TransparencyParameters.laiMode("AUTARCHY", "NONE")).isEqualTo(LaiMode.NONE);
        assertThat(TransparencyParameters.moduleEnabled("AUTARCHY", "NONE")).isFalse();
        assertThat(TransparencyParameters.moduleEnabled("PRIVATE", "EXTERNAL_LINK")).isTrue();
        assertThat(TransparencyParameters.laiMode("PRIVATE", null)).isEqualTo(LaiMode.NONE);
        assertThatThrownBy(() -> TransparencyParameters.laiMode("PRIVATE", "SIC_PROPRIO"))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("EXTERNAL_LINK exige pub.lai_external_url; Fala.BR voluntario; dados abertos desligados por padrao")
    void urlAndFlags() {
        assertThat(TransparencyParameters.externalUrl("https://lai.municipio.exemplo.br", LaiMode.EXTERNAL_LINK))
            .isEqualTo("https://lai.municipio.exemplo.br");
        assertThatThrownBy(() -> TransparencyParameters.externalUrl(null, LaiMode.EXTERNAL_LINK))
            .isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> TransparencyParameters.externalUrl("  ", LaiMode.EXTERNAL_LINK))
            .isInstanceOf(MissingParameterException.class);
        assertThat(TransparencyParameters.falaBrOptIn(null)).isFalse();
        assertThat(TransparencyParameters.falaBrOptIn(false)).isFalse();
        assertThat(TransparencyParameters.falaBrOptIn(true)).isTrue();
        assertThat(TransparencyParameters.openDataEnabled(null)).isFalse();
        assertThat(TransparencyParameters.openDataEnabled(true)).isTrue();
    }
    @Test @DisplayName("LaiAccessPoint: EXTERNAL_LINK valida URL http/https; NATIVE_SIC sem URL; NONE recusado")
    void accessPoint() {
        var link = LaiAccessPoint.forMode(LaiMode.EXTERNAL_LINK, "https://lai.municipio.exemplo.br", false);
        assertThat(link.nativeSic()).isFalse();
        assertThat(link.externalUrl()).isEqualTo("https://lai.municipio.exemplo.br");
        assertThat(LaiAccessPoint.forMode(LaiMode.NATIVE_SIC, null, true).nativeSic()).isTrue();
        assertThatThrownBy(() -> LaiAccessPoint.forMode(LaiMode.EXTERNAL_LINK, "ftp://x.exemplo.br", false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LaiAccessPoint.forMode(LaiMode.EXTERNAL_LINK, "lai-sem-esquema", false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LaiAccessPoint.forMode(LaiMode.NONE, null, false))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
