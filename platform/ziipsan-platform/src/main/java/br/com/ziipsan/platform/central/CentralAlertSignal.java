package br.com.ziipsan.platform.central;

import java.util.Objects;
import java.util.UUID;

/**
 * Sinal de alerta avaliado pelas regras da P2.11: a regra que disparou,
 * o tenant, a métrica, o valor observado e o limiar comparado. É a
 * entrada da abertura de {@code platform.alert_event} pela fiação da
 * Central (que deduplica pelo índice único parcial da V9_1: um alerta
 * aberto por regra e tenant). Avaliar não é persistir: este tipo não
 * escreve no banco.
 *
 * <p><b>Grupo (correção pós-revisão M-2):</b> {@code groupId} carrega o
 * grupo de faturamento do item obrigatório vencido mais antigo quando a
 * regra é a do ciclo ({@code central.billing.cycle_overdue}) — é o que
 * o passo 1 do RB-07 precisa identificar ("a atividade vencida mais
 * antiga <b>e o grupo</b>"). Na regra da fila fiscal o conceito não se
 * aplica (a fila é por tenant, sem grupo) e o campo é {@code null} —
 * ausência declarada aqui, não omissão.
 */
public record CentralAlertSignal(String ruleKey, String metricKey, UUID tenantId, UUID groupId,
                                 long observedValue, long threshold, String detail) {
    public CentralAlertSignal {
        if (ruleKey == null || ruleKey.isBlank()) {
            throw new IllegalArgumentException("rule_key é obrigatório no sinal de alerta");
        }
        if (metricKey == null || metricKey.isBlank()) {
            throw new IllegalArgumentException("metric_key é obrigatório no sinal de alerta");
        }
        Objects.requireNonNull(tenantId, "tenant_id é obrigatório no sinal de alerta (escopo TENANT)");
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail é obrigatório no sinal de alerta");
        }
    }
}
