package br.com.ziipsan.commercial.billing.servicecatalog;

import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Item da tabela de preços de materiais (ED-34), com vigência por tenant. */
public record MaterialPriceItem(String code, String description, String unit,
                                BigDecimal unitPrice, LocalDate validFrom, LocalDate validTo) {
    public MaterialPriceItem {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo do material e obrigatorio");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("descricao do material e obrigatoria");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("unidade do material e obrigatoria");
        Objects.requireNonNull(unitPrice, "preco unitario e obrigatorio");
        if (unitPrice.signum() <= 0) throw new IllegalArgumentException("preco unitario deve ser positivo: " + unitPrice);
        unitPrice = Money.roundHalfUp(unitPrice);
        Objects.requireNonNull(validFrom, "inicio de vigencia e obrigatorio");
        if (validTo != null && validTo.isBefore(validFrom)) throw new IllegalArgumentException("vigencia invalida");
    }

    public boolean appliesOn(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }
}
