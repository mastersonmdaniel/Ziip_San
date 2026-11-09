package br.com.ziipsan.core.certification.lims17025;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Laudo pelo motor da P2.30 (JOR-06), emenda rastreavel e trabalho nao conforme. */
class LabReportAndNonconformingTest {

    @Test void issueGoesThroughTheTemplateEnginePort() {
        TestSupport.FakeTemplateEngine engine = new TestSupport.FakeTemplateEngine();
        AssayExecution assay = TestSupport.validatedAssay();
        LabReport report = LabReport.issue(TestSupport.enabled(), engine, "MODELO-FIXTURE",
                UUID.randomUUID(), TestSupport.TENANT, assay.laboratoryId(), List.of(assay),
                "EMISSOR-1", TestSupport.T0);
        assertEquals(1, engine.calls);
        assertEquals("MODELO-FIXTURE", engine.lastTemplateKey);
        assertEquals("LAUDO-RENDERIZADO-1", report.renderedRef());
        assertEquals(1, report.version());
        assertEquals(LabReport.Status.ISSUED, report.status());
        // o conteudo carrega resultado, incerteza expandida com k, Westgard e a segunda pessoa
        String payload = String.join("\n", engine.lastPayload);
        assertTrue(payload.contains("resultado=5.0"));
        assertTrue(payload.contains("incertezaExpandida=1.0"));
        assertTrue(payload.contains("k=2"));
        assertTrue(payload.contains("westgard=IN_CONTROL"));
        assertTrue(payload.contains("validador=" + TestSupport.VALIDATOR));
    }

    @Test void missingTemplateKeyRefusesBeforeRendering() {
        TestSupport.FakeTemplateEngine engine = new TestSupport.FakeTemplateEngine();
        AssayExecution assay = TestSupport.validatedAssay();
        MissingParameterException ex = assertThrows(MissingParameterException.class, () ->
                LabReport.issue(TestSupport.enabled(), engine, null, UUID.randomUUID(),
                        TestSupport.TENANT, assay.laboratoryId(), List.of(assay),
                        "EMISSOR-1", TestSupport.T0));
        assertEquals(LimsParameters.LAB_REPORT_TEMPLATE_KEY, ex.definitionKey());
        assertEquals(0, engine.calls);
    }

    @Test void unvalidatedAssayDoesNotEnterReport() {
        TestSupport.FakeTemplateEngine engine = new TestSupport.FakeTemplateEngine();
        AssayExecution recordedOnly = TestSupport.recordedAssay();
        assertThrows(IllegalStateException.class, () -> LabReport.issue(TestSupport.enabled(),
                engine, "MODELO-FIXTURE", UUID.randomUUID(), TestSupport.TENANT,
                recordedOnly.laboratoryId(), List.of(recordedOnly), "EMISSOR-1", TestSupport.T0));
        assertEquals(0, engine.calls);
    }

    @Test void amendmentIsTraceableAndPreservesTheOriginal() {
        TestSupport.FakeTemplateEngine engine = new TestSupport.FakeTemplateEngine();
        AssayExecution assay = TestSupport.validatedAssay();
        LabReport v1 = LabReport.issue(TestSupport.enabled(), engine, "MODELO-FIXTURE",
                UUID.randomUUID(), TestSupport.TENANT, assay.laboratoryId(), List.of(assay),
                "EMISSOR-1", TestSupport.T0);
        // emenda sem motivo e recusada
        assertThrows(IllegalArgumentException.class, () -> v1.amend(TestSupport.enabled(), engine,
                UUID.randomUUID(), List.of(assay), " ", "EMISSOR-1", TestSupport.T0.plusSeconds(3600)));
        LabReport v2 = v1.amend(TestSupport.enabled(), engine, UUID.randomUUID(), List.of(assay),
                "correcao da unidade de medida", "EMISSOR-2", TestSupport.T0.plusSeconds(3600));
        assertEquals(2, v2.version());
        assertEquals(v1.id(), v2.supersedesId());
        assertEquals("correcao da unidade de medida", v2.amendmentReason());
        assertEquals(LabReport.Status.SUPERSEDED, v1.status());
        assertEquals(LabReport.Status.ISSUED, v2.status());
        assertTrue(String.join("\n", engine.lastPayload).contains("EMENDA=correcao da unidade"));
        // versao ja substituida nao se emenda de novo
        assertThrows(IllegalStateException.class, () -> v1.amend(TestSupport.enabled(), engine,
                UUID.randomUUID(), List.of(assay), "outra", "EMISSOR-1", TestSupport.T0.plusSeconds(7200)));
    }

    @Test void reportWithNormDisabledIsRefused() {
        TestSupport.FakeTemplateEngine engine = new TestSupport.FakeTemplateEngine();
        AssayExecution assay = TestSupport.validatedAssay();
        assertThrows(IllegalStateException.class, () -> LabReport.issue(Lims17025Activation.disabled(),
                engine, "MODELO-FIXTURE", UUID.randomUUID(), TestSupport.TENANT,
                assay.laboratoryId(), List.of(assay), "EMISSOR-1", TestSupport.T0));
    }

    @Test void nonconformingWorkLifecycle() {
        AssayExecution assay = TestSupport.validatedAssay();
        assay.flagNonconforming("controle da corrida seguinte falhou");
        assertEquals("controle da corrida seguinte falhou", assay.nonconformityNote());
        NonconformingWork nc = NonconformingWork.open(TestSupport.enabled(), UUID.randomUUID(),
                TestSupport.TENANT, assay.id(), null, null, "resultado sob suspeita apos falha do QC",
                NonconformingWork.Disposition.REPEAT_ASSAY, null, "SUPERVISOR-1", TestSupport.T0);
        assertEquals(NonconformingWork.Status.OPEN, nc.status());
        // fechamento sem acao nao existe
        assertThrows(IllegalArgumentException.class,
                () -> nc.close(" ", "SUPERVISOR-1", TestSupport.T0.plusSeconds(3600)));
        nc.close("ensaio repetido com nova corrida aprovada", "SUPERVISOR-1",
                TestSupport.T0.plusSeconds(3600));
        assertEquals(NonconformingWork.Status.CLOSED, nc.status());
        assertThrows(IllegalStateException.class,
                () -> nc.close("de novo", "SUPERVISOR-1", TestSupport.T0.plusSeconds(7200)));
        // sem ensaio nem laudo afetado, o registro nao nasce; OTHER exige justificativa
        assertThrows(IllegalArgumentException.class, () -> NonconformingWork.open(
                TestSupport.enabled(), UUID.randomUUID(), TestSupport.TENANT, null, null, null,
                "desvio", NonconformingWork.Disposition.REPEAT_ASSAY, null, "SUP-1", TestSupport.T0));
        assertThrows(IllegalArgumentException.class, () -> NonconformingWork.open(
                TestSupport.enabled(), UUID.randomUUID(), TestSupport.TENANT, assay.id(), null, null,
                "desvio", NonconformingWork.Disposition.OTHER, " ", "SUP-1", TestSupport.T0));
    }
}
