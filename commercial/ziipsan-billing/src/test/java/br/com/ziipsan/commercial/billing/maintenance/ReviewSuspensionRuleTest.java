package br.com.ziipsan.commercial.billing.maintenance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P2.20: teste por efeito e por opcao de bill.review_effects, com o corte sempre vedado e o teto de dias. */
class ReviewSuspensionRuleTest {

    private static final LocalDate OPENED = LocalDate.of(2026, 3, 1);

    private static MaintenanceParameters params(String effectsJson) {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(MaintenanceParameters.REVIEW_MAX_DAYS, "30");
        if (effectsJson != null) {
            values.put(MaintenanceParameters.REVIEW_EFFECTS, effectsJson);
        }
        return MaintenanceParameters.fromValues(values);
    }

    private static boolean suspended(MaintenanceParameters p, MaintenanceParameters.ReviewEffect effect, LocalDate on) {
        return ReviewSuspensionRule.isSuspended(p, ReviewSuspensionRule.ReviewState.openSince(OPENED), effect, on);
    }

    @Test
    @DisplayName("SUSPEND_ALL (padrao, chave ausente): os 5 efeitos suspensos com revisao ativa")
    void suspendAll() {
        MaintenanceParameters p = params(null);
        LocalDate on = LocalDate.of(2026, 3, 10);
        for (MaintenanceParameters.ReviewEffect effect : MaintenanceParameters.ReviewEffect.values()) {
            assertThat(suspended(p, effect, on)).as("efeito %s", effect).isTrue();
        }
    }

    @Test
    @DisplayName("lista [COLLECTION]: cobra so ela; acrescimo/negativacao/debito seguem; CORTE continua vedado")
    void partialListCutAlwaysForbidden() {
        MaintenanceParameters p = params("[\"COLLECTION\"]");
        LocalDate on = LocalDate.of(2026, 3, 10);
        assertThat(suspended(p, MaintenanceParameters.ReviewEffect.COLLECTION, on)).isTrue();
        assertThat(suspended(p, MaintenanceParameters.ReviewEffect.LATE_FEES, on)).isFalse();
        assertThat(suspended(p, MaintenanceParameters.ReviewEffect.NEGATIVE_LISTING, on)).isFalse();
        assertThat(suspended(p, MaintenanceParameters.ReviewEffect.AUTO_DEBIT, on)).isFalse();
        assertThat(suspended(p, MaintenanceParameters.ReviewEffect.CUT, on)).isTrue();
        assertThat(ReviewSuspensionRule.isCutForbidden(p, ReviewSuspensionRule.ReviewState.openSince(OPENED), on)).isTrue();
    }

    @Test
    @DisplayName("lista vazia []: nada suspenso pelo parametro, mas o corte segue vedado")
    void emptyListCutStillForbidden() {
        MaintenanceParameters p = params("[]");
        LocalDate on = LocalDate.of(2026, 3, 10);
        assertThat(suspended(p, MaintenanceParameters.ReviewEffect.COLLECTION, on)).isFalse();
        assertThat(suspended(p, MaintenanceParameters.ReviewEffect.CUT, on)).isTrue();
    }

    @Test
    @DisplayName("teto bill.review_max_days=30: aberta em 01/03 suspende ate 31/03; em 01/04 nao suspende mais nada")
    void maxDaysCap() {
        MaintenanceParameters p = params(null);
        assertThat(ReviewSuspensionRule.isReviewActive(p, ReviewSuspensionRule.ReviewState.openSince(OPENED),
                LocalDate.of(2026, 3, 31))).isTrue();
        assertThat(ReviewSuspensionRule.isReviewActive(p, ReviewSuspensionRule.ReviewState.openSince(OPENED),
                LocalDate.of(2026, 4, 1))).isFalse();
        assertThat(suspended(p, MaintenanceParameters.ReviewEffect.CUT, LocalDate.of(2026, 4, 1))).isFalse();
    }

    @Test
    @DisplayName("sem revisao aberta: nenhum efeito suspenso")
    void noReview() {
        MaintenanceParameters p = params(null);
        for (MaintenanceParameters.ReviewEffect effect : MaintenanceParameters.ReviewEffect.values()) {
            assertThat(ReviewSuspensionRule.isSuspended(p, ReviewSuspensionRule.ReviewState.none(), effect,
                    LocalDate.of(2026, 3, 10))).as("efeito %s", effect).isFalse();
        }
    }
}
