package br.com.ziipsan.core.certification.lims17025;

import br.com.ziipsan.core.certification.governance.SupplierRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Massa SINTETICA dos testes da ED-51: metodos/parametros genericos
 * (METHOD_A/PARAM_A), limites ambientais e valores de controle
 * arbitrarios de fixture — NAO sao valores de nenhuma norma nem de
 * nenhum laboratorio real. Os dados reais (escopo, limites, medias
 * atribuidas, orcamentos) entram por declaracao do tenant.
 */
final class TestSupport {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000051");
    static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000052");
    static final UUID PROPOSER = UUID.fromString("00000000-0000-0000-0000-0000000000p1".replace('p', 'a'));
    static final UUID ACCEPTER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final LocalDate DAY = LocalDate.of(2026, 6, 15);
    static final Instant T0 = Instant.parse("2026-06-15T10:00:00Z");
    static final String ANALYST = "ANALISTA-1";
    static final String VALIDATOR = "VALIDADOR-1";
    static final String METHOD = "METHOD_A";
    static final String PARAM = "PARAM_A";

    private TestSupport() {}

    static Lims17025Activation enabled() {
        return Lims17025Activation.enabled("Escopo sintetico de fixture", PROPOSER, ACCEPTER);
    }

    static Laboratory laboratory() {
        return Laboratory.register(enabled(), UUID.randomUUID(), TENANT, "Laboratorio Sintetico",
                List.of(new Laboratory.ScopeEntry(METHOD, PARAM, true),
                        new Laboratory.ScopeEntry("METHOD_B", "PARAM_B", false)));
    }

    static LabEquipment validEquipment() {
        return LabEquipment.register(enabled(), UUID.randomUUID(), TENANT, "EQ-01",
                "Equipamento sintetico",
                new LabEquipment.CalibrationRecord(LocalDate.of(2026, 1, 10),
                        LocalDate.of(2027, 1, 9), "CERT-FIXTURE-1", "CALIBRADOR-1"));
    }

    /** Equipamento cuja calibracao venceu em 2026-01-31 (bloqueado em DAY). */
    static LabEquipment expiredEquipment() {
        return LabEquipment.register(enabled(), UUID.randomUUID(), TENANT, "EQ-02",
                "Equipamento sintetico vencido",
                new LabEquipment.CalibrationRecord(LocalDate.of(2025, 2, 1),
                        LocalDate.of(2026, 1, 31), "CERT-FIXTURE-2", "CALIBRADOR-1"));
    }

    static AnalystAuthorization authorization() {
        return AnalystAuthorization.grant(enabled(), ANALYST, METHOD,
                LocalDate.of(2026, 1, 1), null, "SUPERVISOR-1");
    }

    static MethodValidation methodValidation() {
        return MethodValidation.validated(enabled(), METHOD, PARAM,
                new BigDecimal("0.01"), new BigDecimal("0.05"),
                "DOSSIE-FIXTURE-1", "RT-1", LocalDate.of(2026, 2, 1));
    }

    static MeasurementUncertainty uncertainty() {
        return new MeasurementUncertainty(METHOD, PARAM, new BigDecimal("0.5"),
                new BigDecimal("2"), MeasurementUncertainty.DecisionRule.GUARD_BAND, "RT-1");
    }

    /** Requisito ambiental sintetico: temperatura 15..25, umidade 30..70 (fixture). */
    static EnvironmentalControl.Requirement envRequirement() {
        return new EnvironmentalControl.Requirement(new BigDecimal("15"), new BigDecimal("25"),
                new BigDecimal("30"), new BigDecimal("70"), "RT-1");
    }

    static EnvironmentalControl.Evaluation envWithin() {
        return EnvironmentalControl.evaluate(envRequirement(),
                new EnvironmentalControl.Reading(new BigDecimal("20"), new BigDecimal("50"), T0, "TEC-1"));
    }

    static EnvironmentalControl.Evaluation envOut() {
        return EnvironmentalControl.evaluate(envRequirement(),
                new EnvironmentalControl.Reading(new BigDecimal("31"), new BigDecimal("50"), T0, "TEC-1"));
    }

    /** Material de controle sintetico: media 100, desvio 2 (fixture). */
    static WestgardControl.QcMaterial qcMaterial() {
        return new WestgardControl.QcMaterial("QC-A", PARAM, new BigDecimal("100"),
                new BigDecimal("2"), "u");
    }

    static WestgardControl.Evaluation qcInControl() {
        return WestgardControl.evaluate(qcMaterial(), List.of(
                new WestgardControl.QcObservation(new BigDecimal("100"), T0),
                new WestgardControl.QcObservation(new BigDecimal("101"), T0.plusSeconds(60)),
                new WestgardControl.QcObservation(new BigDecimal("99"), T0.plusSeconds(120))));
    }

    static WestgardControl.Evaluation qcOutOfControl() {
        // ultimo ponto a 107 = +3,5 DP da media 100 (desvio 2): regra 1_3s
        return WestgardControl.evaluate(qcMaterial(), List.of(
                new WestgardControl.QcObservation(new BigDecimal("100"), T0),
                new WestgardControl.QcObservation(new BigDecimal("107"), T0.plusSeconds(60))));
    }

    /** Ensaio completo ate RESULT_RECORDED (sem material de referencia). */
    static AssayExecution recordedAssay() {
        AssayExecution a = AssayExecution.start(enabled(), laboratory(), methodValidation(),
                List.of(authorization()), validEquipment(), envWithin(), qcInControl(),
                uncertainty(), null, UUID.randomUUID(), TENANT, UUID.randomUUID(),
                METHOD, PARAM, ANALYST, DAY);
        a.recordResult(new BigDecimal("5.0"), null, "u", T0.plusSeconds(600));
        return a;
    }

    /** Ensaio completo e VALIDADO por segunda pessoa. */
    static AssayExecution validatedAssay() {
        AssayExecution a = recordedAssay();
        a.validateBySecondPerson(VALIDATOR);
        return a;
    }

    /** Catalogo falso da P3.31 sobre SupplierRecord REAIS da governanca. */
    static final class FakeSupplierCatalog implements LimsPorts.SupplierCatalogPort {
        private final Map<UUID, SupplierRecord> byId = new HashMap<>();
        FakeSupplierCatalog add(SupplierRecord s) { byId.put(s.id(), s); return this; }
        @Override public Optional<SupplierRecord> findSupplier(UUID tenantId, UUID supplierId) {
            SupplierRecord s = byId.get(supplierId);
            if (s == null || !s.tenantId().equals(tenantId)) return Optional.empty();
            return Optional.of(s);
        }
    }

    static SupplierRecord supplier(UUID tenantId, SupplierRecord.Type type) {
        return new SupplierRecord(UUID.randomUUID(), tenantId, "Fornecedor Sintetico Ltda",
                "DOC-FIXTURE", type, "CONTATO-FIXTURE");
    }

    /** Motor falso da P2.30: registra as chamadas e devolve referencia deterministica. */
    static final class FakeTemplateEngine implements LimsPorts.LabReportTemplatePort {
        int calls = 0;
        String lastTemplateKey;
        List<String> lastPayload;
        @Override public String render(UUID tenantId, String templateKey, List<String> payloadLines) {
            calls++;
            lastTemplateKey = templateKey;
            lastPayload = payloadLines;
            return "LAUDO-RENDERIZADO-" + calls;
        }
    }
}
