package br.com.ziipsan.operations.metering.rules;

/**
 * Mudança de situação da unidade provocada por uma ocorrência de leitura (JOR-10).
 *
 * <p>Só entram aqui as mudanças que o módulo de medição já representa hoje: a suspensão da
 * leitura do catálogo ({@code reading_anomalies.suspend_reading_months} /
 * {@code suspend_after_occurrences}, V5_1). Outras situações da unidade (corte, supressão,
 * inativação) pertencem ao cadastro (registry, P1.3/P1.41) e não são inventadas neste módulo:
 * quando uma ocorrência do tenant precisar provocá-las, a ligação é trabalho de integração
 * registrado nas notas da frente F4, não um valor novo presumido aqui.
 */
public enum UnitSituationChange {
    /** A ocorrência não muda a situação da unidade. */
    NONE,
    /**
     * A leitura da unidade fica suspensa: por {@code suspendReadingMonths} meses, ao atingir
     * {@code suspendAfterOccurrences} ocorrências (os dois campos do catálogo; pelo menos um
     * deles precisa estar preenchido para esta mudança valer).
     */
    SUSPEND_READING
}
