package br.com.ziipsan.commercial.billing.crisis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExceptionalMeasureTest {

    private ExceptionalMeasure measure(Set<ExceptionalMeasure.Effect> effects) {
        var m = new ExceptionalMeasure(UUID.randomUUID(), ExceptionalMeasure.ActKind.EMERGENCY_DECREE,
                "Decreto 123/2026", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10),
                new ExceptionalMeasure.Area(ExceptionalMeasure.AreaKind.SECTOR, "MS-01", Set.of()), effects);
        m.activate();
        return m;
    }

    @Test @DisplayName("P3.4: corte bloqueado so com medida ativa, na vigencia, no setor e com SUSPEND_CUT")
    void cutBlocked() {
        var m = measure(Set.of(ExceptionalMeasure.Effect.SUSPEND_CUT));
        assertThat(ExceptionalMeasure.cutBlocked(List.of(m), "MS-01", LocalDate.of(2026, 1, 20))).isTrue();
        assertThat(ExceptionalMeasure.cutBlocked(List.of(m), "MS-02", LocalDate.of(2026, 1, 20))).isFalse();
        assertThat(ExceptionalMeasure.cutBlocked(List.of(m), "MS-01", LocalDate.of(2026, 3, 1))).isFalse();
        var semEfeito = measure(Set.of(ExceptionalMeasure.Effect.EXTEND_DUE_DATE));
        assertThat(ExceptionalMeasure.cutBlocked(List.of(semEfeito), "MS-01", LocalDate.of(2026, 1, 20))).isFalse();
    }

    @Test @DisplayName("poligono do GIS cobre os setores membros (ED-54); poligono sem setores recusado")
    void polygonArea() {
        var m = new ExceptionalMeasure(UUID.randomUUID(), ExceptionalMeasure.ActKind.AGENCY_ACT, "Ato 7",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                new ExceptionalMeasure.Area(ExceptionalMeasure.AreaKind.GIS_POLYGON, "POL-9", Set.of("MS-01", "MS-03")),
                Set.of(ExceptionalMeasure.Effect.SUSPEND_CUT));
        m.activate();
        assertThat(ExceptionalMeasure.cutBlocked(List.of(m), "MS-03", LocalDate.of(2026, 1, 15))).isTrue();
        assertThatThrownBy(() -> new ExceptionalMeasure.Area(
                ExceptionalMeasure.AreaKind.GIS_POLYGON, "POL-X", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("ato e ao menos um efeito sao obrigatorios; rascunho nao bloqueia corte")
    void invariants() {
        assertThatThrownBy(() -> new ExceptionalMeasure(UUID.randomUUID(),
                ExceptionalMeasure.ActKind.AGENCY_ACT, " ", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2),
                new ExceptionalMeasure.Area(ExceptionalMeasure.AreaKind.LOCALITY, "Centro", Set.of()),
                Set.of(ExceptionalMeasure.Effect.SUSPEND_CUT))).isInstanceOf(IllegalArgumentException.class);
        var draft = new ExceptionalMeasure(UUID.randomUUID(), ExceptionalMeasure.ActKind.AGENCY_ACT, "Ato 1",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                new ExceptionalMeasure.Area(ExceptionalMeasure.AreaKind.SECTOR, "MS-01", Set.of()),
                Set.of(ExceptionalMeasure.Effect.SUSPEND_CUT));
        assertThat(ExceptionalMeasure.cutBlocked(List.of(draft), "MS-01", LocalDate.of(2026, 1, 10))).isFalse();
    }

    private ExceptionalMeasure measureWithArea(ExceptionalMeasure.Area area) {
        var m = new ExceptionalMeasure(UUID.randomUUID(), ExceptionalMeasure.ActKind.EMERGENCY_DECREE,
                "Decreto 124/2026", LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10),
                area, Set.of(ExceptionalMeasure.Effect.SUSPEND_CUT));
        m.activate();
        return m;
    }

    @Test @DisplayName("M1: area LOCALITY bloqueia corte nos setores membros declarados")
    void localityBlocksCut() {
        var m = measureWithArea(new ExceptionalMeasure.Area(
                ExceptionalMeasure.AreaKind.LOCALITY, "Centro", Set.of("MS-01", "MS-02")));
        assertThat(ExceptionalMeasure.cutBlocked(List.of(m), "MS-01", LocalDate.of(2026, 1, 20))).isTrue();
        assertThat(ExceptionalMeasure.cutBlocked(List.of(m), "MS-02", LocalDate.of(2026, 1, 20))).isTrue();
        assertThat(ExceptionalMeasure.cutBlocked(List.of(m), "MS-03", LocalDate.of(2026, 1, 20))).isFalse();
    }

    @Test @DisplayName("M1: area ROUTE bloqueia corte nos setores membros declarados")
    void routeBlocksCut() {
        var m = measureWithArea(new ExceptionalMeasure.Area(
                ExceptionalMeasure.AreaKind.ROUTE, "ROTA-7", Set.of("MS-02")));
        assertThat(ExceptionalMeasure.cutBlocked(List.of(m), "MS-02", LocalDate.of(2026, 1, 20))).isTrue();
        assertThat(ExceptionalMeasure.cutBlocked(List.of(m), "MS-01", LocalDate.of(2026, 1, 20))).isFalse();
    }

    @Test @DisplayName("M1: LOCALITY/ROUTE sem setores membros e recusada - nunca false silencioso")
    void localityOrRouteWithoutMembersRejected() {
        assertThatThrownBy(() -> new ExceptionalMeasure.Area(
                ExceptionalMeasure.AreaKind.LOCALITY, "Centro", Set.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("setores membros");
        assertThatThrownBy(() -> new ExceptionalMeasure.Area(
                ExceptionalMeasure.AreaKind.ROUTE, "ROTA-7", Set.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("setores membros");
    }

    @Test @DisplayName("m4: vigencia vencida transiciona ACTIVE -> EXPIRED; dentro da vigencia nao expira")
    void expireAfterValidityEnds() {
        var m = measure(Set.of(ExceptionalMeasure.Effect.SUSPEND_CUT));
        assertThat(m.expireIfValidityEnded(LocalDate.of(2026, 2, 10))).isFalse();
        assertThat(m.status()).isEqualTo(ExceptionalMeasure.Status.ACTIVE);
        assertThat(m.expireIfValidityEnded(LocalDate.of(2026, 2, 11))).isTrue();
        assertThat(m.status()).isEqualTo(ExceptionalMeasure.Status.EXPIRED);
        assertThat(m.activeOn(LocalDate.of(2026, 1, 20))).isFalse();
        assertThat(ExceptionalMeasure.cutBlocked(List.of(m), "MS-01", LocalDate.of(2026, 1, 20))).isFalse();
    }

    @Test @DisplayName("m4: applyByBatchOrder exige ACTIVE por si so (uso direto, sem o servico)")
    void applyByBatchOrderRequiresActiveItself() {
        var draft = new ExceptionalMeasure(UUID.randomUUID(), ExceptionalMeasure.ActKind.AGENCY_ACT, "Ato 9",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                new ExceptionalMeasure.Area(ExceptionalMeasure.AreaKind.SECTOR, "MS-01", Set.of()),
                Set.of(ExceptionalMeasure.Effect.SUSPEND_CUT));
        assertThatThrownBy(() -> draft.applyByBatchOrder(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        var active = measure(Set.of(ExceptionalMeasure.Effect.SUSPEND_CUT));
        var batch = UUID.randomUUID();
        active.applyByBatchOrder(batch);
        assertThat(active.batchOrderId()).isEqualTo(batch);
        var expired = measure(Set.of(ExceptionalMeasure.Effect.SUSPEND_CUT));
        expired.expireIfValidityEnded(LocalDate.of(2026, 2, 11));
        assertThatThrownBy(() -> expired.applyByBatchOrder(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("event.* de cada efeito existe e segue o padrao event.")
    void eventKeys() {
        for (var e : ExceptionalMeasure.Effect.values())
            assertThat(ExceptionalMeasure.eventKey(e)).startsWith("event.");
    }
}
