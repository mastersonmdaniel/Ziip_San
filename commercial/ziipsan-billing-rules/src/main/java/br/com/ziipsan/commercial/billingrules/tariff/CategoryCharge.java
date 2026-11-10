package br.com.ziipsan.commercial.billingrules.tariff;

import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Resultado do cálculo para uma categoria/subcategoria: o consumo atribuído a ela e as linhas
 * (parcela fixa e faixas). O valor da categoria é a soma das linhas, o que garante
 * {@code Σ faixas = categoria} por construção.
 *
 * @param consumption m³ atribuídos à categoria (inclui a parcela fixa), somadas as economias
 */
public record CategoryCharge(UUID categoryId, UUID subcategoryId, int economies, int socialEconomies,
                             int consumption, List<ChargeLine> lines) {

    public CategoryCharge {
        lines = List.copyOf(lines);
    }

    public BigDecimal gross() {
        return lines.stream().map(ChargeLine::gross).reduce(Money.ZERO, BigDecimal::add);
    }

    public BigDecimal discount() {
        return lines.stream().map(ChargeLine::discount).reduce(Money.ZERO, BigDecimal::add);
    }

    /** Valor a cobrar da categoria (bruto menos desconto social). */
    public BigDecimal net() {
        return gross().subtract(discount());
    }
}
