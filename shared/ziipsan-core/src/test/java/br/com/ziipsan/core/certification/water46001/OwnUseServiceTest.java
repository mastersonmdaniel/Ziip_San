package br.com.ziipsan.core.certification.water46001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnUseServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID METER = UUID.randomUUID();
    private static final UUID OBJECTIVE = UUID.randomUUID();
    private static final UUID BALANCE = UUID.randomUUID();

    private static Iso46001Activation act() {
        return Iso46001Activation.enabled("escopo", "ETA-CENTRAL", UUID.randomUUID(), UUID.randomUUID());
    }

    private static OperationalUseUnit unit() {
        return new OperationalUseUnit(act(), UNIT, "ETA-CENTRAL", METER,
                OperationalUseUnit.UseNature.OPERATIONAL, "ETA-PROCESSO", true);
    }

    private static OwnUseService service(Set<UUID> units, Set<String> installed,
                                         Set<UUID> objectives, boolean balancePresent) {
        return new OwnUseService(
                (t, u) -> units.contains(u),
                (t, m, u) -> installed.contains(m + "@" + u),
                (t, b) -> balancePresent
                        ? Optional.of(new OwnUseSource(b, UUID.randomUUID(), 2026, 3, 10.0, 2.0, 40.0))
                        : Optional.empty(),
                (t, o) -> objectives.contains(o));
    }

    @Test @DisplayName("cadastro sobre a P1.3 com hidrometro instalado da ED-14: aceito")
    void registerOk() {
        var svc = service(Set.of(UNIT), Set.of(METER + "@" + UNIT), Set.of(OBJECTIVE), true);
        assertThat(svc.registerUnit(TENANT, unit()).consumerUnitId()).isEqualTo(UNIT);
    }

    @Test @DisplayName("unidade inexistente na P1.3 recusa; hidrometro nao instalado na unidade recusa (ED-14)")
    void registerRefusals() {
        var noUnit = service(Set.of(), Set.of(METER + "@" + UNIT), Set.of(OBJECTIVE), true);
        assertThatThrownBy(() -> noUnit.registerUnit(TENANT, unit()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("P1.3");
        var noMeter = service(Set.of(UNIT), Set.of(), Set.of(OBJECTIVE), true);
        assertThatThrownBy(() -> noMeter.registerUnit(TENANT, unit()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ED-14");
    }

    @Test @DisplayName("unidade sem hidrometro ou fora da instalacao do escopo nao nasce")
    void unitGuards() {
        assertThatThrownBy(() -> new OperationalUseUnit(act(), UNIT, "ETA-CENTRAL", null,
                OperationalUseUnit.UseNature.OPERATIONAL, "ETA-PROCESSO", true))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("ED-14");
        assertThatThrownBy(() -> new OperationalUseUnit(act(), UNIT, "ETE-NORTE", METER,
                OperationalUseUnit.UseNature.EMERGENCY, "RESERVA-INCENDIO", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fora do escopo");
        assertThatThrownBy(() -> new OperationalUseUnit(Iso46001Activation.disabled(), UNIT,
                "ETA-CENTRAL", METER, OperationalUseUnit.UseNature.OPERATIONAL, "X", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("fonte: snapshot da ED-85 presente e lido; ausente recusa sem recalcular nada")
    void sourcePort() {
        var svc = service(Set.of(UNIT), Set.of(), Set.of(OBJECTIVE), true);
        assertThat(svc.requireSource(TENANT, BALANCE).totalM3()).isEqualTo(52.0);
        var missing = service(Set.of(UNIT), Set.of(), Set.of(OBJECTIVE), false);
        assertThatThrownBy(() -> missing.requireSource(TENANT, BALANCE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ED-85");
    }

    @Test @DisplayName("plano so nasce ligado a objetivo existente da P3.31")
    void planObjectivePort() {
        var plan = new WaterActionPlan(act(), OBJECTIVE, "ETA-CENTRAL", "Reduzir uso no processo",
                java.util.List.of(new WaterActionPlan.ActionItem("Trocar descarga programada",
                        "operacao", java.time.LocalDate.of(2026, 12, 31), null)));
        var svc = service(Set.of(UNIT), Set.of(), Set.of(OBJECTIVE), true);
        assertThat(svc.registerPlan(TENANT, plan).objectiveId()).isEqualTo(OBJECTIVE);
        var missing = service(Set.of(UNIT), Set.of(), Set.of(), true);
        assertThatThrownBy(() -> missing.registerPlan(TENANT, plan))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("P3.31");
    }
}
