package br.com.ziipsan.core.nondomestic;

/**
 * Modelo da cobranca complementar do esgoto nao domestico, conforme o
 * parametro {@code sewer.nondomestic_charge_model} (aceite da ED-83,
 * classe d). Os tres modelos nomeados no aceite:
 * <ul>
 *   <li>{@link #VOLUME_PERCENT} — percentual do volume de agua: o
 *       volume faturavel de efluente e o volume de agua multiplicado
 *       pelo percentual declarado, precificado pela tarifa unitaria
 *       de esgoto vigente entregue pelo chamador (motor da P2.5);</li>
 *   <li>{@link #POLLUTANT_LOAD} — por carga poluidora: soma das cargas
 *       (concentracao x volume) precificada pelo preco por kg
 *       declarado. SO pode ser usado quando a agencia o previr —
 *       a guarda esta em {@link NonDomesticChargeCalculator};</li>
 *   <li>{@link #FIXED_BY_CLASS} — taxa fixa por classe do gerador,
 *       lida da tabela de taxas do prestador entregue pelo chamador;
 *       classe sem taxa declarada recusa (nenhuma taxa inventada).</li>
 * </ul>
 * Nenhum percentual, preco ou taxa vive neste enum: sao dados do
 * tenant/agencia/prestador, entregues ao calculo.
 */
public enum NonDomesticChargeModel {
    VOLUME_PERCENT,
    POLLUTANT_LOAD,
    FIXED_BY_CLASS
}
