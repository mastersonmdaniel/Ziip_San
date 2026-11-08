package br.com.ziipsan.commercial.billing.largeuser;

import br.com.ziipsan.commercial.billing.special.SpecialSituationParameters;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Calculo do contrato especial de grande usuario (ED-22):
 *
 * <pre>
 *   base      = max(volume medido, volume minimo)   [se large_user.min_volume_required;
 *                                                    desligado, base = volume medido]
 *   valorBase = base x tarifa negociada
 *   excesso   = max(0, volume medido - volume contratado)
 *   penalidade= excesso x tarifa negociada x percentual/100
 *   total     = valorBase + penalidade
 * </pre>
 *
 * O percentual aplicado e o do contrato, conferido contra
 * {@code large_user.excess_penalty_percent}: divergencia e recusada
 * (nunca se escolhe um dos dois em silencio). Sem aprovacao da agencia,
 * quando {@code large_user.agency_approval_required} esta ligado, o
 * contrato nao fatura.
 */
public final class LargeUserBillingCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** Resultado itemizado do calculo (para a fatura e para o relatorio). */
    public record Result(int measuredVolumeM3, int billedBaseVolumeM3, int excessVolumeM3,
                         BigDecimal baseValue, BigDecimal penaltyValue, BigDecimal total) {
    }

    private LargeUserBillingCalculator() {
    }

    public static Result calculate(LargeUserContract contract, int measuredVolumeM3, LocalDate onDate,
                                   SpecialSituationParameters parameters) {
        if (contract == null || parameters == null || onDate == null) {
            throw new IllegalArgumentException("contrato, data e parametros sao obrigatorios");
        }
        if (measuredVolumeM3 < 0) {
            throw new IllegalArgumentException("volume medido negativo: " + measuredVolumeM3);
        }
        if (!contract.activeOn(onDate)) {
            throw new IllegalArgumentException("contrato especial fora da vigencia em " + onDate);
        }
        if (parameters.largeUserAgencyApprovalRequired() && !contract.agencyApproved()) {
            throw new IllegalArgumentException(
                    "contrato especial sem aprovacao da agencia nao fatura (large_user.agency_approval_required)");
        }
        BigDecimal parameterPercent = parameters.largeUserExcessPenaltyPercent();
        if (contract.excessPenaltyPercent().compareTo(parameterPercent) != 0) {
            throw new IllegalArgumentException("penalidade divergente: contrato " + contract.excessPenaltyPercent()
                    + " x parametro large_user.excess_penalty_percent " + parameterPercent);
        }
        int base = parameters.largeUserMinVolumeRequired()
                ? Math.max(measuredVolumeM3, contract.minimumVolumeM3())
                : measuredVolumeM3;
        int excess = Math.max(0, measuredVolumeM3 - contract.contractedVolumeM3());
        BigDecimal baseValue = contract.negotiatedTariff().multiply(BigDecimal.valueOf(base))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal penaltyValue = contract.negotiatedTariff().multiply(BigDecimal.valueOf(excess))
                .multiply(contract.excessPenaltyPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return new Result(measuredVolumeM3, base, excess, baseValue, penaltyValue,
                baseValue.add(penaltyValue));
    }

    /**
     * Relatorio dos contratos a agencia (ED-22): uma linha por contrato,
     * em CSV (delimitador ';'), so quando {@code large_user.report_enabled}
     * esta ligado. Sem cabecalho inventado de valores: so os fatos do
     * contrato.
     */
    public static String agencyReportCsv(List<LargeUserContract> contracts,
                                         SpecialSituationParameters parameters) {
        if (!parameters.largeUserReportEnabled()) {
            throw new IllegalArgumentException("relatorio a agencia desligado (large_user.report_enabled)");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("contrato;unidade;modalidade;volume_contratado_m3;volume_minimo_m3;tarifa_negociada;"
                + "vigencia_de;vigencia_ate;renovacao_automatica;reajuste;penalidade_excesso_percent;"
                + "pontos_medicao;aprovado_agencia\n");
        for (LargeUserContract c : contracts) {
            sb.append(c.id()).append(';')
                    .append(c.unitId()).append(';')
                    .append(c.modality()).append(';')
                    .append(c.contractedVolumeM3()).append(';')
                    .append(c.minimumVolumeM3()).append(';')
                    .append(c.negotiatedTariff().toPlainString()).append(';')
                    .append(c.validFrom()).append(';')
                    .append(c.validTo() == null ? "" : c.validTo()).append(';')
                    .append(c.autoRenew()).append(';')
                    .append(c.readjustmentIndex()).append(';')
                    .append(c.excessPenaltyPercent().toPlainString()).append(';')
                    .append(c.meteringPointIds().size()).append(';')
                    .append(c.agencyApproved()).append('\n');
        }
        return sb.toString();
    }
}
