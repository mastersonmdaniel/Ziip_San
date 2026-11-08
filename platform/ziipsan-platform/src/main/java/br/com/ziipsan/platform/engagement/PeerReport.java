package br.com.ziipsan.platform.engagement;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Relatorio periodico de comparacao com pares (ED-101): o resultado de um
 * periodo para UMA unidade. Publicado, expoe SOMENTE agregados da coorte
 * (media e mediana calculadas pela ED-38), o consumo do proprio usuario e a
 * posicao dele frente a media — nunca valor individual de terceiro, nunca
 * identificacao de outro domicilio, nunca o tamanho exato do grupo alem do
 * minimo exigido ({@code minGroupSize} e parametro do tenant, nao dado de
 * terceiro). Suprimido, nao carrega agregado nenhum.
 */
public record PeerReport(UUID tenantId, UUID unitId, YearMonth period, String groupKey,
                         boolean published, Suppression suppression, int minGroupSize,
                         BigDecimal cohortAverageM3, BigDecimal cohortMedianM3,
                         BigDecimal ownConsumptionM3, String position) {

    public enum Suppression {
        FEATURE_DISABLED,
        PEER_COMPARISON_DISABLED,
        BELOW_MIN_GROUP_SIZE
    }

    static PeerReport suppressed(UUID tenantId, UUID unitId, YearMonth period, String groupKey,
                                 Suppression suppression, int minGroupSize, BigDecimal ownM3) {
        return new PeerReport(tenantId, unitId, period, groupKey, false, suppression,
                minGroupSize, null, null, ownM3, null);
    }

    static PeerReport published(UUID tenantId, UUID unitId, YearMonth period, String groupKey,
                                int minGroupSize, BigDecimal avg, BigDecimal median,
                                BigDecimal ownM3, String position) {
        return new PeerReport(tenantId, unitId, period, groupKey, true, null,
                minGroupSize, avg, median, ownM3, position);
    }
}
