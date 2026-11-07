package br.com.ziipsan.operations.metering.hydrometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Próxima verificação calculada (7 anos, só reduz) e OS em lote no alerta de validade (ED-14). */
class VerificationAndAlertTest {
    @Test @DisplayName("próxima data = última + intervalo; intervalo acima de 7 é recusado (só reduz)")
    void nextDateCalculated() {
        assertThat(VerificationSchedule.nextVerificationOn(LocalDate.of(2020, 3, 1), 7)).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(VerificationSchedule.nextVerificationOn(LocalDate.of(2020, 3, 1), 5)).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThatThrownBy(() -> MeterParameterRules.validateVerificationIntervalYears(8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VerificationSchedule.nextVerificationOn(null, 7)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("alerta gera OS em lote só para vencidos/no horizonte e sem duplicar OS aberta")
    void batchOrders() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        List<UUID> opened = new ArrayList<>();
        VerificationAlertService svc = new VerificationAlertService(
                (t, meter) -> { UUID id = UUID.randomUUID(); opened.add(meter); return id; },
                (t, meter) -> meter.equals(c));
        List<VerificationSchedule.DueMeter> candidates = List.of(
                new VerificationSchedule.DueMeter(a, LocalDate.of(2026, 1, 1)),
                new VerificationSchedule.DueMeter(b, LocalDate.of(2030, 1, 1)),
                new VerificationSchedule.DueMeter(c, LocalDate.of(2026, 6, 1)));
        List<UUID> orders = svc.generateBatch(UUID.randomUUID(), candidates, LocalDate.of(2026, 11, 1), 90);
        assertThat(orders).hasSize(1);
        assertThat(opened).containsExactly(a);
    }
    @Test @DisplayName("A5: candidato duplicado NA MESMA varredura abre uma única OS")
    void dedupWithinSweep() {
        UUID a = UUID.randomUUID();
        List<UUID> opened = new ArrayList<>();
        VerificationAlertService svc = new VerificationAlertService(
                (t, meter) -> { opened.add(meter); return UUID.randomUUID(); },
                (t, meter) -> false);
        List<VerificationSchedule.DueMeter> duplicated = List.of(
                new VerificationSchedule.DueMeter(a, LocalDate.of(2026, 1, 1)),
                new VerificationSchedule.DueMeter(a, LocalDate.of(2026, 1, 1)),
                new VerificationSchedule.DueMeter(a, LocalDate.of(2025, 12, 1)));
        List<UUID> orders = svc.generateBatch(UUID.randomUUID(), duplicated, LocalDate.of(2026, 11, 1), 90);
        assertThat(orders).hasSize(1);
        assertThat(opened).containsExactly(a);
    }
}
