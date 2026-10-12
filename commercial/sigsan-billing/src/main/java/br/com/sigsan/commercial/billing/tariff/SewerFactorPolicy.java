package br.com.sigsan.commercial.billing.tariff;

/**
 * Como o percentual de esgoto ({@code pcesgoto}) vira fator multiplicador do valor. A escolha é do
 * tenant e fica explícita em {@link SewerParameters}: não há padrão.
 *
 * <ul>
 *   <li>{@link #ROUNDED_TO_2_DECIMALS}: fator = pct/100 arredondado a 2 casas (HALF_UP). É o que o
 *       legado faz e o que o levantamento 02 descreve. Com 87,5% o fator vira 0,88 e a cobrança sai
 *       0,57% maior que o percentual contratado.</li>
 *   <li>{@link #EXACT}: fator = pct/100 sem arredondar; arredonda-se só o valor final.</li>
 * </ul>
 */
public enum SewerFactorPolicy {
    ROUNDED_TO_2_DECIMALS,
    EXACT
}
