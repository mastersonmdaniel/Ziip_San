package br.com.ziipsan.core.exit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testes puros dos prazos e portoes da saida de tenant (P3.16), todos
 * calculados a mao sobre o contrato-exemplo: vigencia
 * 2026-03-01 a 2026-12-31 (305 dias; fim numa quinta-feira).
 * Os fluxos com banco so sao exercitados na CI total.
 */
class TenantExitCalculatorTest {

    private static final LocalDate START = LocalDate.of(2026, 3, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);
    private static final OffsetDateTime END_AT = END.atStartOfDay().atOffset(ZoneOffset.UTC);
    private static final TenantExitCalculator.Holds NO_HOLDS =
            new TenantExitCalculator.Holds(false, false);

    private static OffsetDateTime at(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    // ------------------------------------------------------------------
    // Exportacao
    // ------------------------------------------------------------------

    @Test
    @DisplayName("exportacao STANDARD: fim do contrato + 15 dias corridos = 2027-01-15")
    void exportDueStandard() {
        assertThat(TenantExitCalculator.exportDueDate(
                TenantExitCalculator.Level.STANDARD, END, Set.of()))
                .isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(TenantExitCalculator.EXPORT_CALENDAR_DAYS).isEqualTo(15);
    }

    @Test
    @DisplayName("exportacao PUBLIC_SECTOR: 10 dias uteis apos 2026-12-31 (qui) = 2027-01-14 (qui)")
    void exportDuePublicSectorBusinessDays() {
        // Contagem a mao, sem feriados: 01/01 sex=1, 04 seg=2, 05=3, 06=4,
        // 07=5, 08 sex=6, 11 seg=7, 12=8, 13=9, 14 qui=10.
        assertThat(TenantExitCalculator.exportDueDate(
                TenantExitCalculator.Level.PUBLIC_SECTOR, END, Set.of()))
                .isEqualTo(LocalDate.of(2027, 1, 14));
        assertThat(TenantExitCalculator.EXPORT_BUSINESS_DAYS_PUBLIC_SECTOR).isEqualTo(10);
    }

    @Test
    @DisplayName("exportacao PUBLIC_SECTOR com feriado em 01/01: 10o dia util = 2027-01-15")
    void exportDuePublicSectorWithHoliday() {
        assertThat(TenantExitCalculator.exportDueDate(
                TenantExitCalculator.Level.PUBLIC_SECTOR, END, Set.of(LocalDate.of(2027, 1, 1))))
                .isEqualTo(LocalDate.of(2027, 1, 15));
    }

    // ------------------------------------------------------------------
    // Consulta somente leitura
    // ------------------------------------------------------------------

    @Test
    @DisplayName("consulta: contrato de 305 dias estoura o teto — duracao 120, fim 2027-04-30")
    void readonlyCappedAt120Days() {
        assertThat(TenantExitCalculator.readonlyDurationDays(START, END)).isEqualTo(120);
        assertThat(TenantExitCalculator.readonlyEndsAt(START, END_AT))
                .isEqualTo(at(LocalDate.of(2027, 4, 30)));
        assertThat(TenantExitCalculator.READONLY_MAX_DAYS).isEqualTo(120);
    }

    @Test
    @DisplayName("consulta: contrato de 60 dias — duracao 60, fim 2027-03-01")
    void readonlyFollowsShortContractTerm() {
        LocalDate shortStart = LocalDate.of(2026, 11, 1);
        assertThat(TenantExitCalculator.readonlyDurationDays(shortStart, END)).isEqualTo(60);
        assertThat(TenantExitCalculator.readonlyEndsAt(shortStart, END_AT))
                .isEqualTo(at(LocalDate.of(2027, 3, 1)));
    }

    @Test
    @DisplayName("consulta: contrato sem prazo positivo e erro, nao consulta zero")
    void readonlyRejectsNonPositiveTerm() {
        assertThatThrownBy(() -> TenantExitCalculator.readonlyDurationDays(END, END))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("escrita recusada durante a consulta (teste do aceite) e liberada fora dela")
    void writeRefusedDuringReadonly() {
        OffsetDateTime start = END_AT;
        OffsetDateTime end = at(LocalDate.of(2027, 4, 30));
        var refused = org.junit.jupiter.api.Assertions.assertThrows(
                TenantExitCalculator.ExitRefusedException.class,
                () -> TenantExitCalculator.requireWriteAllowed(start, end, at(LocalDate.of(2027, 2, 1))));
        assertThat(refused.code()).isEqualTo("readonly_consultation");
        assertThatCode(() -> TenantExitCalculator.requireWriteAllowed(
                start, end, at(LocalDate.of(2026, 12, 30)))).doesNotThrowAnyException();
        assertThatCode(() -> TenantExitCalculator.requireWriteAllowed(
                start, end, end)).doesNotThrowAnyException();
        assertThatCode(() -> TenantExitCalculator.requireWriteAllowed(
                null, null, at(LocalDate.of(2027, 2, 1)))).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // Carencia
    // ------------------------------------------------------------------

    @Test
    @DisplayName("carencia STANDARD: do fim do contrato, 30 dias — ate 2027-01-30")
    void graceStandardFromContractEnd() {
        OffsetDateTime start = TenantExitCalculator.graceStartsAt(
                TenantExitCalculator.Level.STANDARD, END_AT, null);
        assertThat(start).isEqualTo(END_AT);
        assertThat(TenantExitCalculator.graceEndsAt(start)).isEqualTo(at(LocalDate.of(2027, 1, 30)));
        assertThat(TenantExitCalculator.GRACE_DAYS).isEqualTo(30);
    }

    @Test
    @DisplayName("carencia PUBLIC_SECTOR: conta do fim da consulta (2027-04-30) — ate 2027-05-30")
    void gracePublicSectorFromReadonlyEnd() {
        OffsetDateTime readonlyEnd = at(LocalDate.of(2027, 4, 30));
        OffsetDateTime start = TenantExitCalculator.graceStartsAt(
                TenantExitCalculator.Level.PUBLIC_SECTOR, END_AT, readonlyEnd);
        assertThat(start).isEqualTo(readonlyEnd);
        assertThat(TenantExitCalculator.graceEndsAt(start)).isEqualTo(at(LocalDate.of(2027, 5, 30)));
        assertThatThrownBy(() -> TenantExitCalculator.graceStartsAt(
                TenantExitCalculator.Level.PUBLIC_SECTOR, END_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // Agendamento, cancelamento e execucao
    // ------------------------------------------------------------------

    @Test
    @DisplayName("agendamento: so PLATFORM_OWNER com MFA; outro papel ou sem MFA recusa")
    void schedulingOnlyPlatformOwnerWithMfa() {
        assertThatCode(() -> TenantExitCalculator.requireSchedulingAllowed("PLATFORM_OWNER", true))
                .doesNotThrowAnyException();
        var wrongRole = org.junit.jupiter.api.Assertions.assertThrows(
                TenantExitCalculator.ExitRefusedException.class,
                () -> TenantExitCalculator.requireSchedulingAllowed("PLATFORM_FINANCE", true));
        assertThat(wrongRole.code()).isEqualTo("forbidden_role");
        var noMfa = org.junit.jupiter.api.Assertions.assertThrows(
                TenantExitCalculator.ExitRefusedException.class,
                () -> TenantExitCalculator.requireSchedulingAllowed("PLATFORM_OWNER", false));
        assertThat(noMfa.code()).isEqualTo("mfa_required");
    }

    @Test
    @DisplayName("cancelamento: durante a carencia libera; antes do inicio e depois do fim, recusa")
    void cancellationOnlyDuringGrace() {
        OffsetDateTime graceStart = END_AT;
        OffsetDateTime graceEnd = at(LocalDate.of(2027, 1, 30));
        assertThatCode(() -> TenantExitCalculator.requireCancellationAllowed(
                graceStart, graceEnd, graceEnd)).doesNotThrowAnyException();
        var beforeStart = org.junit.jupiter.api.Assertions.assertThrows(
                TenantExitCalculator.ExitRefusedException.class,
                () -> TenantExitCalculator.requireCancellationAllowed(
                        graceStart, graceEnd, graceStart.minusSeconds(1)));
        assertThat(beforeStart.code()).isEqualTo("grace_not_started");
        var afterEnd = org.junit.jupiter.api.Assertions.assertThrows(
                TenantExitCalculator.ExitRefusedException.class,
                () -> TenantExitCalculator.requireCancellationAllowed(
                        graceStart, graceEnd, graceEnd.plusSeconds(1)));
        assertThat(afterEnd.code()).isEqualTo("grace_elapsed");
    }

    @Test
    @DisplayName("cancelar/executar exigem eliminacao agendada; reagendar tenant eliminado recusa (revisao cruzada)")
    void eliminationMustBeScheduledToCancelOrExecute() {
        assertThatCode(() -> TenantExitCalculator.requireEliminationScheduled("ELIMINATION_SCHEDULED"))
                .doesNotThrowAnyException();
        for (String status : new String[]{"EXPORT_PENDING", "IN_GRACE", "CANCELLED", "ELIMINATED"}) {
            var refused = org.junit.jupiter.api.Assertions.assertThrows(
                    TenantExitCalculator.ExitRefusedException.class,
                    () -> TenantExitCalculator.requireEliminationScheduled(status));
            assertThat(refused.code()).isEqualTo("elimination_not_scheduled");
        }
        var eliminated = org.junit.jupiter.api.Assertions.assertThrows(
                TenantExitCalculator.ExitRefusedException.class,
                () -> TenantExitCalculator.requireSchedulingStateAllowed("ELIMINATED"));
        assertThat(eliminated.code()).isEqualTo("already_eliminated");
        assertThatCode(() -> TenantExitCalculator.requireSchedulingStateAllowed("CANCELLED"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("execucao antes do fim da carencia recusada (teste do aceite)")
    void executionBeforeGraceEndRefused() {
        OffsetDateTime graceEnd = at(LocalDate.of(2027, 1, 30));
        var refused = org.junit.jupiter.api.Assertions.assertThrows(
                TenantExitCalculator.ExitRefusedException.class,
                () -> TenantExitCalculator.requireExecutionAllowed(
                        graceEnd, graceEnd, NO_HOLDS));
        assertThat(refused.code()).isEqualTo("grace_not_elapsed");
        assertThatCode(() -> TenantExitCalculator.requireExecutionAllowed(
                graceEnd, graceEnd.plusSeconds(1), NO_HOLDS)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("contestacao sem resposta suspende a execucao (teste do aceite)")
    void executionHeldByContestation() {
        OffsetDateTime graceEnd = at(LocalDate.of(2027, 1, 30));
        var refused = org.junit.jupiter.api.Assertions.assertThrows(
                TenantExitCalculator.ExitRefusedException.class,
                () -> TenantExitCalculator.requireExecutionAllowed(graceEnd,
                        graceEnd.plusDays(1), new TenantExitCalculator.Holds(true, false)));
        assertThat(refused.code()).isEqualTo("held_by_contestation");
    }

    @Test
    @DisplayName("ordem judicial vigente suspende a execucao; levantada, libera (teste do aceite)")
    void executionHeldByCourtOrder() {
        OffsetDateTime graceEnd = at(LocalDate.of(2027, 1, 30));
        var refused = org.junit.jupiter.api.Assertions.assertThrows(
                TenantExitCalculator.ExitRefusedException.class,
                () -> TenantExitCalculator.requireExecutionAllowed(graceEnd,
                        graceEnd.plusDays(1), new TenantExitCalculator.Holds(false, true)));
        assertThat(refused.code()).isEqualTo("held_by_court_order");
        assertThat(new TenantExitCalculator.Holds(false, false).any()).isFalse();
    }

    // ------------------------------------------------------------------
    // Contestacao: janela, resposta e alerta com relogio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("contestacao fora da carencia recusada; nos limites da carencia, aceita")
    void contestationOnlyInGrace() {
        OffsetDateTime start = END_AT;
        OffsetDateTime end = at(LocalDate.of(2027, 1, 30));
        assertThatCode(() -> TenantExitCalculator.requireContestationInGrace(start, end, end))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> TenantExitCalculator.requireContestationInGrace(
                start, end, end.plusSeconds(1)))
                .isInstanceOf(TenantExitCalculator.ExitRefusedException.class);
    }

    @Test
    @DisplayName("resposta a contestacao em 15 dias e alerta 3 dias antes (teste com relogio do aceite)")
    void contestationResponseAndAlertWithClock() {
        OffsetDateTime registered = OffsetDateTime.of(2027, 1, 30, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime due = TenantExitCalculator.contestationResponseDueAt(registered);
        assertThat(due).isEqualTo(OffsetDateTime.of(2027, 2, 14, 10, 0, 0, 0, ZoneOffset.UTC));
        assertThat(TenantExitCalculator.CONTESTATION_RESPONSE_DAYS).isEqualTo(15);
        // Alerta a partir de 2027-02-11 10:00 (vencimento - 3 dias).
        assertThat(TenantExitCalculator.contestationAlertAt(due))
                .isEqualTo(OffsetDateTime.of(2027, 2, 11, 10, 0, 0, 0, ZoneOffset.UTC));
        assertThat(TenantExitCalculator.contestationAlertDue(
                OffsetDateTime.of(2027, 2, 10, 10, 0, 0, 0, ZoneOffset.UTC), due, false)).isFalse();
        assertThat(TenantExitCalculator.contestationAlertDue(
                OffsetDateTime.of(2027, 2, 11, 10, 0, 0, 0, ZoneOffset.UTC), due, false)).isTrue();
        assertThat(TenantExitCalculator.contestationAlertDue(
                OffsetDateTime.of(2027, 2, 12, 0, 0, 0, 0, ZoneOffset.UTC), due, true)).isFalse();
        assertThat(TenantExitCalculator.contestationOverdue(
                OffsetDateTime.of(2027, 2, 15, 0, 0, 0, 0, ZoneOffset.UTC), due, false)).isTrue();
        assertThat(TenantExitCalculator.contestationOverdue(
                OffsetDateTime.of(2027, 2, 15, 0, 0, 0, 0, ZoneOffset.UTC), due, true)).isFalse();
    }

    // ------------------------------------------------------------------
    // Certificado: 30 apos o marco, tetos de 60/75
    // ------------------------------------------------------------------

    @Test
    @DisplayName("certificado sem contestacao: carencia + 30 = ancora + 60 = 2027-03-01")
    void certificateWithoutContestation() {
        OffsetDateTime due = TenantExitCalculator.certificateDueAt(
                END_AT, at(LocalDate.of(2027, 1, 30)), null);
        assertThat(due).isEqualTo(at(LocalDate.of(2027, 3, 1)));
        assertThat(TenantExitCalculator.CERTIFICATE_CAP_WITHOUT_CONTESTATION_DAYS).isEqualTo(60);
    }

    @Test
    @DisplayName("certificado com contestacao no ultimo dia: resposta 14/02, teto ancora + 75 = 2027-03-16")
    void certificateWithContestationAtGraceEnd() {
        OffsetDateTime response = OffsetDateTime.of(2027, 2, 14, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime due = TenantExitCalculator.certificateDueAt(
                END_AT, at(LocalDate.of(2027, 1, 30)), response);
        // Regra daria 2027-03-16 10:00; o teto de 75 dias (2027-03-16 00:00) aperta.
        assertThat(due).isEqualTo(at(LocalDate.of(2027, 3, 16)));
        assertThat(TenantExitCalculator.CERTIFICATE_CAP_WITH_CONTESTATION_DAYS).isEqualTo(75);
    }

    @Test
    @DisplayName("certificado nao desliza com resposta atrasada: o teto de 75 segura")
    void certificateCapBindsOnLateResponse() {
        OffsetDateTime lateResponse = at(LocalDate.of(2027, 3, 10));
        assertThat(TenantExitCalculator.certificateDueAt(
                END_AT, at(LocalDate.of(2027, 1, 30)), lateResponse))
                .isEqualTo(at(LocalDate.of(2027, 3, 16)));
    }
}
