package br.com.ziipsan.commercial.billingrules.tariff;

import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Parte do período cobrada por uma versão de tarifa.
 *
 * @param factor fração do período (4 casas) que esta versão responde; 1,0000 quando o período inteiro
 *               cai numa única versão. Os fatores de um período somam exatamente 1,0000.
 */
public record SliceCharge(UUID tariffVersionId, LocalDate from, LocalDate to, int days, BigDecimal factor,
                          List<CategoryCharge> categories) {

    public SliceCharge {
        categories = List.copyOf(categories);
    }

    public BigDecimal net() {
        return categories.stream().map(CategoryCharge::net).reduce(Money.ZERO, BigDecimal::add);
    }

    public BigDecimal gross() {
        return categories.stream().map(CategoryCharge::gross).reduce(Money.ZERO, BigDecimal::add);
    }

    public BigDecimal discount() {
        return categories.stream().map(CategoryCharge::discount).reduce(Money.ZERO, BigDecimal::add);
    }
}
