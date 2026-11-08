package br.com.ziipsan.intelligence.losses.pressure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

class PrvAdjustmentTest {
    static final UUID TEN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID ZONE = UUID.randomUUID();
    static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 10, 9, 0);

    static PrvAdjustment adj(LocalDateTime at, double prev, double next) {
        return new PrvAdjustment(UUID.randomUUID(), TEN, ZONE, "VRP-01",
                UUID.randomUUID(), UUID.randomUUID(), at, "reclamacao de pouca agua na zona", prev, next);
    }

    @Test @DisplayName("ajuste por OS com quem/quando/motivo/anterior/novo: delta calculado")
    void adjustmentRecordedByWorkOrder() {
        PrvAdjustment a = adj(T0, 25.0, 32.0);
        assertThat(a.deltaMca()).isEqualTo(7.0);
        assertThat(a.workOrderId()).isNotNull();
        assertThat(a.adjustedByUserId()).isNotNull();
        assertThat(a.reason()).isNotBlank();
    }

    @Test @DisplayName("ajuste invalido recusa: sem mudanca de valor, sem motivo, sem OS")
    void invalidAdjustmentsRefuse() {
        assertThatThrownBy(() -> adj(T0, 30.0, 30.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PrvAdjustment(UUID.randomUUID(), TEN, ZONE, "VRP-01",
                UUID.randomUUID(), UUID.randomUUID(), T0, "  ", 25.0, 30.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PrvAdjustment(UUID.randomUUID(), TEN, ZONE, "VRP-01",
                null, UUID.randomUUID(), T0, "motivo", 25.0, 30.0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test @DisplayName("cadeia continua da VRP aceita fora de ordem; cadeia quebrada denuncia ajuste nao registrado")
    void continuityChain() {
        PrvAdjustment a1 = adj(T0, 25.0, 32.0);
        PrvAdjustment a2 = adj(T0.plusDays(2), 32.0, 28.0);
        PrvAdjustment broken = adj(T0.plusDays(3), 35.0, 30.0);
        assertThatCode(() -> PrvAdjustment.requireContinuity(List.of(a2, a1))).doesNotThrowAnyException();
        assertThatThrownBy(() -> PrvAdjustment.requireContinuity(List.of(a1, a2, broken)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cadeia quebrada");
    }
}
