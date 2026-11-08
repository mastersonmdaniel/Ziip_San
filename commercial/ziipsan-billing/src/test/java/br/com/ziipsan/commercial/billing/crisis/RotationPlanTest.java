package br.com.ziipsan.commercial.billing.crisis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RotationPlanTest {

    private RotationPlan plan() {
        var p = new RotationPlan(UUID.randomUUID(), Set.of("MS-01", "MS-02"), true);
        p.add(new RotationPlan.RotationEntry("MS-01", LocalDateTime.of(2026, 1, 10, 6, 0), LocalDateTime.of(2026, 1, 10, 18, 0)));
        p.add(new RotationPlan.RotationEntry("MS-01", LocalDateTime.of(2026, 1, 12, 6, 0), LocalDateTime.of(2026, 1, 12, 12, 0)));
        p.add(new RotationPlan.RotationEntry("MS-02", LocalDateTime.of(2026, 1, 10, 0, 0), LocalDateTime.of(2026, 1, 11, 0, 0)));
        return p;
    }

    @Test @DisplayName("horas sem agua por setor (entrada da intermitencia da ED-92): 12h + 6h = 18h")
    void hours() {
        var p = plan();
        assertThat(p.hoursWithoutWater("MS-01", LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 2, 1, 0, 0)))
                .isEqualTo(Duration.ofHours(18));
        assertThat(p.hoursWithoutWater("MS-02", LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 2, 1, 0, 0)))
                .isEqualTo(Duration.ofHours(24));
    }

    @Test @DisplayName("janela parcial recorta a entrada: 06h-12h dentro de 06h-18h = 6h")
    void hoursClipped() {
        assertThat(plan().hoursWithoutWater("MS-01", LocalDateTime.of(2026, 1, 10, 6, 0), LocalDateTime.of(2026, 1, 10, 12, 0)))
                .isEqualTo(Duration.ofHours(6));
    }

    @Test @DisplayName("m1: janelas sobrepostas do mesmo setor somam a UNIAO (06h-18h + 12h-24h = 18h, nao 24h)")
    void overlappingWindowsUnion() {
        var p = new RotationPlan(UUID.randomUUID(), Set.of("MS-01"), true);
        p.add(new RotationPlan.RotationEntry("MS-01", LocalDateTime.of(2026, 1, 10, 6, 0), LocalDateTime.of(2026, 1, 10, 18, 0)));
        p.add(new RotationPlan.RotationEntry("MS-01", LocalDateTime.of(2026, 1, 10, 12, 0), LocalDateTime.of(2026, 1, 11, 0, 0)));
        assertThat(p.hoursWithoutWater("MS-01", LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 2, 1, 0, 0)))
                .isEqualTo(Duration.ofHours(18));
    }

    @Test @DisplayName("rodizio desligado (crisis.rotation_enabled=false) recusa o plano")
    void disabled() {
        assertThatThrownBy(() -> new RotationPlan(UUID.randomUUID(), Set.of("MS-01"), false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("setor fora do plano e recusado no calendario")
    void unknownSector() {
        assertThatThrownBy(() -> plan().add(new RotationPlan.RotationEntry("MS-99",
                LocalDateTime.of(2026, 1, 10, 6, 0), LocalDateTime.of(2026, 1, 10, 7, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
