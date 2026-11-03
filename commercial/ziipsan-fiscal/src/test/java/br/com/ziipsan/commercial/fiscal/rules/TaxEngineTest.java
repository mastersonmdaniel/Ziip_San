package br.com.ziipsan.commercial.fiscal.rules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Vetores do motor C# (FISCAL-PORTE-JAVA §5). Nenhum número aqui foi ajustado para passar:
 * o vetor de Florianópolis e o de IBS/CBS são os publicados no documento de porte.
 */
class TaxEngineTest {

    @Test
    void florianopolisVector() {
        TaxEngine.TaxResult result = TaxEngine.calculate(
                new BigDecimal("5440.00"), TaxEngine.standard2026Rates());
        assertEquals(new BigDecimal("108.80"), result.iss());
        assertEquals(new BigDecimal("81.60"), result.irrf());
        assertEquals(new BigDecimal("252.96"), result.csrf());
        // Liquido do vetor: bruto − IRRF − CSRF (o ISS e destacado, nao descontado — ver TaxEngine).
        assertEquals(new BigDecimal("5105.44"), result.net());
    }

    @Test
    void ibsCbsInformativeVector() {
        TaxEngine.TaxResult result = TaxEngine.calculate(
                new BigDecimal("10000.00"), TaxEngine.standard2026Rates());
        assertEquals(new BigDecimal("90.00"), result.cbsInformative());
        assertEquals(new BigDecimal("10.00"), result.ibsInformative());
    }

    @Test
    void halfEvenRoundingVector() {
        assertEquals(new BigDecimal("0.12"), TaxEngine.roundHalfEven(new BigDecimal("0.125")));
        assertEquals(new BigDecimal("0.14"), TaxEngine.roundHalfEven(new BigDecimal("0.135")));
    }

    @Test
    void issRangeTwoToFivePercent() {
        // ISSQN de 2% a 5% (documento de porte §1): o motor aceita qualquer aliquota do intervalo.
        TaxEngine.TaxRates rates = new TaxEngine.TaxRates(
                new BigDecimal("0.05"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
        assertEquals(new BigDecimal("50.00"),
                TaxEngine.calculate(new BigDecimal("1000.00"), rates).iss());
    }
}
