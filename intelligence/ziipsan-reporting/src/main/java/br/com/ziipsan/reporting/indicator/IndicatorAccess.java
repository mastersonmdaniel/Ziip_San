package br.com.ziipsan.reporting.indicator;

/** Papel de acesso ao registro unico de indicadores (P3.41). */
public enum IndicatorAccess {
    /** Define indicador, muda formula, fixa meta, fecha e reabre periodo. */
    EDITOR,
    /** So le: definicao, metas, fechamentos e semaforo (P3.31/P3.39/ED-48/ED-77/ED-49/ED-93). */
    READER
}
