package br.com.ziipsan.commercial.billing.cobill;

/**
 * Tipos de servico cofaturaveis — enum ABERTO da generalizacao da ED-25
 * (Camada 3 da Arquitetura Tarifaria Unificada, ADR-0047/0048; desenho
 * da Camada 0, secao 3.1 item 1).
 *
 * <p>Este enum e a visao GENERALIZADA do tipo de servico: nasce ao lado
 * do enum vigente {@link CobillContract.ServiceKind} (que NAO e editado)
 * e liga-se a ele pelo de-para bijetivo de
 * {@link CobillServiceKindAdapter}. Enquanto o contrato vigente so
 * conhece residuos solidos, este enum nasce SO com
 * {@link #SOLID_WASTE} — nenhum outro valor e inventado.
 *
 * <p><b>Mecanismo de extensao (como um tipo novo entra):</b>
 * <ol>
 *   <li>so entra tipo de servico com CONTRATO REAL de cofaturamento
 *       (prestador identificado, rotulo discriminado e vigencia — o
 *       desenho de {@link CobillContract}); ate la o tipo fica
 *       {@code [A DEFINIR]} e NAO ganha constante neste enum;</li>
 *   <li>a constante nova nasce aqui E ganha o par no
 *       {@link CobillServiceKindAdapter}; enquanto nao houver
 *       correspondente no enum vigente do contrato, o adapter falha
 *       fechado (nunca mapeamento presumido);</li>
 *   <li>o calculo do valor do tipo novo e da calculadora propria dele
 *       (como {@link SolidWasteChargeCalculator} e de residuos solidos):
 *       esta camada nao generaliza formula nenhuma — generaliza o
 *       REGISTO do tipo, a ligacao ao catalogo (Camada 1/2) e as
 *       fronteiras contabil e fiscal.</li>
 * </ol>
 */
public enum CobillServiceKind {

    /** Residuos solidos — o servico entregue pela ED-25 (NR 13, arts. 46 a 56). */
    SOLID_WASTE
}
