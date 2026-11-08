package br.com.ziipsan.platform.omnichannel;

import br.com.ziipsan.platform.notification.DeliveryProofRule;
import br.com.ziipsan.platform.notification.multichannel.ChannelDelivery;
import br.com.ziipsan.platform.notification.multichannel.DeliverabilityRule;
import br.com.ziipsan.platform.notification.multichannel.UsageReportService;
import br.com.ziipsan.platform.notification.multichannel.WhatsAppCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Medição do omnichannel LIDA da mensageria da ED-41 (aceite da ED-42,
 * GES-19) — esta classe NÃO mede nada por conta própria:
 *
 * <ul>
 *   <li><b>uso por tenant/módulo:</b> é o {@link UsageReportService} da
 *       ED-41 sobre os fatos que os serviços desta entrega gravam no
 *       {@code UsageStore} dela (módulo {@code ED-42});</li>
 *   <li><b>custo por categoria de template:</b> agregação pura sobre as
 *       tentativas {@link ChannelDelivery} da ED-41 (que já carregam
 *       categoria e custo estimado) — {@link #costByTemplateCategory};</li>
 *   <li><b>e-mail com a entregabilidade por tenant da P3.5 (PLT-10):</b>
 *       a taxa de reclamação é a {@link DeliveryProofRule} da P3.5 e a
 *       entregabilidade por canal é a {@link DeliverabilityRule} da
 *       ED-41, ambas apenas CONSUMIDAS aqui para a visão do atendimento
 *       — nenhuma régua de entregabilidade é reescrita.</li>
 * </ul>
 */
public class OmnichannelUsageService {

    private final UsageReportService usageReportService;

    public OmnichannelUsageService(UsageReportService usageReportService) {
        this.usageReportService = Objects.requireNonNull(usageReportService,
                "relatório de uso da ED-41 obrigatório");
    }

    /** Uso medido do tenant no período, por módulo — leitura direta da ED-41. */
    public UsageReportService.UsageReport usage(UUID tenantId, Instant from, Instant to) {
        return usageReportService.report(tenantId, from, to);
    }

    /** Custo por categoria de template, somado das tentativas de WhatsApp da ED-41 no recorte dado. */
    public static Map<WhatsAppCategory, BigDecimal> costByTemplateCategory(List<ChannelDelivery> deliveries) {
        if (deliveries == null) {
            throw new IllegalArgumentException("as tentativas da ED-41 são obrigatórias (podem ser vazias, não nulas)");
        }
        Map<WhatsAppCategory, BigDecimal> totals = new EnumMap<>(WhatsAppCategory.class);
        for (ChannelDelivery delivery : deliveries) {
            if (delivery.whatsappCategory() != null && delivery.estimatedCost() != null) {
                totals.merge(delivery.whatsappCategory(), delivery.estimatedCost(), BigDecimal::add);
            }
        }
        return Map.copyOf(totals);
    }

    /** A taxa de reclamação do e-mail do tenant fere o teto da P3.5 (PLT-10)? Régua da P3.5, sem cópia. */
    public static boolean emailComplaintRateBreaches(long sent, long complaints) {
        return DeliveryProofRule.complaintRateBreaches(sent, complaints);
    }

    /** Entregabilidade medida de um canal do tenant — régua e coorte da ED-41, sem cópia. */
    public static DeliverabilityRule.DeliverabilitySnapshot deliverability(
            UUID tenantId, br.com.ziipsan.platform.notification.NotificationChannel channel,
            Instant from, Instant to, long sent, long delivered, long failed, long rejected) {
        return DeliverabilityRule.snapshot(tenantId, channel, from, to, sent, delivered, failed, rejected);
    }
}
