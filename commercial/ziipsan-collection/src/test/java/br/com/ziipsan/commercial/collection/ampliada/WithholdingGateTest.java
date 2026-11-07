package br.com.ziipsan.commercial.collection.ampliada;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-26 - portao do parecer da retencao (PD-10/RAS-27): sem parecer, sem aplicar,
 * mesmo com o parametro ligado. Caso a mao: bruto 1.000,00, retido 65,00.
 */
class WithholdingGateTest {

    private final WithholdingGate gate = new WithholdingGate();

    @Test
    void semParecerRetencaoNaoEAplicadaMesmoComParametroLigado() {
        assertThatThrownBy(() -> gate.settle(false, true, true,
                new BigDecimal("1000.00"), new BigDecimal("65.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("parecer");
    }

    @Test
    void comParecerEParametroLigadoAplicaRegraDaP31() {
        var r = gate.settle(true, true, true, new BigDecimal("1000.00"), new BigDecimal("65.00"));
        assertThat(r.settledAmount()).isEqualByComparingTo("1000.00");
        assertThat(r.netReceived()).isEqualByComparingTo("935.00");
        assertThat(r.tenantCredit()).isEqualByComparingTo("65.00");
    }

    @Test
    void comParecerMasParametroDesligadoSegueRecusaDaP31() {
        assertThatThrownBy(() -> gate.settle(true, false, true,
                new BigDecimal("1000.00"), new BigDecimal("65.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void semRetencaoNaoPrecisaDeParecer() {
        var r = gate.settle(false, false, true, new BigDecimal("1000.00"), new BigDecimal("0.00"));
        assertThat(r.settledAmount()).isEqualByComparingTo("1000.00");
        assertThat(r.tenantCredit()).isEqualByComparingTo("0.00");
    }
}
