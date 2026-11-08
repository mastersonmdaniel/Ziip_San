package br.com.ziipsan.core.quality;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HealthRiskCommunicationTest {

    static final class FakeDispatch implements QualityPorts.LegalTemplateDispatchPort {
        final List<String> calls = new ArrayList<>();
        @Override public String dispatchByTemplate(UUID tenantId, String templateKey,
                                                   QualityPorts.RiskRecipient recipient, UUID ref) {
            calls.add(recipient + ":" + templateKey);
            return "ENVIO-" + recipient;
        }
    }

    private ConformityEvaluator.Assessment riskAssessment() {
        NormativeCatalog catalog = TestSupport.catalog(TestSupport.maxStandard("PARAM_A", "10", true, true));
        Sample s = TestSupport.receivedSample("ETQ-501");
        AnalysisResult r = TestSupport.numericResult(s, "PARAM_A", "99", true);
        return ConformityEvaluator.evaluate(r, catalog, LocalDate.of(2026, 1, 10));
    }

    private Sample sample() {
        return TestSupport.receivedSample("ETQ-502");
    }

    @Test void openRequiresNonConformingRiskAssessment() {
        ConformityEvaluator.Assessment conforming = new ConformityEvaluator.Assessment("PARAM_A",
                ConformityEvaluator.Verdict.CONFORMING, "ok", true, "NORMA");
        assertThrows(IllegalArgumentException.class, () -> HealthRiskCommunication.open(
                UUID.randomUUID(), TestSupport.TENANT, sample(), conforming,
                LocalDate.of(2026, 1, 12), true, "RT-1"));
        ConformityEvaluator.Assessment noRisk = new ConformityEvaluator.Assessment("PARAM_A",
                ConformityEvaluator.Verdict.NON_CONFORMING, "fora", false, "NORMA");
        assertThrows(IllegalArgumentException.class, () -> HealthRiskCommunication.open(
                UUID.randomUUID(), TestSupport.TENANT, sample(), noRisk,
                LocalDate.of(2026, 1, 12), true, "RT-1"));
    }

    @Test void dispatchRefusedBeforeTechnicalArticleCheck() {
        // JOR-22: sem a conferencia dos artigos com o responsavel tecnico, nada dispara.
        HealthRiskCommunication c = HealthRiskCommunication.open(UUID.randomUUID(), TestSupport.TENANT,
                sample(), riskAssessment(), LocalDate.of(2026, 1, 12), false, null);
        FakeDispatch fake = new FakeDispatch();
        assertThrows(IllegalStateException.class,
                () -> c.dispatchToAuthority(fake, "MODELO-RISCO", Instant.parse("2026-01-11T10:00:00Z")));
        assertTrue(fake.calls.isEmpty());
    }

    @Test void dispatchRefusedWithoutLoadedTemplateKey() {
        HealthRiskCommunication c = HealthRiskCommunication.open(UUID.randomUUID(), TestSupport.TENANT,
                sample(), riskAssessment(), LocalDate.of(2026, 1, 12), true, "RT-1");
        assertThrows(MissingParameterException.class,
                () -> c.dispatchToAuthority(new FakeDispatch(), null, Instant.parse("2026-01-11T10:00:00Z")));
    }

    @Test void fullDispatchRecordsBothRecipients() {
        HealthRiskCommunication c = HealthRiskCommunication.open(UUID.randomUUID(), TestSupport.TENANT,
                sample(), riskAssessment(), LocalDate.of(2026, 1, 12), true, "RT-1");
        FakeDispatch fake = new FakeDispatch();
        c.dispatchToAuthority(fake, "MODELO-RISCO", Instant.parse("2026-01-11T10:00:00Z"));
        c.dispatchToPopulation(fake, "MODELO-RISCO", Instant.parse("2026-01-11T11:00:00Z"));
        assertTrue(c.fullyDispatched());
        assertFalse(c.dispatchedLate());
        assertEquals("ENVIO-HEALTH_AUTHORITY", c.authorityDispatchRef());
        assertEquals("ENVIO-POPULATION", c.populationDispatchRef());
        assertEquals(2, fake.calls.size());
        assertThrows(IllegalStateException.class,
                () -> c.dispatchToAuthority(fake, "MODELO-RISCO", Instant.parse("2026-01-11T12:00:00Z")));
    }

    @Test void lateAndOverdueAreFactsNotHidden() {
        HealthRiskCommunication c = HealthRiskCommunication.open(UUID.randomUUID(), TestSupport.TENANT,
                sample(), riskAssessment(), LocalDate.of(2026, 1, 12), true, "RT-1");
        assertTrue(c.overdueOn(LocalDate.of(2026, 1, 13)));
        c.dispatchToAuthority(new FakeDispatch(), "MODELO-RISCO", Instant.parse("2026-01-13T10:00:00Z"));
        assertTrue(c.dispatchedLate());
        assertTrue(c.overdueOn(LocalDate.of(2026, 1, 13)));
    }
}
