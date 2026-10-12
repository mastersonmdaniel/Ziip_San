package br.com.sigsan.intelligence.losses.service;

/**
 * Faltam dados medidos para um cálculo de perdas (macromedição, faturamento, estimativa de perdas
 * aparentes).
 *
 * <p>Existe para que a ausência de dado seja um erro visível, e não um resultado. A versão anterior
 * do balanço hídrico completava as entradas com volumes fixos e gravava o balanço como rascunho: um
 * índice de perdas calculado sobre números inventados, pronto para ser lido como real.
 */
public class MissingInputDataException extends RuntimeException {

    public MissingInputDataException(String message) {
        super(message);
    }
}
