package br.com.ziipsan.platform.engagement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Meta de consumo definida PELO USUARIO (ED-101): teto de consumo em m3 que
 * o proprio usuario fixa para a sua unidade num periodo (mes de referencia
 * do ciclo de faturamento). Nenhum valor de meta tem fabrica nem sugestao
 * neste codigo — a meta nasce exclusivamente do ato do usuario no app da
 * ED-39 ou no portal (a exibicao e fiacao de integracao; aqui vive a regra).
 * Redefinir a meta do mesmo periodo substitui a anterior (ato do usuario,
 * registrado com novo {@code definedAt}).
 */
public record ConsumptionGoal(UUID id, UUID tenantId, UUID customerId, UUID unitId,
                              YearMonth period, BigDecimal targetM3, Instant definedAt) {

    public ConsumptionGoal {
        Objects.requireNonNull(id, "id e obrigatorio");
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(customerId, "customerId e obrigatorio");
        Objects.requireNonNull(unitId, "unitId e obrigatorio");
        Objects.requireNonNull(period, "period e obrigatorio");
        Objects.requireNonNull(definedAt, "definedAt e obrigatorio");
        if (targetM3 == null || targetM3.signum() <= 0) {
            throw new EngagementValidationException("GOAL_TARGET_INVALID",
                    "meta de consumo deve ser positiva (m3): " + targetM3);
        }
    }
}
