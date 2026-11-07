package br.com.ziipsan.core.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aviso de corte da P3.4: o caso da v1 (aviso em 15/10 com corte a
 * partir de 16/11), a prova de entrega da P3.5, a validade e o aviso
 * pos-corte — calculados a mao.
 */
class CutoffNoticeRuleTest {

    private static final UUID UNIT = UUID.randomUUID();
    private static final CutoffCalendar.CalendarData SC = new CutoffCalendar.CalendarData(
            Set.of(LocalDate.of(2026, 11, 20)), Set.of(), "SC");

    private static CutoffNoticeRule.Notice notice(String noticeDate, String earliest,
            CutoffEnums.DeliveryProofStatus proof) {
        return new CutoffNoticeRule.Notice(UUID.randomUUID(), UNIT, LocalDate.parse(noticeDate),
                LocalDate.parse(earliest), "CUTOFF_NOTICE", "3", proof);
    }

    @Test
    @DisplayName("caso da v1: aviso em 15/10/2026 + 30 dias cai no sabado 14/11; corte a partir de 16/11")
    void v1CaseNoticeFifteenOctober() {
        var params = CutoffParameters.parse(Map.of("cut.min_notice_days", "30"));
        assertThat(CutoffNoticeRule.earliestCutDate(LocalDate.of(2026, 10, 15), params, SC))
                .isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    @DisplayName("sem prova de entrega o aviso nao sustenta o corte; BOUNCED/COMPLAINED contam como nao entregue")
    void proofRequired() {
        var params = CutoffParameters.parse(Map.of());
        var execution = LocalDate.of(2026, 11, 16);
        assertThat(CutoffNoticeRule.noticeFailure(
                notice("2026-10-15", "2026-11-16", CutoffEnums.DeliveryProofStatus.NONE), execution, params))
                .contains(CutoffEnums.RevalidationFailure.NOTICE_NOT_DELIVERED);
        assertThat(CutoffNoticeRule.noticeFailure(
                notice("2026-10-15", "2026-11-16", CutoffEnums.DeliveryProofStatus.NOT_DELIVERED),
                execution, params))
                .contains(CutoffEnums.RevalidationFailure.NOTICE_NOT_DELIVERED);
        assertThat(CutoffNoticeRule.noticeFailure(
                notice("2026-10-15", "2026-11-16", CutoffEnums.DeliveryProofStatus.DELIVERED),
                execution, params)).isEmpty();
    }

    @Test
    @DisplayName("antes da primeira data do aviso: NOTICE_PERIOD_NOT_ELAPSED")
    void periodNotElapsed() {
        var params = CutoffParameters.parse(Map.of());
        assertThat(CutoffNoticeRule.noticeFailure(
                notice("2026-10-15", "2026-11-16", CutoffEnums.DeliveryProofStatus.DELIVERED),
                LocalDate.of(2026, 11, 13), params))
                .contains(CutoffEnums.RevalidationFailure.NOTICE_PERIOD_NOT_ELAPSED);
    }

    @Test
    @DisplayName("validade do aviso: dentro dela executa, depois dela NOTICE_EXPIRED")
    void validity() {
        var params = CutoffParameters.parse(Map.of("cut.notice_validity_days", "90"));
        var n = notice("2026-10-15", "2026-11-16", CutoffEnums.DeliveryProofStatus.DELIVERED);
        // 15/10 + 90 dias = 13/01/2027 (ultimo dia valido).
        assertThat(CutoffNoticeRule.noticeFailure(n, LocalDate.of(2027, 1, 13), params)).isEmpty();
        assertThat(CutoffNoticeRule.noticeFailure(n, LocalDate.of(2027, 1, 14), params))
                .contains(CutoffEnums.RevalidationFailure.NOTICE_EXPIRED);
        // Sem validade declarada pela ERI, o aviso nao expira por esta regra.
        var noValidity = CutoffParameters.parse(Map.of());
        assertThat(CutoffNoticeRule.noticeFailure(n, LocalDate.of(2027, 6, 1), noValidity)).isEmpty();
    }

    @Test
    @DisplayName("aviso pos-corte: prazo = execucao + cut.post_cut_notice_hours; sem o parametro, erro explicito")
    void postCutNotice() {
        var executedAt = OffsetDateTime.of(2026, 11, 16, 10, 0, 0, 0, ZoneOffset.ofHours(-3));
        var params = CutoffParameters.parse(Map.of("cut.post_cut_notice_hours", "24"));
        assertThat(CutoffNoticeRule.postCutNoticeDueAt(executedAt, params))
                .isEqualTo(executedAt.plusHours(24));
        var missing = CutoffParameters.parse(Map.of());
        assertThatThrownBy(() -> CutoffNoticeRule.postCutNoticeDueAt(executedAt, missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cut.post_cut_notice_hours");
    }
}
