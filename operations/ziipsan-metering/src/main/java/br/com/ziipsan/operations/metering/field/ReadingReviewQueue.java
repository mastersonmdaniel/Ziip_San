package br.com.ziipsan.operations.metering.field;

import java.util.UUID;

/**
 * Fila de revisao de leituras (ED-18): divergencia de OCR e demais criticas de campo
 * entram aqui para conferencia humana antes de virarem leitura faturada. A implementacao
 * de producao grava na fila de revisao do metering; a porta mantem a regra testavel.
 */
public interface ReadingReviewQueue {

    void enqueue(UUID tenantId, UUID taskId, String reason);
}
