package br.com.ziipsan.commercial.collection.cession;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** ED-103/E2+E3 — exportacao generica, minimizacao e trilha de auditoria. */
class PortfolioExportTest {

    private PortfolioReport report() {
        return PortfolioReport.build(CessionFixtures.TENANT, CessionFixtures.REF,
                AgingBands.proofDefaults(), CessionFixtures.portfolio());
    }

    @Test
    void exportWithoutDeclaredDestinationIsRefusedAndLeavesNoAudit() {
        var store = new InMemoryCessionStore();
        var exporter = new PortfolioExporter(store);
        var ex = assertThrows(ReceivablesCessionException.class, () ->
                exporter.export(report(), PortfolioExporter.Format.CSV, "  ", "operador-1", Instant.now()));
        assertEquals("EXPORT_DESTINATION_REQUIRED", ex.code());
        assertTrue(store.exportAudits(CessionFixtures.TENANT).isEmpty());
    }

    @Test
    void csvExportIsMinimizedAndAuditedWithContentHash() {
        var store = new InMemoryCessionStore();
        var exporter = new PortfolioExporter(store);
        var at = Instant.parse("2026-11-10T10:00:00Z");
        var result = exporter.export(report(), PortfolioExporter.Format.CSV,
                "FUNDO-DECLARADO-PELO-CLIENTE", "operador-1", at);

        // So linhas com saldo em aberto (R3, pago integral, fica fora): R1, R2, R4
        assertEquals(4, result.content().split("\n").length); // cabecalho + 3
        assertTrue(result.content().startsWith("receivable_id,debtor_key,amount_due,due_date,aging_band\n"));
        assertTrue(result.content().contains("R1,dev-hash-001,750.00,2026-12-10,A_VENCER"));
        assertTrue(result.content().contains("R2,dev-hash-002,500.00,2026-10-21,0-30"));
        assertTrue(result.content().contains("R4,dev-hash-003,200.00,2025-10-06,ACIMA_360"));
        assertFalse(result.content().contains("R3"));

        // Trilha completa: quem, quando, ambito, destino, hash, linhas
        var audits = store.exportAudits(CessionFixtures.TENANT);
        assertEquals(1, audits.size());
        var a = audits.get(0);
        assertEquals("operador-1", a.actor());
        assertEquals(at, a.exportedAt());
        assertEquals("FUNDO-DECLARADO-PELO-CLIENTE", a.destination());
        assertEquals("CSV", a.format());
        assertEquals(3, a.lineCount());
        assertTrue(a.scope().contains("referenceDate=2026-11-10"));
        assertEquals(PortfolioExporter.sha256(result.content()), a.contentHash());
        assertEquals(64, a.contentHash().length());
    }

    @Test
    void jsonExportCarriesTheSameMinimizedWhitelist() {
        var store = new InMemoryCessionStore();
        var exporter = new PortfolioExporter(store);
        var result = exporter.export(report(), PortfolioExporter.Format.JSON,
                "CONTA-VINCULADA-DECLARADA", "operador-2", Instant.now());
        assertTrue(result.content().contains("\"receivableId\":\"R1\""));
        assertTrue(result.content().contains("\"amountDue\":750.00"));
        assertFalse(result.content().toLowerCase().contains("cpf"));
        assertFalse(result.content().toLowerCase().contains("cnpj"));
        assertFalse(result.content().toLowerCase().contains("address"));
        assertFalse(result.content().toLowerCase().contains("\"name\""));
        assertEquals(1, store.exportAudits(CessionFixtures.TENANT).size());
    }
}
