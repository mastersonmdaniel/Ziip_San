package br.com.ziipsan.operations.metering.rules;

/**
 * Regra posterior ao teto de ciclos estimados: o valor do parâmetro do tenant
 * {@code meter.after_cap_rule} (RAS-14, P2.5).
 *
 * <p>Os valores são as divergências que o plano registra entre as ERIs de SC para a "regra
 * depois da média" (aceite da P2.31, sobre o mapa de ERIs): ARIS e AGIR faturam o consumo
 * mínimo; a ARESC parcela o saldo em 3 vezes. Nenhum outro valor é presumido aqui: um terceiro
 * comportamento só entra neste enum com a norma que o define, registrada nas notas da frente F4.
 *
 * <p>A chave {@code meter.after_cap_rule} ainda <b>não existe</b> no catálogo global de
 * parâmetros (V3 do core só registra {@code meter.average_months} e
 * {@code meter.max_estimated_cycles}): o registro da chave está proposto no DDL da frente F4
 * para decisão do coordenador, porque sem definição no catálogo a tela do tenant recusa
 * qualquer valor proposto para ela.
 */
public enum AfterCapRule {
    /** Depois do teto, fatura o consumo mínimo da ligação (ARIS e AGIR, no mapa de ERIs de SC). */
    BILL_MINIMUM,
    /**
     * Depois do teto, o saldo é parcelado em 3 vezes (ARESC, no mapa de ERIs de SC). A medição
     * para de estimar e sinaliza o acerto; o parcelamento em si é executado pelo faturamento
     * sobre o saldo apurado na leitura real seguinte — esta regra não cria parcela.
     */
    SETTLE_BALANCE_IN_3_INSTALLMENTS
}
