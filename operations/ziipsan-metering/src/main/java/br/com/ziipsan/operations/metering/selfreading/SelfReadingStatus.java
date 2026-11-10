package br.com.ziipsan.operations.metering.selfreading;

/**
 * O estado final de uma submissao de autoleitura (II-7, E3). Nao ha
 * caminho silencioso: toda a submissao termina num destes tres estados,
 * sempre com motivos declarados no desfecho.
 */
public enum SelfReadingStatus {
    /** Score &gt;= limiar e prova geografica dentro do filtro: aceite automatico, segue para a critica da II-2. */
    ACCEPTED_FOR_CRITIQUE,
    /** Conferencia humana necessaria: entra na fila de revisao (estado, sem tela -- decisao 57). */
    PENDING_HUMAN_REVIEW,
    /** Sem leitura extraida porque nao ha motor OCR disponivel: aguarda a porta ter adaptador real (P-II7-OCR). */
    PENDING_OCR
}
