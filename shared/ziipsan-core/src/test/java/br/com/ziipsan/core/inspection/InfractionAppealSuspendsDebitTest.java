package br.com.ziipsan.core.inspection;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Aceite ED-24 (teste nomeado): recurso por OS INFRACTION_APPEAL suspende o
 * debito. Cobre os dois timings de infraction.charge_timing e o servico
 * inteiro com portas falsas que so registram chamadas (a maquina de estados
 * real decide; nenhum falso decide transicao).
 */
class InfractionAppealSuspendsDebitTest {

    private static final LocalDateTime ISSUED = LocalDateTime.of(2026, 11, 9, 10, 0);
    private static final UUID JUDGE = UUID.fromString("00000000-0000-0000-0000-0000000000e9");
    private static final BigDecimal AMOUNT = new BigDecimal("500.00");

    private static final class FakeDebits implements InfractionService.InfractionDebitGateway {
        final List<String> calls = new ArrayList<>();
        public UUID launchDebit(UUID noticeId, UUID unitId, BigDecimal amount) {
            calls.add("LAUNCH"); return UUID.fromString("00000000-0000-0000-0000-0000000000f1");
        }
        public void suspendDebit(UUID noticeId) { calls.add("SUSPEND"); }
        public void reactivateDebit(UUID noticeId) { calls.add("REACTIVATE"); }
        public void cancelDebit(UUID noticeId) { calls.add("CANCEL"); }
    }

    private static final class FakeOrders implements InfractionService.InfractionOrderGateway {
        final List<String> types = new ArrayList<>();
        public UUID openOrder(String type, UUID unitId, UUID noticeId, UUID by) {
            types.add(type); return UUID.fromString("00000000-0000-0000-0000-0000000000f2");
        }
    }

    private static InfractionService service(FakeDebits debits, FakeOrders orders) {
        return new InfractionService(
                request -> UUID.fromString("00000000-0000-0000-0000-0000000000f3"), orders, debits);
    }

    private static FraudRecord confirmedFraud() {
        return FraudRecordTest.fraud(FraudRecord.FraudStatus.CONFIRMED);
    }

    @Test
    void onIssueTimingAppealSuspendsLaunchedDebit() {
        FakeDebits debits = new FakeDebits();
        FakeOrders orders = new FakeOrders();
        InfractionService service = service(debits, orders);

        InfractionLifecycle.Case notice = service.issue(confirmedFraud(), AMOUNT, ISSUED, 30,
                InspectionParameters.ChargeTiming.ON_ISSUE_SUSPENDED_BY_APPEAL,
                "NOME EXEMPLO", "000000-0", "2026-000001", JUDGE);
        assertEquals(InfractionLifecycle.DebitState.LAUNCHED, notice.debitState());
        assertEquals(List.of("LAUNCH"), debits.calls);
        assertEquals(List.of(InspectionOrderTypes.INFRACTION_NOTICE), orders.types);
        assertNotNull(notice.documentId(), "auto emitido pelo motor de modelos (P2.30)");

        InfractionLifecycle.Case appealed = service.appeal(notice, ISSUED.plusDays(5), JUDGE);
        assertEquals(InfractionLifecycle.NoticeStatus.UNDER_APPEAL, appealed.status());
        assertEquals(InfractionLifecycle.DebitState.SUSPENDED_BY_APPEAL, appealed.debitState());
        assertEquals(List.of("LAUNCH", "SUSPEND"), debits.calls);
        assertEquals(List.of(InspectionOrderTypes.INFRACTION_NOTICE, InspectionOrderTypes.INFRACTION_APPEAL), orders.types);

        // Mantido apos o recurso: reativa o MESMO debito, nao lanca outro.
        InfractionLifecycle.Case decided = service.decide(appealed, true, JUDGE, ISSUED.plusDays(20));
        assertEquals(InfractionLifecycle.DebitState.LAUNCHED, decided.debitState());
        assertEquals(List.of("LAUNCH", "SUSPEND", "REACTIVATE"), debits.calls);
    }

    @Test
    void afterDecisionTimingNoDebitBeforeDecisionAndAppealBlocksIt() {
        FakeDebits debits = new FakeDebits();
        FakeOrders orders = new FakeOrders();
        InfractionService service = service(debits, orders);

        InfractionLifecycle.Case notice = service.issue(confirmedFraud(), AMOUNT, ISSUED, 30,
                InspectionParameters.ChargeTiming.AFTER_DECISION,
                "NOME EXEMPLO", "000000-0", "2026-000001", JUDGE);
        assertEquals(InfractionLifecycle.DebitState.NOT_LAUNCHED, notice.debitState());
        assertTrue(debits.calls.isEmpty(), "AFTER_DECISION: nenhum debito na emissao");
        assertThrows(IllegalStateException.class, () -> InfractionLifecycle.assertDebitLaunchAllowed(notice));

        InfractionLifecycle.Case appealed = service.appeal(notice, ISSUED.plusDays(3), JUDGE);
        assertEquals(InfractionLifecycle.DebitState.NOT_LAUNCHED, appealed.debitState());
        assertTrue(debits.calls.isEmpty(), "sob recurso, o debito continua bloqueado");
        assertThrows(IllegalStateException.class, () -> InfractionLifecycle.assertDebitLaunchAllowed(appealed));

        InfractionLifecycle.Case decided = service.decide(appealed, true, JUDGE, ISSUED.plusDays(15));
        assertEquals(InfractionLifecycle.DebitState.LAUNCHED, decided.debitState());
        assertEquals(List.of("LAUNCH"), debits.calls, "debito nasce so depois da decisao");
    }

    @Test
    void annulmentCancelsSuspendedDebitAndNeverLaunches() {
        FakeDebits debits = new FakeDebits();
        InfractionService service = service(debits, new FakeOrders());
        InfractionLifecycle.Case notice = service.issue(confirmedFraud(), AMOUNT, ISSUED, 30,
                InspectionParameters.ChargeTiming.ON_ISSUE_SUSPENDED_BY_APPEAL,
                "NOME EXEMPLO", "000000-0", "2026-000001", JUDGE);
        InfractionLifecycle.Case appealed = service.appeal(notice, ISSUED.plusDays(2), JUDGE);
        InfractionLifecycle.Case decided = service.decide(appealed, false, JUDGE, ISSUED.plusDays(10));
        assertEquals(InfractionLifecycle.NoticeStatus.DECIDED_ANNULLED, decided.status());
        assertEquals(InfractionLifecycle.DebitState.CANCELLED_BY_ANNULMENT, decided.debitState());
        assertEquals(List.of("LAUNCH", "SUSPEND", "CANCEL"), debits.calls);
        assertThrows(IllegalStateException.class, () -> InfractionLifecycle.assertDebitLaunchAllowed(decided));
    }

    @Test
    void appealDeadlineFromAppealDaysParameter() {
        InfractionLifecycle.Case notice = InfractionLifecycle.issue(confirmedFraud(), AMOUNT, ISSUED, 30,
                InspectionParameters.ChargeTiming.AFTER_DECISION).newCase();
        assertEquals(LocalDate.of(2026, 12, 9), notice.appealDeadline());
        assertTrue(notice.appealOpenAt(LocalDate.of(2026, 12, 9)));
        assertFalse(notice.appealOpenAt(LocalDate.of(2026, 12, 10)));
        assertThrows(IllegalStateException.class,
                () -> InfractionLifecycle.appeal(notice, LocalDateTime.of(2026, 12, 10, 9, 0)));
    }

    @Test
    void parametersAreExplicitNeverAssumed() {
        assertThrows(br.com.ziipsan.core.parameters.MissingParameterException.class,
                () -> InspectionParameters.appealDays(null));
        assertThrows(br.com.ziipsan.core.parameters.MissingParameterException.class,
                () -> InspectionParameters.chargeTiming(null));
        // D2 da revisao cruzada: a ausencia do TERCEIRO parametro
        // (infraction.max_retroactive_months) tambem lanca
        // MissingParameterException — ele segue validado-sem-aplicacao
        // (nenhuma regra de calculo inventada; ver InspectionParameters).
        assertThrows(br.com.ziipsan.core.parameters.MissingParameterException.class,
                () -> InspectionParameters.maxRetroactiveMonths(null));
        assertThrows(br.com.ziipsan.core.parameters.MissingParameterException.class,
                () -> InspectionParameters.chargeTiming(" "));
        assertEquals(6, InspectionParameters.maxRetroactiveMonths(6));
        assertThrows(IllegalArgumentException.class, () -> InspectionParameters.maxRetroactiveMonths(0));
        assertEquals(InspectionParameters.ChargeTiming.AFTER_DECISION,
                InspectionParameters.chargeTiming("AFTER_DECISION"));
        assertThrows(IllegalArgumentException.class, () -> InspectionParameters.chargeTiming("WHENEVER"));
    }
}
