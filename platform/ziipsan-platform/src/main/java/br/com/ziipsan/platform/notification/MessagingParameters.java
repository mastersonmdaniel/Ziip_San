package br.com.ziipsan.platform.notification;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Porta para os parâmetros de mensageria e de alerta do tenant:
 *
 * <ul>
 *   <li>{@code msg.email_from_domain} (classe d) — domínio de envio DO tenant,
 *       com SPF, DKIM e DMARC alinhados a ele;</li>
 *   <li>{@code msg.suppression_policy} (classe b) — política de supressão do tenant;</li>
 *   <li>{@code msg.channels_enabled} (classe d) — canais ligados do tenant;</li>
 *   <li>{@code alert.high_consumption_pct} — percentual acima da média que dispara
 *       o alerta preventivo de consumo anormal (a média é a da crítica da P2.5);</li>
 *   <li>{@code alert.channels} (classe d) — canais do alerta preventivo.</li>
 * </ul>
 *
 * Ausência de valor vigente é erro explícito da implementação (nunca padrão
 * inventado aqui), como nas demais portas de parâmetros do projeto.
 *
 * <p><b>Registro no catálogo:</b> o aceite da P3.5 não reserva versão Flyway para
 * estas chaves e o mapa do plano (§3) também não — as definições no catálogo
 * global de parâmetros entram quando houver versão reservada para a tarefa
 * (declaração no relatório da entrega); esta porta já fixa as chaves e as classes.
 */
public interface MessagingParameters {

    String emailFromDomain(UUID tenantId);

    SuppressionPolicy suppressionPolicy(UUID tenantId);

    Set<NotificationChannel> channelsEnabled(UUID tenantId);

    BigDecimal highConsumptionPct(UUID tenantId);

    Set<NotificationChannel> alertChannels(UUID tenantId);
}
