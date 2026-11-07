package br.com.ziipsan.commercial.collection.parameters;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ED-26 - parametros da arrecadacao ampliada (JSON so via Json do core). */
class ArrecadacaoParametersTest {

    @Test
    void ordemDeQuitacaoParcialELidaDoParametro() {
        var p = ArrecadacaoParameters.fromValues(Map.of(
                ArrecadacaoParameters.PARTIAL_SETTLEMENT_ORDER, "\"DUE_DATE_ASC\""));
        assertThat(p.partialSettlementOrder())
                .isEqualTo(ArrecadacaoParameters.PartialSettlementOrder.DUE_DATE_ASC);
    }

    @Test
    void ordemAusenteEErroExplicitoSemPadraoPresumido() {
        var p = ArrecadacaoParameters.fromValues(Map.of());
        assertThatThrownBy(p::partialSettlementOrder).isInstanceOf(MissingParameterException.class);
    }

    @Test
    void ordemDesconhecidaERecusada() {
        var p = ArrecadacaoParameters.fromValues(Map.of(
                ArrecadacaoParameters.PARTIAL_SETTLEMENT_ORDER, "\"MAIOR_PRIMEIRO\""));
        assertThatThrownBy(p::partialSettlementOrder).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void caixaDesligadoPorPadraoELigadoSoPorParametro() {
        assertThat(ArrecadacaoParameters.fromValues(Map.of()).ownCashierEnabled()).isFalse();
        assertThat(ArrecadacaoParameters.fromValues(Map.of(
                ArrecadacaoParameters.OWN_CASHIER_ENABLED, "true")).ownCashierEnabled()).isTrue();
        assertThat(ArrecadacaoParameters.fromValues(Map.of(
                ArrecadacaoParameters.OWN_CASHIER_ENABLED, "false")).ownCashierEnabled()).isFalse();
    }
}
