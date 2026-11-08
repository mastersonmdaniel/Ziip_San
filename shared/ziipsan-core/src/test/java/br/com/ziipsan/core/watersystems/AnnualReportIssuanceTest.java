package br.com.ziipsan.core.watersystems;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AnnualReportIssuanceTest {

    private static final Instant AT = Instant.parse("2027-01-15T12:00:00Z");

    private AnnualQualityReport readyReport(InMemorySystemsStore store) {
        List<AnnualQualityReport.MonthlyQualitySummary> rows = new ArrayList<>();
        for (int m = 1; m <= 12; m++) rows.add(SystemsTestSupport.summary(m, "PARAM_A", 10, 10, 10, 10, 0, 0));
        AnnualQualityReport report = AnnualReportBuilder.build(UUID.randomUUID(), SystemsTestSupport.TENANT,
                SystemsTestSupport.UNIT, "SAA-01", 2026, rows, List.of(), List.of());
        store.saveReport(report);
        return report;
    }

    private SystemsPorts.LegalMessagePort proofPort() {
        return req -> Optional.of(SystemsPorts.LegalEmissionProof.fromAdapter(
                "PROOF-P232-TESTE", req.billId(), req.billVersion(), 1, 1, List.of(req.catalogKey())));
    }

    @Test void issueWithProofAndDeliveryMarksIssued() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        AnnualQualityReport report = readyReport(store);
        UUID attempt = UUID.randomUUID();
        var record = AnnualReportIssuanceService.issue(store, proofPort(),
                req -> SystemsPorts.DeliveryOutcome.sent(attempt),
                SystemsTestSupport.TENANT, report.id(), UUID.randomUUID(), 3,
                SystemsPorts.ANNUAL_REPORT_CATALOG_KEY_PROPOSED, AT);
        assertEquals(AnnualReportIssuanceService.IssuanceOutcome.ISSUED, record.outcome());
        assertEquals("PROOF-P232-TESTE", record.legalProofRef());
        assertEquals(attempt, record.notificationAttemptId());
        assertEquals(AnnualQualityReport.Status.ISSUED,
                store.report(SystemsTestSupport.TENANT, report.id()).orElseThrow().status());
    }

    @Test void withoutProofFromP232NothingIsDelivered() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        AnnualQualityReport report = readyReport(store);
        AtomicInteger deliveries = new AtomicInteger();
        SystemsPorts.NotificationPort counting = req -> {
            deliveries.incrementAndGet();
            return SystemsPorts.DeliveryOutcome.sent(UUID.randomUUID());
        };
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> AnnualReportIssuanceService.issue(store, req -> Optional.empty(), counting,
                        SystemsTestSupport.TENANT, report.id(), UUID.randomUUID(), 1,
                        SystemsPorts.ANNUAL_REPORT_CATALOG_KEY_PROPOSED, AT));
        assertEquals("LEGAL_EMISSION_PROOF_MISSING", ex.code());
        assertEquals(0, deliveries.get());
        assertEquals(AnnualQualityReport.Status.READY,
                store.report(SystemsTestSupport.TENANT, report.id()).orElseThrow().status());
        assertTrue(store.issuances(SystemsTestSupport.TENANT, report.id()).isEmpty());
    }

    @Test void deliveryNotSentKeepsReportReadyAndRecordsReason() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        AnnualQualityReport report = readyReport(store);
        var record = AnnualReportIssuanceService.issue(store, proofPort(),
                req -> SystemsPorts.DeliveryOutcome.notSent("NO_CONTACT"),
                SystemsTestSupport.TENANT, report.id(), UUID.randomUUID(), 1,
                SystemsPorts.ANNUAL_REPORT_CATALOG_KEY_PROPOSED, AT);
        assertEquals(AnnualReportIssuanceService.IssuanceOutcome.DELIVERY_NOT_SENT, record.outcome());
        assertEquals("NO_CONTACT", record.deliveryReason());
        assertEquals("PROOF-P232-TESTE", record.legalProofRef());
        assertEquals(AnnualQualityReport.Status.READY,
                store.report(SystemsTestSupport.TENANT, report.id()).orElseThrow().status());
        assertEquals(1, store.issuances(SystemsTestSupport.TENANT, report.id()).size());
    }

    @Test void draftReportCannotIssue() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        AnnualQualityReport draft = AnnualReportBuilder.build(UUID.randomUUID(), SystemsTestSupport.TENANT,
                SystemsTestSupport.UNIT, "SAA-01", 2026,
                List.of(SystemsTestSupport.summary(1, "PARAM_A", 1, 1, 1, 1, 0, 0)), List.of(), List.of());
        store.saveReport(draft);
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> AnnualReportIssuanceService.issue(store, proofPort(),
                        req -> SystemsPorts.DeliveryOutcome.sent(UUID.randomUUID()),
                        SystemsTestSupport.TENANT, draft.id(), UUID.randomUUID(), 1,
                        SystemsPorts.ANNUAL_REPORT_CATALOG_KEY_PROPOSED, AT));
        assertEquals("REPORT_NOT_READY", ex.code());
    }

    @Test void alreadyIssuedCannotIssueAgain() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        AnnualQualityReport report = readyReport(store);
        AnnualReportIssuanceService.issue(store, proofPort(),
                req -> SystemsPorts.DeliveryOutcome.sent(UUID.randomUUID()),
                SystemsTestSupport.TENANT, report.id(), UUID.randomUUID(), 1,
                SystemsPorts.ANNUAL_REPORT_CATALOG_KEY_PROPOSED, AT);
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> AnnualReportIssuanceService.issue(store, proofPort(),
                        req -> SystemsPorts.DeliveryOutcome.sent(UUID.randomUUID()),
                        SystemsTestSupport.TENANT, report.id(), UUID.randomUUID(), 2,
                        SystemsPorts.ANNUAL_REPORT_CATALOG_KEY_PROPOSED, AT));
        assertEquals("REPORT_ALREADY_ISSUED", ex.code());
    }

    @Test void deliveryRequestCarriesUnitPurposeAndReport() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        AnnualQualityReport report = readyReport(store);
        List<SystemsPorts.DeliveryRequest> seen = new ArrayList<>();
        AnnualReportIssuanceService.issue(store, proofPort(),
                req -> { seen.add(req); return SystemsPorts.DeliveryOutcome.sent(UUID.randomUUID()); },
                SystemsTestSupport.TENANT, report.id(), UUID.randomUUID(), 1,
                SystemsPorts.ANNUAL_REPORT_CATALOG_KEY_PROPOSED, AT);
        assertEquals(1, seen.size());
        assertEquals(SystemsTestSupport.UNIT, seen.get(0).consumerUnitId());
        assertEquals(SystemsPorts.ANNUAL_REPORT_PURPOSE, seen.get(0).purpose());
        assertEquals(report.id(), seen.get(0).reportId());
    }

    @Test void legalRequestCarriesBillLinkage() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        AnnualQualityReport report = readyReport(store);
        UUID bill = UUID.randomUUID();
        List<SystemsPorts.LegalEmissionRequest> seen = new ArrayList<>();
        AnnualReportIssuanceService.issue(store,
                req -> { seen.add(req); return Optional.of(SystemsPorts.LegalEmissionProof.fromAdapter(
                        "P", bill, 7, 1, 1, List.of(req.catalogKey()))); },
                req -> SystemsPorts.DeliveryOutcome.sent(UUID.randomUUID()),
                SystemsTestSupport.TENANT, report.id(), bill, 7,
                SystemsPorts.WATER_QUALITY_BLOCK_CATALOG_KEY, AT);
        assertEquals(bill, seen.get(0).billId());
        assertEquals(7, seen.get(0).billVersion());
        assertEquals("legal.water_quality_block", seen.get(0).catalogKey());
    }

    // Correcao D1: prova desencontrada do pedido (outra conta) recusa antes da entrega.
    @Test void proofForAnotherBillRejectedBeforeDelivery() {
        InMemorySystemsStore store = new InMemorySystemsStore();
        AnnualQualityReport report = readyReport(store);
        AtomicInteger deliveries = new AtomicInteger();
        SystemsPorts.NotificationPort counting = req -> {
            deliveries.incrementAndGet();
            return SystemsPorts.DeliveryOutcome.sent(UUID.randomUUID());
        };
        SystemsPorts.LegalMessagePort foreignProof = req -> Optional.of(
                SystemsPorts.LegalEmissionProof.fromAdapter("PROOF-ALHEIA", UUID.randomUUID(), 99, 1, 1,
                        List.of(req.catalogKey())));
        SystemsExceptions.ValidationException ex = assertThrows(SystemsExceptions.ValidationException.class,
                () -> AnnualReportIssuanceService.issue(store, foreignProof, counting,
                        SystemsTestSupport.TENANT, report.id(), UUID.randomUUID(), 1,
                        SystemsPorts.ANNUAL_REPORT_CATALOG_KEY_PROPOSED, AT));
        assertEquals("LEGAL_EMISSION_PROOF_MISMATCH", ex.code());
        assertEquals(0, deliveries.get());
        assertEquals(AnnualQualityReport.Status.READY,
                store.report(SystemsTestSupport.TENANT, report.id()).orElseThrow().status());
        assertTrue(store.issuances(SystemsTestSupport.TENANT, report.id()).isEmpty());
    }
}
