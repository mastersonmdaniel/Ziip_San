package br.com.ziipsan.integration.erp;

import br.com.ziipsan.core.backoffice.PlannedMaterials;
import br.com.ziipsan.core.inventory.StockMovement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ErpExportServiceTest {

    @Test
    void accountingExportSendsDebitAndCreditLinesWithCostCenter() {
        FakeErpTransport fake = new FakeErpTransport();
        ErpExportService svc = ErpFixtures.service(fake, "ERP");
        var batch = svc.exportAccounting(ErpFixtures.TENANT, "vault:x", "corr-1", "idem-acc-1",
                List.of(ErpFixtures.journalEntry()), costCenterLink());
        assertTrue(batch.succeeded());
        assertEquals(1, fake.sentPayloads.size());
        String payload = fake.sentPayloads.get(0);
        assertEquals(2, payload.lines().filter(l -> !l.isBlank()).count());
        assertTrue(payload.contains("F_DC=D"), payload);
        assertTrue(payload.contains("F_DC=C"), payload);
        assertTrue(payload.contains("F_CENTRO_CUSTO=CC-ORG-01"), payload);
        assertTrue(payload.contains("F_VALOR=150.00"), payload);
        assertEquals(ErpPayloads.OP_ACCOUNTING, fake.sentOperations.get(0));
    }

    @Test
    void accountingExportIsIdempotentPerBatchKey() {
        FakeErpTransport fake = new FakeErpTransport();
        ErpExportService svc = ErpFixtures.service(fake, "ERP");
        var first = svc.exportAccounting(ErpFixtures.TENANT, "vault:x", "corr-1", "idem-acc-2",
                List.of(ErpFixtures.journalEntry()), costCenterLink());
        var replay = svc.exportAccounting(ErpFixtures.TENANT, "vault:x", "corr-2", "idem-acc-2",
                List.of(ErpFixtures.journalEntry()), costCenterLink());
        assertSame(first, replay);
        assertEquals(1, fake.sentPayloads.size());
    }

    @Test
    void forecastSendsPlannedMaterialsWithExpenseNatureSg476() {
        FakeErpTransport fake = new FakeErpTransport();
        ErpExportService svc = ErpFixtures.service(fake, "ZIIPSAN");
        var planned = List.of(new PlannedMaterials.Line("TUBO-100", new BigDecimal("12"), null));
        var hooks = Map.of("TUBO-100", new ErpHooks.MaterialErpHook(ErpFixtures.TENANT, "TUBO-100", "ND-339030"));
        var batch = svc.sendMaterialForecast(ErpFixtures.TENANT, "vault:x", "corr-3", "idem-fc-1", "OS-900", planned, hooks);
        assertTrue(batch.succeeded());
        String payload = fake.sentPayloads.get(0);
        assertTrue(payload.contains("F_MATERIAL=TUBO-100"), payload);
        assertTrue(payload.contains("F_QUANTIDADE=12"), payload);
        assertTrue(payload.contains("F_NATUREZA_DESPESA=ND-339030"), payload);
        assertTrue(payload.contains("F_REFERENCIA=OS-900"), payload);
    }

    @Test
    void forecastRefusesMaterialWithoutDeclaredExpenseNature() {
        FakeErpTransport fake = new FakeErpTransport();
        ErpExportService svc = ErpFixtures.service(fake, "ERP");
        var planned = List.of(new PlannedMaterials.Line("TUBO-100", new BigDecimal("12"), null));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> svc.sendMaterialForecast(ErpFixtures.TENANT, "vault:x", "corr-4", "idem-fc-2", "OS-900", planned, Map.of()));
        assertTrue(e.getMessage().contains("TUBO-100"));
        assertTrue(fake.sentPayloads.isEmpty());
    }

    @Test
    void movementsRefusedWhenZiipSanIsTheOfficialStockAdr0032() {
        FakeErpTransport fake = new FakeErpTransport();
        ErpExportService svc = ErpFixtures.service(fake, "ZIIPSAN");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> svc.exportStockMovements(ErpFixtures.TENANT, "vault:x", "corr-5", "idem-mv-1",
                        List.of(movement()), Map.of("TUBO-100", hook())));
        assertTrue(e.getMessage().contains("ADR-0032"));
        assertTrue(fake.sentPayloads.isEmpty());
    }

    @Test
    void movementsExportedWhenErpIsTheOfficialStock() {
        FakeErpTransport fake = new FakeErpTransport();
        ErpExportService svc = ErpFixtures.service(fake, "ERP");
        var batch = svc.exportStockMovements(ErpFixtures.TENANT, "vault:x", "corr-6", "idem-mv-2",
                List.of(movement()), Map.of("TUBO-100", hook()));
        assertTrue(batch.succeeded());
        String payload = fake.sentPayloads.get(0);
        assertTrue(payload.contains("F_TIPO_MOVIMENTO=DELIVERY"), payload);
        assertTrue(payload.contains("F_NATUREZA_DESPESA=ND-339030"), payload);
    }

    @Test
    void contractHookRequiresCommitmentOrExternalOrderAndCarriesPncp() {
        assertThrows(IllegalArgumentException.class,
                () -> new ErpHooks.ThirdPartyContractHook(ErpFixtures.TENANT, UUID.randomUUID(), null, " ", null));
        FakeErpTransport fake = new FakeErpTransport();
        ErpExportService svc = ErpFixtures.service(fake, "ERP");
        var hook = new ErpHooks.ThirdPartyContractHook(ErpFixtures.TENANT, UUID.randomUUID(), "EMP-2026-77", null, "PNCP-123");
        var batch = svc.sendContractHook(ErpFixtures.TENANT, "vault:x", "corr-7", "idem-ct-1", hook, "CT-55");
        assertTrue(batch.succeeded());
        String payload = fake.sentPayloads.get(0);
        assertTrue(payload.contains("F_EMPENHO=EMP-2026-77"), payload);
        assertTrue(payload.contains("F_PNCP=PNCP-123"), payload);
        assertTrue(payload.contains("F_CONTRATO=CT-55"), payload);
    }

    // --- Correcao R2 (revisao ED-62): centro de custo via CostCenterLink ---

    @Test
    void accountingExportWithResolvedCostCenterLinkIncludesCostCenterOnEveryLine() {
        FakeErpTransport fake = new FakeErpTransport();
        ErpExportService svc = ErpFixtures.service(fake, "ERP");
        var batch = svc.exportAccounting(ErpFixtures.TENANT, "vault:x", "corr-r2-1", "idem-acc-r2-1",
                List.of(ErpFixtures.journalEntry()), costCenterLink());
        assertTrue(batch.succeeded());
        assertEquals(1, fake.sentPayloads.size());
        String payload = fake.sentPayloads.get(0);
        // As DUAS linhas (D e C) carregam o centro de custo do vinculo resolvido.
        assertEquals(2, payload.lines().filter(l -> l.contains("F_CENTRO_CUSTO=CC-ORG-01")).count(), payload);
    }

    @Test
    void accountingExportRefusesWhenCostCenterLinkIsMissing() {
        FakeErpTransport fake = new FakeErpTransport();
        ErpExportService svc = ErpFixtures.service(fake, "ERP");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> svc.exportAccounting(ErpFixtures.TENANT, "vault:x", "corr-r2-2", "idem-acc-r2-2",
                        List.of(ErpFixtures.journalEntry()), null));
        assertTrue(e.getMessage().contains("[A DEFINIR]"), e.getMessage());
        assertTrue(e.getMessage().contains("CostCenterLink"), e.getMessage());
        // Nunca omissao silenciosa: nada foi enviado ao ERP.
        assertTrue(fake.sentPayloads.isEmpty());
    }

    @Test
    void accountingExportRefusesCostCenterLinkOfAnotherTenant() {
        FakeErpTransport fake = new FakeErpTransport();
        ErpExportService svc = ErpFixtures.service(fake, "ERP");
        var foreignLink = new ErpHooks.CostCenterLink(ErpFixtures.TENANT_B, "ORG-01", "CC-ORG-01");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> svc.exportAccounting(ErpFixtures.TENANT, "vault:x", "corr-r2-3", "idem-acc-r2-3",
                        List.of(ErpFixtures.journalEntry()), foreignLink));
        assertTrue(e.getMessage().contains("[A DEFINIR]"), e.getMessage());
        assertTrue(fake.sentPayloads.isEmpty());
    }

    private static ErpHooks.CostCenterLink costCenterLink() {
        return new ErpHooks.CostCenterLink(ErpFixtures.TENANT, "ORG-01", "CC-ORG-01");
    }

    private static ErpHooks.MaterialErpHook hook() {
        return new ErpHooks.MaterialErpHook(ErpFixtures.TENANT, "TUBO-100", "ND-339030");
    }

    private static StockMovement movement() {
        return new StockMovement(UUID.randomUUID(), ErpFixtures.TENANT, StockMovement.Type.DELIVERY,
                "TUBO-100", "ALM-01", new BigDecimal("5"), new BigDecimal("10.00"), "OS-900",
                UUID.randomUUID(), null, Instant.parse("2026-11-05T10:00:00Z"));
    }
}
