package br.com.ziipsan.core.agenda;

/**
 * Tipos de evento que NASCEM registrados com a P3.39 (aceite, literal): o
 * cronograma de faturamento da P2.5 e as auditorias da P3.31.
 *
 * <p>NAO registrados aqui, de proposito (aceite: "entram depois, pela tarefa
 * de cada fonte"): programacao de OS (ED-36), interrupcoes programadas
 * (ED-35), calibracoes (ED-51), preventivas (ED-56) e obrigacoes (ED-98).
 * Quando cada tarefa nascer, ELA registra o seu tipo e expoe a sua fonte
 * ({@link AgendaEventSource}); ate la a agenda simplesmente nao os exibe —
 * e a ausencia da fonte nao e erro (ver AgendaService da P3.39).
 */
public final class AgendaEventTypes {

    /** Cronograma mensal de faturamento por grupo (itens da V4_3 da P2.5). */
    public static final AgendaEventType BILLING_SCHEDULE = new AgendaEventType(
            "billing-schedule", "Cronograma de faturamento", "P2.5", "billing");

    /** Auditorias internas programadas (programa de auditoria da P3.31). */
    public static final AgendaEventType INTERNAL_AUDIT = new AgendaEventType(
            "internal-audit", "Auditorias internas", "P3.31", "platform");

    private AgendaEventTypes() {
    }
}
