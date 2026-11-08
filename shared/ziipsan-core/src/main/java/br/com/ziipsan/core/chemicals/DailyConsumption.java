package br.com.ziipsan.core.chemicals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Consumo diario de insumo quimico por unidade de tratamento (ED-89):
 * consumo por dia e por m3 tratado, com conciliacao entre o consumo
 * TEORICO da dosagem e o REAL.
 *
 * <p>DOSAGEM SEM FONTE: a dosagem ({@code dosagePerM3}, na unidade do
 * item por m3 tratado) e DADO DECLARADO do tenant/unidade em cada
 * lancamento — nenhuma dosagem, concentracao ou fator de conversao
 * vive nesta classe nem nasce de fabrica. O teorico e aritmetica pura
 * sobre o declarado: {@code dosagem x volume tratado}.
 *
 * <p>Veredito da conciliacao: sem a tolerancia do tenant
 * ({@code chem.reconciliation_tolerance_pct}, chave proposta, sem
 * fabrica), o resultado e {@link Status#REPORTED} — os valores sao
 * reportados, sem presumir conformidade nem divergencia.
 */
public record DailyConsumption(
        UUID tenantId,
        String itemCode,
        String locationCode,
        String treatmentUnitCode,
        LocalDate consumptionDate,
        BigDecimal treatedVolumeM3,
        BigDecimal dosagePerM3,
        BigDecimal actualQty) {

    public DailyConsumption {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (itemCode == null || itemCode.isBlank()) throw new IllegalArgumentException("item do consumo e obrigatorio");
        if (treatmentUnitCode == null || treatmentUnitCode.isBlank())
            throw new IllegalArgumentException("unidade de tratamento do consumo e obrigatoria");
        Objects.requireNonNull(consumptionDate, "data do consumo e obrigatoria");
        if (treatedVolumeM3 == null || treatedVolumeM3.signum() < 0)
            throw new IllegalArgumentException("volume tratado (m3) nao pode ser nulo/negativo");
        if (dosagePerM3 == null || dosagePerM3.signum() < 0)
            throw new IllegalArgumentException("dosagem declarada nao pode ser nula/negativa");
        if (actualQty == null || actualQty.signum() < 0)
            throw new IllegalArgumentException("consumo real nao pode ser nulo/negativo");
        if (treatedVolumeM3.signum() == 0 && dosagePerM3.signum() > 0)
            throw new IllegalArgumentException("dosagem declarada sem volume tratado no dia nao concilia: volume zero com dosagem positiva");
    }

    /** Consumo teorico da dosagem: dosagem declarada x volume tratado. */
    public BigDecimal theoreticalQty() {
        return dosagePerM3.multiply(treatedVolumeM3).setScale(4, RoundingMode.HALF_UP);
    }

    /** Consumo real por m3 tratado; null quando nao houve volume tratado. */
    public BigDecimal actualPerM3() {
        if (treatedVolumeM3.signum() == 0) return null;
        return actualQty.divide(treatedVolumeM3, 6, RoundingMode.HALF_UP);
    }

    public enum Status { CONCILED, DIVERGENT, REPORTED }

    public record Reconciliation(DailyConsumption day, BigDecimal theoreticalQty, BigDecimal varianceQty,
                                 BigDecimal variancePct, Status status) {}

    /**
     * Concilia UM dia. {@code tolerancePct} null (parametro ausente) =
     * REPORTED: nenhum veredito e presumido sem a tolerancia do tenant.
     */
    public Reconciliation reconcile(BigDecimal tolerancePct) {
        BigDecimal theoretical = theoreticalQty();
        BigDecimal variance = actualQty.subtract(theoretical).setScale(4, RoundingMode.HALF_UP);
        BigDecimal variancePct = theoretical.signum() == 0 ? null
                : variance.abs().divide(theoretical, 6, RoundingMode.HALF_UP).movePointRight(2).setScale(4, RoundingMode.HALF_UP);
        Status status;
        if (tolerancePct == null) status = Status.REPORTED;
        else if (variancePct == null) status = actualQty.signum() == 0 ? Status.CONCILED : Status.DIVERGENT;
        else status = variancePct.compareTo(tolerancePct) <= 0 ? Status.CONCILED : Status.DIVERGENT;
        return new Reconciliation(this, theoretical, variance, variancePct, status);
    }
}
