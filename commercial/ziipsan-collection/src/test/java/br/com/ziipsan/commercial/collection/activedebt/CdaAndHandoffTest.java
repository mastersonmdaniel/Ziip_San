package br.com.ziipsan.commercial.collection.activedebt;

import br.com.ziipsan.commercial.collection.dunning.DunningPorts;
import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class CdaAndHandoffTest {
    static final class FakeTemplate implements DunningPorts.TemplatePort {
        final AtomicInteger calls = new AtomicInteger();
        public RenderedDocument render(UUID tenantId, String code, Map<String, Object> vars) {
            calls.incrementAndGet();
            return new RenderedDocument(code.replace("\"", ""), "v3", "doc-" + calls.get());
        }
    }
    private ActiveDebtEnrollment.Enrollment enrollment() {
        var s = new ActiveDebtEnrollment(ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                new ActiveDebtEnrollment.Book(), Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        return s.enrollIndividual(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ActiveDebtEnrollment.DebtItem(UUID.randomUUID(), LocalDate.of(2025, 1, 10), new BigDecimal("1500.00"), null)), false, Set.of());
    }

    @Test void cdaIssuedByTemplateEngineAndReprintDoesNotRerender() {
        var fake = new FakeTemplate();
        var cda = new CdaDocument(fake, ActiveDebtTestSupport.params(Map.of()),
                ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        var issued = cda.issue(enrollment());
        assertThat(issued.templateCode()).isEqualTo("CDA_TENANT_CODE");
        assertThat(issued.templateVersion()).isEqualTo("v3");
        assertThat(fake.calls.get()).isEqualTo(1);
        assertThat(cda.reprint(issued)).isEqualTo(issued);
        assertThat(fake.calls.get()).isEqualTo(1); // reimpressao nao chama o motor
    }

    @Test void cdaWithoutTenantTemplateCodeIsRefusedNotInvented() {
        var p = ActiveDebtParameters.fromValues(Map.of());
        var cda = new CdaDocument(new FakeTemplate(), p,
                ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        assertThatThrownBy(() -> cda.issue(enrollment()))
                .hasMessageContaining("da.cda_template_code");
    }

    @Test void handoffGeneratesFileAndPetitionModelForProsecution() {
        var fake = new FakeTemplate();
        var h = new ProsecutionHandoff(fake, ActiveDebtTestSupport.params(Map.of()),
                ActiveDebtTestSupport.gateAt(LocalDate.of(2026, 11, 8)),
                Prescription.ProviderNature.PRIVATE_LAW, Prescription.ClaimType.GENERAL);
        var e = enrollment();
        var out = h.generate(e.tenantId(), List.of(e));
        assertThat(out.fileTemplateCode()).isEqualTo("HANDOFF_TENANT_CODE");
        assertThat(out.petitionTemplateCode()).isEqualTo("PETITION_TENANT_CODE");
        assertThat(out.enrollmentIds()).containsExactly(e.id());
        assertThat(fake.calls.get()).isEqualTo(2);
    }
}
