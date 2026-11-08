package br.com.ziipsan.platform.notification.multichannel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Um fato de uso medido: quanto um módulo consumiu, de qual métrica, em
 * qual tenant, com o custo quando ele existe ({@code cost} nulo = métrica
 * medida por contagem, sem custo atribuído — ex.: e-mail e push nesta
 * entrega). {@code moduleCode} é o código do módulo produtor declarado por
 * quem mede (faturamento, atendimento, telemetria…); {@code referenceId} é
 * a referência do fato (ex.: o id da tentativa de entrega), quando houver.
 * É a base da franquia/repasse da ED-10 e da medição da ED-42.
 */
public record UsageRecord(UUID tenantId, String moduleCode, UsageMetric metric, long quantity,
                          BigDecimal cost, UUID referenceId, Instant occurredAt) {

    public UsageRecord {
        if (tenantId == null || moduleCode == null || moduleCode.isBlank() || metric == null
                || occurredAt == null) {
            throw new IllegalArgumentException("uso medido exige tenant, módulo, métrica e instante");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("a quantidade do uso medido precisa ser positiva");
        }
        if (cost != null && cost.signum() < 0) {
            throw new IllegalArgumentException("o custo do uso medido não pode ser negativo");
        }
    }
}
