package br.com.ziipsan.commercial.billing.crisis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporaryAndApplicationTest {

    private TemporaryConnection conn(int maxDays) {
        return new TemporaryConnection(UUID.randomUUID(), TemporaryConnection.Purpose.EVENT,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10), "TAXA-EVENTO", maxDays);
    }

    @Test @DisplayName("vigencia dentro de connection.temporary_max_days aceita; acima recusada")
    void maxDays() {
        assertThat(conn(30).activeOn(LocalDate.of(2026, 1, 5))).isTrue();
        assertThatThrownBy(() -> conn(5)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temporary_max_days");
    }

    @Test @DisplayName("temporaria NAO e a situacao PROVISIONAL do ADR-0039 (cadastro incompleto)")
    void notProvisional() {
        assertThat(conn(30).isProvisionalSituation()).isFalse();
        assertThat(conn(30).situation()).isEqualTo(TemporaryConnection.RegistrationSituation.COMPLETE);
    }

    @Test @DisplayName("aviso de vencimento na janela final; encerramento so por OS e uma vez")
    void noticeAndClosure() {
        var c = conn(30);
        assertThat(c.expiryNoticeDue(LocalDate.of(2026, 1, 9), 3)).isTrue();
        assertThat(c.expiryNoticeDue(LocalDate.of(2026, 1, 2), 3)).isFalse();
        c.closeByOrder(UUID.randomUUID());
        assertThat(c.activeOn(LocalDate.of(2026, 1, 5))).isFalse();
        assertThatThrownBy(() -> c.closeByOrder(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("hidrante: permissao com medicao obrigatoria e positiva")
    void hydrant() {
        var p = new TemporaryConnection.HydrantUsePermit(UUID.randomUUID(), "HID-7", "PERM-1", new BigDecimal("12.5"));
        assertThat(p.meteredVolumeM3()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThatThrownBy(() -> new TemporaryConnection.HydrantUsePermit(UUID.randomUUID(), "HID-7", "PERM-1", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test @DisplayName("aplicacao por OS de lote registra o lote; rodizio comunica por ED-35 e ED-54")
    void applicationService() {
        var batchId = UUID.randomUUID();
        var sacCalls = new AtomicInteger(); var manCalls = new AtomicInteger();
        var svc = new CrisisApplicationService((purpose, ids) -> batchId,
                plan -> sacCalls.incrementAndGet(), plan -> manCalls.incrementAndGet());
        var m = new ExceptionalMeasure(UUID.randomUUID(), ExceptionalMeasure.ActKind.AGENCY_ACT, "Ato 3",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                new ExceptionalMeasure.Area(ExceptionalMeasure.AreaKind.SECTOR, "MS-01", Set.of()),
                Set.of(ExceptionalMeasure.Effect.SUSPEND_CUT));
        m.activate();
        assertThat(svc.applyMeasure(m)).isEqualTo(batchId);
        assertThat(m.batchOrderId()).isEqualTo(batchId);
        svc.communicateRotation(new RotationPlan(UUID.randomUUID(), Set.of("MS-01"), true));
        assertThat(sacCalls.get()).isEqualTo(1);
        assertThat(manCalls.get()).isEqualTo(1);
        assertThat(List.of(m)).isNotEmpty();
    }
}
