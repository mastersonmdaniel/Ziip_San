package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * E10 — Relatorio de pagamentos e remuneracao POR EMPRESA e periodo,
 * derivado das entradas de remuneracao (E9) — sem estado duplicado: as
 * somas sao calculadas das entradas no periodo entregue.
 */
public record CompanyCollectionReport(UUID tenantId, UUID companySupplierId,
                                      LocalDate periodStart, LocalDate periodEnd,
                                      int attributedPaymentCount,
                                      BigDecimal totalPayments, BigDecimal totalRemuneration) {

    public CompanyCollectionReport {
        if (tenantId == null || companySupplierId == null || periodStart == null || periodEnd == null)
            throw new ThirdPartyCollectionException("REPORT_SCOPE_REQUIRED",
                    "relatorio exige tenant, empresa e periodo declarados");
        if (periodEnd.isBefore(periodStart))
            throw new ThirdPartyCollectionException("REPORT_PERIOD_INVERTED", "periodo do relatorio invertido");
        if (attributedPaymentCount < 0 || totalPayments == null || totalRemuneration == null
                || totalPayments.signum() < 0 || totalRemuneration.signum() < 0)
            throw new ThirdPartyCollectionException("REPORT_TOTALS_INVALID", "totais do relatorio invalidos");
    }

    /** Deriva o relatorio das entradas da empresa cujo PAGAMENTO cai no periodo. */
    public static CompanyCollectionReport derive(UUID tenantId, UUID companySupplierId,
                                                 LocalDate periodStart, LocalDate periodEnd,
                                                 List<RemunerationEntry> entries,
                                                 java.util.function.Function<UUID, LocalDate> paidOnByEventId) {
        BigDecimal payments = BigDecimal.ZERO;
        BigDecimal remuneration = BigDecimal.ZERO;
        int count = 0;
        for (RemunerationEntry e : entries) {
            if (!e.tenantId().equals(tenantId) || !e.companySupplierId().equals(companySupplierId)) continue;
            LocalDate paidOn = paidOnByEventId.apply(e.paymentEventId());
            if (paidOn == null || paidOn.isBefore(periodStart) || paidOn.isAfter(periodEnd)) continue;
            count++;
            payments = payments.add(e.paymentAmount());
            remuneration = remuneration.add(e.remunerationAmount());
        }
        return new CompanyCollectionReport(tenantId, companySupplierId, periodStart, periodEnd,
                count, payments, remuneration);
    }
}
