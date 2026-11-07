package br.com.ziipsan.commercial.collection.parameters;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P3.1 - parametros da arrecadacao: ausencia e erro explicito, nunca presuncao. */
class CollectionParametersTest {

    @Test
    void leToleranciaERetencaoDoJsonViaClasseJsonDoCore() {
        var params = CollectionParameters.fromValues(Map.of(
                CollectionParameters.ADJUST_TOLERANCE, "5.00",
                CollectionParameters.WITHHOLDING_ENABLED, "false"));
        assertThat(params.adjustTolerance()).isEqualByComparingTo("5.00");
        assertThat(params.withholdingEnabled()).isFalse();
    }

    @Test
    void toleranciaAusenteEErroExplicito() {
        var params = CollectionParameters.fromValues(Map.of(
                CollectionParameters.WITHHOLDING_ENABLED, "true"));
        assertThatThrownBy(params::adjustTolerance).isInstanceOf(MissingParameterException.class);
    }

    @Test
    void retencaoAusenteEErroExplicitoNuncaPresumeDesligadaNemLigada() {
        var params = CollectionParameters.fromValues(Map.of(
                CollectionParameters.ADJUST_TOLERANCE, "5.00"));
        assertThatThrownBy(params::withholdingEnabled).isInstanceOf(MissingParameterException.class);
    }

    @Test
    void toleranciaNegativaERecusada() {
        var params = CollectionParameters.fromValues(Map.of(
                CollectionParameters.ADJUST_TOLERANCE, "-1.00",
                CollectionParameters.WITHHOLDING_ENABLED, "false"));
        assertThatThrownBy(params::adjustTolerance).isInstanceOf(IllegalStateException.class);
    }
}
