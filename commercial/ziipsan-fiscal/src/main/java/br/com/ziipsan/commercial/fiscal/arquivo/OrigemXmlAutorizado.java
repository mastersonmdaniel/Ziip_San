package br.com.ziipsan.commercial.fiscal.arquivo;

/**
 * Origem declarada do XML autorizado que pede arquivamento com retenção (P2.18).
 *
 * <p>O aceite da P2.18 separa três origens possíveis de um XML fiscal assinado no
 * ambiente local:
 *
 * <ul>
 *   <li>{@link #SIMULADOR} — XML produzido pelo simulador local (ambiente
 *       {@code SIMULATED} do dicionário fiscal);</li>
 *   <li>{@link #SINTETICO_CERTIFICADO_TESTE} — XML sintético assinado com
 *       certificado de teste (nunca um certificado real de cliente nem o e-CNPJ
 *       real da Ziip/TGP);</li>
 *   <li>{@link #HOMOLOGACAO_ECNPJ_ZIIP_TGP} — XML da homologação na SEFAZ
 *       ({@code tpAmb=2}) assinado com o e-CNPJ real da Ziip/TGP (P2.6/P3.7).
 *       Estes XML vivem só na área separada da homologação e são
 *       <b>apagados ao fim de cada ciclo</b> (P3.7): <b>nunca</b> entram no
 *       armazenamento com retenção desta tarefa.</li>
 * </ul>
 *
 * <p>A origem é declarada por quem chama (o ciclo que produziu o XML sabe com
 * que certificado assinou); o serviço de arquivamento a cruza com o ambiente do
 * dicionário e recusa qualquer combinação fora das duas primeiras origens em
 * ambiente simulado — ver {@link XmlAutorizadoRetencaoService}.
 */
public enum OrigemXmlAutorizado {
    SIMULADOR,
    SINTETICO_CERTIFICADO_TESTE,
    HOMOLOGACAO_ECNPJ_ZIIP_TGP
}
