package br.com.ziipsan.platform.engagement;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Adesao voluntaria do usuario ao modulo de engajamento (ED-101): o aceite
 * exige adesao voluntaria — sem registro de adesao ativa, nao ha meta,
 * relatorio de pares nem gamificacao para aquele usuario/unidade. A
 * revogacao ({@code optedOutAt}) encerra a adesao sem apagar o historico:
 * relatorios ja gerados nao sao reescritos, mas nenhum novo e produzido.
 */
public record UserEnrollment(UUID tenantId, UUID customerId, UUID unitId,
                             Instant optedInAt, Instant optedOutAt) {

    public UserEnrollment {
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(customerId, "customerId e obrigatorio");
        Objects.requireNonNull(unitId, "unitId e obrigatorio");
        Objects.requireNonNull(optedInAt, "optedInAt e obrigatorio");
        if (optedOutAt != null && optedOutAt.isBefore(optedInAt)) {
            throw new EngagementValidationException("ENROLLMENT_PERIOD_INVALID",
                    "optedOutAt anterior a optedInAt");
        }
    }

    public static UserEnrollment optIn(UUID tenantId, UUID customerId, UUID unitId, Instant at) {
        return new UserEnrollment(tenantId, customerId, unitId, at, null);
    }

    public UserEnrollment optOut(Instant at) {
        Objects.requireNonNull(at, "instante da revogacao e obrigatorio");
        return new UserEnrollment(tenantId, customerId, unitId, optedInAt, at);
    }

    public boolean active() {
        return optedOutAt == null;
    }
}
