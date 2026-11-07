package br.com.ziipsan.operations.metering.hydrometry;

import br.com.ziipsan.operations.metering.domain.Meter;
import br.com.ziipsan.operations.metering.domain.MeterInstallation;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.RemovalDestination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** B2: instalação é serviço — ligação, OS, catálogos e lacre exigidos; retirada fecha e move. */
class MeterInstallationServiceTest {
    static final UUID OS = UUID.randomUUID(), CONN = UUID.randomUUID(), PROT = UUID.randomUUID(),
            LOC = UUID.randomUUID(), STORE = UUID.randomUUID();

    static class FakeInstallations implements MeterInstallationService.InstallationStore {
        MeterInstallation open;
        public MeterInstallation findOpen(UUID t, UUID meterId) { return open; }
        public void save(MeterInstallation i) { if (i.getRemovedOn() == null) open = i; else open = null; last = i; }
        MeterInstallation last;
    }
    static class FakeMeters implements MeterLifecycleService.MeterStore, MeterLifecycleService.MovementStore {
        Meter meter; List<String> history = new ArrayList<>();
        public Meter find(UUID t, UUID id) { return meter; }
        public void save(Meter m) { meter = m; }
        public void append(UUID t, UUID mid, HydrometryEnums.MovementType ty, LocalDate on, UUID os, UUID inst,
                           UUID loc, RemovalDestination d, String r) { history.add(ty.name()); }
    }

    private record Fixture(FakeMeters meters, FakeInstallations installations, MeterInstallationService svc, Meter meter) {}
    private Fixture fixture() {
        FakeMeters fm = new FakeMeters(); FakeInstallations fi = new FakeInstallations();
        Meter m = new Meter(); m.setId(UUID.randomUUID()); m.transitionTo(Meter.Status.AVAILABLE);
        m.setStorageLocationId(STORE); fm.meter = m;
        MeterLifecycleService lifecycle = new MeterLifecycleService(fm, fm, (t, lot) -> UUID.randomUUID());
        MeterInstallationService svc = new MeterInstallationService(fi,
                (t, id) -> id.equals(PROT) ? "PROTECTION" : id.equals(LOC) ? "INSTALL_LOCATION" : null,
                (t, id) -> id.equals(CONN), lifecycle);
        return new Fixture(fm, fi, svc, m);
    }
    private MeterInstallationService.InstallCommand cmd(UUID meterId) {
        return new MeterInstallationService.InstallCommand(meterId, UUID.randomUUID(), CONN, OS, PROT, LOC,
                "LACRE-1", 0, false, LocalDate.of(2026, 1, 10));
    }

    @Test @DisplayName("instalar grava instalação completa (ligação, OS, catálogos, lacre) e instala o hidrômetro")
    void installFull() {
        Fixture f = fixture();
        MeterInstallation inst = f.svc().install(UUID.randomUUID(), cmd(f.meter().getId()));
        assertThat(inst.getServiceConnectionId()).isEqualTo(CONN);
        assertThat(inst.getInstallServiceOrderId()).isEqualTo(OS);
        assertThat(inst.getSealNumber()).isEqualTo("LACRE-1");
        assertThat(f.meter().getStatus()).isEqualTo(Meter.Status.INSTALLED);
        assertThat(f.meters().history).containsExactly("INSTALL");
    }

    @Test @DisplayName("instalação sem ligação, sem OS, sem catálogo ou sem lacre é recusada")
    void installGuards() {
        Fixture f = fixture(); UUID mid = f.meter().getId();
        var base = cmd(mid);
        assertThatThrownBy(() -> f.svc().install(UUID.randomUUID(), new MeterInstallationService.InstallCommand(
                mid, base.unitId(), null, OS, PROT, LOC, "L", 0, false, LocalDate.now()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.svc().install(UUID.randomUUID(), new MeterInstallationService.InstallCommand(
                mid, base.unitId(), CONN, null, PROT, LOC, "L", 0, false, LocalDate.now()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.svc().install(UUID.randomUUID(), new MeterInstallationService.InstallCommand(
                mid, base.unitId(), CONN, OS, LOC, LOC, "L", 0, false, LocalDate.now()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.svc().install(UUID.randomUUID(), new MeterInstallationService.InstallCommand(
                mid, base.unitId(), CONN, OS, PROT, LOC, " ", 0, false, LocalDate.now()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.svc().install(UUID.randomUUID(), new MeterInstallationService.InstallCommand(
                mid, base.unitId(), UUID.randomUUID(), OS, PROT, LOC, "L", 0, false, LocalDate.now()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("retirada exige OS e destino, fecha a instalação e devolve o hidrômetro ao estoque")
    void removeFull() {
        Fixture f = fixture(); UUID mid = f.meter().getId();
        f.svc().install(UUID.randomUUID(), cmd(mid));
        assertThatThrownBy(() -> f.svc().remove(UUID.randomUUID(), new MeterInstallationService.RemoveCommand(
                mid, null, RemovalDestination.AVAILABLE, 10, LocalDate.now(), STORE, null))).isInstanceOf(IllegalArgumentException.class);
        MeterInstallation closed = f.svc().remove(UUID.randomUUID(), new MeterInstallationService.RemoveCommand(
                mid, OS, RemovalDestination.AVAILABLE, 10, LocalDate.of(2026, 2, 1), STORE, null));
        assertThat(closed.getRemoveServiceOrderId()).isEqualTo(OS);
        assertThat(closed.getRemovalDestination()).isEqualTo(RemovalDestination.AVAILABLE);
        assertThat(f.meter().getStatus()).isEqualTo(Meter.Status.AVAILABLE);
    }
}
