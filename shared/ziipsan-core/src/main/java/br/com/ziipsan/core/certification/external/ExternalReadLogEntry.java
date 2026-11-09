package br.com.ziipsan.core.certification.external;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de UMA leitura de observador externo (ED-75: "registro de
 * cada leitura"). Cada tentativa de leitura pelo
 * {@link ExternalAccessService} gera exatamente uma linha desta —
 * concedida ou negada — com concessao, perfil, recurso, referencia
 * do recurso, instante e desfecho. A negacao tambem se registra: o
 * rastro de quem tentou ler fora do escopo faz parte da evidencia de
 * auditoria.
 */
public record ExternalReadLogEntry(UUID grantId, UUID tenantId, ExternalProfile profile,
                                   ExternalResource resource, String resourceRef,
                                   Instant readAt, Outcome outcome, String denialReason) {

    /** Desfecho da leitura registrada. */
    public enum Outcome {
        GRANTED,
        DENIED
    }

    public ExternalReadLogEntry {
        Objects.requireNonNull(grantId, "grantId e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(profile, "profile e obrigatorio");
        Objects.requireNonNull(resource, "resource e obrigatorio");
        if (resourceRef == null || resourceRef.isBlank()) {
            throw new IllegalArgumentException("resourceRef (referencia do recurso lido) e "
                    + "obrigatoria: leitura sem recurso identificado nao se registra");
        }
        resourceRef = resourceRef.trim();
        Objects.requireNonNull(readAt, "readAt e obrigatorio");
        Objects.requireNonNull(outcome, "outcome e obrigatorio");
        if (outcome == Outcome.DENIED && (denialReason == null || denialReason.isBlank())) {
            throw new IllegalArgumentException("leitura negada exige denialReason: o registro "
                    + "de auditoria declara por que a leitura foi recusada");
        }
        if (outcome == Outcome.GRANTED && denialReason != null) {
            throw new IllegalArgumentException("leitura concedida nao carrega denialReason");
        }
    }
}
