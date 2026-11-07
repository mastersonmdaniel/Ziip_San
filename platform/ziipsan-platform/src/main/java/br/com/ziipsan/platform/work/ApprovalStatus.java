package br.com.ziipsan.platform.work;

/** Estado da tarefa de aprovação comum ({@code approval_task}). */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public boolean isPending() {
        return this == PENDING;
    }
}
