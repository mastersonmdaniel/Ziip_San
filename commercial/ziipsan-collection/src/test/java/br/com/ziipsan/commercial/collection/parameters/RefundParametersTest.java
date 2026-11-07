package br.com.ziipsan.commercial.collection.parameters;

import br.com.ziipsan.commercial.collection.parameters.RefundParameters.RefundDestination;
import br.com.ziipsan.commercial.collection.parameters.RefundParameters.RefundMode;
import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.29 - parametros da devolucao: uma leitura por opcao; ausencia e erro explicito. */
class RefundParametersTest {

    private static RefundParameters params(String key, String json) {
        return RefundParameters.fromValues(Map.of(key, json));
    }

    @Test
    void refundModeLeCadaOpcao() {
        assertThat(params(RefundParameters.REFUND_MODE, "\"USER_CHOICE\"").refundMode())
                .isEqualTo(RefundMode.USER_CHOICE);
        assertThat(params(RefundParameters.REFUND_MODE, "\"CREDIT_NEXT_BILL\"").refundMode())
                .isEqualTo(RefundMode.CREDIT_NEXT_BILL);
        assertThat(params(RefundParameters.REFUND_MODE, "\"BANK_TRANSFER\"").refundMode())
                .isEqualTo(RefundMode.BANK_TRANSFER);
    }

    @Test
    void refundDestinationLeCadaOpcao() {
        assertThat(params(RefundParameters.REFUND_DESTINATION, "\"PIX_RETURN_TO_PAYER\"").refundDestination())
                .isEqualTo(RefundDestination.PIX_RETURN_TO_PAYER);
        assertThat(params(RefundParameters.REFUND_DESTINATION, "\"PIX_KEY\"").refundDestination())
                .isEqualTo(RefundDestination.PIX_KEY);
        assertThat(params(RefundParameters.REFUND_DESTINATION, "\"BANK_ACCOUNT\"").refundDestination())
                .isEqualTo(RefundDestination.BANK_ACCOUNT);
    }

    @Test
    void parametroAusenteEErroExplicitoNuncaPadraoPresumido() {
        var p = RefundParameters.fromValues(Map.of());
        assertThatThrownBy(p::refundMode).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(p::refundDestination).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(p::undueChargeDoubleRefund).isInstanceOf(MissingParameterException.class);
    }

    @Test
    void opcaoDesconhecidaERecusada() {
        assertThatThrownBy(() -> params(RefundParameters.REFUND_MODE, "\"CHEQUE\"").refundMode())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void dobroLigadoSemNorma() {
        var s = params(RefundParameters.UNDUE_CHARGE_DOUBLE_REFUND, "{\"enabled\":true}")
                .undueChargeDoubleRefund();
        assertThat(s.enabled()).isTrue();
        assertThat(s.normCitation()).isNull();
    }

    @Test
    void dobroDesligadoExigeNormaCitada() {
        assertThatThrownBy(() -> params(RefundParameters.UNDUE_CHARGE_DOUBLE_REFUND,
                "{\"enabled\":false}").undueChargeDoubleRefund())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("norma citada");
        var s = params(RefundParameters.UNDUE_CHARGE_DOUBLE_REFUND,
                "{\"enabled\":false,\"normCitation\":\"CDC art. 42 - decisao do tenant\"}")
                .undueChargeDoubleRefund();
        assertThat(s.enabled()).isFalse();
        assertThat(s.normCitation()).isNotBlank();
    }
}
