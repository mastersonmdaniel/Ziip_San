package br.com.ziipsan.core.certification.health45001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PpeDeliveryTest {

    private static final Iso45001Activation ON =
            Iso45001Activation.enabled("SYN-escopo", UUID.randomUUID(), UUID.randomUUID());
    private static final LocalDate DAY = LocalDate.of(2026, 1, 15);

    private static PpeDelivery delivery(String ca, LocalDate validUntil, boolean ack) {
        return PpeDelivery.deliver(ON, UUID.randomUUID(), "SYN-FUNCAO", "SYN-EPI-1", "SYN-LOTE-1",
                ca, 1, "SYN-REQ-1", "SYN-ALMOX-1", UUID.randomUUID(), DAY, validUntil, ack);
    }

    @Test
    @DisplayName("entrega válida com CA, lote válido e ciência do trabalhador")
    void validDelivery() {
        PpeDelivery d = delivery("SYN-CA-123", DAY.plusMonths(11), true);
        assertThatCode(() -> d.requireUsableOn(DAY.plusMonths(11))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("EPI sem CA não é entregue")
    void missingCaRefused() {
        assertThatThrownBy(() -> delivery("  ", DAY.plusMonths(6), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("lote vencido na entrega é recusado e o uso após vencer bloqueia")
    void expiredLot() {
        assertThatThrownBy(() -> delivery("SYN-CA-1", DAY.minusDays(1), true))
                .isInstanceOf(IllegalArgumentException.class);
        PpeDelivery d = delivery("SYN-CA-1", DAY.plusDays(10), true);
        assertThatThrownBy(() -> d.requireUsableOn(DAY.plusDays(11)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("sem ciência do trabalhador a entrega não nasce (ficha de EPI)")
    void acknowledgementRequired() {
        assertThatThrownBy(() -> delivery("SYN-CA-1", DAY.plusMonths(6), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("norma desligada: a entrega não nasce")
    void disabledRefused() {
        assertThatThrownBy(() -> PpeDelivery.deliver(Iso45001Activation.disabled(),
                UUID.randomUUID(), "SYN-F", "SYN-I", "SYN-L", "SYN-CA", 1, "SYN-R", "SYN-A",
                UUID.randomUUID(), DAY, DAY.plusDays(30), true))
                .isInstanceOf(IllegalStateException.class);
    }
}
