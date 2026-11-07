package br.com.ziipsan.commercial.billing.tariff;

/**
 * Origem de uma versão da estrutura tarifária (coluna
 * {@code tariff_versions.change_type}, V4_9; P2.29). Cada origem gera uma
 * <b>nova</b> versão: nenhuma origem edita a versão vigente no lugar.
 * Versões anteriores à P2.29 têm {@code change_type} nulo no banco (a
 * migration não reclassifica o passado).
 */
public enum TariffVersionChangeType {

    /** Primeira estrutura do prestador (sem versão de origem). */
    INITIAL,

    /** Reajuste por índice ou fórmula paramétrica, a partir da data-base. */
    READJUSTMENT,

    /** Revisão por ato da agência: os preços são os que o ato fixa. */
    REVIEW,

    /** Cópia de uma estrutura existente como ponto de partida da nova versão. */
    COPY
}
