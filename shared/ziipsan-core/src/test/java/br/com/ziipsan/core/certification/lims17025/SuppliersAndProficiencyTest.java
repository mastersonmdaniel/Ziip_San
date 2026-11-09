package br.com.ziipsan.core.certification.lims17025;

import br.com.ziipsan.core.certification.governance.SupplierRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Fornecedores lidos da P3.31 (JOR-18): lab externo, provedor de EP, material de referencia. */
class SuppliersAndProficiencyTest {

    @Test void externalLabRequiresExternalLaboratoryType() {
        SupplierRecord lab = TestSupport.supplier(TestSupport.TENANT,
                SupplierRecord.Type.EXTERNAL_LABORATORY);
        TestSupport.FakeSupplierCatalog catalog = new TestSupport.FakeSupplierCatalog().add(lab);
        ExternalLabAssay ok = ExternalLabAssay.register(TestSupport.enabled(), catalog,
                UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(), lab.id(),
                TestSupport.METHOD, TestSupport.PARAM, "LAUDO-EXT-FIXTURE", TestSupport.DAY);
        assertEquals(lab.id(), ok.externalLabSupplierId());
        // sem o laudo do laboratorio externo, o resultado nao entra
        assertThrows(IllegalArgumentException.class, () -> ExternalLabAssay.register(
                TestSupport.enabled(), catalog, UUID.randomUUID(), TestSupport.TENANT,
                UUID.randomUUID(), lab.id(), TestSupport.METHOD, TestSupport.PARAM, " ",
                TestSupport.DAY));
        // fornecedor de outro tipo nao serve como laboratorio externo
        SupplierRecord material = TestSupport.supplier(TestSupport.TENANT, SupplierRecord.Type.MATERIAL);
        catalog.add(material);
        assertThrows(IllegalArgumentException.class, () -> ExternalLabAssay.register(
                TestSupport.enabled(), catalog, UUID.randomUUID(), TestSupport.TENANT,
                UUID.randomUUID(), material.id(), TestSupport.METHOD, TestSupport.PARAM,
                "LAUDO-X", TestSupport.DAY));
        // inexistente no cadastro: sem cadastro paralelo
        assertThrows(IllegalArgumentException.class, () -> ExternalLabAssay.register(
                TestSupport.enabled(), catalog, UUID.randomUUID(), TestSupport.TENANT,
                UUID.randomUUID(), UUID.randomUUID(), TestSupport.METHOD, TestSupport.PARAM,
                "LAUDO-X", TestSupport.DAY));
        // de outro tenant: a porta nem devolve
        SupplierRecord foreign = TestSupport.supplier(TestSupport.OTHER_TENANT,
                SupplierRecord.Type.EXTERNAL_LABORATORY);
        catalog.add(foreign);
        assertThrows(IllegalArgumentException.class, () -> ExternalLabAssay.register(
                TestSupport.enabled(), catalog, UUID.randomUUID(), TestSupport.TENANT,
                UUID.randomUUID(), foreign.id(), TestSupport.METHOD, TestSupport.PARAM,
                "LAUDO-X", TestSupport.DAY));
    }

    @Test void proficiencyRoundComputesZAndRequiresServiceProvider() {
        SupplierRecord provider = TestSupport.supplier(TestSupport.TENANT, SupplierRecord.Type.SERVICE);
        TestSupport.FakeSupplierCatalog catalog = new TestSupport.FakeSupplierCatalog().add(provider);
        ProficiencyTesting pt = ProficiencyTesting.register(TestSupport.enabled(), catalog,
                UUID.randomUUID(), TestSupport.TENANT, provider.id(), "PROGRAMA-FIXTURE", "RODADA-1",
                TestSupport.METHOD, TestSupport.PARAM, new BigDecimal("100"), new BigDecimal("4"),
                new BigDecimal("106"), ProficiencyTesting.ProviderEvaluation.QUESTIONABLE,
                TestSupport.DAY);
        // z = (106 - 100) / 4 = 1,5000 (aritmetica; a avaliacao e a reportada pelo provedor)
        assertEquals(new BigDecimal("1.5000"), pt.zScore());
        assertEquals(ProficiencyTesting.ProviderEvaluation.QUESTIONABLE, pt.providerEvaluation());
        // laboratorio externo nao e provedor de EP no mapeamento declarado
        SupplierRecord lab = TestSupport.supplier(TestSupport.TENANT,
                SupplierRecord.Type.EXTERNAL_LABORATORY);
        catalog.add(lab);
        assertThrows(IllegalArgumentException.class, () -> ProficiencyTesting.register(
                TestSupport.enabled(), catalog, UUID.randomUUID(), TestSupport.TENANT, lab.id(),
                "PROGRAMA", "RODADA-2", TestSupport.METHOD, TestSupport.PARAM,
                new BigDecimal("100"), new BigDecimal("4"), new BigDecimal("100"),
                ProficiencyTesting.ProviderEvaluation.SATISFACTORY, TestSupport.DAY));
        // sigma do programa deve ser positivo
        assertThrows(IllegalArgumentException.class, () -> ProficiencyTesting.register(
                TestSupport.enabled(), catalog, UUID.randomUUID(), TestSupport.TENANT, provider.id(),
                "PROGRAMA", "RODADA-3", TestSupport.METHOD, TestSupport.PARAM,
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"),
                ProficiencyTesting.ProviderEvaluation.SATISFACTORY, TestSupport.DAY));
    }

    @Test void referenceMaterialRequiresMaterialSupplierAndValidity() {
        SupplierRecord supplier = TestSupport.supplier(TestSupport.TENANT, SupplierRecord.Type.MATERIAL);
        TestSupport.FakeSupplierCatalog catalog = new TestSupport.FakeSupplierCatalog().add(supplier);
        ReferenceMaterial rm = ReferenceMaterial.register(TestSupport.enabled(), catalog,
                UUID.randomUUID(), TestSupport.TENANT, "RM-A", "Material sintetico", "LOTE-1",
                new BigDecimal("100"), new BigDecimal("1.5"), "u", LocalDate.of(2026, 12, 31),
                supplier.id(), "CERT-RM-FIXTURE");
        assertTrue(rm.usableOn(TestSupport.DAY));
        assertTrue(rm.usableOn(LocalDate.of(2026, 12, 31)));
        assertThrows(IllegalStateException.class, () -> rm.requireUsableOn(LocalDate.of(2027, 1, 1)));
        // fornecedor de servico nao fornece material de referencia
        SupplierRecord service = TestSupport.supplier(TestSupport.TENANT, SupplierRecord.Type.SERVICE);
        catalog.add(service);
        assertThrows(IllegalArgumentException.class, () -> ReferenceMaterial.register(
                TestSupport.enabled(), catalog, UUID.randomUUID(), TestSupport.TENANT, "RM-B",
                "Material", "LOTE-2", new BigDecimal("10"), new BigDecimal("0.5"), "u",
                LocalDate.of(2027, 1, 1), service.id(), "CERT-2"));
    }

    @Test void expiredReferenceMaterialBlocksTheAssay() {
        SupplierRecord supplier = TestSupport.supplier(TestSupport.TENANT, SupplierRecord.Type.MATERIAL);
        TestSupport.FakeSupplierCatalog catalog = new TestSupport.FakeSupplierCatalog().add(supplier);
        ReferenceMaterial expired = ReferenceMaterial.register(TestSupport.enabled(), catalog,
                UUID.randomUUID(), TestSupport.TENANT, "RM-VENCIDO", "Material sintetico", "LOTE-9",
                new BigDecimal("100"), new BigDecimal("1"), "u", LocalDate.of(2026, 1, 31),
                supplier.id(), "CERT-RM-9");
        assertThrows(IllegalStateException.class, () -> AssayExecution.start(TestSupport.enabled(),
                TestSupport.laboratory(), TestSupport.methodValidation(),
                List.of(TestSupport.authorization()), TestSupport.validEquipment(),
                TestSupport.envWithin(), TestSupport.qcInControl(), TestSupport.uncertainty(),
                expired, UUID.randomUUID(), TestSupport.TENANT, UUID.randomUUID(),
                TestSupport.METHOD, TestSupport.PARAM, TestSupport.ANALYST, TestSupport.DAY));
    }
}
