package br.com.ziipsan.operations.metering.hydrometry;

import br.com.ziipsan.operations.metering.domain.Meter;
import br.com.ziipsan.operations.metering.hydrometry.HydrometryEnums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Ciclo POC-12..14: recebimento, instalação, retirada (disponível/sucata), reinstalação, histórico (ED-14). */
class MeterLifecycleTest {
    private static final UUID OS = UUID.randomUUID(), STORE = UUID.randomUUID();

    static MeterLifecycleService newService(FakeStore s) {
        // lote -> modelo: o lote de teste devolve o modelo passado no recebimento via mapa simples
        return new MeterLifecycleService(s, s, (tenant, lot) -> LOT_MODELS.get(lot));
    }
    static final java.util.Map<UUID, UUID> LOT_MODELS = new java.util.HashMap<>();

    static class FakeStore implements MeterLifecycleService.MeterStore, MeterLifecycleService.MovementStore {
        Meter meter; List<String> history = new ArrayList<>();
        public Meter find(UUID t, UUID id) { return meter; }
        public void save(Meter m) { meter = m; }
        public void append(UUID t, UUID mid, MovementType ty, java.time.LocalDate on, UUID os, UUID inst,
                           UUID loc, RemovalDestination d, String reason) { history.add(ty.name()); }
    }

    @Test @DisplayName("fluxo completo com histórico: recebe, instala, retira p/ disponível, reinstala, retira p/ sucata")
    void fullCycle() {
        FakeStore s = new FakeStore();
        MeterLifecycleService svc = newService(s);
        Meter m = new Meter(); m.setId(UUID.randomUUID());
        UUID lot = UUID.randomUUID(), model = UUID.randomUUID(); LOT_MODELS.put(lot, model);
        svc.receive(UUID.randomUUID(), m, lot, model, STORE, java.time.LocalDate.now());
        assertThat(m.getStatus()).isEqualTo(Meter.Status.AVAILABLE);
        svc.move(UUID.randomUUID(), m.getId(), MovementType.INSTALL, null, OS, UUID.randomUUID(), null, null, java.time.LocalDate.now());
        assertThat(m.getStatus()).isEqualTo(Meter.Status.INSTALLED);
        svc.move(UUID.randomUUID(), m.getId(), MovementType.REMOVE, RemovalDestination.AVAILABLE, OS, null, STORE, null, java.time.LocalDate.now());
        assertThat(m.getStatus()).isEqualTo(Meter.Status.AVAILABLE);
        svc.move(UUID.randomUUID(), m.getId(), MovementType.REINSTALL, null, OS, UUID.randomUUID(), null, null, java.time.LocalDate.now());
        svc.move(UUID.randomUUID(), m.getId(), MovementType.REMOVE, RemovalDestination.SCRAP, OS, null, null, "fim de vida útil", java.time.LocalDate.now());
        assertThat(m.getStatus()).isEqualTo(Meter.Status.WRITTEN_OFF);
        assertThat(s.history).containsExactly("RECEIPT", "INSTALL", "REMOVE", "REINSTALL", "REMOVE");
    }
    @Test @DisplayName("estado não muda sem OS, sem armazenagem (disponível/manutenção) nem sem motivo de baixa")
    void guards() {
        assertThatThrownBy(() -> MeterLifecycleRule.requireTransition(Meter.Status.AVAILABLE,
                MovementType.INSTALL, null, null, STORE, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MeterLifecycleRule.requireTransition(null,
                MovementType.RECEIPT, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MeterLifecycleRule.requireTransition(Meter.Status.IN_MAINTENANCE,
                MovementType.WRITE_OFF, null, OS, STORE, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MeterLifecycleRule.requireTransition(Meter.Status.INSTALLED,
                MovementType.INSTALL, null, OS, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MeterLifecycleRule.requireTransition(Meter.Status.AVAILABLE,
                MovementType.REMOVE, null, OS, STORE, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("A3: re-recebimento de hidrômetro com estado é recusado; lote nulo e modelo divergente do lote também")
    void receiveGuards() {
        FakeStore s = new FakeStore();
        MeterLifecycleService svc = newService(s);
        Meter m = new Meter(); m.setId(UUID.randomUUID());
        UUID lot = UUID.randomUUID(), model = UUID.randomUUID(); LOT_MODELS.put(lot, model);
        svc.receive(UUID.randomUUID(), m, lot, model, STORE, java.time.LocalDate.now());
        assertThatThrownBy(() -> svc.receive(UUID.randomUUID(), m, lot, model, STORE, java.time.LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
        Meter fresh = new Meter(); fresh.setId(UUID.randomUUID());
        assertThatThrownBy(() -> svc.receive(UUID.randomUUID(), fresh, null, model, STORE, java.time.LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.receive(UUID.randomUUID(), fresh, lot, UUID.randomUUID(), STORE, java.time.LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("A2: cadastro novo com texto livre legado preenchido é recusado")
    void receiveRejectsLegacyFreeText() {
        FakeStore s = new FakeStore();
        MeterLifecycleService svc = newService(s);
        Meter m = new Meter(); m.setId(UUID.randomUUID()); m.setBrand("Marca Livre");
        UUID lot = UUID.randomUUID(), model = UUID.randomUUID(); LOT_MODELS.put(lot, model);
        assertThatThrownBy(() -> svc.receive(UUID.randomUUID(), m, lot, model, STORE, java.time.LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
        Meter m2 = new Meter(); m2.setId(UUID.randomUUID()); m2.setMetrologicalClass("B");
        assertThatThrownBy(() -> svc.receive(UUID.randomUUID(), m2, lot, model, STORE, java.time.LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("A4/B2: Meter não expõe setter público de status; INSTALL sem installationId é recusado")
    void structuralGuards() {
        assertThatThrownBy(() -> Meter.class.getMethod("setStatus", Meter.Status.class))
                .isInstanceOf(NoSuchMethodException.class);
        FakeStore s = new FakeStore();
        MeterLifecycleService svc = newService(s);
        Meter m = new Meter(); m.setId(UUID.randomUUID());
        UUID lot = UUID.randomUUID(), model = UUID.randomUUID(); LOT_MODELS.put(lot, model);
        svc.receive(UUID.randomUUID(), m, lot, model, STORE, java.time.LocalDate.now());
        s.meter = m;
        assertThatThrownBy(() -> svc.move(UUID.randomUUID(), m.getId(), MovementType.INSTALL, null, OS, null, null, null, java.time.LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
