package br.com.ziipsan.commercial.fiscal.arquivo;

/**
 * Recusa de retenção imutável (P2.18): apagar ou sobrescrever a cópia retida
 * de um XML autorizado é recusado, sempre com esta exceção explícita — nunca
 * com silêncio, nunca com sucesso parcial. É a exceção que os testes do aceite
 * ("apagar ou sobrescrever é recusado") exercitam.
 */
public class RetencaoImutavelException extends RuntimeException {

    public RetencaoImutavelException(String mensagem) {
        super(mensagem);
    }
}
