package br.com.ziipsan.core.incident;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Testes da CORRECAO da ED-92 (revisao REPROVOU: M1, M2, m2).
 * Cada teste captura um defeito: falha no pacote original.
 */
class CorrectionEd92Test {
    static final UUID TEN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID WO1 = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 10, 8, 0);

    NetworkIncident closedUnknown() {
        NetworkIncident i = new NetworkIncident(UUID.randomUUID(), TEN, "OC-U",
                NetworkIncident.Type.INTERRUPTION, false, NetworkIncident.Origin.TELEMETRY, T0,
                NetworkIncident.AffectedSnapshot.empty(), null);
        i.linkWorkOrder(WO1); i.markRestored(T0.plusHours(4));
        i.close(new NetworkIncident.ClosureData("ALAGAMENTO", null, null, WO1), List.of("ALAGAMENTO"));
        return i;
    }

    @Test @DisplayName("M1: bicondicional — planned=false com origem PLANNED (sem vinculo) recusa no construtor")
    void m1ConstructorReverse() {
        assertThatThrownBy(() -> new NetworkIncident(UUID.randomUUID(), TEN, "OC-M1",
                NetworkIncident.Type.INTERRUPTION, false, NetworkIncident.Origin.PLANNED, T0,
                NetworkIncident.AffectedSnapshot.empty(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("M1: bicondicional — service.open com origem PLANNED recusa (sem vinculo ED-35)")
    void m1ServiceOpenPlannedOrigin() {
        NetworkIncidentService.StorePort store = new NetworkIncidentService.StorePort() {
            public void save(NetworkIncident i) {}
            public Optional<NetworkIncident> find(UUID t, UUID id) { return Optional.empty(); }
            public List<NetworkIncident> list(UUID t) { return List.of(); }
        };
        NetworkIncidentService v = new NetworkIncidentService(t -> "OC-S", null, store);
        assertThatThrownBy(() -> v.open(TEN, NetworkIncident.Type.INTERRUPTION,
                NetworkIncident.Origin.PLANNED, T0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("M2: JOR-33 com TUDO desconhecido devolve total/media AUSENTES (null), nao 0.0")
    void m2AllUnknownIsAbsentNotZero() {
        var rows = IncidentReport.byCause(List.of(closedUnknown(), closedUnknown()));
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.volumeKnown()).isEqualTo(0);
        assertThat(row.repairKnown()).isEqualTo(0);
        assertThat((Object) row.totalLostVolumeM3()).isNull();
        assertThat((Object) row.avgRepairMinutesKnown()).isNull();
    }

    @Test @DisplayName("m2: fechamento direto na entidade valida a causa contra o catalogo")
    void m2EntityCloseValidatesCatalog() {
        NetworkIncident i = new NetworkIncident(UUID.randomUUID(), TEN, "OC-C",
                NetworkIncident.Type.RUPTURE, false, NetworkIncident.Origin.CREW, T0,
                NetworkIncident.AffectedSnapshot.empty(), null);
        i.linkWorkOrder(WO1); i.markRestored(T0.plusHours(2));
        assertThatThrownBy(() -> i.close(
                new NetworkIncident.ClosureData("QUALQUER_UMA", null, null, WO1), List.of("ALAGAMENTO")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(i.status()).isEqualTo(NetworkIncident.Status.RESTORED); // nao corrompe o estado
        i.close(new NetworkIncident.ClosureData("ALAGAMENTO", null, null, WO1), List.of("ALAGAMENTO"));
        assertThat(i.status()).isEqualTo(NetworkIncident.Status.CLOSED);
    }

    @Test @DisplayName("m2: datas de aviso/emergencia NAO sao gravaveis apos CLOSED")
    void m2NoNoticeWritesAfterClosed() {
        NetworkIncident i = closedUnknown();
        assertThat(i.status()).isEqualTo(NetworkIncident.Status.CLOSED);
        assertThatThrownBy(() -> i.recordAgencyNotice(T0.plusHours(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> i.recordUsersNotice(T0.plusHours(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> i.recordEmergencySupplyStart(T0.plusHours(30)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(i.agencyNotifiedAt()).isNull();
        assertThat(i.usersNotifiedAt()).isNull();
        assertThat(i.emergencySupplyStartedAt()).isNull();
    }
}
