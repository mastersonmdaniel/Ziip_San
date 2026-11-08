package br.com.ziipsan.integration.hub;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * P3.37: registro de log de uma chamada de integração — correlação por
 * {@code correlation_id} e carga SEMPRE mascarada ({@link PayloadMasker}):
 * a fábrica {@link #of} mascara na construção, de modo que não existe
 * caminho para montar um registro com a carga crua. A gravação (appender,
 * tabela, trilha) é da fiação; esta é a forma que ela recebe.
 */
public record IntegrationCallLog(UUID tenantId, String integrationName, String operation,
                                 String correlationId, IntegrationMode mode,
                                 String maskedPayloadJson, String outcome, Instant at) {

    public IntegrationCallLog {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório no log de integração");
        IntegrationParameters.requireValidName(integrationName);
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlation_id é obrigatório no log de integração");
        }
        Objects.requireNonNull(mode, "modo é obrigatório no log de integração");
        Objects.requireNonNull(maskedPayloadJson, "carga mascarada é obrigatória no log de integração");
        if (outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException("desfecho é obrigatório no log de integração");
        }
        Objects.requireNonNull(at, "instante é obrigatório no log de integração");
    }

    /** Monta o registro mascarando a carga crua — único caminho de construção usado pelo hub. */
    public static IntegrationCallLog of(UUID tenantId, String integrationName, String operation,
                                        String correlationId, IntegrationMode mode,
                                        String rawPayloadJson, String outcome, Instant at) {
        return new IntegrationCallLog(tenantId, integrationName, operation, correlationId, mode,
                PayloadMasker.maskForLog(rawPayloadJson), outcome, at);
    }
}
