package br.com.ziipsan.platform.work;

/**
 * Estado do item de trabalho. {@code DONE} e {@code CANCELLED} são terminais (têm
 * {@code closed_at} e {@code close_reason}); {@code ESCALATED} continua aberto — o
 * escalonamento por prazo (wf.escalation_hours) muda o responsável de acompanhamento,
 * não encerra o trabalho.
 */
public enum WorkItemStatus {
    OPEN,
    IN_PROGRESS,
    ESCALATED,
    DONE,
    CANCELLED;

    public boolean isTerminal() {
        return this == DONE || this == CANCELLED;
    }

    public boolean isOpen() {
        return !isTerminal();
    }
}
