package br.com.ziipsan.commercial.fiscal.arquivo;

/**
 * Recusa da guarda de origem do arquivamento local (P2.18): no ambiente local
 * só entram XML do simulador ou sintéticos assinados com certificado de teste.
 * XML da homologação com o e-CNPJ da Ziip/TGP (apagados ao fim de cada ciclo,
 * P3.7), XML em ambiente de homologação e XML de produção são recusados com
 * esta exceção, antes de qualquer leitura, gravação ou registro na cadeia.
 */
public class OrigemXmlRecusadaException extends RuntimeException {

    public OrigemXmlRecusadaException(String mensagem) {
        super(mensagem);
    }
}
