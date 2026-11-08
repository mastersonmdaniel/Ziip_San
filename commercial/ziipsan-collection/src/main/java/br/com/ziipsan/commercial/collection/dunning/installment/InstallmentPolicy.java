package br.com.ziipsan.commercial.collection.dunning.installment;

import br.com.ziipsan.commercial.collection.dunning.DunningParameters;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Perfil de parcelamento VERSIONADO (DAD-21: o acordo grava o
 * {@code policy_version_id}). Os limites vem dos parametros {@code inst.*}
 * aceitos do tenant (P0.19) — nenhum limite e constante neste codigo.
 */
public record InstallmentPolicy(
        UUID policyVersionId,
        BigDecimal minDownPaymentPct,
        BigDecimal minDownPayment,
        int entryDueDays,
        int maxInstallments,
        BigDecimal minInstallment,
        BigDecimal maxDiscountPct,
        BigDecimal principalDiscountLimitPct) {

    public InstallmentPolicy {
        if (policyVersionId == null) {
            throw new IllegalArgumentException("policy_version_id e obrigatorio (perfil versionado, DAD-21)");
        }
        if (minDownPaymentPct == null || minDownPayment == null || minInstallment == null
                || maxDiscountPct == null || principalDiscountLimitPct == null) {
            throw new IllegalArgumentException("limites do perfil de parcelamento sao obrigatorios");
        }
        if (maxInstallments < 1) {
            throw new IllegalArgumentException("maximo de parcelas deve ser positivo");
        }
        if (entryDueDays < 0) {
            throw new IllegalArgumentException("prazo minimo do vencimento da entrada nao pode ser negativo");
        }
    }

    /** Constroi o perfil a partir dos parametros {@code inst.*} vigentes (ausencia = erro explicito). */
    public static InstallmentPolicy fromParameters(UUID policyVersionId, DunningParameters params) {
        return new InstallmentPolicy(
                policyVersionId,
                params.requiredDecimal(DunningParameters.MIN_DOWN_PAYMENT_PCT),
                params.requiredDecimal(DunningParameters.MIN_DOWN_PAYMENT),
                params.requiredInt(DunningParameters.ENTRY_DUE_DAYS),
                params.requiredInt(DunningParameters.MAX_INSTALLMENTS),
                params.requiredDecimal(DunningParameters.MIN_INSTALLMENT),
                params.requiredDecimal(DunningParameters.MAX_DISCOUNT_PCT),
                params.requiredDecimal(DunningParameters.PRINCIPAL_DISCOUNT_LIMIT_PCT));
    }

    /** Entrada minima em R$: o maior entre o percentual do total e o minimo absoluto. */
    public BigDecimal minimumDownPayment(BigDecimal totalDebt) {
        BigDecimal byPct = totalDebt.multiply(minDownPaymentPct)
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        return byPct.max(minDownPayment);
    }
}
