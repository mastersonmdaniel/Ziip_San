package br.com.ziipsan.commercial.billing.tariff;

/**
 * Modelo do componente fixo por disponibilidade de infraestrutura
 * ({@code tariff.fixed_part_model}, P2.5/RAS-22): como a TFDI da
 * Res. ARESC 105/2018 (Lei 11.445/2007, art. 45) é formada quando ela é
 * cobrada de forma avulsa, fora do consumo — em especial da unidade
 * <b>não conectada</b> à rede, que não tem consumo mas tem a infraestrutura
 * disponível.
 *
 * <ul>
 *   <li>{@link #MINIMUM_CHARGE_PER_ECONOMY}: a parte fixa é a parcela fixa
 *       (tarifa mínima) da regra da categoria, por economia — o mesmo valor
 *       que a {@link TariffEngine} cobra como linha {@code MINIMUM} de uma
 *       unidade conectada sem excedente.</li>
 *   <li>{@link #NONE}: o tenant não tem componente fixo avulso; unidade não
 *       conectada não é cobrada por disponibilidade.</li>
 * </ul>
 *
 * Nenhum valor nasce aqui: o montante vem sempre da regra tarifária do
 * tenant ({@link CategoryRule#minCharge()}).
 */
public enum FixedPartModel {
    MINIMUM_CHARGE_PER_ECONOMY,
    NONE
}
