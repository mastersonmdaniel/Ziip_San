package br.com.ziipsan.core.sac;

/**
 * Origem externa do protocolo de atendimento (ED-35; acceptance: Consumidor.gov.br,
 * Procon pelo SINDEC, agencia reguladora, Ministerio Publico e Defensoria).
 * Cada origem tem prazo proprio em {@code sac.external_channels} — nenhum prazo
 * vive neste enum.
 */
public enum ExternalOrigin {
    CONSUMER_GOV_BR,
    PROCON_SINDEC,
    REGULATOR_AGENCY,
    PUBLIC_PROSECUTOR,
    PUBLIC_DEFENDER
}
