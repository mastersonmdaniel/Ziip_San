package br.com.ziipsan.core.regulatory.export;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de envio (ED-97, RAS-08): a evidencia LOCAL de que um pacote
 * foi validado e gerado para envio assistido, com o hash do pacote.
 * No modo assistido do ADR-0004 o envio fisico e do tenant: o estado
 * nasce GENERATED_VALIDATED e so vira SUBMITTED quando o tenant declara
 * o protocolo/confirmacao da agencia — este codigo nao finge envio.
 * A agenda da ED-98 le esta evidencia pelo {@link #evidenceRef()}.
 */
public record SubmissionRecord(UUID id, UUID tenantId, String agencyCode, String profileKey,
                               String layoutVersion, String periodRef, ExportPackage.Kind kind,
                               String packageSha256Hex, Status status, Instant validatedAt,
                               String agencyProtocol) {

    public enum Status { GENERATED_VALIDATED, SUBMITTED_BY_TENANT }

    public SubmissionRecord {
        Objects.requireNonNull(id); Objects.requireNonNull(tenantId);
        Objects.requireNonNull(agencyCode); Objects.requireNonNull(profileKey);
        Objects.requireNonNull(layoutVersion); Objects.requireNonNull(periodRef);
        Objects.requireNonNull(kind); Objects.requireNonNull(packageSha256Hex);
        Objects.requireNonNull(status); Objects.requireNonNull(validatedAt);
        if (!packageSha256Hex.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("hash do pacote invalido");
        if (status == Status.SUBMITTED_BY_TENANT && (agencyProtocol == null || agencyProtocol.isBlank()))
            throw new IllegalArgumentException("envio pelo tenant exige protocolo da agencia");
    }

    /** Referencia estavel da evidencia, lida pela agenda da ED-98 (RAS-08). */
    public String evidenceRef() {
        return "ED97-EVIDENCE:" + agencyCode + ":" + periodRef + ":" + packageSha256Hex.substring(0, 16);
    }

    public SubmissionRecord markSubmittedByTenant(String protocol, Instant when) {
        Objects.requireNonNull(when);
        return new SubmissionRecord(id, tenantId, agencyCode, profileKey, layoutVersion, periodRef,
                kind, packageSha256Hex, Status.SUBMITTED_BY_TENANT, validatedAt, protocol);
    }
}
