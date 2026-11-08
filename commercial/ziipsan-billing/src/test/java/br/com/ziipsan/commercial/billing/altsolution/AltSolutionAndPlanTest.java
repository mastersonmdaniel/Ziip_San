package br.com.ziipsan.commercial.billing.altsolution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AltSolutionAndPlanTest {

    private static final UUID PROP = UUID.randomUUID();
    private static final UUID UNIT = UUID.randomUUID();
    private static final LocalDate REG = LocalDate.of(2026, 1, 15);

    private static AltSolution solution(LocalDate lastCleaning) {
        return AltSolution.register(UUID.randomUUID(), PROP, UNIT,
                Set.of(AltSolution.Component.SEPTIC_TANK, AltSolution.Component.FILTER),
                new BigDecimal("5000.00"), lastCleaning, "ETE municipal", REG);
    }

    @Test @DisplayName("cadastro SG-154: componentes, volume, ultima limpeza e destino do lodo obrigatorios")
    void registerValidations() {
        assertThatThrownBy(() -> AltSolution.register(UUID.randomUUID(), PROP, UNIT, Set.of(),
                new BigDecimal("1000"), null, "ETE", REG)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AltSolution.register(UUID.randomUUID(), PROP, UNIT,
                Set.of(AltSolution.Component.SOAKAWAY), new BigDecimal("0"), null, "ETE", REG))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AltSolution.register(UUID.randomUUID(), PROP, UNIT,
                Set.of(AltSolution.Component.SOAKAWAY), new BigDecimal("1000"), null, " ", REG))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> solution(LocalDate.of(2026, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class); // limpeza depois do cadastro
        assertThat(solution(null).components()).containsExactlyInAnyOrder(
                AltSolution.Component.SEPTIC_TANK, AltSolution.Component.FILTER);
    }

    @Test @DisplayName("desativacao depois da ligacao: registra data e OS; segunda desativacao recusada")
    void deactivateByConnection() {
        UUID os = UUID.randomUUID();
        AltSolution off = solution(null).deactivateByConnection(LocalDate.of(2026, 6, 1), os);
        assertThat(off.status()).isEqualTo(AltSolution.Status.DEACTIVATED_BY_CONNECTION);
        assertThat(off.deactivatedOn()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(off.deactivationServiceOrderId()).isEqualTo(os);
        assertThatThrownBy(() -> off.deactivateByConnection(LocalDate.of(2026, 6, 2), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("servico desligado ou solucao desativada: so cadastro, sem agenda")
    void registerOnlyModes() {
        var p1 = AltSolutionServicePlan.plan(solution(null), false, 6, LocalDate.of(2026, 7, 1));
        assertThat(p1.mode()).isEqualTo(AltSolutionServicePlan.Mode.REGISTER_ONLY);
        var off = solution(null).deactivateByConnection(LocalDate.of(2026, 3, 1), UUID.randomUUID());
        var p2 = AltSolutionServicePlan.plan(off, true, 6, LocalDate.of(2026, 7, 1));
        assertThat(p2.mode()).isEqualTo(AltSolutionServicePlan.Mode.REGISTER_ONLY);
    }

    @Test @DisplayName("servico ligado sem intervalo: sob demanda")
    void onDemand() {
        var p = AltSolutionServicePlan.plan(solution(null), true, null, LocalDate.of(2026, 7, 1));
        assertThat(p.mode()).isEqualTo(AltSolutionServicePlan.Mode.ON_DEMAND);
        assertThat(p.due()).isFalse();
    }

    @Test @DisplayName("periodico: base na ultima limpeza; vence 6 meses depois (15/01 + limpeza 20/03 -> 20/09)")
    void periodicFromLastCleaning() {
        AltSolution s = solution(LocalDate.of(2026, 1, 10)).withCleaningOn(LocalDate.of(2026, 3, 20));
        var before = AltSolutionServicePlan.plan(s, true, 6, LocalDate.of(2026, 9, 19));
        assertThat(before.nextDueOn()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(before.due()).isFalse();
        var onDay = AltSolutionServicePlan.plan(s, true, 6, LocalDate.of(2026, 9, 20));
        assertThat(onDay.due()).isTrue();
    }

    @Test @DisplayName("periodico sem limpeza registrada: base no cadastro (15/01 + 12m -> 15/01/2027)")
    void periodicFromRegistration() {
        var p = AltSolutionServicePlan.plan(solution(null), true, 12, LocalDate.of(2027, 1, 14));
        assertThat(p.nextDueOn()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(p.due()).isFalse();
    }
}
