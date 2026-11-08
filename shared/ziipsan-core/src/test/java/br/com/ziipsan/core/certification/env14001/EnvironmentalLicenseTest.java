package br.com.ziipsan.core.certification.env14001;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvironmentalLicenseTest {

    private EnvironmentalLicense license() {
        return new EnvironmentalLicense(TestSupport.enabledTarget(), TestSupport.id(),
                TestSupport.TENANT, "SYN-LIC-001", "SYN-licenca de operacao da ETE",
                "SYN-orgao-emissor-declarado",
                LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1), TestSupport.ACTOR,
                List.of(new EnvironmentalLicense.EvidenceRef("DOCUMENT", "SYN-doc-licenca")),
                List.of(new EnvironmentalLicense.Condition("SYN-condicionante cumprida",
                                LocalDate.of(2026, 6, 1), "SYN-evidencia-1"),
                        new EnvironmentalLicense.Condition("SYN-condicionante pendente",
                                null, null)));
    }

    @Test
    void licenseCarriesValidityResponsibleEvidenceAndConditions() {
        EnvironmentalLicense lic = license();
        assertThat(lic.validUntil()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(lic.responsibleId()).isEqualTo(TestSupport.ACTOR);
        assertThat(lic.evidence()).hasSize(1);
        assertThat(lic.conditionsWithoutEvidence())
                .extracting(EnvironmentalLicense.Condition::description)
                .containsExactly("SYN-condicionante pendente");
    }

    @Test
    void expiryAlertUsesCallerDeclaredWindow() {
        EnvironmentalLicense lic = license();
        assertThat(lic.expiryStatus(LocalDate.of(2026, 1, 1), 30))
                .isEqualTo(EnvironmentalLicense.ExpiryStatus.VALID);
        assertThat(lic.expiryStatus(LocalDate.of(2026, 12, 15), 30))
                .isEqualTo(EnvironmentalLicense.ExpiryStatus.EXPIRING);
        assertThat(lic.expiryStatus(LocalDate.of(2027, 1, 2), 30))
                .isEqualTo(EnvironmentalLicense.ExpiryStatus.EXPIRED);
        assertThat(lic.expired(LocalDate.of(2027, 1, 2))).isTrue();
        assertThat(lic.expired(LocalDate.of(2026, 12, 31))).isFalse();
        assertThatThrownBy(() -> lic.expiryStatus(LocalDate.of(2026, 1, 1), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invertedValidityAndDisabledNormRefused() {
        assertThatThrownBy(() -> new EnvironmentalLicense(TestSupport.enabledTarget(),
                TestSupport.id(), TestSupport.TENANT, "SYN-X", "SYN-d", "SYN-orgao",
                LocalDate.of(2027, 1, 1), LocalDate.of(2025, 1, 1), TestSupport.ACTOR,
                List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invertida");
        assertThatThrownBy(() -> new EnvironmentalLicense(TestSupport.disabled(),
                TestSupport.id(), TestSupport.TENANT, "SYN-X", "SYN-d", "SYN-orgao",
                LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1), TestSupport.ACTOR,
                List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }
}
