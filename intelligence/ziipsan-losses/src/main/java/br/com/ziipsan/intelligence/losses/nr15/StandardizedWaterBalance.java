package br.com.ziipsan.intelligence.losses.nr15;

import br.com.ziipsan.intelligence.losses.dmc.WaterBalanceCalculator;

import java.util.Objects;
import java.util.UUID;

/**
 * Balanco hidrico padronizado anual na base do SINISA (ED-78; NR 15,
 * art. 4o e Anexo I): a foto anual do prestador, montada SOMENTE por
 * soma das fotos mensais por DMC da ED-85 ({@link
 * Nr15BalanceAssembler}) - sem calculo proprio de balanco.
 *
 * <p>Componentes (todos em m3, somas das colunas homonimas de
 * {@code loss_monthly_balances} da ED-85):
 * <ul>
 *   <li>{@code systemInputM3} - volume de entrada do sistema no ano
 *       (a variavel {@code AGUA.VOLUME_PRODUZIDO} do mapa da P4.2:
 *       "volume de entrada do sistema");</li>
 *   <li>{@code exportedM3} - agua tratada exportada em bloco (OPE-05
 *       da ED-85), excluidada entrada liquida pela ED-85;</li>
 *   <li>{@code netInputM3} - entrada liquida (disponibilizado);</li>
 *   <li>faturado micromedido ajustado (JOR-34), faturado nao
 *       micromedido, faturado total, autorizado nao faturado e
 *       consumo autorizado;</li>
 *   <li>perdas totais e agua nao faturada (somas); perdas aparentes
 *       e reais sao somas ANULAVEIS: se qualquer foto mensal as
 *       desconhece, o ano as desconhece (null, nunca zero).</li>
 * </ul>
 *
 * <p>{@code producedVolumeP42M3} e o volume produzido LIDO DA P4.2
 * pela {@link ProducedVolumePort}; {@code producedDivergenceM3} =
 * produzido da P4.2 - entrada somada da ED-85, gravado as claras: e o
 * termometro de cobertura dos DMCs (DMC faltante aparece aqui, nao e
 * escondido). A declaracao anual exige divergencia reconciliada
 * ({@link Nr15DeclarationService}).
 *
 * <p>{@code confidence} e o elo mais fraco das fotos do ano (metodo
 * interno declarado: o ano nao e mais confiavel que seu mes menos
 * confiavel) - nao e grau de norma.
 *
 * <p>A apresentacao na matriz do Anexo I usa mil m3 ({@link
 * Nr15NormCatalog#toMatrixUnit}); os valores guardados sao m3.
 */
public record StandardizedWaterBalance(
        UUID tenantId,
        int baseYear,
        int dmcCount,
        int snapshotCount,
        double systemInputM3,
        double exportedM3,
        double netInputM3,
        double billedMeteredM3,
        double billedUnmeteredM3,
        double billedTotalM3,
        double authorizedUnbilledM3,
        double authorizedConsumptionM3,
        double totalLossesM3,
        Double apparentLossesM3OrNull,
        Double realLossesM3OrNull,
        double nonRevenueWaterM3,
        double nonRevenuePercent,
        double producedVolumeP42M3,
        double producedDivergenceM3,
        WaterBalanceCalculator.ConfidenceGrade confidence) {

    public StandardizedWaterBalance {
        Objects.requireNonNull(tenantId, "tenant do balanco padronizado obrigatorio");
        Objects.requireNonNull(confidence, "grau de confianca do balanco obrigatorio");
        if (baseYear < 2000 || baseYear > 2200) throw new IllegalArgumentException("ano-base invalido: " + baseYear);
        if (dmcCount <= 0) throw new IllegalArgumentException("balanco padronizado sem DMC");
        if (snapshotCount <= 0) throw new IllegalArgumentException("balanco padronizado sem foto da ED-85");
        requireNonNegative(systemInputM3, "volume de entrada");
        requireNonNegative(exportedM3, "volume exportado");
        requireNonNegative(netInputM3, "entrada liquida");
        requireNonNegative(billedTotalM3, "faturado total");
        requireNonNegative(authorizedConsumptionM3, "consumo autorizado");
        requireNonNegative(nonRevenueWaterM3, "agua nao faturada");
        requireNonNegative(producedVolumeP42M3, "volume produzido da P4.2");
        if (Double.isNaN(totalLossesM3) || Double.isNaN(producedDivergenceM3))
            throw new IllegalArgumentException("balanco padronizado com NaN");
        if (apparentLossesM3OrNull != null) requireNonNegative(apparentLossesM3OrNull, "perdas aparentes");
        // Perda real agregada e soma de residuos da ED-85: negativa e
        // possivel (aparentes > totais) e fica visivel, nao recusada.
        if (realLossesM3OrNull != null && Double.isNaN(realLossesM3OrNull))
            throw new IllegalArgumentException("perdas reais NaN");
    }

    private static void requireNonNegative(double value, String what) {
        if (value < 0 || Double.isNaN(value)) throw new IllegalArgumentException(what + " invalido: " + value);
    }

    /** Volume de entrada na unidade de apresentacao da matriz do Anexo I (mil m3/ano). */
    public double systemInputMatrixUnit() {
        return Nr15NormCatalog.toMatrixUnit(systemInputM3);
    }

    /** A divergencia com o volume produzido da P4.2 esta reconciliada? */
    public boolean producedReconciled() {
        return Math.abs(producedDivergenceM3) <= Nr15Reconciler.TOLERANCE_M3;
    }
}
