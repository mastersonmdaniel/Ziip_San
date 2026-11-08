package br.com.ziipsan.core.fieldos;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CutRevalidationTest {
    static CutPackage.Facts facts(CutPackage.DebitState d, boolean cont, boolean notice, Set<CutPackage.Prohibition> p) {
        return new CutPackage.Facts(d, cont, notice, p);
    }
    static final CutPackage.Facts OPEN_OK = facts(CutPackage.DebitState.OPEN, false, true, Set.of());

    @Test void openDebitNoticeGivenNoProhibitionAllows() {
        assertTrue(CutRevalidation.evaluate(OPEN_OK, OPEN_OK).allowed());
    }
    @Test void offlinePaymentAfterIssueCancels() {
        var now = facts(CutPackage.DebitState.PAID, false, true, Set.of());
        var d = CutRevalidation.evaluate(OPEN_OK, now);
        assertTrue(d.cancelled());
        assertEquals(java.util.List.of(CutRevalidation.Reason.DEBIT_PAID_AFTER_ISSUE), d.reasons());
    }
    @Test void contestationOpenCancels() {
        var now = facts(CutPackage.DebitState.OPEN, true, true, Set.of());
        assertTrue(CutRevalidation.evaluate(OPEN_OK, now).reasons().contains(CutRevalidation.Reason.CONTESTATION_OPEN));
    }
    @Test void noticeNotGivenCancels() {
        var now = facts(CutPackage.DebitState.OPEN, false, false, Set.of());
        assertTrue(CutRevalidation.evaluate(OPEN_OK, now).reasons().contains(CutRevalidation.Reason.NOTICE_NOT_GIVEN));
    }
    @Test void legalProhibitionCancels() {
        var now = facts(CutPackage.DebitState.OPEN, false, true, Set.of(CutPackage.Prohibition.LEGAL_PROHIBITION));
        assertTrue(CutRevalidation.evaluate(OPEN_OK, now).cancelled());
    }
    @Test void snapshotCarriesDebitContestationNoticeAndProhibitions() {
        var s = new CutPackage.Snapshot(UUID.randomUUID(), UUID.randomUUID(), "task-1", Instant.now(), OPEN_OK);
        assertEquals(CutPackage.DebitState.OPEN, s.factsAtIssue().debitState());
        assertEquals("task-1", s.taskId());
    }
}
