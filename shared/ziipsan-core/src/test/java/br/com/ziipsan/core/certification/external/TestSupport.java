package br.com.ziipsan.core.certification.external;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Apoio dos testes da ED-75: dados 100% sinteticos (prefixo SYN-). */
final class TestSupport {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000751");
    static final UUID GRANTOR = UUID.fromString("00000000-0000-0000-0000-000000000752");
    static final UUID ACCEPTER = UUID.fromString("00000000-0000-0000-0000-000000000753");
    static final Instant FROM = Instant.parse("2026-11-08T00:00:00Z");
    static final Instant TO = Instant.parse("2026-11-20T00:00:00Z");
    static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    static final String SHA_A = "a".repeat(64);
    static final String SHA_B = "b".repeat(64);
    static final String SHA_C = "c".repeat(64);
    static final String SHA_D = "d".repeat(64);
    static final String SHA_E = "e".repeat(64);
    static final String SHA_F = "f".repeat(64);

    private TestSupport() {
    }

    static EvidenceItem item(CertificationStandard standard, EvidenceItem.DataKind kind,
                             String ref, String sha) {
        return new EvidenceItem(standard, kind, ref, sha);
    }

    static List<SupplierEvidenceItem> fullSupplierEvidence() {
        return List.of(
                new SupplierEvidenceItem(SupplierEvidenceItem.ArtifactKind.SECURITY_BASELINE_P38,
                        "SYN-baseline-p38", SHA_C),
                new SupplierEvidenceItem(SupplierEvidenceItem.ArtifactKind.AUDIT_TRAIL_EXPORT_P17,
                        "SYN-trilha-p17", SHA_D),
                new SupplierEvidenceItem(SupplierEvidenceItem.ArtifactKind.CONTRACT_DOCUMENT_P39,
                        "SYN-minuta-p39", SHA_E),
                new SupplierEvidenceItem(SupplierEvidenceItem.ArtifactKind.SECURITY_INCIDENT_P317,
                        "SYN-incidente-p317", SHA_F));
    }

    static EvidencePack packOf(Set<CertificationStandard> enabled, EvidenceItem... items) {
        return new EvidencePack(enabled, List.of(items), fullSupplierEvidence());
    }

    static ExternalAccessGrant auditorGrant(Set<CertificationStandard> scope, boolean paramOn) {
        return ExternalAccessGrant.forAuditor(UUID.randomUUID(), TENANT, "SYN-auditor-01",
                scope, GRANTOR, ACCEPTER, FROM, TO, paramOn);
    }

    static ExternalAccessGrant regulatorGrant(boolean paramOn) {
        return ExternalAccessGrant.forRegulator(UUID.randomUUID(), TENANT, "SYN-agencia-01",
                GRANTOR, ACCEPTER, FROM, TO, paramOn);
    }
}
