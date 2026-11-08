package br.com.ziipsan.commercial.collection.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prioridade de TR-409 (ordem declarada pelo tenant) e cota diaria de TR-410. */
class CutoffDailyPlannerTest {

    private static CutoffDailyPlanner.Candidate candidate(LocalDate due, String amount, int routeSeq) {
        return new CutoffDailyPlanner.Candidate(UUID.randomUUID(), CutoffLevel.VALVE, routeSeq, due,
                new BigDecimal(amount), "RESIDENTIAL", "RESIDENTIAL", "OCCASIONAL", false);
    }

    @Test
    @DisplayName("prioridade: vencimento mais antigo primeiro; no empate, maior valor; depois a rota")
    void priorityOrder() {
        var oldest = candidate(LocalDate.of(2026, 8, 1), "100.00", 9);
        var sameDateBigger = candidate(LocalDate.of(2026, 9, 1), "900.00", 5);
        var sameDateSmaller = candidate(LocalDate.of(2026, 9, 1), "200.00", 1);
        var plan = CutoffDailyPlanner.plan(List.of(sameDateSmaller, sameDateBigger, oldest),
                CutoffExtTestSupport.extParams(), LocalDate.of(2026, 11, 16));
        // Cota do fixture = 2: entram a mais antiga e a de maior valor do empate.
        assertThat(plan.planned()).extracting(p -> p.candidate()).containsExactly(oldest, sameDateBigger);
        assertThat(plan.planned()).extracting(CutoffDailyPlanner.Planned::position).containsExactly(1, 2);
    }

    @Test
    @DisplayName("cota diaria: excedente volta ADIADA com motivo registrado, nunca some")
    void quotaDefersWithReason() {
        var plan = CutoffDailyPlanner.plan(List.of(
                candidate(LocalDate.of(2026, 8, 1), "100.00", 1),
                candidate(LocalDate.of(2026, 8, 2), "100.00", 2),
                candidate(LocalDate.of(2026, 8, 3), "100.00", 3)),
                CutoffExtTestSupport.extParams(), LocalDate.of(2026, 11, 16));
        assertThat(plan.dailyLimit()).isEqualTo(2);
        assertThat(plan.planned()).hasSize(2);
        assertThat(plan.deferred()).hasSize(1);
        assertThat(plan.deferred().get(0).reason()).isEqualTo("DAILY_QUOTA_EXCEEDED");
    }

    @Test
    @DisplayName("postos nominais do tenant: tarifa SOCIAL (posto 1) precede RESIDENTIAL quando ela e o 1o criterio")
    void nominalRanksApply() {
        var raw = new java.util.HashMap<String, String>();
        raw.put(CutoffExtParameters.DAILY_ORDER_LIMIT, "10");
        raw.put(CutoffExtParameters.PRIORITY_ORDER,
                "{\"order\":[\"TARIFF\"],\"ranks\":{\"TARIFF\":{\"SOCIAL\":1,\"RESIDENTIAL\":2}}}");
        raw.put(CutoffExtParameters.LEVEL_SCHEDULE, "{\"VALVE\":{\"weekdays\":[\"MONDAY\"],"
                + "\"from\":\"08:00\",\"to\":\"18:00\",\"minDaysSincePreviousLevel\":0}}");
        var params = CutoffExtParameters.fromValues(raw);
        var social = new CutoffDailyPlanner.Candidate(UUID.randomUUID(), CutoffLevel.VALVE, 5,
                LocalDate.of(2026, 9, 1), new BigDecimal("50.00"), "SOCIAL", "RESIDENTIAL", "OCCASIONAL", false);
        var residential = new CutoffDailyPlanner.Candidate(UUID.randomUUID(), CutoffLevel.VALVE, 1,
                LocalDate.of(2026, 8, 1), new BigDecimal("999.00"), "RESIDENTIAL", "RESIDENTIAL", "OCCASIONAL", false);
        var plan = CutoffDailyPlanner.plan(List.of(residential, social), params, LocalDate.of(2026, 11, 16));
        assertThat(plan.planned().get(0).candidate()).isEqualTo(social);
    }

    @Test
    @DisplayName("equipe especial (TR-529/SG-486): ordem restrita em equipe comum recusa; em equipe especial, passa")
    void specialCrewGate() {
        var restricted = new CutoffDailyPlanner.Candidate(UUID.randomUUID(), CutoffLevel.VALVE, 1,
                LocalDate.of(2026, 8, 1), new BigDecimal("100.00"), "RESIDENTIAL", "COMMERCIAL",
                "LARGE_CONSUMER", true);
        assertThatThrownBy(() -> CutoffDailyPlanner.assertCrewAllowed(restricted, false))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> CutoffDailyPlanner.assertCrewAllowed(restricted, true))
                .doesNotThrowAnyException();
        var common = candidate(LocalDate.of(2026, 8, 1), "100.00", 1);
        assertThatCode(() -> CutoffDailyPlanner.assertCrewAllowed(common, false))
                .doesNotThrowAnyException();
    }
}
