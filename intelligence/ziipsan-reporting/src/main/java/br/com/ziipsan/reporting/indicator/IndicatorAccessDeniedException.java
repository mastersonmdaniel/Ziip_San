package br.com.ziipsan.reporting.indicator;

/**
 * Leitor tentando gravar definicao (aceite da P3.41: "leitores so leem e nao
 * gravam definicao"). Os leitores nomeados no aceite — objetivos da P3.31,
 * pagina inicial da P3.39, catalogo SINISA e painel da ED-48, fichas da
 * ED-77, paineis da ED-49 e sala de situacao da ED-93 — acessam o registro
 * com {@link IndicatorAccess#READER} e qualquer escrita deles e recusada
 * com esta excecao, sem efeito colateral.
 */
public class IndicatorAccessDeniedException extends RuntimeException {

    public IndicatorAccessDeniedException(String operation, String indicatorCode) {
        super("READER_CANNOT_WRITE: leitor nao pode executar " + operation
                + " no indicador " + indicatorCode);
    }
}
