package br.com.ziipsan.commercial.billing.bill;

import java.math.BigDecimal;

/** Imposto retido na conta. */
public record TaxLine(String code, BigDecimal ratePct, BigDecimal base, BigDecimal amount, int order) {
}
