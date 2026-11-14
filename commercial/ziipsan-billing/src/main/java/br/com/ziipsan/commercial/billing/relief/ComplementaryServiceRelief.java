package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffConfigurationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * ISSQN em servicos complementares (NT 40/2026, item 6.4.4 — materia nova das
 * fontes primarias, ausente do desenho original):
 *
 * <ul>
 *   <li>onde houver ISSQN embutido no servico complementar, a desoneracao e
 *       {@code (1 - theta - a)} numa so operacao (a = aliquota municipal do
 *       ISSQN, parametro do tenant/municipio com proveniencia — nunca
 *       literal);</li>
 *   <li>tributos "por dentro" SOMAM-SE os factores, nunca se aplicam
 *       sucessivamente (NT 6.2.3.7);</li>
 *   <li>o ISSQN destaca-se depois, por agravamento:
 *       {@code destacado = valor desonerado x [a / (1 - a)]}.</li>
 * </ul>
 *
 * <p>Ressalvas da fonte: validacao junto a administracao tributaria municipal
 * (NT 6.4.4.8) e limitacao do leiaute da NFAg para o destaque (NT 6.4.5) —
 * registadas como pendencias, nao resolvidas aqui.
 */
public final class ComplementaryServiceRelief {

    private ComplementaryServiceRelief() {
    }

    /** Factor combinado "por dentro": {@code 1 - (theta + a)/100}. */
    public static BigDecimal combinedFactor(BigDecimal pisCofinsPct, BigDecimal issqnPct) {
        Objects.requireNonNull(pisCofinsPct, "theta e obrigatorio");
        Objects.requireNonNull(issqnPct, "aliquota do ISSQN e obrigatoria");
        BigDecimal sum = pisCofinsPct.add(issqnPct);
        if (sum.signum() < 0 || sum.compareTo(new BigDecimal("100")) >= 0) {
            throw new TariffConfigurationException(
                    "Soma de tributos por dentro fora de [0, 100): " + sum);
        }
        return BigDecimal.ONE.subtract(sum.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
    }

    /** Preco do servico complementar desonerado de PIS/Cofins E de ISSQN embutido. */
    public static BigDecimal relieveWithIssqn(BigDecimal price, BigDecimal pisCofinsPct,
                                              BigDecimal issqnPct, ReliefRoundingPolicy policy) {
        return RelievedVersionGenerator.relieveAmount(
                price, combinedFactor(pisCofinsPct, issqnPct), policy);
    }

    /**
     * Destaque do ISSQN por agravamento sobre o valor ja desonerado:
     * {@code valor x [a / (1 - a)]}, com {@code a} em percentual.
     */
    public static BigDecimal issqnHighlight(BigDecimal relievedValue, BigDecimal issqnPct) {
        Objects.requireNonNull(relievedValue, "valor desonerado e obrigatorio");
        Objects.requireNonNull(issqnPct, "aliquota do ISSQN e obrigatoria");
        if (issqnPct.signum() < 0 || issqnPct.compareTo(new BigDecimal("100")) >= 0) {
            throw new TariffConfigurationException("Aliquota do ISSQN fora de [0, 100): " + issqnPct);
        }
        BigDecimal a = issqnPct.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        BigDecimal grossUp = a.divide(BigDecimal.ONE.subtract(a), 10, RoundingMode.HALF_UP);
        return relievedValue.multiply(grossUp).setScale(2, RoundingMode.HALF_UP);
    }
}
