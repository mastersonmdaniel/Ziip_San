package br.com.ziipsan.core.quality;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SampleCustodyTest {

    @Test void labelIsMandatory() {
        assertThrows(IllegalArgumentException.class, () -> Sample.planned(UUID.randomUUID(),
                TestSupport.TENANT, TestSupport.POINT, Sample.SampleType.ROUTINE, " ", null, null));
    }

    @Test void fullCustodyChainFromPointToLab() {
        Sample s = TestSupport.receivedSample("ETQ-001");
        assertEquals(Sample.Status.RECEIVED_BY_LAB, s.status());
        assertEquals(4, s.custody().size());
        assertEquals(Sample.CustodyKind.COLLECTED, s.custody().get(0).kind());
        assertEquals(Sample.CustodyKind.RECEIVED_BY_LAB, s.custody().get(3).kind());
        assertEquals("LAB-1", s.labReceiverRef());
        assertNotNull(s.labReceivedAt());
    }

    @Test void outOfOrderTransitionIsRefused() {
        Sample s = Sample.planned(UUID.randomUUID(), TestSupport.TENANT, TestSupport.POINT,
                Sample.SampleType.ROUTINE, "ETQ-002", null, null);
        assertThrows(IllegalStateException.class,
                () -> s.receiveByLab("LAB-1", TestSupport.T0));
        assertThrows(IllegalStateException.class,
                () -> s.sealAndDispatch("C-1", TestSupport.T0));
    }

    @Test void resultBeforeLabReceiptIsRefused() {
        // JOR-37: sem recebimento registrado pelo laboratorio, nao ha resultado.
        Sample s = Sample.planned(UUID.randomUUID(), TestSupport.TENANT, TestSupport.POINT,
                Sample.SampleType.ROUTINE, "ETQ-003", null, null);
        s.collect("C-1", TestSupport.T0);
        s.sealAndDispatch("C-1", TestSupport.T0.plusSeconds(60));
        assertThrows(IllegalStateException.class, () -> AnalysisResult.firstVersion(UUID.randomUUID(),
                s, "PARAM_A", new java.math.BigDecimal("1"), null, "u", "M", "A-1",
                TestSupport.T0.plusSeconds(120)));
    }

    @Test void custodyOutOfChronologyIsRefused() {
        Sample s = Sample.planned(UUID.randomUUID(), TestSupport.TENANT, TestSupport.POINT,
                Sample.SampleType.ROUTINE, "ETQ-004", null, null);
        s.collect("C-1", TestSupport.T0.plusSeconds(600));
        assertThrows(IllegalArgumentException.class,
                () -> s.sealAndDispatch("C-1", TestSupport.T0));
    }

    @Test void complaintSampleRequiresProtocol() {
        assertThrows(IllegalArgumentException.class, () -> Sample.planned(UUID.randomUUID(),
                TestSupport.TENANT, TestSupport.CONSUMER_POINT, Sample.SampleType.COMPLAINT,
                "ETQ-005", null, null));
    }

    @Test void recollectionLinksOriginal() {
        Sample original = TestSupport.receivedSample("ETQ-006");
        Sample rec = Sample.recollectionOf(original, UUID.randomUUID(), "ETQ-007", UUID.randomUUID());
        assertEquals(Sample.SampleType.RECOLLECTION, rec.type());
        assertEquals(original.id(), rec.originalSampleId());
        assertEquals(original.pointId(), rec.pointId());
    }

    @Test void cancelRequiresReasonAndEarlyState() {
        Sample s = Sample.planned(UUID.randomUUID(), TestSupport.TENANT, TestSupport.POINT,
                Sample.SampleType.ROUTINE, "ETQ-008", null, null);
        assertThrows(IllegalArgumentException.class, () -> s.cancel(" "));
        s.cancel("frasco quebrado");
        assertEquals(Sample.Status.CANCELLED, s.status());
        Sample received = TestSupport.receivedSample("ETQ-009");
        assertThrows(IllegalStateException.class, () -> received.cancel("tarde demais"));
    }

    @Test void resultCorrectionRequiresJustificationAndKeepsHistory() {
        Sample s = TestSupport.receivedSample("ETQ-010");
        AnalysisResult v1 = TestSupport.numericResult(s, "PARAM_A", "5", false);
        assertThrows(IllegalArgumentException.class, () -> v1.corrected(UUID.randomUUID(),
                new java.math.BigDecimal("6"), null, " ", "A-2", TestSupport.T0.plusSeconds(9000)));
        AnalysisResult v2 = v1.corrected(UUID.randomUUID(), new java.math.BigDecimal("6"), null,
                "transcricao corrigida pelo supervisor", "A-2", TestSupport.T0.plusSeconds(9000));
        assertEquals(2, v2.version());
        assertEquals(v1.id(), v2.supersedesId());
        assertFalse(v2.validated());
    }

    @Test void resultFormIsExactlyOne() {
        Sample s = TestSupport.receivedSample("ETQ-011");
        assertThrows(IllegalArgumentException.class, () -> AnalysisResult.firstVersion(UUID.randomUUID(),
                s, "PARAM_A", new java.math.BigDecimal("1"), "TEXTO", "u", "M", "A-1",
                TestSupport.T0.plusSeconds(8000)));
        assertThrows(IllegalArgumentException.class, () -> AnalysisResult.firstVersion(UUID.randomUUID(),
                s, "PARAM_A", null, null, "u", "M", "A-1", TestSupport.T0.plusSeconds(8000)));
    }
}
