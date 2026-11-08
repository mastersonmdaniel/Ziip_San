package br.com.ziipsan.core.incident;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.*;
class NetworkIncidentTest {
    static final UUID TEN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID WO1 = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 10, 8, 0);
    NetworkIncident inc(NetworkIncident.Type t) {
        return new NetworkIncident(UUID.randomUUID(), TEN, "OC-1", t, false,
                NetworkIncident.Origin.CREW, T0,
                new NetworkIncident.AffectedSnapshot(List.of(U1), 10, null), null);
    }
    @Test @DisplayName("ciclo OPEN->CONTROLLED->RESTORED->CLOSED; salto e terminal recusam")
    void lifecycle() {
        NetworkIncident i = inc(NetworkIncident.Type.RUPTURE);
        assertThatThrownBy(() -> i.close(new NetworkIncident.ClosureData("X", null, null, WO1), List.of("X")))
                .isInstanceOf(IllegalStateException.class);
        i.markControlled(); i.linkWorkOrder(WO1); i.markRestored(T0.plusHours(5));
        i.close(new NetworkIncident.ClosureData("X", 12.5, 300L, WO1), List.of("X"));
        assertThat(i.status()).isEqualTo(NetworkIncident.Status.CLOSED);
        assertThat(i.closure().lostVolumeM3OrNull()).isEqualTo(12.5);
        assertThatThrownBy(i::markControlled).isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("encerramento exige causa e OS de reparo vinculada")
    void closureGuards() {
        NetworkIncident i = inc(NetworkIncident.Type.RUPTURE);
        i.markRestored(T0.plusHours(2));
        assertThatThrownBy(() -> new NetworkIncident.ClosureData(" ", null, null, WO1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> i.close(new NetworkIncident.ClosureData("X", null, null, WO1), List.of("X")))
                .isInstanceOf(IllegalArgumentException.class); // WO nao vinculada
    }
    @Test @DisplayName("programada exige origem PLANNED e vinculo ED-35")
    void plannedSubtype() {
        assertThatThrownBy(() -> new NetworkIncident(UUID.randomUUID(), TEN, "OC-2",
                NetworkIncident.Type.INTERRUPTION, true, NetworkIncident.Origin.CREW, T0,
                NetworkIncident.AffectedSnapshot.empty(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NetworkIncident(UUID.randomUUID(), TEN, "OC-2",
                NetworkIncident.Type.INTERRUPTION, true, NetworkIncident.Origin.PLANNED, T0,
                NetworkIncident.AffectedSnapshot.empty(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("emergencia devida so apos o limiar e so em interrupcao/falta de energia")
    void emergencyDue() {
        NetworkIncident i = inc(NetworkIncident.Type.INTERRUPTION);
        assertThat(i.emergencySupplyDue(T0.plusHours(24), 24)).isFalse();
        assertThat(i.emergencySupplyDue(T0.plusHours(25), 24)).isTrue();
        assertThat(inc(NetworkIncident.Type.OBSTRUCTION).emergencySupplyDue(T0.plusHours(48), 24)).isFalse();
    }
    @Test @DisplayName("datas de aviso/emergencia anteriores ao inicio recusam")
    void noticeDates() {
        NetworkIncident i = inc(NetworkIncident.Type.FLOODING);
        assertThatThrownBy(() -> i.recordAgencyNotice(T0.minusHours(1))).isInstanceOf(IllegalArgumentException.class);
        i.recordAgencyNotice(T0.plusHours(1)); i.recordUsersNotice(T0.plusHours(2));
        i.recordEmergencySupplyStart(T0.plusHours(26));
        assertThat(i.agencyNotifiedAt()).isEqualTo(T0.plusHours(1));
    }
}
