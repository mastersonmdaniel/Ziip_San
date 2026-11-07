package br.com.ziipsan.operations.metering.field;

/**
 * Veredito da comparacao OCR x digitado x historico (ED-18):
 *
 * <ul>
 *   <li>{@link #DISABLED} — {@code read.ocr_mode} desligado: nenhuma comparacao roda;</li>
 *   <li>{@link #MATCH} — OCR e digitado concordam dentro da tolerancia e o digitado e
 *       compativel com o historico (nao retrocede sem virada do mostrador);</li>
 *   <li>{@link #SUGGESTION} — modo SUGGEST com divergencia: o app sugere o valor do OCR,
 *       o leiturista decide, a leitura NAO vai a revisao por este motivo;</li>
 *   <li>{@link #REVIEW} — divergencia alem da tolerancia (modo REQUIRE) ou digitado
 *       incompativel com o historico: a leitura vai a fila de revisao.</li>
 * </ul>
 */
public enum OcrVerdict {
    DISABLED,
    MATCH,
    SUGGESTION,
    REVIEW
}
