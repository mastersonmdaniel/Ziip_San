package br.com.ziipsan.core.sac;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import br.com.ziipsan.core.parameters.MissingParameterException;
import java.util.Map;
class SacParametersTest {
    @Test @DisplayName("ombudsman_days: ausente e erro explicito; acima de 30 recusa (Lei 13.460, art. 16)")
    void ombudsman() {
        assertThatThrownBy(() -> SacParameters.ombudsmanDays(null)).isInstanceOf(MissingParameterException.class);
        assertThat(SacParameters.ombudsmanDays(30)).isEqualTo(30);
        assertThat(SacParameters.ombudsmanDays(10)).isEqualTo(10);
        assertThatThrownBy(() -> SacParameters.ombudsmanDays(31)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SacParameters.ombudsmanDays(0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("external_channels: prazo proprio por origem; origem sem prazo e erro explicito")
    void external() {
        var map = Map.of(ExternalOrigin.CONSUMER_GOV_BR, 15, ExternalOrigin.PROCON_SINDEC, 10);
        assertThat(SacParameters.externalChannelDays(map, ExternalOrigin.CONSUMER_GOV_BR)).isEqualTo(15);
        assertThat(SacParameters.externalChannelDays(map, ExternalOrigin.PROCON_SINDEC)).isEqualTo(10);
        assertThatThrownBy(() -> SacParameters.externalChannelDays(map, ExternalOrigin.PUBLIC_DEFENDER))
            .isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> SacParameters.externalChannelDays(null, ExternalOrigin.CONSUMER_GOV_BR))
            .isInstanceOf(MissingParameterException.class);
    }
    @Test @DisplayName("outage_status_visibility: ausente e erro; valor desconhecido recusado")
    void visibility() {
        assertThat(SacParameters.outageVisibility("PUBLIC_SUMMARY"))
            .isEqualTo(SacParameters.OutageVisibility.PUBLIC_SUMMARY);
        assertThatThrownBy(() -> SacParameters.outageVisibility(null)).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> SacParameters.outageVisibility("TUDO")).isInstanceOf(IllegalArgumentException.class);
    }
}
