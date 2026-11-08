package br.com.ziipsan.commercial.billingrules;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Uma faixa progressiva da tarifa resolvida para campo (ED-19): cobre a largura
 * de {@code widthM3} m3 POR ECONOMIA acima do consumo minimo, a
 * {@code pricePerM3} por m3. A ultima faixa da lista e aberta
 * ({@code widthM3 == null}): absorve todo o restante.
 *
 * <p>As faixas chegam ao modulo ja resolvidas da versao de tarifa vigente da
 * P2.5 pelo montador do pacote da rota (servidor); o modulo nao le tarifa de
 * banco nenhum — ele e a aritmetica unica sobre o retrato (snapshot) recebido.
 */
public record TariffBracket(Integer widthM3, BigDecimal pricePerM3) {

    public TariffBracket {
        if (widthM3 != null && widthM3 <= 0) {
            throw new IllegalArgumentException("Largura da faixa deve ser positiva: " + widthM3);
        }
        Objects.requireNonNull(pricePerM3, "preco por m3 da faixa e obrigatorio");
        if (pricePerM3.signum() < 0) {
            throw new IllegalArgumentException("Preco por m3 negativo: " + pricePerM3);
        }
    }

    /** Faixa aberta (ultima): absorve todo o volume restante. */
    public static TariffBracket open(BigDecimal pricePerM3) {
        return new TariffBracket(null, pricePerM3);
    }

    public boolean isOpen() {
        return widthM3 == null;
    }
}
