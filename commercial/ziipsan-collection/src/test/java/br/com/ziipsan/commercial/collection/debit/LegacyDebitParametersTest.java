package br.com.ziipsan.commercial.collection.debit;

import br.com.ziipsan.commercial.collection.parameters.LegacyDebitParameters;
import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyDebitParametersTest {

    @Test
    void ausenciaDoParametroEhErroExplicitoNuncaPresumido() {
        LegacyDebitParameters p = LegacyDebitParameters.fromValues(Map.of());
        assertThatThrownBy(p::legacyDebitEnabled).isInstanceOf(MissingParameterException.class);
    }

    @Test
    void ligadoEDesligadoSaoLidosDoParametro() {
        assertThat(LegacyDebitParameters.fromValues(Map.of(
                LegacyDebitParameters.LEGACY_DEBIT_ENABLED, "true")).legacyDebitEnabled()).isTrue();
        assertThat(LegacyDebitParameters.fromValues(Map.of(
                LegacyDebitParameters.LEGACY_DEBIT_ENABLED, "false")).legacyDebitEnabled()).isFalse();
    }

    @Test
    void valorNaoBooleanoEhRecusado() {
        LegacyDebitParameters p = LegacyDebitParameters.fromValues(Map.of(
                LegacyDebitParameters.LEGACY_DEBIT_ENABLED, "\"sim\""));
        assertThatThrownBy(p::legacyDebitEnabled).isInstanceOf(IllegalStateException.class);
    }
}
