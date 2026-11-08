package br.com.ziipsan.reporting.forecast;

import java.math.BigDecimal;

/**
 * Exposicao por faixa: o saldo a receber aberto na faixa (estoque de
 * recebiveis por aging na data-base da projecao). Entrada explicita —
 * quem chama le o saldo dos eventos contabeis da ED-32 por faixa.
 */
public record BracketExposure(String bracketCode, BigDecimal exposure) {

    public BracketExposure {
        if (bracketCode == null || bracketCode.isBlank()) {
            throw new ForecastValidationException("EXPOSURE_BRACKET_REQUIRED",
                    "faixa da exposicao e obrigatoria");
        }
        ForecastMath.requireMoney("exposicao da faixa " + bracketCode, exposure);
    }
}
