package br.com.ziipsan.commercial.collection.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Niveis sucessivos (cavalete, ramal, supressao) com intervalo do calendario do nivel. */
class LevelEscalationRuleTest {

    private static final LocalDate VALVE_AT = LocalDate.of(2026, 11, 16);

    @Test
    @DisplayName("sem historico e com divida aberta: o nivel devido e o cavalete")
    void firstLevelIsValve() {
        var d = LevelEscalationRule.nextDue(List.of(), true,
                CutoffExtTestSupport.extParams(), VALVE_AT);
        assertThat(d.due()).isTrue();
        assertThat(d.level()).isEqualTo(CutoffLevel.VALVE);
        assertThat(CutoffLevel.VALVE.coreLevel())
                .contains(br.com.ziipsan.core.cutoff.CutoffEnums.CutLevel.VALVE);
        assertThat(CutoffLevel.BRANCH.coreLevel())
                .contains(br.com.ziipsan.core.cutoff.CutoffEnums.CutLevel.BRANCH);
        assertThat(CutoffLevel.SUPPRESSION.coreLevel()).isEmpty();
        assertThat(CutoffLevel.SUPPRESSION.statusAfterExecution())
                .isEqualTo(br.com.ziipsan.core.cutoff.CutoffEnums.ConnectionStatus.SUPPRESSED);
    }

    @Test
    @DisplayName("ramal so depois do cavalete E do intervalo minimo de 15 dias do calendario do nivel")
    void branchNeedsValveAndInterval() {
        var history = List.of(new LevelEscalationRule.ExecutedLevel(CutoffLevel.VALVE, VALVE_AT));
        var early = LevelEscalationRule.nextDue(history, true,
                CutoffExtTestSupport.extParams(), VALVE_AT.plusDays(14));
        assertThat(early.due()).isFalse();
        assertThat(early.blockedReason()).isEqualTo(LevelEscalationRule.BlockReason.MIN_INTERVAL_NOT_ELAPSED);
        assertThat(early.earliestDate()).isEqualTo(VALVE_AT.plusDays(15));
        var due = LevelEscalationRule.nextDue(history, true,
                CutoffExtTestSupport.extParams(), VALVE_AT.plusDays(15));
        assertThat(due.due()).isTrue();
        assertThat(due.level()).isEqualTo(CutoffLevel.BRANCH);
    }

    @Test
    @DisplayName("supressao so depois do ramal; depois da supressao, a sucessao acabou")
    void suppressionIsLast() {
        var history = List.of(
                new LevelEscalationRule.ExecutedLevel(CutoffLevel.VALVE, VALVE_AT),
                new LevelEscalationRule.ExecutedLevel(CutoffLevel.BRANCH, VALVE_AT.plusDays(20)));
        var due = LevelEscalationRule.nextDue(history, true,
                CutoffExtTestSupport.extParams(), VALVE_AT.plusDays(35));
        assertThat(due.due()).isTrue();
        assertThat(due.level()).isEqualTo(CutoffLevel.SUPPRESSION);
        var all = List.of(history.get(0), history.get(1),
                new LevelEscalationRule.ExecutedLevel(CutoffLevel.SUPPRESSION, VALVE_AT.plusDays(35)));
        var end = LevelEscalationRule.nextDue(all, true,
                CutoffExtTestSupport.extParams(), VALVE_AT.plusDays(60));
        assertThat(end.due()).isFalse();
        assertThat(end.blockedReason()).isEqualTo(LevelEscalationRule.BlockReason.ALREADY_AT_LAST_LEVEL);
    }

    @Test
    @DisplayName("historico furado (ramal sem cavalete) nao avanca: o nivel que falta e o devido-bloqueado")
    void gapInHistoryBlocks() {
        var history = List.of(new LevelEscalationRule.ExecutedLevel(CutoffLevel.BRANCH, VALVE_AT));
        var d = LevelEscalationRule.nextDue(history, true,
                CutoffExtTestSupport.extParams(), VALVE_AT.plusDays(30));
        assertThat(d.due()).isFalse();
        assertThat(d.blockedReason()).isEqualTo(LevelEscalationRule.BlockReason.PREVIOUS_LEVEL_NOT_EXECUTED);
        assertThat(d.level()).isEqualTo(CutoffLevel.VALVE);
    }

    @Test
    @DisplayName("divida quitada ou em acordo encerra a sucessao")
    void settledDebtEndsSuccession() {
        var d = LevelEscalationRule.nextDue(List.of(), false,
                CutoffExtTestSupport.extParams(), VALVE_AT);
        assertThat(d.due()).isFalse();
        assertThat(d.blockedReason()).isEqualTo(LevelEscalationRule.BlockReason.DEBT_SETTLED_OR_AGREED);
    }
}
