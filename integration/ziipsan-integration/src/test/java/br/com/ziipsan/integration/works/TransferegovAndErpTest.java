package br.com.ziipsan.integration.works;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** C4 (prestacao de contas Transferegov, [SEM FONTE]) e C5 (ERP de obras, padrao ED-62). */
class TransferegovAndErpTest {

    private WorksAgreementService serviceWithTwoMeasurements(FundingAgreement a) {
        var s = WorksFixtures.serviceWith(a);
        s.addMeasurement(WorksFixtures.measurement(a.id(), 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), "50", "500000.00"));
        s.addMeasurement(WorksFixtures.measurement(a.id(), 2,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31), "50", "500000.00"));
        return s;
    }

    // ---------- C4 ----------
    @Test void accountabilityPackCarriesAgreementMeasurementsAndAccumulated() {
        var a = WorksFixtures.agreement(FundingAgreement.Kind.FEDERAL_CONVENIO);
        var svc = new AccountabilityService(serviceWithTwoMeasurements(a));
        var mapping = TransferegovLayoutMapping.declared("generico-v1", false, WorksFixtures.fullMapping("x_"));
        var pack = svc.buildPack(WorksFixtures.TENANT, a.id(), mapping);
        assertFalse(pack.officialLayout(), "[SEM FONTE] o leiaute produzido nunca e o oficial");
        assertEquals("generico-v1", pack.layoutVersion());
        assertEquals(2, pack.measurementLines().size());
        var last = pack.measurementLines().get(1);
        assertTrue(last.stream().anyMatch(e -> e.getKey().equals("x_medicao_percentual_acumulado")
                && e.getValue().equals("100")));
        assertTrue(last.stream().anyMatch(e -> e.getKey().equals("x_medicao_valor_acumulado")
                && e.getValue().equals("1000000.00")));
        assertTrue(pack.header().stream().anyMatch(e -> e.getKey().equals("x_projeto_gis_ref")
                && e.getValue().equals("GIS-PRJ-001")));
    }

    @Test void officialLayoutFlagIsRefusedSemFonte() {
        assertThrows(IllegalArgumentException.class, () ->
                TransferegovLayoutMapping.declared("oficial?", true, WorksFixtures.fullMapping("x_")));
    }

    @Test void incompleteMappingRefusesNamingTheField() {
        var partial = new java.util.LinkedHashMap<>(WorksFixtures.fullMapping("x_"));
        partial.remove("medicao_sequencia");
        var mapping = TransferegovLayoutMapping.declared("generico-v1", false, partial);
        var a = WorksFixtures.agreement(FundingAgreement.Kind.ESTADUAL_CONVENIO);
        var svc = new AccountabilityService(serviceWithTwoMeasurements(a));
        var ex = assertThrows(IllegalArgumentException.class,
                () -> svc.buildPack(WorksFixtures.TENANT, a.id(), mapping));
        assertTrue(ex.getMessage().contains("medicao_sequencia"));
    }

    @Test void accountabilityWithoutMeasurementsIsRefused() {
        var a = WorksFixtures.agreement(FundingAgreement.Kind.FEDERAL_CONVENIO);
        var svc = new AccountabilityService(WorksFixtures.serviceWith(a));
        var mapping = TransferegovLayoutMapping.declared("generico-v1", false, WorksFixtures.fullMapping("x_"));
        assertThrows(IllegalStateException.class,
                () -> svc.buildPack(WorksFixtures.TENANT, a.id(), mapping));
    }

    // ---------- C5 ----------
    @Test void worksErpExportTranslatesByDeclaredMappingAndSends() {
        var a = WorksFixtures.agreement(FundingAgreement.Kind.FEDERAL_CONTRATO_REPASSE);
        List<String> sent = new ArrayList<>();
        WorksErpIntegration.WorksErpPort port = (op, payload) -> {
            sent.add(op);
            assertTrue(payload.stream().anyMatch(e -> e.getKey().equals("erp_convenio_ref")));
            return "PROTO-FICTICIO-1";
        };
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        for (String f : WorksErpIntegration.AGREEMENT_FIELDS) fields.put(f, "erp_" + f);
        var mapping = new WorksErpIntegration.WorksErpLayoutMapping(
                "ERP de obras ficticio de teste", "v1", fields);
        var ref = new WorksErpIntegration.WorksErpExportService(port).exportAgreement(a, mapping);
        assertEquals("PROTO-FICTICIO-1", ref);
        assertEquals(List.of(WorksErpIntegration.OP_AGREEMENT), sent);
    }

    @Test void worksErpExportRefusesMissingMapping() {
        var a = WorksFixtures.agreement(FundingAgreement.Kind.FEDERAL_CONTRATO_REPASSE);
        WorksErpIntegration.WorksErpPort port = (op, payload) -> "X";
        var mapping = new WorksErpIntegration.WorksErpLayoutMapping(
                "ERP ficticio", "v1", Map.of("convenio_ref", "erp_convenio_ref"));
        assertThrows(IllegalArgumentException.class, () ->
                new WorksErpIntegration.WorksErpExportService(port).exportAgreement(a, mapping));
    }

    @Test void worksErpMeasurementPayloadCarriesAccumulated() {
        var a = WorksFixtures.agreement(FundingAgreement.Kind.ESTADUAL_CONVENIO);
        var m = WorksFixtures.measurement(a.id(), 2, LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 12, 31), "50", "500000.00");
        var payload = WorksErpIntegration.measurementPayload(a, m,
                new BigDecimal("100"), new BigDecimal("1000000.00"));
        assertTrue(payload.stream().anyMatch(e -> e.getKey().equals("percentual_acumulado")
                && e.getValue().equals("100")));
    }
}
