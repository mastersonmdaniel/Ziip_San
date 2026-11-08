package br.com.ziipsan.commercial.billing.altsolution;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AltSolutionParametersTest {

    @Test @DisplayName("servico habilitado: true/false lidos; ausencia e erro explicito")
    void serviceEnabled() {
        assertThat(AltSolutionParameters.serviceEnabled("true")).isTrue();
        assertThat(AltSolutionParameters.serviceEnabled("false")).isFalse();
        assertThatThrownBy(() -> AltSolutionParameters.serviceEnabled(null))
                .isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> AltSolutionParameters.serviceEnabled("\"true\""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("intervalo periodico: positivo; zero/ausente recusados")
    void interval() {
        assertThat(AltSolutionParameters.serviceIntervalMonths("6")).isEqualTo(6);
        assertThatThrownBy(() -> AltSolutionParameters.serviceIntervalMonths("0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AltSolutionParameters.serviceIntervalMonths(null))
                .isInstanceOf(MissingParameterException.class);
    }

    @Test @DisplayName("fonte tarifaria: codigo da agencia obrigatorio, sem enumeracao inventada")
    void tariffSource() {
        assertThat(AltSolutionParameters.tariffSource("\"AGENCIA_TABELA_PROPRIA\""))
                .isEqualTo("AGENCIA_TABELA_PROPRIA");
        assertThatThrownBy(() -> AltSolutionParameters.tariffSource("\"  \""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AltSolutionParameters.tariffSource(null))
                .isInstanceOf(MissingParameterException.class);
    }

    @Test @DisplayName("avail.notice_days (classe c): 89 recusado; piso de 90 e ampliacao aceitos")
    void noticeDaysFloor() {
        assertThatThrownBy(() -> AltSolutionParameters.noticeDays("89"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("90");
        assertThat(AltSolutionParameters.noticeDays("90")).isEqualTo(90);
        assertThat(AltSolutionParameters.noticeDays("120")).isEqualTo(120);
    }

    @Test @DisplayName("avail.max_connect_months (classe c): teto de 1 ano — 13 recusado, 12 aceito")
    void maxConnectCeiling() {
        assertThatThrownBy(() -> AltSolutionParameters.maxConnectMonths("13"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("12");
        assertThat(AltSolutionParameters.maxConnectMonths("12")).isEqualTo(12);
        assertThat(AltSolutionParameters.maxConnectMonths("6")).isEqualTo(6);
        assertThatThrownBy(() -> AltSolutionParameters.maxConnectMonths("0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("avail.exemption_requires_proof (classe a): false e recusado — dispensa sem prova nao existe")
    void exemptionProofLocked() {
        assertThat(AltSolutionParameters.exemptionRequiresProof("true")).isTrue();
        assertThatThrownBy(() -> AltSolutionParameters.exemptionRequiresProof("false"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("classe a");
    }

    @Test @DisplayName("OBRIGACAO NAO DESATIVAVEL: gravar false na chave de obrigacao e recusado; ausencia nao desliga")
    void obligationNotDisableable() {
        assertThatThrownBy(() -> AltSolutionParameters.requireObligationNotDisabled("false"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nao e desativavel");
        assertThatCode(() -> AltSolutionParameters.requireObligationNotDisabled(null))
                .doesNotThrowAnyException();
        assertThatCode(() -> AltSolutionParameters.requireObligationNotDisabled("true"))
                .doesNotThrowAnyException();
    }
}
