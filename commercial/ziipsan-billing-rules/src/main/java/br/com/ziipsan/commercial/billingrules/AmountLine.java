package br.com.ziipsan.commercial.billingrules;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Linha de valor ja resolvida que entra na conta de campo (ED-19): um debito
 * ou um credito selecionado para esta emissao pelo montador do pacote da rota
 * (servidor, a partir dos recebiveis da P2.5). O modulo so soma — selecao,
 * parcelamento e origem sao de quem monta o pacote.
 */
public record AmountLine(String code, BigDecimal amount) {

    public AmountLine {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Codigo da linha de valor e obrigatorio");
        }
        Objects.requireNonNull(amount, "valor da linha e obrigatorio");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Valor da linha negativo (" + code + "): " + amount);
        }
    }
}
