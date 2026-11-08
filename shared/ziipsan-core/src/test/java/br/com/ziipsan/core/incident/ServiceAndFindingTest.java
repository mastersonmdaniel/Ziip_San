package br.com.ziipsan.core.incident;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
class ServiceAndFindingTest {
    static final UUID TEN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 10, 8, 0);
    static final class MemStore implements NetworkIncidentService.StorePort {
        final Map<UUID, NetworkIncident> m = new HashMap<>();
        public void save(NetworkIncident i) { m.put(i.id(), i); }
        public Optional<NetworkIncident> find(UUID t, UUID id) {
            NetworkIncident i = m.get(id); return i != null && i.tenantId().equals(t) ? Optional.of(i) : Optional.empty();
        }
        public List<NetworkIncident> list(UUID t) { return m.values().stream().filter(i -> i.tenantId().equals(t)).toList(); }
    }
    NetworkIncidentService svc(MemStore s) {
        AtomicInteger seq = new AtomicInteger();
        return new NetworkIncidentService(t -> "OC-" + seq.incrementAndGet(), null, s);
    }
    @Test @DisplayName("codigo unico por tenant via porta; subtipo programada; verificador P1.31")
    void service() {
        MemStore s = new MemStore(); NetworkIncidentService v = svc(s);
        NetworkIncident a = v.open(TEN, NetworkIncident.Type.RUPTURE, NetworkIncident.Origin.CREW, T0, null, null);
        NetworkIncident b = v.openPlanned(TEN, UUID.randomUUID(), T0, NetworkIncident.AffectedSnapshot.empty());
        assertThat(a.code()).isNotEqualTo(b.code());
        assertThat(b.planned()).isTrue(); assertThat(b.origin()).isEqualTo(NetworkIncident.Origin.PLANNED);
        assertThat(v.targetExists(TEN, a.id())).isTrue();
        assertThat(v.targetExists(UUID.randomUUID(), a.id())).isFalse();
        assertThat(v.targetExists(TEN, UUID.randomUUID())).isFalse();
    }
    @Test @DisplayName("JOR-14: relato anonimo mapeia tipos declarados e ignora WATER_QUALITY/OTHER")
    void anonymous() {
        MemStore s = new MemStore(); NetworkIncidentService v = svc(s);
        assertThat(v.fromAnonymousReport(TEN, "LEAK", T0)).get().extracting(NetworkIncident::type)
                .isEqualTo(NetworkIncident.Type.RUPTURE);
        assertThat(v.fromAnonymousReport(TEN, "SEWAGE", T0)).get().extracting(NetworkIncident::type)
                .isEqualTo(NetworkIncident.Type.OVERFLOW);
        assertThat(v.fromAnonymousReport(TEN, "WATER_QUALITY", T0)).isEmpty();
        assertThat(v.fromAnonymousReport(TEN, "OTHER", T0)).isEmpty();
        assertThat(s.list(TEN)).hasSize(2);
        assertThat(s.list(TEN)).allSatisfy(i -> assertThat(i.origin()).isEqualTo(NetworkIncident.Origin.COMPLAINTS));
    }
    @Test @DisplayName("encerramento pelo servico valida a causa contra o catalogo do tenant")
    void serviceClose() {
        MemStore s = new MemStore(); NetworkIncidentService v = svc(s);
        NetworkIncident a = v.open(TEN, NetworkIncident.Type.FLOODING, NetworkIncident.Origin.TELEMETRY, T0, null, null);
        UUID wo = UUID.randomUUID(); a.linkWorkOrder(wo); a.markRestored(T0.plusHours(3));
        assertThatThrownBy(() -> v.close(TEN, a.id(), List.of("ALAGAMENTO"),
                new NetworkIncident.ClosureData("INVENTADA", null, null, wo))).isInstanceOf(IllegalArgumentException.class);
        v.close(TEN, a.id(), List.of("ALAGAMENTO"), new NetworkIncident.ClosureData("ALAGAMENTO", 5.0, 90L, wo));
        assertThat(a.status()).isEqualTo(NetworkIncident.Status.CLOSED);
    }
    @Test @DisplayName("DAD-27: achado sem ciclo de reparo; confirmacao exige ocorrencia+OS")
    void finding() {
        LeakDetectionFinding f = new LeakDetectionFinding(UUID.randomUUID(), TEN, "LD-1", T0, "Rua A, 100");
        assertThatThrownBy(() -> f.confirmLinked(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class);
        f.confirmLinked(UUID.randomUUID(), UUID.randomUUID());
        assertThat(f.disposition()).isEqualTo(LeakDetectionFinding.Disposition.CONFIRMED_LINKED);
        // Nenhum metodo de reparo existe na classe (o reparo e so da OS):
        assertThat(Arrays.stream(LeakDetectionFinding.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .noneMatch(n -> n.toLowerCase().contains("repair") || n.toLowerCase().contains("reparo"));
    }
}
