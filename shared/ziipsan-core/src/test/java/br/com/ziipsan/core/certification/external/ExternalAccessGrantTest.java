package br.com.ziipsan.core.certification.external;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aceite (3) e (4): desligado por padrao; expiracao obrigatoria; classe b com aceite. */
class ExternalAccessGrantTest {

    @Test
    void parameterOffMeansInactiveEvenInsideValidity() {
        ExternalAccessGrant grant = TestSupport.auditorGrant(
                Set.of(CertificationStandard.ISO_9001), false);
        assertThat(grant.isActiveAt(TestSupport.NOW)).isFalse();
    }

    @Test
    void parameterNamesFollowTheCatalog() {
        assertThat(ExternalAccessPolicy.REG_EXTERNAL_ACCESS_PARAM).isEqualTo("reg.external_access");
        assertThat(ExternalAccessPolicy.CERT_EXTERNAL_AUDITOR_ACCESS_PARAM)
                .isEqualTo("cert.external_auditor_access");
    }

    @Test
    void grantWithoutExpiryCannotBeBuilt() {
        assertThatThrownBy(() -> ExternalAccessGrant.forAuditor(UUID.randomUUID(),
                TestSupport.TENANT, "SYN-auditor", Set.of(CertificationStandard.ISO_9001),
                TestSupport.GRANTOR, TestSupport.ACCEPTER, TestSupport.FROM, null, true))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("OBRIGATORIO");
    }

    @Test
    void expiryNotAfterStartRefused() {
        assertThatThrownBy(() -> ExternalAccessGrant.forRegulator(UUID.randomUUID(),
                TestSupport.TENANT, "SYN-agencia", TestSupport.GRANTOR, TestSupport.ACCEPTER,
                TestSupport.TO, TestSupport.TO, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior");
    }

    @Test
    void expiredGrantIsInactive() {
        ExternalAccessGrant grant = TestSupport.regulatorGrant(true);
        assertThat(grant.isExpiredAt(TestSupport.TO)).isTrue();
        assertThat(grant.isActiveAt(TestSupport.TO)).isFalse();
        assertThat(grant.isActiveAt(TestSupport.NOW)).isTrue();
    }

    @Test
    void samePersonProposeAndAcceptRefused() {
        assertThatThrownBy(() -> ExternalAccessGrant.forRegulator(UUID.randomUUID(),
                TestSupport.TENANT, "SYN-agencia", TestSupport.GRANTOR, TestSupport.GRANTOR,
                TestSupport.FROM, TestSupport.TO, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aceitante");
    }

    @Test
    void regulatorWithEvidenceScopeRefusedByConstruction() {
        assertThatThrownBy(() -> new ExternalAccessGrant(UUID.randomUUID(), TestSupport.TENANT,
                ExternalProfile.REGULATOR_SERVICE_OWNER, "SYN-agencia",
                Set.of(CertificationStandard.ISO_9001), TestSupport.GRANTOR,
                TestSupport.ACCEPTER, TestSupport.FROM, TestSupport.TO, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NAO tem escopo de evidencia");
    }

    @Test
    void auditorWithoutEvidenceScopeRefused() {
        assertThatThrownBy(() -> ExternalAccessGrant.forAuditor(UUID.randomUUID(),
                TestSupport.TENANT, "SYN-auditor", Set.of(), TestSupport.GRANTOR,
                TestSupport.ACCEPTER, TestSupport.FROM, TestSupport.TO, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escopo de evidencia");
    }
}
