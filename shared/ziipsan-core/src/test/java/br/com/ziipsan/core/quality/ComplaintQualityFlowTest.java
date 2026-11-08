package br.com.ziipsan.core.quality;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ComplaintQualityFlowTest {

    private static final UUID PROTOCOL = UUID.randomUUID();
    private static final LocalDate OPENED = LocalDate.of(2026, 1, 5);

    static final class FakeOrders implements QualityPorts.FieldCollectionOrderPort {
        final List<String> calls = new ArrayList<>();
        @Override public UUID openCollectionOrder(UUID tenantId, UUID pointId,
                                                  Sample.SampleType type, String reason) {
            calls.add(type + ":" + reason);
            return UUID.randomUUID();
        }
    }

    static final class FakeResponses implements QualityPorts.ProtocolResponsePort {
        UUID lastProtocol;
        @Override public void respondOnProtocol(UUID tenantId, UUID protocolId, String ref) {
            this.lastProtocol = protocolId;
        }
    }

    private ComplaintQualityFlow openedFlow(Integer days) {
        return ComplaintQualityFlow.open(TestSupport.TENANT, PROTOCOL, TestSupport.CONSUMER_POINT,
                ComplaintQualityFlow.ComplaintKind.COLOR, OPENED, days);
    }

    private Sample complaintSample() {
        Sample s = Sample.planned(UUID.randomUUID(), TestSupport.TENANT, TestSupport.CONSUMER_POINT,
                Sample.SampleType.COMPLAINT, "ETQ-301", UUID.randomUUID(), PROTOCOL);
        s.collect("C-1", TestSupport.T0);
        s.sealAndDispatch("C-1", TestSupport.T0.plusSeconds(60));
        s.receiveByLab("LAB-1", TestSupport.T0.plusSeconds(3600));
        return s;
    }

    @Test void deadlineComesFromNormCappedParameter() {
        ComplaintQualityFlow f = openedFlow(30);
        assertEquals(LocalDate.of(2026, 2, 4), f.deadline());
        ComplaintQualityFlow reduced = openedFlow(10);
        assertEquals(LocalDate.of(2026, 1, 15), reduced.deadline());
        assertThrows(IllegalArgumentException.class, () -> openedFlow(45));
        assertThrows(br.com.ziipsan.core.parameters.MissingParameterException.class, () -> openedFlow(null));
    }

    @Test void fullFlowConformingRespondsOnSameProtocol() {
        ComplaintQualityFlow f = openedFlow(30);
        FakeOrders orders = new FakeOrders();
        UUID orderId = f.orderCollection(orders);
        assertNotNull(orderId);
        assertTrue(orders.calls.get(0).startsWith("COMPLAINT"));
        Sample s = complaintSample();
        NormativeCatalog catalog = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", true, false));
        AnalysisResult r = TestSupport.numericResult(s, "PARAM_A", "5", true);
        f.recordResult(s, ConformityEvaluator.evaluate(r, catalog, OPENED));
        assertNull(f.orderRecollectionIfNonConforming(orders));
        assertEquals(1, orders.calls.size());
        FakeResponses responses = new FakeResponses();
        f.respond(responses, "RESP-1", LocalDate.of(2026, 1, 20));
        assertEquals(PROTOCOL, responses.lastProtocol);
        assertEquals(ComplaintQualityFlow.Stage.RESPONDED, f.stage());
        assertFalse(f.overdueOn(LocalDate.of(2026, 3, 1)));
    }

    @Test void nonConformingTriggersRecollection() {
        ComplaintQualityFlow f = openedFlow(30);
        FakeOrders orders = new FakeOrders();
        f.orderCollection(orders);
        Sample s = complaintSample();
        NormativeCatalog catalog = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", true, false));
        AnalysisResult r = TestSupport.numericResult(s, "PARAM_A", "99", true);
        f.recordResult(s, ConformityEvaluator.evaluate(r, catalog, OPENED));
        UUID recollection = f.orderRecollectionIfNonConforming(orders);
        assertNotNull(recollection);
        assertEquals(2, orders.calls.size());
        assertTrue(orders.calls.get(1).startsWith("RECOLLECTION"));
        assertEquals(ComplaintQualityFlow.Stage.RECOLLECTION_ORDERED, f.stage());
    }

    @Test void responseBeforeResultIsRefused() {
        ComplaintQualityFlow f = openedFlow(30);
        f.orderCollection(new FakeOrders());
        assertThrows(IllegalStateException.class,
                () -> f.respond(new FakeResponses(), "RESP-X", LocalDate.of(2026, 1, 6)));
    }

    @Test void sampleOfAnotherProtocolIsRefused() {
        ComplaintQualityFlow f = openedFlow(30);
        f.orderCollection(new FakeOrders());
        Sample other = Sample.planned(UUID.randomUUID(), TestSupport.TENANT, TestSupport.CONSUMER_POINT,
                Sample.SampleType.COMPLAINT, "ETQ-302", UUID.randomUUID(), UUID.randomUUID());
        NormativeCatalog catalog = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", true, false));
        ConformityEvaluator.Assessment a = new ConformityEvaluator.Assessment("PARAM_A",
                ConformityEvaluator.Verdict.CONFORMING, "fixture", false, "NORMA");
        assertThrows(IllegalArgumentException.class, () -> f.recordResult(other, a));
    }

    @Test void overdueIsVisibleBeforeAndAfterResponse() {
        ComplaintQualityFlow f = openedFlow(10);
        assertTrue(f.overdueOn(LocalDate.of(2026, 1, 16)));
        assertFalse(f.overdueOn(LocalDate.of(2026, 1, 15)));
        f.orderCollection(new FakeOrders());
        Sample s = complaintSample();
        NormativeCatalog catalog = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", true, false));
        AnalysisResult r = TestSupport.numericResult(s, "PARAM_A", "5", true);
        f.recordResult(s, ConformityEvaluator.evaluate(r, catalog, OPENED));
        f.respond(new FakeResponses(), "RESP-TARDE", LocalDate.of(2026, 1, 20));
        assertEquals(ComplaintQualityFlow.Stage.OVERDUE, f.stage());
    }
}
