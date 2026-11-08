package br.com.ziipsan.commercial.billing.largeuser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Contrato especial de grande usuario (ED-22; NR 11/ANA, art. 19):
 * volume contratado e minimo, tarifa negociada, vigencia e renovacao,
 * reajuste, penalidade por excesso, pontos de medicao dedicados e
 * aprovacao pela agencia. A <b>venda de agua tratada em bloco</b> a outro
 * prestador e uma modalidade deste mesmo contrato
 * ({@link Modality#BULK_TREATED_WATER_SALE}).
 *
 * <p>Nenhum valor e presumido: tarifa, volumes, percentual de penalidade
 * e indice de reajuste vem do contrato/parametro do tenant
 * ({@code large_user.*}); percentual divergente entre contrato e
 * parametro e recusado pelo servico de calculo.
 *
 * @param contractedVolumeM3 volume contratado, em m3 por competencia
 * @param minimumVolumeM3    volume minimo faturavel, em m3 (<= contratado)
 * @param negotiatedTariff   tarifa negociada, por m3
 * @param validFrom          inicio da vigencia
 * @param validTo            fim da vigencia; nulo = vigencia aberta (renovacao)
 * @param autoRenew          renovacao automatica ao fim da vigencia
 * @param readjustmentIndex  indice/regra de reajuste declarado no contrato (texto; nao inventado)
 * @param excessPenaltyPercent percentual de penalidade sobre o volume excedente ao contratado
 * @param meteringPointIds   pontos de medicao dedicados (ao menos 1)
 * @param agencyApproved     aprovacao pela agencia registrada
 * @param modality           modalidade do contrato especial
 */
public record LargeUserContract(
        UUID id,
        UUID unitId,
        Modality modality,
        int contractedVolumeM3,
        int minimumVolumeM3,
        BigDecimal negotiatedTariff,
        LocalDate validFrom,
        LocalDate validTo,
        boolean autoRenew,
        String readjustmentIndex,
        BigDecimal excessPenaltyPercent,
        List<UUID> meteringPointIds,
        boolean agencyApproved) {

    /** Modalidades do contrato especial. */
    public enum Modality {
        /** Grande usuario comum (NR 11, art. 19). */
        LARGE_USER,
        /** Venda de agua tratada em bloco a outro prestador. */
        BULK_TREATED_WATER_SALE
    }

    public LargeUserContract {
        if (id == null || unitId == null || modality == null) {
            throw new IllegalArgumentException("contrato especial exige id, unidade e modalidade");
        }
        if (contractedVolumeM3 < 1) {
            throw new IllegalArgumentException("volume contratado deve ser ao menos 1 m3: " + contractedVolumeM3);
        }
        if (minimumVolumeM3 < 0 || minimumVolumeM3 > contractedVolumeM3) {
            throw new IllegalArgumentException("volume minimo fora de 0..volume contratado: " + minimumVolumeM3);
        }
        if (negotiatedTariff == null || negotiatedTariff.signum() < 0) {
            throw new IllegalArgumentException("tarifa negociada invalida: " + negotiatedTariff);
        }
        if (validFrom == null) {
            throw new IllegalArgumentException("contrato especial exige inicio de vigencia");
        }
        if (validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalArgumentException("fim de vigencia anterior ao inicio");
        }
        if (readjustmentIndex == null || readjustmentIndex.isBlank()) {
            throw new IllegalArgumentException("contrato especial exige a regra de reajuste declarada");
        }
        if (excessPenaltyPercent == null || excessPenaltyPercent.signum() < 0
                || excessPenaltyPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("penalidade por excesso fora de 0..100: " + excessPenaltyPercent);
        }
        if (meteringPointIds == null || meteringPointIds.isEmpty()) {
            throw new IllegalArgumentException("contrato especial exige ao menos 1 ponto de medicao dedicado");
        }
        meteringPointIds = List.copyOf(meteringPointIds);
    }

    /** O contrato vale na data? Vigencia aberta vale enquanto nao encerrado. */
    public boolean activeOn(LocalDate date) {
        return date != null && !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }
}
