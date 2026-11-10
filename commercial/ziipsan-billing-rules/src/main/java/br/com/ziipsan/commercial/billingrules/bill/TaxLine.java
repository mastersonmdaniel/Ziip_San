package br.com.ziipsan.commercial.billingrules.bill;

import java.math.BigDecimal;

/** Imposto retido na conta. */
public record TaxLine(String code, BigDecimal ratePct, BigDecimal base, BigDecimal amount, int order) {
}
