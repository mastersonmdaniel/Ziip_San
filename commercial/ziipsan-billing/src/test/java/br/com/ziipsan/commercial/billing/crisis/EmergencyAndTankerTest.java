package br.com.ziipsan.commercial.billing.crisis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmergencyAndTankerTest {

    @Test @DisplayName("emergencia a essenciais: >24h dispara; 24h exatas e sem essenciais nao")
    void threshold() {
        assertThat(EmergencyAndTanker.emergencySupplyRequired(Duration.ofHours(25), true)).isTrue();
        assertThat(EmergencyAndTanker.emergencySupplyRequired(Duration.ofHours(24), true)).isFalse();
        assertThat(EmergencyAndTanker.emergencySupplyRequired(Duration.ofHours(48), false)).isFalse();
    }

    @Test @DisplayName("carro-pipa conforme: autorizacao + documento + cloro 0,5 no minimo 0,5")
    void tankerOk() {
        var d = new EmergencyAndTanker.TankerDelivery(UUID.randomUUID(), "AUT-1", "DOC-1",
                new BigDecimal("0.5"), new BigDecimal("0.5"), new BigDecimal("10000"), UUID.randomUUID());
        assertThat(d.measuredChlorineMgL()).isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test @DisplayName("cloro abaixo do minimo recusa a entrega como nao conforme")
    void tankerChlorineBelow() {
        assertThatThrownBy(() -> new EmergencyAndTanker.TankerDelivery(UUID.randomUUID(), "AUT-1", "DOC-1",
                new BigDecimal("0.3"), new BigDecimal("0.5"), new BigDecimal("10000"), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cloro");
        assertThatThrownBy(() -> new EmergencyAndTanker.TankerDelivery(UUID.randomUUID(), " ", "DOC-1",
                new BigDecimal("0.6"), new BigDecimal("0.5"), new BigDecimal("10000"), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("venda avulsa no ponto de abastecimento exige comprovante (CAD-72)")
    void fillingPointSale() {
        var s = new EmergencyAndTanker.FillingPointSale(UUID.randomUUID(), "PONTO-1",
                new BigDecimal("5000"), new BigDecimal("150.00"), "REC-42");
        assertThat(s.receiptCode()).isEqualTo("REC-42");
        assertThatThrownBy(() -> new EmergencyAndTanker.FillingPointSale(UUID.randomUUID(), "PONTO-1",
                new BigDecimal("5000"), new BigDecimal("150.00"), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
