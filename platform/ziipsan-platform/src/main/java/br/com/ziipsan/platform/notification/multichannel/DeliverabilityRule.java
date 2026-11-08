package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.NotificationChannel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Entregabilidade medida (aceite da ED-41): taxas de entrega e de falha por
 * tenant e canal, calculadas das tentativas registradas — nunca de um
 * painel do provedor presumido. Sem envios no período, a taxa NÃO existe
 * ({@code Optional} vazio): taxa de 0 envios não é 0% nem 100%.
 *
 * <p>Calculado à mão: 200 envios, 190 entregues, 7 falhas, 3 rejeitados →
 * entrega 0,9500; falha (falhas + rejeitados) 0,0500.
 *
 * <p><b>Coorte temporal (correção D4 da revisão):</b> as contagens que
 * alimentam esta regra vêm da porta {@link ChannelDeliveryStore}, cujo
 * contrato fixa UMA base única — o {@code sentAt} (a janela seleciona a
 * coorte de envios; a situação é o estado atual de cada tentativa dessa
 * coorte). Com a coorte única, resolvidas &gt; envios é impossível por
 * construção e a guarda abaixo só dispara contra uma implementação da
 * porta que viole o contrato — ela NÃO foi afrouxada: segue recusando
 * contagens incoerentes em vez de devolver uma taxa fabricada.
 */
public final class DeliverabilityRule {

    public record DeliverabilitySnapshot(
            UUID tenantId, NotificationChannel channel, Instant from, Instant to,
            long sent, long delivered, long failed, long rejected,
            Optional<BigDecimal> deliveryRate, Optional<BigDecimal> failureRate) {
    }

    private DeliverabilityRule() {
    }

    public static DeliverabilitySnapshot snapshot(UUID tenantId, NotificationChannel channel,
                                                  Instant from, Instant to,
                                                  long sent, long delivered, long failed, long rejected) {
        if (tenantId == null || channel == null || from == null || to == null) {
            throw new IllegalArgumentException("tenant, canal e período são obrigatórios na entregabilidade");
        }
        if (sent < 0 || delivered < 0 || failed < 0 || rejected < 0) {
            throw new IllegalArgumentException("as contagens da entregabilidade não podem ser negativas");
        }
        if (delivered + failed + rejected > sent) {
            throw new IllegalArgumentException("resolvidas não podem exceder os envios do período");
        }
        if (sent == 0) {
            return new DeliverabilitySnapshot(tenantId, channel, from, to, 0, 0, 0, 0,
                    Optional.empty(), Optional.empty());
        }
        BigDecimal denominator = BigDecimal.valueOf(sent);
        BigDecimal deliveryRate = BigDecimal.valueOf(delivered).divide(denominator, 4, RoundingMode.HALF_UP);
        BigDecimal failureRate = BigDecimal.valueOf(failed + rejected).divide(denominator, 4, RoundingMode.HALF_UP);
        return new DeliverabilitySnapshot(tenantId, channel, from, to, sent, delivered, failed, rejected,
                Optional.of(deliveryRate), Optional.of(failureRate));
    }
}
