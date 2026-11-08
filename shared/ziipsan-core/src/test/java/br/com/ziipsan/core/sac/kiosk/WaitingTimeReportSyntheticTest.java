package br.com.ziipsan.core.sac.kiosk;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAS-26: massa SINTETICA do relatorio de espera. O calendario de
 * feriados abaixo e FIXTURE do teste (dado de entrada sintetico, nao
 * dado do produto): feriado prolongado sintetico na segunda 02/03/2026
 * (sequencia sab 28/02 + dom 01/03 + seg 02/03) e feriado isolado
 * sintetico na quarta 11/03/2026 (sem emenda). Limites pelos parametros
 * com a fabrica da Lei SC 12.465/2002 (15/30), via KioskParameters.
 */
class WaitingTimeReportSyntheticTest {

    private static final UUID UNIT_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID UNIT_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private static final HolidayCalendar CALENDAR = HolidayCalendar.of(Set.of(
            LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 11)));

    private static WaitingLimitPolicy policy() {
        return new WaitingLimitPolicy(CALENDAR,
                KioskParameters.waitLimitBusinessDayMinutes(
                        KioskParameters.FACTORY_WAIT_LIMIT_BUSINESS_DAY_MINUTES),
                KioskParameters.waitLimitHolidayAdjacentMinutes(
                        KioskParameters.FACTORY_WAIT_LIMIT_HOLIDAY_ADJACENT_MINUTES));
    }

    private static QueueTicket started(UUID unit, int seq, LocalDate day, int waitMinutes) {
        var issued = day.atTime(8, 0);
        var t = new QueueTicket(UUID.randomUUID(), unit, seq, "ATENDIMENTO",
                PriorityAttendanceProfile.common(), issued);
        t.call(issued.plusMinutes(Math.max(1, waitMinutes - 1)), "G1");
        t.startService(issued.plusMinutes(waitMinutes));
        return t;
    }

    private static List<QueueTicket> syntheticMass() {
        var all = new ArrayList<QueueTicket>();
        // Dia util comum (qua 04/03/2026), limite 15: esperas 10 e 15 dentro; 16 e 40 fora
        all.add(started(UNIT_A, 1, LocalDate.of(2026, 3, 4), 10));
        all.add(started(UNIT_A, 2, LocalDate.of(2026, 3, 4), 15));
        all.add(started(UNIT_A, 3, LocalDate.of(2026, 3, 4), 16));
        all.add(started(UNIT_A, 4, LocalDate.of(2026, 3, 4), 40));
        // Vespora do feriado prolongado (sex 27/02/2026), limite 30: 20 e 30 dentro; 31 fora
        all.add(started(UNIT_A, 5, LocalDate.of(2026, 2, 27), 20));
        all.add(started(UNIT_A, 6, LocalDate.of(2026, 2, 27), 30));
        all.add(started(UNIT_A, 7, LocalDate.of(2026, 2, 27), 31));
        // Dia seguinte ao feriado prolongado (ter 03/03/2026), limite 30: 29 dentro; 45 fora
        all.add(started(UNIT_A, 8, LocalDate.of(2026, 3, 3), 29));
        all.add(started(UNIT_A, 9, LocalDate.of(2026, 3, 3), 45));
        // Vespora de feriado ISOLADO (ter 10/03/2026): sem emenda, limite segue 15: 14 dentro; 16 fora
        all.add(started(UNIT_A, 10, LocalDate.of(2026, 3, 10), 14));
        all.add(started(UNIT_A, 11, LocalDate.of(2026, 3, 10), 16));
        // Outra unidade, mesmo dia comum: separacao por unidade
        all.add(started(UNIT_B, 1, LocalDate.of(2026, 3, 4), 5));
        // Senha emitida e ainda nao iniciada no dia comum: pendente, fora de dentro/fora
        var pending = new QueueTicket(UUID.randomUUID(), UNIT_A, 12, "ATENDIMENTO",
                PriorityAttendanceProfile.common(), LocalDateTime.of(2026, 3, 4, 9, 0));
        all.add(pending);
        return all;
    }

    private static WaitingTimeReport.DayRow row(List<WaitingTimeReport.DayRow> rows, UUID unit, LocalDate day) {
        return rows.stream().filter(r -> r.serviceUnitId().equals(unit) && r.date().equals(day))
                .findFirst().orElseThrow(() -> new AssertionError("linha ausente " + unit + " " + day));
    }

    @Test void calendarClassifiesProlongedAndIsolatedHolidays() {
        assertTrue(CALENDAR.isProlongedHoliday(LocalDate.of(2026, 3, 2)));
        assertFalse(CALENDAR.isProlongedHoliday(LocalDate.of(2026, 3, 11)));
        assertTrue(CALENDAR.isHolidayAdjacent(LocalDate.of(2026, 2, 27)));
        assertTrue(CALENDAR.isHolidayAdjacent(LocalDate.of(2026, 3, 3)));
        assertFalse(CALENDAR.isHolidayAdjacent(LocalDate.of(2026, 3, 10)));
        assertFalse(CALENDAR.isHolidayAdjacent(LocalDate.of(2026, 3, 12)));
        assertEquals(30, policy().limitFor(LocalDate.of(2026, 2, 27)).minutes());
        assertEquals(15, policy().limitFor(LocalDate.of(2026, 3, 4)).minutes());
    }

    @Test void syntheticMassSeparatesWithinAndOutsidePerUnitAndDay() {
        var rows = WaitingTimeReport.generate(syntheticMass(), policy());
        assertEquals(5, rows.size());

        var common = row(rows, UNIT_A, LocalDate.of(2026, 3, 4));
        assertEquals(15, common.limitMinutes());
        assertEquals(WaitingLimitPolicy.LimitKind.BUSINESS_DAY, common.limitKind());
        assertEquals(4, common.startedCount());
        assertEquals(2, common.withinLimit());
        assertEquals(2, common.outsideLimit());
        assertEquals(0, common.withinPct().compareTo(new BigDecimal("50.00")));
        assertEquals(40, common.maxWaitMinutes());
        assertEquals(1, common.pendingStart());

        var eve = row(rows, UNIT_A, LocalDate.of(2026, 2, 27));
        assertEquals(30, eve.limitMinutes());
        assertEquals(WaitingLimitPolicy.LimitKind.HOLIDAY_ADJACENT, eve.limitKind());
        assertEquals(2, eve.withinLimit());
        assertEquals(1, eve.outsideLimit());

        var after = row(rows, UNIT_A, LocalDate.of(2026, 3, 3));
        assertEquals(30, after.limitMinutes());
        assertEquals(1, after.withinLimit());
        assertEquals(1, after.outsideLimit());

        var isolatedEve = row(rows, UNIT_A, LocalDate.of(2026, 3, 10));
        assertEquals(15, isolatedEve.limitMinutes());
        assertEquals(1, isolatedEve.withinLimit());
        assertEquals(1, isolatedEve.outsideLimit());

        var unitB = row(rows, UNIT_B, LocalDate.of(2026, 3, 4));
        assertEquals(1, unitB.startedCount());
        assertEquals(1, unitB.withinLimit());
        assertEquals(0, unitB.pendingStart());
    }

    @Test void noShowAndCancelledDoNotInflatePendingStart() {
        // Correcao D3 da revisao: pendingStart conta SO senhas vivas
        // aguardando inicio (WAITING/CALLED). NO_SHOW e CANCELLED sao
        // encerradas sem inicio — nunca iniciarao — e contam a parte
        // em closedWithoutStart.
        var day = LocalDate.of(2026, 3, 4);
        var issued = day.atTime(8, 0);

        var waiting = new QueueTicket(UUID.randomUUID(), UNIT_A, 21, "ATENDIMENTO",
                PriorityAttendanceProfile.common(), issued);

        var called = new QueueTicket(UUID.randomUUID(), UNIT_A, 22, "ATENDIMENTO",
                PriorityAttendanceProfile.common(), issued);
        called.call(issued.plusMinutes(3), "G1");

        var noShow = new QueueTicket(UUID.randomUUID(), UNIT_A, 23, "ATENDIMENTO",
                PriorityAttendanceProfile.common(), issued);
        noShow.call(issued.plusMinutes(4), "G1");
        noShow.markNoShow(issued.plusMinutes(10));

        var cancelledWaiting = new QueueTicket(UUID.randomUUID(), UNIT_A, 24, "ATENDIMENTO",
                PriorityAttendanceProfile.common(), issued);
        cancelledWaiting.cancel(issued.plusMinutes(5));

        var cancelledCalled = new QueueTicket(UUID.randomUUID(), UNIT_A, 25, "ATENDIMENTO",
                PriorityAttendanceProfile.common(), issued);
        cancelledCalled.call(issued.plusMinutes(2), "G2");
        cancelledCalled.cancel(issued.plusMinutes(6));

        var rows = WaitingTimeReport.generate(List.of(
                waiting, called, noShow, cancelledWaiting, cancelledCalled,
                started(UNIT_A, 26, day, 10)), policy());

        var r = row(rows, UNIT_A, day);
        assertEquals(1, r.startedCount());
        assertEquals(1, r.withinLimit());
        assertEquals(2, r.pendingStart());
        assertEquals(3, r.closedWithoutStart());
    }

    @Test void boundaryWaitExactlyAtLimitIsWithin() {
        var rows = WaitingTimeReport.generate(List.of(
                started(UNIT_A, 1, LocalDate.of(2026, 3, 4), 15),
                started(UNIT_A, 2, LocalDate.of(2026, 2, 27), 30)), policy());
        assertEquals(1, row(rows, UNIT_A, LocalDate.of(2026, 3, 4)).withinLimit());
        assertEquals(1, row(rows, UNIT_A, LocalDate.of(2026, 2, 27)).withinLimit());
    }
}
