package br.com.ziipsan.commercial.billing.catalog;

/**
 * Dono da cobranca registrada no catalogo unico (proposta de arquitetura
 * tarifaria, secao 2.1): toda cobranca que pode entrar numa conta carrega
 * quem e o seu dono, porque a segregacao contabil e os portoes de corte
 * dependem dessa distincao na origem.
 *
 * <ul>
 *   <li>{@link #OWN} — cobranca propria do prestador de saneamento
 *       (tarifa regulada, preco publico de servico ou taxa propria);</li>
 *   <li>{@link #THIRD_PARTY} — cobranca de terceiro cobrada na conta
 *       (convenio/cofaturamento, no espirito da ED-25): o valor e do
 *       parceiro, a autarquia nao o versiona nem o reajusta, e a
 *       cobranca exige a identificacao do prestador/convenio em
 *       {@link ChargeDefinition#thirdPartyProviderId()}.</li>
 * </ul>
 */
public enum ChargeOwner {
    OWN,
    THIRD_PARTY
}
