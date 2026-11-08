package br.com.ziipsan.commercial.billing.servicecatalog;

import br.com.ziipsan.commercial.billing.bill.Installments;
import br.com.ziipsan.commercial.billing.math.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parcelamento de serviço (POC-31, ED-34). Reusa a regra única de parcelas do billing
 * ({@link Installments}: truncada DOWN, centavos na última; a soma é exatamente o total).
 *
 * <p>Limites vêm do parâmetro do tenant {@code credit.type_limits} (chave SERVICE da P2.5:
 * maxAmount/maxInstallments), resolvidos pelo chamador — nenhum limite é inventado aqui.
 * Total acima do máximo ou parcelas acima do máximo são recusados.
 */
public record ServiceInstallmentPlan(BigDecimal total, List<BigDecimal> installments) {
    public ServiceInstallmentPlan {
        installments = List.copyOf(installments);
        BigDecimal sum = installments.stream().reduce(Money.ZERO, BigDecimal::add);
        if (sum.compareTo(Money.roundHalfUp(total)) != 0)
            throw new IllegalArgumentException("parcelas nao somam o total do servico");
    }

    public static ServiceInstallmentPlan plan(BigDecimal total, int installments,
                                              BigDecimal maxAmount, int maxInstallments) {
        if (total == null || total.signum() <= 0) throw new IllegalArgumentException("total do servico deve ser positivo");
        if (installments < 1) throw new IllegalArgumentException("numero de parcelas deve ser pelo menos 1: " + installments);
        if (maxAmount != null && total.compareTo(maxAmount) > 0)
            throw new IllegalArgumentException("total " + total + " acima do limite credit.type_limits[SERVICE].maxAmount " + maxAmount);
        if (maxInstallments > 0 && installments > maxInstallments)
            throw new IllegalArgumentException("parcelas " + installments + " acima de credit.type_limits[SERVICE].maxInstallments " + maxInstallments);
        List<BigDecimal> parts = new ArrayList<>();
        for (int done = 0; done < installments; done++) parts.add(Installments.next(total, installments, done));
        return new ServiceInstallmentPlan(Money.roundHalfUp(total), parts);
    }
}
