package br.com.ziipsan.commercial.billing.cycle;

/**
 * Codigos das atividades do ciclo que o aceite da P2.5 implica, e so eles: leitura,
 * transmissao da rota de leitura, faturamento e fechamento. Nenhuma outra atividade
 * e inventada aqui; atividade nova do produto entra primeiro no aceite e so depois
 * neste enum e em {@link ProductActivityCatalog}.
 */
public enum CycleActivityCode {
    READING,
    TRANSMISSION,
    BILLING,
    CLOSING
}
