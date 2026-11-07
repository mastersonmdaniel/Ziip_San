package br.com.ziipsan.commercial.billing.cycle;

/**
 * Situacao da referencia mensal (V4_3). OPEN admite comando de atividades e geracao
 * de contas; CLOSED e o encerramento do mes, depois de todos os grupos: nenhuma
 * geracao e admitida em referencia fechada. Os nomes sao os do CHECK de
 * {@code billing_references.status}, mantidos iguais por teste
 * (CycleMigrationConsistencyTest).
 */
public enum BillingReferenceStatus {
    OPEN,
    CLOSED
}
