package br.com.ziipsan.commercial.collection.parameters;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.2 - parametros do Pix: ausencia e erro explicito; regras do Pix
 * Automatico validadas contra o teto normativo; canais como capacidade
 * do provedor.
 */
class PixParametersTest {

    private static Map<String, String> full() {
        return Map.of(
                PixParameters.AUTO_DEBIT_RETRY_POLICY,
                "{\"maxRetries\":3,\"windowDays\":7,\"foreseenInAuthorization\":true}",
                PixParameters.AUTO_DEBIT_NOTICE_DAYS, "2",
                PixParameters.AUTO_DEBIT_INCENTIVE, "\"NONE\"",
                PixParameters.CHANNELS, "[\"PIX\",\"OPEN_FINANCE_JSR\",\"PIX_PROXIMITY\"]",
                PixParameters.HYBRID_OFFER_ENABLED, "true");
    }

    @Test
    void lePoliticaDeRetentativaNoTetoNormativo() {
        var policy = PixParameters.fromValues(full()).autoDebitRetryPolicy();
        assertThat(policy.maxRetries()).isEqualTo(3);
        assertThat(policy.windowDays()).isEqualTo(7);
        assertThat(policy.foreseenInAuthorization()).isTrue();
    }

    @Test
    void retentativaAcimaDeTresEmSeteDiasERecusada() {
        var params = PixParameters.fromValues(Map.of(
                PixParameters.AUTO_DEBIT_RETRY_POLICY,
                "{\"maxRetries\":4,\"windowDays\":7,\"foreseenInAuthorization\":true}"));
        assertThatThrownBy(params::autoDebitRetryPolicy).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retentativaSemPrevisaoNaAutorizacaoERecusada() {
        var params = PixParameters.fromValues(Map.of(
                PixParameters.AUTO_DEBIT_RETRY_POLICY,
                "{\"maxRetries\":1,\"windowDays\":7,\"foreseenInAuthorization\":false}"));
        assertThatThrownBy(params::autoDebitRetryPolicy).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void canaisDeclaramCapacidadesDoProvedor() {
        var params = PixParameters.fromValues(full());
        assertThat(params.providerDeclaresOpenFinanceJsr()).isTrue();
        assertThat(params.providerDeclaresPixProximity()).isTrue();
        assertThat(params.hybridOfferEnabled()).isTrue();
        assertThat(params.autoDebitNoticeDays()).isEqualTo(2);
        assertThat(params.autoDebitIncentive()).isEqualTo("NONE");
    }

    @Test
    void parametroAusenteEErroExplicito() {
        var params = PixParameters.fromValues(Map.of(PixParameters.CHANNELS, "[\"PIX\"]"));
        assertThatThrownBy(params::autoDebitRetryPolicy).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(params::hybridOfferEnabled).isInstanceOf(MissingParameterException.class);
        assertThat(params.providerDeclaresOpenFinanceJsr()).isFalse();
    }

    @Test
    void regrasRegistradasDoPixAutomaticoTemLimitesConferidos() {
        assertThat(br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules
                .isValidScheduleLeadDays(2)).isTrue();
        assertThat(br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules
                .isValidScheduleLeadDays(10)).isTrue();
        assertThat(br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules
                .isValidScheduleLeadDays(1)).isFalse();
        assertThat(br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules
                .isValidScheduleLeadDays(11)).isFalse();
        assertThat(br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules
                .isValidAuthorizationJourney(4)).isTrue();
        assertThat(br.com.ziipsan.commercial.collection.pix.PixAutoDebitRules
                .isValidAuthorizationJourney(5)).isFalse();
    }
}
