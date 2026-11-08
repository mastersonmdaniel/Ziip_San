package br.com.ziipsan.reporting.forecast;

import java.math.BigDecimal;

/**
 * Observacao por faixa de atraso (ED-100): quanto foi faturado (base) e
 * quanto foi efetivamente recebido das contas que atingiram a faixa, na
 * janela observada. E a materia-prima da curva de recebimento — quem
 * chama agrega os eventos contabeis da ED-32 (faturado x pago por faixa);
 * esta classe nao le banco nem presume janela.
 */
public record BracketObservation(DelayBracket bracket, BigDecimal billedAmount, BigDecimal collectedAmount) {

    public BracketObservation {
        if (bracket == null) {
            throw new ForecastValidationException("BRACKET_REQUIRED", "faixa da observacao e obrigatoria");
        }
        ForecastMath.requireMoney("faturado da faixa " + bracket.code(), billedAmount);
        ForecastMath.requireMoney("recebido da faixa " + bracket.code(), collectedAmount);
        if (billedAmount.signum() == 0) {
            throw new ForecastValidationException("BRACKET_BILLED_ZERO",
                    "faixa " + bracket.code() + " com faturado zero: taxa indefinida, observacao recusada");
        }
        if (collectedAmount.compareTo(billedAmount) > 0) {
            throw new ForecastValidationException("COLLECTED_EXCEEDS_BILLED",
                    "faixa " + bracket.code() + ": recebido " + collectedAmount
                            + " maior que o faturado " + billedAmount);
        }
    }
}
