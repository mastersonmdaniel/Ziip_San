package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.NotificationChannel;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de PREÇO por mensagem dos canais pagos: o preço unitário vigente do
 * tenant para (canal, categoria de WhatsApp) vem do contrato com o provedor
 * dele — NENHUM preço é fixado neste código. Sem preço vigente, não há
 * estimativa honesta: o disparo é recusado com {@code PRICE_NOT_CONFIGURED}
 * em vez de estimar com valor presumido. Para SMS a categoria é nula.
 */
public interface ChannelPricing {
    Optional<BigDecimal> unitPrice(UUID tenantId, NotificationChannel channel, WhatsAppCategory category);
}
