package br.com.ziipsan.commercial.collection.cession;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** ED-103/E1+E2 — porta, aging parametrizavel e adimplencia exacta. */
class PortfolioReportTest {

    @Test
    void agingAndAdimplenciaAreExactPerBandAndTotal() {
        var report = PortfolioReport.build(CessionFixtures.TENANT, CessionFixtures.REF,
                AgingBands.proofDefaults(), CessionFixtures.portfolio());
        var byLabel = new java.util.HashMap<String, PortfolioReport.BandRow>();
        report.rows().forEach(r -> byLabel.put(r.label(), r));

        // A_VENCER: R1 — face 1000, pago 250, devido 750
        assertEquals(1, byLabel.get("A_VENCER").count());
        assertEquals(new BigDecimal("1000.00"), byLabel.get("A_VENCER").face());
        assertEquals(new BigDecimal("250.00"), byLabel.get("A_VENCER").paid());
        assertEquals(new BigDecimal("750.00"), byLabel.get("A_VENCER").due());
        // 0-30: R2 (20 dias) — 500/0/500
        assertEquals(new BigDecimal("500.00"), byLabel.get("0-30").due());
        // 31-60: R3 (45 dias, pago integral) — conta na adimplencia, devido 0
        assertEquals(1, byLabel.get("31-60").count());
        assertEquals(new BigDecimal("800.00"), byLabel.get("31-60").paid());
        assertEquals(new BigDecimal("0.00"), byLabel.get("31-60").due());
        // ACIMA_360: R4 (400 dias) — 300/100/200
        assertEquals(new BigDecimal("200.00"), byLabel.get("ACIMA_360").due());
        // Total exacto: face 2600, pago 1150, devido 1450
        assertEquals(4, report.total().count());
        assertEquals(new BigDecimal("2600.00"), report.total().face());
        assertEquals(new BigDecimal("1150.00"), report.total().paid());
        assertEquals(new BigDecimal("1450.00"), report.total().due());
        // face = pago + devido, sem perda de centavos
        assertEquals(report.total().face(), report.total().paid().add(report.total().due()));
    }

    @Test
    void bandsAreParametrizableAndChangeTheReport() {
        var custom = new AgingBands(List.of(
                new AgingBands.Band("NAO_VENCIDO", null, -1),
                new AgingBands.Band("VENCIDO", 0, null)));
        var report = PortfolioReport.build(CessionFixtures.TENANT, CessionFixtures.REF,
                custom, CessionFixtures.portfolio());
        assertEquals(2, report.rows().size());
        assertEquals(new BigDecimal("750.00"), report.rows().get(0).due());
        assertEquals(new BigDecimal("700.00"), report.rows().get(1).due());
    }

    @Test
    void nonContiguousOrOpenEndedBandsAreRefused() {
        assertThrows(ReceivablesCessionException.class, () -> new AgingBands(List.of(
                new AgingBands.Band("A", null, 10), new AgingBands.Band("B", 20, null))));
        assertThrows(ReceivablesCessionException.class, () -> new AgingBands(List.of(
                new AgingBands.Band("A", 0, 10), new AgingBands.Band("B", 11, null))));
        assertThrows(ReceivablesCessionException.class, () -> new AgingBands(List.of(
                new AgingBands.Band("A", null, 10), new AgingBands.Band("B", 11, 99))));
    }

    @Test
    void portfolioLineRejectsPaidAboveFaceAndCarriesNoPersonalDataFields() {
        assertThrows(ReceivablesCessionException.class, () ->
                CessionFixtures.line("RX", "dev-hash-009", "100.00", "100.01", LocalDate.of(2026, 1, 1)));
        // Whitelist E1/E3: a linha so tem os 6 campos declarados — sem nome,
        // sem documento, sem endereco (nem existem no modelo).
        var fields = java.util.Arrays.stream(
                        ReceivablesPortfolioSource.PortfolioLine.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertEquals(List.of("receivableId", "debtorKey", "faceAmount", "paidAmount", "dueDate", "origin"), fields);
    }
}
