package br.com.ziipsan.core.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Revalidacao do corte na execucao (classe a): um teste por falha, na
 * ordem do aceite — quitacao ou parcelamento, contestacao, vedacoes,
 * aviso valido, classe protegida. O snapshot e a entrada: a regra
 * funciona offline (a execucao em campo e da ED-20).
 */
class CutoffRevalidationRuleTest {

    private static final ZoneId TZ = ZoneId.of("America/Sao_Paulo");
    private static final CutoffCalendar.CalendarData SC = new CutoffCalendar.CalendarData(
            Set.of(LocalDate.of(2026, 11, 20)), Set.of(), "SC");
    private static final ZonedDateTime MONDAY_10H = ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, TZ);
    private static final CutoffParameters.Values PARAMS = CutoffParameters.parse(Map.of());

    private static CutoffNoticeRule.Notice notice(String earliest, CutoffEnums.DeliveryProofStatus proof) {
        return new CutoffNoticeRule.Notice(UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 10, 15), LocalDate.parse(earliest),
                "CUTOFF_NOTICE", "3", proof);
    }

    private static CutoffRevalidationRule.Snapshot base() {
        return new CutoffRevalidationRule.Snapshot(false, false, CutoffEnums.Reason.DEFAULT, false,
                MONDAY_10H, SC, notice("2026-11-16", CutoffEnums.DeliveryProofStatus.DELIVERED),
                Set.of(), PARAMS);
    }

    @Test
    @DisplayName("snapshot limpo: revalidacao passa (vazio)")
    void passes() {
        assertThat(CutoffRevalidationRule.evaluate(base())).isEmpty();
    }

    @Test
    @DisplayName("quitacao ou parcelamento posterior: DEBT_SETTLED (e vence a contestacao simultanea)")
    void debtSettled() {
        var settled = new CutoffRevalidationRule.Snapshot(true, true, CutoffEnums.Reason.DEFAULT, false,
                MONDAY_10H, SC, notice("2026-11-16", CutoffEnums.DeliveryProofStatus.DELIVERED),
                Set.of(), PARAMS);
        assertThat(CutoffRevalidationRule.evaluate(settled))
                .contains(CutoffEnums.RevalidationFailure.DEBT_SETTLED);
    }

    @Test
    @DisplayName("conta contestada ou em revisao: BILL_UNDER_REVIEW_OR_CONTESTED")
    void contested() {
        var s = new CutoffRevalidationRule.Snapshot(false, true, CutoffEnums.Reason.DEFAULT, false,
                MONDAY_10H, SC, notice("2026-11-16", CutoffEnums.DeliveryProofStatus.DELIVERED),
                Set.of(), PARAMS);
        assertThat(CutoffRevalidationRule.evaluate(s))
                .contains(CutoffEnums.RevalidationFailure.BILL_UNDER_REVIEW_OR_CONTESTED);
    }

    @Test
    @DisplayName("execucao na sexta do feriado: TEMPORAL_BAN; com ordem judicial, passa desta camada")
    void temporalBan() {
        var friday = ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, TZ);
        var s = new CutoffRevalidationRule.Snapshot(false, false, CutoffEnums.Reason.DEFAULT, false,
                friday, SC, notice("2026-11-16", CutoffEnums.DeliveryProofStatus.DELIVERED),
                Set.of(), PARAMS);
        assertThat(CutoffRevalidationRule.evaluate(s))
                .contains(CutoffEnums.RevalidationFailure.TEMPORAL_BAN);
        var judicial = new CutoffRevalidationRule.Snapshot(false, false, CutoffEnums.Reason.DEFAULT, true,
                friday, SC, notice("2026-11-16", CutoffEnums.DeliveryProofStatus.DELIVERED),
                Set.of(), PARAMS);
        assertThat(CutoffRevalidationRule.evaluate(judicial)).isEmpty();
    }

    @Test
    @DisplayName("aviso sem prova na execucao: NOTICE_NOT_DELIVERED")
    void noticeNotDelivered() {
        var tuesday = ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, TZ);
        var s = new CutoffRevalidationRule.Snapshot(false, false, CutoffEnums.Reason.DEFAULT, false,
                tuesday, SC, notice("2026-11-16", CutoffEnums.DeliveryProofStatus.NOT_DELIVERED),
                Set.of(), PARAMS);
        assertThat(CutoffRevalidationRule.evaluate(s))
                .contains(CutoffEnums.RevalidationFailure.NOTICE_NOT_DELIVERED);
    }

    @Test
    @DisplayName("execucao antes da data do aviso: NOTICE_PERIOD_NOT_ELAPSED; aviso vencido: NOTICE_EXPIRED")
    void noticePeriodAndExpiry() {
        var early = new CutoffRevalidationRule.Snapshot(false, false, CutoffEnums.Reason.DEFAULT, false,
                MONDAY_10H, SC, notice("2026-11-18", CutoffEnums.DeliveryProofStatus.DELIVERED),
                Set.of(), PARAMS);
        assertThat(CutoffRevalidationRule.evaluate(early))
                .contains(CutoffEnums.RevalidationFailure.NOTICE_PERIOD_NOT_ELAPSED);
        var shortValidity = CutoffParameters.parse(Map.of("cut.notice_validity_days", "10"));
        var tuesday = ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, TZ);
        var expired = new CutoffRevalidationRule.Snapshot(false, false, CutoffEnums.Reason.DEFAULT, false,
                tuesday, SC, notice("2026-11-16", CutoffEnums.DeliveryProofStatus.DELIVERED),
                Set.of(), shortValidity);
        assertThat(CutoffRevalidationRule.evaluate(expired))
                .contains(CutoffEnums.RevalidationFailure.NOTICE_EXPIRED);
    }

    @Test
    @DisplayName("classe protegida confirmada depois da emissao: PROTECTED_CLASS")
    void protectedClass() {
        var s = new CutoffRevalidationRule.Snapshot(false, false, CutoffEnums.Reason.DEFAULT, false,
                MONDAY_10H, SC, notice("2026-11-16", CutoffEnums.DeliveryProofStatus.DELIVERED),
                Set.of(CutoffEnums.ProtectedClass.ESSENTIAL_USER), PARAMS);
        assertThat(CutoffRevalidationRule.evaluate(s))
                .contains(CutoffEnums.RevalidationFailure.PROTECTED_CLASS);
    }
}
