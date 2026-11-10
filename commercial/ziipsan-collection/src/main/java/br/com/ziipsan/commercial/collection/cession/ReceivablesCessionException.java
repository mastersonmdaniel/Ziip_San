package br.com.ziipsan.commercial.collection.cession;

/**
 * ED-103 (decisao 62 do dono) — erro tipificado da mecanica de antecipacao
 * de recebiveis e conta vinculada. Produtor (execucao ED-103, decisao 62):
 * sessao 66676ee1-5190-4d31-b6f5-21ed3322dfe5.
 *
 * <p>Cada recusa da mecanica carrega um {@code code} estavel, para o
 * chamador distinguir a causa sem analisar a mensagem: transicao ilegal,
 * trava de dupla cessao, exportacao sem destino, divergencia de
 * reconciliacao nunca e erro — e sinalizada no relatorio, nao lancada.
 */
public class ReceivablesCessionException extends RuntimeException {

    private final String code;

    public ReceivablesCessionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
