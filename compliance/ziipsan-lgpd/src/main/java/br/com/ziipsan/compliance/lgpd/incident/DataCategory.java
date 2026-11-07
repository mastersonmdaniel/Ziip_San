package br.com.ziipsan.compliance.lgpd.incident;

/**
 * Categorias de dado da classificação por coluna da P0.11 (DAD-29), usadas para descrever o
 * ESCOPO do incidente: o registro do incidente aponta as colunas classificadas afetadas e o
 * escopo é o conjunto das suas classes — nunca texto livre inventado na hora do incidente.
 *
 * <p>Classes conforme o acceptance da P0.11 no plano v3.0: {@code ID, CT, LOC, FIN, SOC, SENS}
 * (catálogo {@code data_classification} por tabela e coluna). A conferir na integração contra
 * {@code docs/seguranca/CLASSIFICACAO-DADOS.md} (leitura bloqueada por rate limit do conector
 * nesta entrega — ver relatório).
 */
public enum DataCategory {
    /** Identificação. */
    ID,
    /** Contato. */
    CT,
    /** Localização. */
    LOC,
    /** Financeiro. */
    FIN,
    /** Social (programas/tarifa social). */
    SOC,
    /** Sensível (LGPD, art. 11). */
    SENS
}
