package br.com.ziipsan.operations.metering.rules;

/**
 * Motivo de recusa de um cálculo. Cada código é um dado que faltou ou veio incoerente: o cálculo
 * nunca preenche o vazio com um valor "razoável". Quem chama (o job por rota) captura a exceção,
 * grava o motivo e segue para a próxima unidade, em vez de derrubar o lote.
 */
public enum MeteringErrorCode {
    /** Parâmetro ou campo obrigatório nulo, ou valor fora do domínio (negativo, zero onde se exige positivo). */
    INVALID_INPUT,
    /** Leitura anterior faturada ausente: sem ela não há diferença de leituras. */
    MISSING_PREVIOUS_READING,
    /** Hidrômetro instalado dentro do período sem a leitura de instalação. */
    MISSING_INSTALLATION_READING,
    /** Datas incoerentes (leitura anterior depois da atual, ou dias negativos). */
    INVALID_DATES,
    /** Histórico com mês igual ou posterior à referência calculada. */
    HISTORY_NOT_BEFORE_REFERENCE,
    /** Dois registros de histórico para o mesmo mês. */
    DUPLICATE_HISTORY_MONTH,
    /** Unidade sem nenhuma economia. */
    NO_ECONOMIES,
    /** Efeito de anormalidade que não se aplica ao caso (ex.: "maior entre média e medido" sem leitura). */
    EFFECT_NOT_APPLICABLE,
    /** Cálculo de não medido por área sem a área construída. */
    MISSING_BUILT_AREA,
    /** Cálculo de não medido por área sem o número de economias virtuais. */
    MISSING_VIRTUAL_ECONOMIES,
    /** Nenhuma linha da tabela de consumo mínimo cobre o valor (área, pontos ou moradores). */
    NO_CONSUMPTION_RULE_FOR_VALUE,
    /** O ajuste de múltiplas economias deixaria resto negativo (consumo inflado). */
    INCONSISTENT_ECONOMY_ADJUSTMENT,
    /** Esgoto sem água, sem poço e sem volume de referência. */
    SEWER_VOLUME_UNAVAILABLE
}
