package br.com.ziipsan.commercial.collection.dunning.leveled;

import br.com.ziipsan.commercial.collection.dunning.DunningParameters;
import br.com.ziipsan.core.api.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Fatura nivelada opcional e acordo por capacidade de pagamento
 * (CAD-104): a fatura nivelada cobra a MEDIA dos valores recentes com
 * ACERTO PERIODICO da diferenca acumulada; o acordo por capacidade —
 * para baixa renda FORA da tarifa social — fixa parcelas pela renda
 * declarada, sem consumir alem do que resta apos o MINIMO EXISTENCIAL
 * (parametro versionado, nunca constante aqui).
 */
public final class LeveledBilling {

    private LeveledBilling() {
    }

    /** Media nivelada sobre os valores informados (historico do chamador). */
    public static BigDecimal leveledAmount(List<BigDecimal> recentBillAmounts) {
        if (recentBillAmounts == null || recentBillAmounts.isEmpty()) {
            throw new IllegalArgumentException("fatura nivelada exige historico de valores");
        }
        BigDecimal sum = recentBillAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(recentBillAmounts.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Acerto periodico: diferenca entre o total REAL do periodo e o total
     * NIVELADO ja cobrado. Positiva = o titular deve a diferenca;
     * negativa = credito dele no acerto.
     */
    public static BigDecimal periodicSettlement(List<BigDecimal> actualAmounts,
                                                List<BigDecimal> leveledChargedAmounts) {
        if (actualAmounts == null || leveledChargedAmounts == null
                || actualAmounts.size() != leveledChargedAmounts.size()) {
            throw new IllegalArgumentException("acerto exige series real e nivelada do mesmo periodo");
        }
        BigDecimal actual = actualAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal charged = leveledChargedAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return actual.subtract(charged);
    }

    /**
     * Capacidade mensal de pagamento: renda declarada menos o minimo
     * existencial vigente. Renda igual ou abaixo do minimo = capacidade
     * zero (nenhuma parcela pode ser fixada).
     */
    public static BigDecimal paymentCapacity(BigDecimal declaredIncome, DunningParameters params) {
        if (declaredIncome == null || declaredIncome.signum() < 0) {
            throw new IllegalArgumentException("renda declarada e obrigatoria e nao negativa");
        }
        BigDecimal minimum = params.existentialMinimum();
        BigDecimal capacity = declaredIncome.subtract(minimum);
        return capacity.signum() < 0 ? BigDecimal.ZERO : capacity;
    }

    /**
     * Parcela maxima do acordo por capacidade ({@code inst.income_based}):
     * sem o parametro ligado, a modalidade nao existe; com capacidade
     * zero, recusa; a parcela proposta nao pode exceder a capacidade.
     */
    public static BigDecimal requireIncomeBasedInstallment(BigDecimal declaredIncome,
                                                           BigDecimal proposedInstallment,
                                                           DunningParameters params) {
        if (!params.incomeBasedEnabled()) {
            throw new ApiException.Validation(
                    "Acordo por capacidade de pagamento desligado neste tenant ("
                            + DunningParameters.INCOME_BASED + ").", List.of(), List.of());
        }
        BigDecimal capacity = paymentCapacity(declaredIncome, params);
        if (capacity.signum() == 0) {
            throw new ApiException.Validation(
                    "Renda declarada nao supera o minimo existencial vigente: "
                            + "nenhuma parcela por capacidade pode ser fixada.", List.of(), List.of());
        }
        if (proposedInstallment == null || proposedInstallment.signum() <= 0
                || proposedInstallment.compareTo(capacity) > 0) {
            throw new ApiException.Validation(
                    "Parcela proposta excede a capacidade de pagamento (" + capacity + ").",
                    List.of(), List.of());
        }
        return proposedInstallment;
    }
}
