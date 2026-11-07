package br.com.ziipsan.reporting.domain;

import java.util.UUID;

/**
 * Porta da trilha de execucoes (correcao G4 pos-revisao): TODA geracao -
 * sincrona, assincrona enfileirada e agendada - grava uma linha em
 * report_executions. Antes nenhuma classe escrevia na tabela: ela era
 * morta, apesar de a migration a desenhar como trilha.
 */
public interface ReportExecutionRecorder {

    /** Abre a linha de execucao e devolve o id. */
    UUID start(Start start);

    /**
     * Atualiza o desfecho. {@code finishedAt} so e gravado nos estados
     * terminais (READY, FAILED, DENIED); QUEUED continua em aberto ate o
     * worker concluir a entrega.
     */
    void finish(UUID executionId, ReportExecution.Status status, Long rowCount,
                UUID generatedFileId, String error);

    record Start(UUID tenantId, UUID userId, String definitionCode, String format,
                 String paramsJson, boolean scheduled, ReportExecution.Status status,
                 String dataSource, int statementTimeoutMs, Long rowCount,
                 UUID generatedFileId, String error) {
    }
}
