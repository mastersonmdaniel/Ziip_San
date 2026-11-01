package br.com.ziipsan.commercial.billing.tariff;

import br.com.ziipsan.commercial.billing.math.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado da tarifa para um período de leitura: uma fatia por versão de tarifa vigente no período
 * (duas, quando a vigência troca no meio).
 *
 * @param totalDays dias do período, contando as duas pontas ({@code atual − anterior + 1})
 */
public record TariffCharge(LocalDate from, LocalDate to, int totalDays, List<SliceCharge> slices) {

    public TariffCharge {
        slices = List.copyOf(slices);
    }

    /** Valor a cobrar do período: soma das fatias. */
    public BigDecimal net() {
        return slices.stream().map(SliceCharge::net).reduce(Money.ZERO, BigDecimal::add);
    }

    public BigDecimal gross() {
        return slices.stream().map(SliceCharge::gross).reduce(Money.ZERO, BigDecimal::add);
    }

    public BigDecimal discount() {
        return slices.stream().map(SliceCharge::discount).reduce(Money.ZERO, BigDecimal::add);
    }
}
