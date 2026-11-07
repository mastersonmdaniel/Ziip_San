package br.com.ziipsan.commercial.billing.cycle;

/**
 * Situacao de um item do cronograma (V4_3), amarrada as datas do item pelo CHECK do
 * banco: PENDING sem comando; COMMANDED com data comandada e sem realizada; DONE com
 * as duas; CANCELLED sem realizada. Os nomes sao os do CHECK de
 * {@code cycle_schedule_items.status}, mantidos iguais por teste
 * (CycleMigrationConsistencyTest).
 */
public enum ScheduleItemStatus {
    PENDING,
    COMMANDED,
    DONE,
    CANCELLED
}
