package br.com.ziipsan.commercial.collection.retorno;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3.1 - retencao na fonte (IN RFB 1.234/2012), recurso desligado e ligado.
 * Caso calculado a mao: conta de 1.000,00, retencao de 65,00.
 */
class WithholdingRuleTest {

    private final WithholdingRule rule = new WithholdingRule();

    @Test
    void recursoDesligadoSemRetencaoBaixaNormal() {
        var r = rule.apply(false, true, new BigDecimal("1000.00"), new BigDecimal("0.00"));
        assertThat(r.settledAmount()).isEqualByComparingTo("1000.00");
        assertThat(r.netReceived()).isEqualByComparingTo("1000.00");
        assertThat(r.tenantCredit()).isEqualByComparingTo("0.00");
    }

    @Test
    void recursoDesligadoComRetencaoInformadaEErroExplicito() {
        assertThatThrownBy(() -> rule.apply(false, true, new BigDecimal("1000.00"), new BigDecimal("65.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PD-10");
    }

    @Test
    void recursoLigadoPagadorPublicoGeraCreditoDoTenant() {
        // Baixa pelo bruto 1.000,00; caixa 935,00; credito do tenant 65,00.
        var r = rule.apply(true, true, new BigDecimal("1000.00"), new BigDecimal("65.00"));
        assertThat(r.settledAmount()).isEqualByComparingTo("1000.00");
        assertThat(r.netReceived()).isEqualByComparingTo("935.00");
        assertThat(r.tenantCredit()).isEqualByComparingTo("65.00");
    }

    @Test
    void recursoLigadoPagadorPrivadoNaoRete() {
        assertThatThrownBy(() -> rule.apply(true, false, new BigDecimal("1000.00"), new BigDecimal("65.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recursoLigadoSemRetencaoNaoGeraCredito() {
        var r = rule.apply(true, true, new BigDecimal("1000.00"), new BigDecimal("0.00"));
        assertThat(r.tenantCredit()).isEqualByComparingTo("0.00");
        assertThat(r.netReceived()).isEqualByComparingTo("1000.00");
    }
}
