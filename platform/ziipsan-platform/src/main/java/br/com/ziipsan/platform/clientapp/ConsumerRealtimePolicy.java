package br.com.ziipsan.platform.clientapp;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Consumo quase em tempo real no app do cliente (ED-39, sobre a ED-17 —
 * GES-27): a curva horaria da telemetria chega ao consumidor pelas
 * MESMAS regras da ED-17, sem regra nova inventada aqui:
 *
 * <ul>
 *   <li>a curva so e visivel ao consumidor com o parametro do tenant
 *       ligado ({@code HourlyProfileAccessRule.visibleToCustomer} da
 *       ED-17, aqui pela porta {@link ClientAppParameters}); desligado,
 *       o app mostra o consumo diario/faturado, nao a curva;</li>
 *   <li>dado exibido sempre com o instante da leitura e o estado
 *       (medido/estimado — {@code DataState} da ED-17); leitura com mais
 *       de {@link #STALE_AFTER} e rotulada desatualizada no app, nunca
 *       apresentada como "agora";</li>
 *   <li>a atualizacao no app e por invalidacao via push (payload sem
 *       dado pessoal, {@link PushPayloadSanitizer}) ou por polling com
 *       piso de {@link #MIN_POLL_INTERVAL} — nenhum laco mais rapido
 *       e permitido ao cliente.</li>
 * </ul>
 */
public final class ConsumerRealtimePolicy {

    /** Piso de polling do app: abaixo disto o cliente nao atualiza. */
    public static final Duration MIN_POLL_INTERVAL = Duration.ofSeconds(60);

    /** Acima desta idade, a leitura e rotulada desatualizada. */
    public static final Duration STALE_AFTER = Duration.ofHours(2);

    private final ClientAppParameters parameters;

    public ConsumerRealtimePolicy(ClientAppParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parametros sao obrigatorios");
        }
        this.parameters = parameters;
    }

    /** A curva horaria pode ser mostrada a este tenant no app? */
    public boolean hourlyCurveVisible(java.util.UUID tenantId) {
        return parameters.hourlyProfileVisibleToCustomer(tenantId);
    }

    /** O intervalo pedido pelo cliente, elevado ao piso. */
    public static Duration effectivePollInterval(Duration requested) {
        if (requested == null || requested.isNegative() || requested.isZero()) {
            throw new IllegalArgumentException("intervalo de atualizacao deve ser positivo");
        }
        return requested.compareTo(MIN_POLL_INTERVAL) < 0 ? MIN_POLL_INTERVAL : requested;
    }

    /** A leitura esta desatualizada para exibicao como quase tempo real? */
    public static boolean isStale(OffsetDateTime readingAt, OffsetDateTime now) {
        if (readingAt == null || now == null) {
            throw new IllegalArgumentException("instantes da leitura e de agora sao obrigatorios");
        }
        return Duration.between(readingAt, now).compareTo(STALE_AFTER) > 0;
    }
}
