package br.com.ziipsan.platform.notification.multichannel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Porta dos alertas do teto mensal (80% e 100% de {@code msg.monthly_cap},
 * ADR-0019): o serviço multicanal EMITE o alerta na travessia EFETIVA —
 * depois que o envio que cruza o limiar foi aceito e a tentativa gravada,
 * sobre o gasto comprometido ({@code projectedSpend} já realizado; correção
 * D3 da revisão: envio bloqueado pelo teto e falha de gateway não emitem
 * alerta). Quem entrega o alerta (central de avisos da P3.36 ao responsável
 * do tenant, página inicial por perfil) é a fiação da integração —
 * pendência nomeada no relatório da ED-41.
 */
public interface CapAlertSink {
    void onCapAlert(UUID tenantId, MonthlyCapRule.CapLevel level, BigDecimal projectedSpend,
                    BigDecimal cap, Instant at);
}
