package br.com.ziipsan.commercial.collection.card;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Parametros card.*: ausencia e erro explicito, nunca padrao presumido. */
class CardParametersTest {

    @Test
    @DisplayName("sem card.enabled: erro explicito nomeando a chave")
    void missingEnabledIsExplicitError() {
        var params = CardParameters.fromValues(Map.of());
        assertThatThrownBy(params::enabled)
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("card.enabled");
    }

    @Test
    @DisplayName("ligado sem credenciadora: erro explicito; ligado com tudo: le os valores")
    void enabledRequiresAcquirer() {
        var semAcq = CardParameters.fromValues(Map.of(CardParameters.ENABLED, "true"));
        assertThatThrownBy(semAcq::acquirerCode)
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("card.acquirer_code");

        var params = CardTestSupport.params(true);
        assertThat(params.enabled()).isTrue();
        assertThat(params.acquirerCode()).isEqualTo(CardTestSupport.ACQUIRER);
        assertThat(params.checkoutMode()).isEqualTo(CardEnums.CheckoutMode.PAYMENT_LINK);
        assertThat(params.statementTolerance()).isEqualByComparingTo(new BigDecimal("0.05"));
    }

    @Test
    @DisplayName("desligado: credenciadora e modo nao sao exigidos (null legitimo)")
    void disabledDoesNotRequireAcquirer() {
        var params = CardParameters.fromValues(Map.of(CardParameters.ENABLED, "false"));
        assertThat(params.enabled()).isFalse();
        assertThat(params.acquirerCode()).isNull();
        assertThat(params.checkoutMode()).isNull();
    }

    @Test
    @DisplayName("modo de checkout desconhecido e tolerancia negativa recusam")
    void invalidValuesRejected() {
        Map<String, String> values = new HashMap<>();
        values.put(CardParameters.ENABLED, "true");
        values.put(CardParameters.ACQUIRER_CODE, "\"ACQ\"");
        values.put(CardParameters.CHECKOUT_MODE, "\"TEF\"");
        var params = CardParameters.fromValues(values);
        assertThatThrownBy(params::checkoutMode).isInstanceOf(IllegalStateException.class);

        var tol = CardParameters.fromValues(Map.of(CardParameters.STATEMENT_TOLERANCE, "-1.00"));
        assertThatThrownBy(tol::statementTolerance).isInstanceOf(IllegalStateException.class);
    }
}
