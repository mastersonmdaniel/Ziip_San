package br.com.ziipsan.core.certification.external;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aceite (3)-(6): desligado por padrao, expirado recusado, somente leitura, registro de cada leitura. */
class ExternalAccessServiceTest {

    private EvidencePack pack9001() {
        return TestSupport.packOf(Set.of(CertificationStandard.ISO_9001),
                TestSupport.item(CertificationStandard.ISO_9001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-qms", TestSupport.SHA_A));
    }

    @Test
    void readWithParameterOffDeniedAndLogged() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant grant = TestSupport.auditorGrant(
                Set.of(CertificationStandard.ISO_9001), false);
        assertThatThrownBy(() -> service.readEvidencePack(grant, pack9001(),
                Set.of(CertificationStandard.ISO_9001), TestSupport.NOW))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("DESLIGADO");
        assertThat(service.readLog()).hasSize(1);
        assertThat(service.readLog().get(0).outcome())
                .isEqualTo(ExternalReadLogEntry.Outcome.DENIED);
    }

    @Test
    void expiredGrantReadDeniedAndLogged() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant grant = TestSupport.auditorGrant(
                Set.of(CertificationStandard.ISO_9001), true);
        assertThatThrownBy(() -> service.readEvidencePack(grant, pack9001(),
                Set.of(CertificationStandard.ISO_9001), TestSupport.TO))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("EXPIRADA");
        assertThat(service.readLog()).hasSize(1);
        assertThat(service.readLog().get(0).denialReason()).contains("EXPIRADA");
    }

    @Test
    void writeAlwaysRefused() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant grant = TestSupport.auditorGrant(
                Set.of(CertificationStandard.ISO_9001), true);
        assertThatThrownBy(() -> service.attemptWrite(grant,
                ExternalResource.EVIDENCE_PACK, "SYN-qms"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOMENTE LEITURA");
        ExternalAccessGrant regulator = TestSupport.regulatorGrant(true);
        assertThatThrownBy(() -> service.attemptWrite(regulator,
                ExternalResource.REGULATORY_DATA, "SYN-reg"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void attemptWriteRefusalIsNotLogged() {
        // Dobra R6-A (R6-A2): fixa o comportamento documentado —
        // attemptWrite recusa sempre, SEM registrar: a escrita nao e
        // leitura e o log registra leituras. Nao muda o comportamento,
        // so o fixa (o Javadoc da classe e do metodo foi ajustado).
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant grant = TestSupport.auditorGrant(
                Set.of(CertificationStandard.ISO_9001), true);
        assertThatThrownBy(() -> service.attemptWrite(grant,
                ExternalResource.EVIDENCE_PACK, "SYN-qms"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SOMENTE LEITURA");
        assertThat(service.readLog()).isEmpty();
        ExternalAccessGrant regulator = TestSupport.regulatorGrant(true);
        assertThatThrownBy(() -> service.attemptWrite(regulator,
                ExternalResource.REGULATORY_DATA, "SYN-reg"))
                .isInstanceOf(SecurityException.class);
        assertThat(service.readLog()).isEmpty();
    }

    @Test
    void eachGrantedReadIsLoggedWithWhoProfileResourceWhen() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant grant = TestSupport.auditorGrant(
                Set.of(CertificationStandard.ISO_9001), true);
        String manifest = service.readEvidencePack(grant, pack9001(),
                Set.of(CertificationStandard.ISO_9001), TestSupport.NOW);
        assertThat(manifest).contains("ISO_9001");
        service.readEvidencePack(grant, pack9001(),
                Set.of(CertificationStandard.ISO_9001), TestSupport.NOW);
        assertThat(service.readLog()).hasSize(2);
        ExternalReadLogEntry entry = service.readLog().get(0);
        assertThat(entry.grantId()).isEqualTo(grant.grantId());
        assertThat(entry.tenantId()).isEqualTo(TestSupport.TENANT);
        assertThat(entry.profile()).isEqualTo(ExternalProfile.CERTIFICATION_AUDITOR);
        assertThat(entry.resource()).isEqualTo(ExternalResource.EVIDENCE_PACK);
        assertThat(entry.resourceRef()).startsWith("evidence-pack:");
        assertThat(entry.readAt()).isEqualTo(TestSupport.NOW);
        assertThat(entry.outcome()).isEqualTo(ExternalReadLogEntry.Outcome.GRANTED);
        assertThat(entry.denialReason()).isNull();
    }

    @Test
    void packWithStandardTurnedOffAtReadTimeDenied() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant grant = TestSupport.auditorGrant(
                Set.of(CertificationStandard.ISO_9001, CertificationStandard.ISO_14001), true);
        EvidencePack pack = TestSupport.packOf(
                Set.of(CertificationStandard.ISO_9001, CertificationStandard.ISO_14001),
                TestSupport.item(CertificationStandard.ISO_9001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-qms", TestSupport.SHA_A),
                TestSupport.item(CertificationStandard.ISO_14001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-ems", TestSupport.SHA_B));
        assertThatThrownBy(() -> service.readEvidencePack(grant, pack,
                Set.of(CertificationStandard.ISO_9001), TestSupport.NOW))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("desligada");
    }

    @Test
    void packBeyondGrantScopeDenied() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant grant = TestSupport.auditorGrant(
                Set.of(CertificationStandard.ISO_9001), true);
        EvidencePack pack = TestSupport.packOf(
                Set.of(CertificationStandard.ISO_9001, CertificationStandard.ISO_27001),
                TestSupport.item(CertificationStandard.ISO_9001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-qms", TestSupport.SHA_A),
                TestSupport.item(CertificationStandard.ISO_27001,
                        EvidenceItem.DataKind.AGGREGATE, "SYN-sgsi", TestSupport.SHA_B));
        assertThatThrownBy(() -> service.readEvidencePack(grant, pack,
                CertificationStandard.all(), TestSupport.NOW))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("fora do escopo");
    }

    // Dobra R5-A (F1-B1): o caminho concedido passava o ref cru ao
    // registro — ref nulo/em branco lancava IllegalArgumentException
    // SEM registro (e o recibo do readRegulatory daria NPE). Agora o
    // ref e sanitizado para "(sem referencia)", no mesmo padrao do
    // deny(), e a leitura concedida e registrada.

    @Test
    void readRegulatoryGrantedWithNullRefLogsSentinel() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant regulator = TestSupport.regulatorGrant(true);
        assertThat(service.readRegulatory(regulator, ExternalResource.REGULATORY_DATA,
                null, TestSupport.NOW)).isEqualTo("READ:REGULATORY_DATA:(sem referencia)");
        assertThat(service.readLog()).hasSize(1);
        ExternalReadLogEntry entry = service.readLog().get(0);
        assertThat(entry.outcome()).isEqualTo(ExternalReadLogEntry.Outcome.GRANTED);
        assertThat(entry.resourceRef()).isEqualTo("(sem referencia)");
        assertThat(entry.denialReason()).isNull();
    }

    @Test
    void readRegulatoryGrantedWithBlankRefLogsSentinel() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant regulator = TestSupport.regulatorGrant(true);
        assertThat(service.readRegulatory(regulator, ExternalResource.REGULATORY_REPORT,
                "   ", TestSupport.NOW)).isEqualTo("READ:REGULATORY_REPORT:(sem referencia)");
        assertThat(service.readLog()).hasSize(1);
        assertThat(service.readLog().get(0).resourceRef()).isEqualTo("(sem referencia)");
    }

    @Test
    void readGrantedWithNullRefLogsSentinel() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant regulator = TestSupport.regulatorGrant(true);
        service.read(regulator, ExternalResource.REGULATORY_DATA, null, TestSupport.NOW);
        assertThat(service.readLog()).hasSize(1);
        ExternalReadLogEntry entry = service.readLog().get(0);
        assertThat(entry.outcome()).isEqualTo(ExternalReadLogEntry.Outcome.GRANTED);
        assertThat(entry.resourceRef()).isEqualTo("(sem referencia)");
    }

    @Test
    void readGrantedWithBlankRefLogsSentinel() {
        ExternalAccessService service = new ExternalAccessService();
        ExternalAccessGrant regulator = TestSupport.regulatorGrant(true);
        service.read(regulator, ExternalResource.PERSONAL_DATA_MINIMUM, "  ",
                TestSupport.NOW);
        assertThat(service.readLog()).hasSize(1);
        assertThat(service.readLog().get(0).resourceRef()).isEqualTo("(sem referencia)");
    }
}
